package com.vrgames.deepsea.game

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pico.spatial.core.math.Vector3
import com.vrgames.deepsea.core.Dive
import com.vrgames.deepsea.core.Ev
import com.vrgames.deepsea.core.Solver
import com.vrgames.deepsea.core.Stage
import com.vrgames.deepsea.core.Stages
import com.vrgames.deepsea.render.Music
import com.vrgames.deepsea.render.SeaView
import com.vrgames.deepsea.render.Sfx
import com.vrgames.deepsea.render.Sound
import com.vrgames.deepsea.render.Target

/**
 * State the stage, its panels and the windows share. [play] is the dive under way; the frame loop
 * steps it and turns its events into notes, sounds and flashes. Stars and the album are saved.
 */
object Game {
    private const val PREFS = "deepsea"
    private const val TAG = "DeepGame"

    enum class Phase { CARD, INTRO, PLAYING, RESULT, SHOW }

    val lock = Any()
    private var prefs: android.content.SharedPreferences? = null
    val best = LinkedHashMap<String, Int>()
    /** Species photographed at least once. */
    val seen = LinkedHashSet<String>()
    var play: Dive? = null; private set
    var playVersion by mutableIntStateOf(0); private set
    var stage by mutableStateOf(Stages.ALL[0]); private set
    var phase by mutableStateOf(Phase.CARD)
    var version by mutableIntStateOf(0); private set
    var note by mutableStateOf<String?>(null)
    var noteVersion by mutableIntStateOf(0); private set
    var window by mutableStateOf<String?>(null); private set
    private var windowCopy: Any? = null
    var closeWindow: (String) -> Unit = {}
    var menuOpen by mutableStateOf(false)
    var confirmRestart by mutableStateOf(false)
    var musicOn by mutableStateOf(true); private set
    var faceRequest by mutableIntStateOf(0)
    var view: SeaView? = null
    var sound: Sound? = null
    var music: Music? = null
    var trophiesBefore = 0
    var bot: Solver? = null
    var timeScale = 1.0
    var debugView by mutableStateOf<String?>(null)
    var debugOpen by mutableStateOf<String?>(null)
    private var lastSecond = -1
    private var showing = false

    val stars get() = best.values.sum()
    val maxStars get() = Stages.ALL.size * 3
    val bronze get() = Stages.ALL.all { (best[it.id] ?: 0) > 0 }
    val silver get() = stars >= maxStars * 2 / 3
    val gold get() = stars >= maxStars
    val trophies get() = if (gold) 3 else if (silver) 2 else if (bronze) 1 else 0

    fun init(context: Context) {
        appContext = context.applicationContext
        if (prefs != null) return
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { p ->
            musicOn = p.getBoolean("music", true)
            p.getString("best", "")!!.split(",").filter { it.contains(":") }.forEach {
                val (k, v) = it.split(":")
                best[k] = v.toIntOrNull() ?: 0
            }
            seen += p.getString("seen", "")!!.split(",").filter { it.isNotEmpty() }
        }
        Log.i(TAG, "stars $stars/$maxStars album ${seen.size}")
        AutoPilot.register(context)
    }

    fun say(text: String) {
        note = text
        noteVersion++
    }

    fun open(s: Stage): Boolean {
        val k = Stages.ALL.indexOf(s)
        return k == 0 || (best[Stages.ALL[k - 1].id] ?: 0) > 0 || (best[s.id] ?: 0) > 0
    }

    fun start() = intro(Stages.ALL.firstOrNull { (best[it.id] ?: 0) == 0 } ?: Stages.ALL.last())

    private fun fresh(s: Stage) {
        stage = s
        synchronized(lock) { play = Dive(s) { it in seen } }
        bot = null
        playVersion++
    }

    fun intro(s: Stage) {
        if (!open(s)) {
            say("这次下潜还没开放：先完成前面的。")
            return
        }
        fresh(s)
        phase = Phase.INTRO
        version++
    }

    fun begin() {
        showing = false
        fresh(stage)
        phase = Phase.PLAYING
        music?.resume()
        Log.i(TAG, "begin ${stage.id}")
        version++
    }

    fun retry() = begin()

    fun next() {
        val k = Stages.ALL.indexOf(stage)
        if (k + 1 < Stages.ALL.size) intro(Stages.ALL[k + 1]) else toCard()
    }

    fun toCard() {
        phase = Phase.CARD
        version++
    }

    /** A pinch on the tank. On water right beside something, it means the thing. */
    fun tap(t: Target) {
        val p = play ?: return
        if (phase != Phase.PLAYING) return
        synchronized(lock) {
            when (t) {
                is Target.OnThing -> p.act(t.key)
                is Target.Water -> {
                    val near = p.things.filter { it.lit && (it.at - t.at).len() < MAGNET }.minByOrNull { (it.at - t.at).len() }
                    if (near != null) p.act(near.key) else p.steer(t.at)
                    sound?.play(Sfx.PING)
                }
            }
        }
        version++
    }

    fun lamp() {
        synchronized(lock) { play?.toggleLamp() }
        version++
    }

