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
    var version by mutableIntStateOf(0)
    var note by mutableStateOf<String?>(null)
    /** The second view: inside the sub (the tank in the lab is the first, and the default). */
    var cockpit by mutableStateOf(false)
    private var cockpitTold = false
    private var zoneTold = false

    /**
     * The cabin view is for watching the animal the sub is following (device test 2026-11-12: from inside there is no
     * overview to find anything): it opens once an animal has been photographed in the overview and is being followed.
     */
    val canCabin get() = true
    private var cabinHintDue = false

    /** v0.5: in the deep (the Full Space stage is open). */
    var diving by mutableStateOf(false)
    /** Asks the console to open the deep, or the deep to close (each window acts on its own). */
    var diveRequest by mutableIntStateOf(0)
    var surfaceRequest by mutableIntStateOf(0)

    /** Set by the cabin while it is open: goes back outside at once (v0.5.1, device 2026-11-19: the button sometimes did nothing). */
    var surfaceNow: (() -> Unit)? = null
    /** Test hook: a pull of the hatch handle by this many metres (the cabin plays it through its drag code). */
    @Volatile var debugHatch: Double? = null

    /** 「回到舱外视角」 from anywhere: the cabin's buttons, the hatch, the console. */
    fun requestSurface() {
        Log.i(TAG, "back outside requested (cabin ${if (surfaceNow != null) "open" else "not open"})")
        surfaceNow?.invoke() ?: run { surfaceRequest++ }
        version++
    }

    /** The console window's own minimize / restore (it sets these): folded away in the cabin, back outside. */
    var minimizeConsole: () -> Boolean = { false }
    var restoreConsole: () -> Boolean = { false }
    /** The console window is up (not closed). */
    var consoleOpen by mutableStateOf(false)
    private val windowHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * The console and the tank back after the cabin closed: the system can refuse a restore while it is still switching, so
     * try again. v0.5.2: the tank too (it folds away in the cabin, which has its own tank on the right); if it had to be
     * closed instead, it opens again to the right of the console (its old place).
     */
    fun afterDive() {
        cabinConsoleCopy = false
        var tankBack = false
        var tankAsked = false
        var consoleAsked = false
        val times = listOf(300L, 1000L, 2200L, 3600L)
        for (ms in times) windowHandler.postDelayed({
            if (diving || quitting) return@postDelayed
            if (consoleOpen) { if (restoreConsole()) consoleMinimized = false }
            else if (ms >= 1000L && !consoleAsked) { consoleAsked = true; Log.i(TAG, "console was closed: opened again ${openWindow(com.vrgames.deepsea.CONSOLE_ID)}") }
            if (tankClosedForCabin) {
                if (!tankOpen && !tankAsked && ms >= 1000L) { tankAsked = true; Log.i(TAG, "tank reopened right of the console: ${openWindow(com.vrgames.deepsea.TANK_ID)}") }
            } else if (tankOpen && !tankBack) {
                val ok = restoreTank()
                if (ok) { tankBack = true; tankMinimized = false }
                if (ok || ms == times.last()) Log.i(TAG, "tank restored: $ok")
            }
        }, ms)
    }

    // ---- v0.5.2: the windows while in the cabin ----
    /** The tank window's own minimize / restore / close (it sets these), like the console's. */
    var minimizeTank: () -> Boolean = { false }
    var restoreTank: () -> Boolean = { false }
    var closeTankForCabin: () -> Unit = {}
    /** The console / the tank window folded away (minimized). */
    var consoleMinimized by mutableStateOf(false)
    var tankMinimized by mutableStateOf(false)
    /** The tank would not minimize and was closed for the cabin: it opens again outside (and its close is not a quit). */
    @Volatile var tankClosedForCabin = false
    /** The console window would not come back in the cabin: a copy of it stands at the cabin's left. */
    var cabinConsoleCopy by mutableStateOf(false)
    var consoleBusy by mutableStateOf(false)
    /** The cabin asked a window to show at this time (ms): a stop of the cabin just then is not taken for Home. */
    @Volatile var cabinWindowAskAt = 0L
    /** Every open window (and the cabin) registers how it opens another window. */
    val windowOpeners = linkedMapOf<String, (String) -> Unit>()
    /** The console as the cabin's 「打开控制台 / 收起控制台」 sees it. */
    val consoleUp get() = (consoleOpen && !consoleMinimized) || cabinConsoleCopy

    fun openWindow(id: String): Boolean {
        val o = (if (diving) windowOpeners[com.vrgames.deepsea.DIVE_ID] else null) ?: windowOpeners.values.firstOrNull() ?: return false
        return runCatching { o(id) }.onFailure { Log.i(TAG, "open $id: ${it.message}") }.isSuccess
    }

    private fun retry(tries: Int, gapMs: Long, attempt: () -> Boolean, done: (Boolean) -> Unit) {
        fun go(k: Int) {
            val ok = runCatching(attempt).getOrDefault(false)
            if (ok || k + 1 >= tries) done(ok) else windowHandler.postDelayed({ go(k + 1) }, gapMs)
        }
        go(0)
    }

    /** Into the cabin, once the console has folded away: the tank window folds away too; if it will not, it closes. */
    fun foldTank() {
        if (!tankOpen) { Log.i(TAG, "tank minimized: false (the tank window is not open)"); return }
        retry(3, 400L, { diving && minimizeTank() }) { ok ->
            if (ok) tankMinimized = true
            Log.i(TAG, "tank minimized: $ok")
            if (!ok && diving && tankOpen && !quitting) {
                Log.i(TAG, "tank would not minimize: closed for the cabin, it opens again outside")
                tankClosedForCabin = true
                closeTankForCabin()
            }
        }
    }

    /**
     * The cabin's 「打开控制台 / 收起控制台」: the console window minimized or restored (3 tries, 400 ms apart). Closed, it is
     * opened again; if it will not come back, a copy of it stands in the cabin on the left.
     */
    fun toggleConsole() {
        if (consoleBusy) return
        cabinWindowAskAt = android.os.SystemClock.uptimeMillis()
        if (consoleUp) {
            if (cabinConsoleCopy) {
                cabinConsoleCopy = false
                Log.i(TAG, "console off: true (the copy in the cabin put away)")
                version++
                if (!consoleOpen || consoleMinimized) return
            }
            consoleBusy = true
            retry(3, 400L, { minimizeConsole() }) { ok ->
                consoleBusy = false
                Log.i(TAG, "console off: $ok")
                if (!ok) say("控制台窗口没能收起：可以点它自己的收起按钮。")
                version++
            }
        } else if (!consoleOpen) {
            val ok = openWindow(com.vrgames.deepsea.CONSOLE_ID)
            Log.i(TAG, "console on: $ok (the console window had been closed: opened again)")
            if (!ok) { cabinConsoleCopy = true; say("控制台窗口打不开：左边放了一份控制台。") }
            version++
        } else {
            consoleBusy = true
            retry(3, 400L, { restoreConsole() }) { ok ->
                consoleBusy = false
                Log.i(TAG, "console on: $ok")
                if (!ok) {
                    cabinConsoleCopy = true
                    Log.i(TAG, "console copy shown in the cabin")
                    say("控制台窗口没能叫回来：左边放了一份控制台。")
                }
                version++
            }
        }
        version++
    }

    /** Test hook "console on|off": the cabin's button, if it is not that way already. */
    fun debugConsole(on: Boolean) {
        if (consoleUp == on) { Log.i(TAG, "console ${if (on) "on" else "off"}: true (already)"); return }
        toggleConsole()
    }

    /** Set by the cabin while it closes on purpose (its surfacing), cleared once it is gone: any other end is a quit. */
    @Volatile var leavingCabin = false
    /** v0.5.2: the cabin's own tank, on its right (the same dive, the same sub as the tank window). */
    var cabinTank: SeaView? = null
    /** 「航速 x.x 节 →」 for the cabin's screen. */
    var cabinSpeed by mutableStateOf("航速 0.0 节")
    /** Test hooks the cabin sets: Home pressed in it; the eyes measured again (the head reading zeros for some seconds). */
    var debugHome: () -> Unit = { Log.i(TAG, "home: not in the cabin") }
    var debugReplace: (Double) -> Unit = { Log.i(TAG, "replace: not in the cabin") }

    /** Test hook "cabintap x,y": a pinch on the cabin's tank at that point of the sea. */
    fun cabinTap(at: com.vrgames.deepsea.core.P) {
        val t = cabinTank
        if (t == null || !diving) { Log.i(TAG, "cabintap: no tank in the cabin (not diving)"); return }
        val w = t.waterAt(at)
        Log.i(TAG, "cabintap ${at.x},${at.y}: ${if (w != null) "water cell ${"%.2f".format(w.at.x)},${"%.2f".format(w.at.y)}" else "no cell"}")
        if (w != null) tap(w)
        Log.i(TAG, "cabintap: sub heads to ${play?.sub?.target}")
    }

    /** 退出 confirmed, or the console or the tank closed with its own close button: every window of the app goes. */
    var quitting = false; private set
    /** The app started again from the launcher: a quit before is over. */
    fun reopened() { quitting = false }
    /** The photo opened large from the 照片屏 (v0.5.1), or null. */
    var viewing by mutableStateOf<String?>(null)
    /**
     * v0.5.1 (emulator 2026-11-19: after 退出 the tank and console stayed; `appTasks` listed only one task): every open
     * window (and the cabin) registers how it closes itself, and quitting closes each one before finishing the tasks.
     */
    val windowClosers = linkedMapOf<String, () -> Unit>()

    fun quitApp(why: String) {
        if (quitting) return
        quitting = true
        val closers = windowClosers.values.toList()
        windowClosers.clear()
        Log.i(TAG, "quit ($why): closing ${closers.size} window(s), then every task")
        stopNarration()
        closers.forEach { runCatching { it() } }
        val am = appContext?.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        am?.appTasks?.forEach { t -> runCatching { t.finishAndRemoveTask() } }
    }

    fun toggleCockpit() {
        val f = play?.follow
        // v0.5: the cabin is the deep in Full Space; it can be entered any time (it follows the sub, or the animal).
        if (!cockpit) {
            // v0.5.2: from the level card the dive starts first (the cabin used to open and close again at once:
            // it only stays while a dive is being played); from the title or result card it is explained instead.
            when (phase) {
                Phase.INTRO -> begin()
                Phase.PLAYING -> Unit
                else -> { say("先开始一关潜航，再进入舱内视角。"); version++; return }
            }
            diveRequest++; version++; return
        }
        if (diving) { requestSurface(); return }
        cockpit = !cockpit
        Log.i(TAG, "cabin ${if (cockpit) "in" else "out"} following ${f?.key}")
        sound?.play(Sfx.ZONE)
        say(if (cockpit) "进入舱内视角。潜水器跟着${f?.sp?.name ?: "它"}游，窗外的海随深度变暗；看够了，点面前操纵台上的「回到舱外视角」。" else "回到舱外视角，看整只水缸。")
        version++
    }
    var noteVersion by mutableIntStateOf(0); private set
    var window by mutableStateOf<String?>(null); private set
    private var windowCopy: Any? = null
    var closeWindow: (String) -> Unit = {}
    var menuOpen by mutableStateOf(false)
    var confirmRestart by mutableStateOf(false)
    var musicOn by mutableStateOf(true); private set
    var faceRequest by mutableIntStateOf(0)
    var view: SeaView? = null
    /** v0.5: the tank's volume is up. */
    var tankOpen by mutableStateOf(false)
    /** v0.5: 退出 — every window of the app closes. */
    var quitAll: () -> Unit = {}
    var sound: Sound? = null
    var music: Music? = null
    var trophiesBefore = 0
    var bot: Solver? = null
    var timeScale = 1.0
    var debugView by mutableStateOf<String?>(null)
    /** Test hook: what [com.vrgames.deepsea.render.Cockpit.debug] gets. */
    @Volatile var debugCabin: String? = null
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
        cockpit = false
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
        if (cabinHintDue && speaking == null) { cabinHintDue = false; speak("cockpit_hint"); version++ }
        // Following nothing any more (a new order): the cabin view has nothing to watch, back to the overview.
        if (cockpit && !diving && p.follow == null && p.job == null) { cockpit = false; say("没有在跟随的动物了，回到舱外视角。"); version++ }
        val b = bot
        val evs = synchronized(lock) { if (b != null) p.step(dt) { b.update(com.vrgames.deepsea.core.Dive.STEP) } else p.step(dt) }
        var changed = false
        for (e in evs) {
            changed = true
            when (e) {
                is Ev.Note -> say(e.text)
                is Ev.Photo -> onPhoto(p, e)
                is Ev.Sample -> { sound?.play(Sfx.SAMPLE, here(p, e.key)); p.thing(e.key)?.let { view?.flashAt(it.at); cabinTank?.flashAt(it.at); say("采到了：${it.sp.name}。") } }
                is Ev.Lit -> sound?.play(Sfx.LIT, here(p, e.key))
                is Ev.Bump -> {}
                is Ev.ZoneIn -> {
                    sound?.play(Sfx.ZONE)
                    // Going deeper: once, a word that the cockpit shows it best.
                    val more = if (!cockpit && !zoneTold && e.zone.ordinal >= 1 && canCabin) { zoneTold = true; "想坐进潜水器里看海一点点变暗：点「进入舱内视角（全沉浸）」，或捏住水缸里的潜水器往自己这边一拉。" } else ""
                    say("${e.zone.title}（${e.zone.meters}）。${e.zone.color}$more")
                    if (cockpit && e.zone.ordinal == 2 && !zoneTold) { zoneTold = true; speak("cockpit_zone") }
                }
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
        Log.i(TAG, "photo ${e.key} first=${e.first} follow=${p.follow?.key}")
        sound?.play(Sfx.PHOTO, here(p, e.key))
        view?.flashAt(t.at)
        cabinTank?.flashAt(t.at)
        if (seen.add(t.sp.id)) save()
        // Every first meeting is an event: the creature performs, and its card and voice come up.
        if (e.first) {
            view?.perform(e.key)
            cabinTank?.perform(e.key)
            meeting = t.sp
            speak(t.sp.id)
        } else say("又拍到一张${t.sp.name}。")
        // The first animal photographed: once its own words are said, the voice tells that the sub now follows it and
        // the cabin view is open.
        if (t.sp.kind == com.vrgames.deepsea.core.Kind.ANIMAL && !cockpitTold) { cockpitTold = true; cabinHintDue = true }
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
        // v0.5.1 (device 2026-11-19: the manta photographed from the cabin got no words): when the last goal is a first
        // meeting, the level ends on that very photo; its words are let finish instead of being cut off.
        if (speaking == null || speaking != meeting?.id) stopNarration()
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