    fun tick(dt0: Double) {
        val dt = dt0 * timeScale
        val p = play ?: return
        if (phase == Phase.SHOW) view?.show(dt)
        if (phase != Phase.PLAYING) return
        val b = bot
        val evs = synchronized(lock) { if (b != null) p.step(dt) { b.update(com.vrgames.deepsea.core.Dive.STEP) } else p.step(dt) }
        var changed = false
        for (e in evs) {
            changed = true
            when (e) {
                is Ev.Note -> say(e.text)
                is Ev.Photo -> onPhoto(p, e)
                is Ev.Sample -> { sound?.play(Sfx.SAMPLE, here(p, e.key)); p.thing(e.key)?.let { view?.flashAt(it.at); say("采到了：${it.sp.name}。") } }
                is Ev.Lit -> sound?.play(Sfx.LIT, here(p, e.key))
                is Ev.Bump -> {}
                is Ev.ZoneIn -> { sound?.play(Sfx.ZONE); say("${e.zone.title}（${e.zone.meters}）。${e.zone.color}") }
                Ev.Goal -> sound?.play(Sfx.GOAL)
                Ev.Low -> sound?.play(Sfx.LOW)
                Ev.Won -> finish(true)
                Ev.Lost -> finish(false)
            }
        }
        val second = p.time.toInt()
        if (changed || second != lastSecond) version++
        lastSecond = second
    }

    private fun here(p: Dive, key: String) = p.thing(key)?.let { Vector3(it.at.x.toFloat(), it.at.y.toFloat(), 0f) } ?: Vector3(0f, 0.5f, 0f)

    private fun onPhoto(p: Dive, e: Ev.Photo) {
        val t = p.thing(e.key) ?: return
        sound?.play(Sfx.PHOTO, here(p, e.key))
        view?.flashAt(t.at)
        if (seen.add(t.sp.id)) save()
        // Every first meeting is an event: the creature performs, and its card and voice come up.
        if (e.first) {
            view?.perform(e.key)
            meeting = t.sp
            speak(t.sp.id)
        } else say("又拍到一张${t.sp.name}。")
    }

    /** The creature just met for the first time, whose card is up. */
    var meeting by mutableStateOf<com.vrgames.deepsea.core.Species?>(null)
    /** The narration clip now playing, or null. */
    var speaking by mutableStateOf<String?>(null); private set
    private var narrator: android.media.MediaPlayer? = null
    private var appContext: Context? = null

    fun speak(id: String) {
        stopNarration()
        val c = appContext ?: return
        runCatching {
            val fd = c.assets.openFd("narration/$id.ogg")
            narrator = android.media.MediaPlayer().apply {
                setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                fd.close()
                setOnCompletionListener { stopNarration() }
                prepare()
                start()
            }
            speaking = id
            music?.ducked = true
        }.onFailure { Log.i(TAG, "narration $id: ${it.message}") }
    }

    fun stopNarration() {
        runCatching { narrator?.stop() }
        runCatching { narrator?.release() }
        narrator = null
        speaking = null
        music?.ducked = false
    }

    private fun finish(won: Boolean) {
        stopNarration()
        meeting = null
        val p = play ?: return
        trophiesBefore = trophies
        if (won) {
            val s = p.stars()
            if (s > (best[p.stage.id] ?: 0)) best[p.stage.id] = s
        }
        save()
        phase = Phase.RESULT
        sound?.play(if (won) Sfx.FANFARE else Sfx.LOSE)
        Log.i(TAG, "finish ${p.stage.id} won=$won stars=${p.stars()} battery=${"%.1f".format(p.battery)} time=${"%.1f".format(p.time)} album=${seen.size}")
        version++
    }

    /** The gold trophy's show: every creature lit, the sea full of sparks. */
    fun show() {
        if (!gold && !AutoPilot.debug) return
        fresh(Stages.ALL.last())
        synchronized(lock) { play?.things?.forEach { it.lit = true } }
        phase = Phase.SHOW
        sound?.play(Sfx.FANFARE)
        say("万灯之海！")
        version++
    }

    fun endShow() {
        fresh(Stages.ALL[0])
        toCard()
    }

    private fun save() {
        prefs?.edit()?.putString("best", best.entries.joinToString(",") { "${it.key}:${it.value}" })?.putString("seen", seen.joinToString(","))?.apply()
    }

    fun restart() {
        best.clear()
        seen.clear()
        save()
        fresh(Stages.ALL[0])
        phase = Phase.CARD
        version++
    }

    fun toggleMusic() {
        musicOn = !musicOn
        music?.muted = !musicOn
        prefs?.edit()?.putBoolean("music", musicOn)?.apply()
    }

    fun grant(n: Int) {
        for (s in Stages.ALL) best[s.id] = n
        save()
        version++
    }

    fun windowShown(id: String, copy: Any? = null) {
        window = id
        if (copy != null) windowCopy = copy
        version++
    }

    fun windowGone(id: String, copy: Any? = null) {
        if (copy != null && copy !== windowCopy) return
        if (window == id) window = null
        version++
    }

    private const val MAGNET = 0.08
}
