package com.vrgames.deepsea.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** A point in the tank's front-on plane: x across, y up, in meters from the middle of the tank's floor. */
data class P(val x: Double, val y: Double) {
    operator fun plus(o: P) = P(x + o.x, y + o.y)
    operator fun minus(o: P) = P(x - o.x, y - o.y)
    operator fun times(s: Double) = P(x * s, y * s)
    fun len() = hypot(x, y)
}

/** The four zones of the sea, top to bottom of the tank. */
enum class Zone(val title: String, val meters: String, val color: String) {
    SUN("阳光带", "0–200 米", "海面还有阳光，颜色最丰富。"),
    TWILIGHT("暮光带", "200–1000 米", "阳光只剩一点蓝，很多生物开始自己发光。"),
    MIDNIGHT("午夜带", "1000–4000 米", "一丝阳光也没有。看得见的光都是生物发出来的。"),
    ABYSS("深渊带", "4000 米以下", "水压是海面的四百多倍，海底有热液和鲸落。");
}

enum class Kind { ANIMAL, THING }

/** A kind of creature or object, for the album. */
class Species(
    val id: String,
    val name: String,
    val kind: Kind,
    val zone: Zone,
    /** Moves away from the light when the sub is close. */
    val shy: Boolean = false,
    /** Gives its own light in the dark. */
    val glows: Boolean = false,
    /** How fast it wanders, m/s. */
    val swim: Double = 0.0,
    /** How big the model stands in the tank. */
    val size: Double = 0.1,
    val fact: String = "",
    val figure: String = "",
)

object Sea {
    const val W = 0.92
    const val H = 1.04
    const val ZONE_H = H / 4

    fun zoneAt(y: Double): Zone = when {
        y > ZONE_H * 3 -> Zone.SUN
        y > ZONE_H * 2 -> Zone.TWILIGHT
        y > ZONE_H -> Zone.MIDNIGHT
        else -> Zone.ABYSS
    }

    /** Depth on the sub's gauge: the zones are not equally deep, so the scale changes with them. */
    fun meters(y: Double): Int {
        val u = (H - y).coerceIn(0.0, H) / ZONE_H
        val ends = doubleArrayOf(0.0, 200.0, 1000.0, 4000.0, 6000.0)
        val k = u.toInt().coerceAtMost(3)
        return (ends[k] + (ends[k + 1] - ends[k]) * (u - k)).toInt()
    }

    val SPECIES = listOf(
        Species("manta", "蝠鲼", Kind.ANIMAL, Zone.SUN, swim = 0.035, size = 0.16, shy = false, fact = "翼展能到 7 米，却只吃浮游生物。它张开大嘴一路滤过去，像开着门的吸尘器。", figure = "翼展 7 米"),
        Species("turtle", "棱皮龟", Kind.ANIMAL, Zone.SUN, swim = 0.03, size = 0.12, shy = true, fact = "世界上最大的海龟，背上没有硬壳，是一层带棱的皮。能潜到 1200 米深追水母吃。", figure = "下潜 1200 米"),
        Species("coral", "冷水珊瑚", Kind.THING, Zone.TWILIGHT, size = 0.1, fact = "不靠阳光也能长的珊瑚，几千年才长成一片。一块拳头大的可能比长城还老。", figure = "寿命可达数千年"),
        Species("lantern", "灯笼鱼", Kind.ANIMAL, Zone.TWILIGHT, swim = 0.05, size = 0.05, glows = true, fact = "肚子上有一排发光点，亮度和头顶的微光一样，从下往上看就看不见它的影子。这叫反照明。", figure = "占深海鱼类总重的大半"),
        Species("jelly", "深海水母", Kind.ANIMAL, Zone.TWILIGHT, swim = 0.015, size = 0.09, glows = true, fact = "身体九成多是水，没有大脑也没有心脏。受惊时会发出一圈光，把吃它的动物的天敌招来。", figure = "含水 95%"),
        Species("squid", "大王乌贼", Kind.ANIMAL, Zone.MIDNIGHT, swim = 0.04, size = 0.2, shy = true, fact = "眼睛像篮球那么大，是动物界最大的眼睛之一，用来在黑暗中发现抹香鲸。2004 年才第一次拍到活的。", figure = "眼径约 27 厘米"),
        Species("angler", "鮟鱇鱼", Kind.ANIMAL, Zone.MIDNIGHT, swim = 0.012, size = 0.11, glows = true, fact = "头上的小灯里养着发光细菌，用来钓猎物。雄鱼只有雌鱼几十分之一大，一辈子咬在雌鱼身上。", figure = "雄鱼仅几厘米"),
        Species("wreck", "沉船", Kind.THING, Zone.ABYSS, size = 0.3, fact = "深海又冷又黑，几乎没有木头虫，水流也弱，沉船能保存很多年。", figure = "水温约 2℃"),
        Species("whale", "鲸落", Kind.THING, Zone.ABYSS, size = 0.28, fact = "一头鲸死后沉到海底，能养活上万只生物几十年。一具骨架就是一片深海绿洲。", figure = "供养生物可达数十年"),
        Species("vent", "热液喷口", Kind.THING, Zone.ABYSS, size = 0.18, fact = "喷出的水超过 300℃。周围的生物不靠阳光，靠细菌分解硫化物过活。生命也许就起源于这样的地方。", figure = "水温 300℃+"),
        Species("tube", "巨型管虫", Kind.THING, Zone.ABYSS, size = 0.12, fact = "长到 2 米多，没有嘴也没有胃，全靠体内共生的细菌提供养分。红色的顶是它的鳃。", figure = "身长 2 米"),
        Species("chest", "科考样本舱", Kind.THING, Zone.ABYSS, size = 0.05, fact = "上一次科考留下的样本舱。深海科考要带回样本，才能化验出里面的微生物。", figure = "回收率很要紧"),
    )

    fun species(id: String) = SPECIES.first { it.id == id }

    /** Where each thing rests or swims around, per zone; the same layout every dive so the player learns the sea. */
    val PLACES: Map<String, List<P>> = mapOf(
        "manta" to listOf(P(-0.2, 0.92)),
        "turtle" to listOf(P(0.24, 0.84)),
        "coral" to listOf(P(-0.38, 0.54)),
        "lantern" to listOf(P(0.22, 0.68), P(-0.14, 0.6), P(0.05, 0.56)),
        "jelly" to listOf(P(-0.22, 0.7), P(0.32, 0.58)),
        "squid" to listOf(P(0.22, 0.38)),
        "angler" to listOf(P(-0.2, 0.36)),
        "wreck" to listOf(P(-0.29, 0.02)),
        "whale" to listOf(P(0.31, 0.02)),
        "vent" to listOf(P(0.0, 0.02)),
        "tube" to listOf(P(0.13, 0.02)),
        "chest" to listOf(P(-0.12, 0.02)),
    )
}

/** One thing in the sea during a dive. */
class Thing(val key: String, val sp: Species, var at: P) {
    val home = at
    var photographed = false
    var sampled = false
    var lit = false
    var flash = 0.0
    var heading = 1.0
    var phase = abs(key.hashCode() % 100) / 100.0 * 6.28
    /** Fleeing from the lamp for this much longer. */
    var fleeing = 0.0
}

enum class GoalKind { PHOTO, SAMPLE, REACH, SURFACE }

class Goal(val kind: GoalKind, val target: String, val text: String, val n: Int = 1) {
    var got = 0
    val met get() = got >= n
}

class Stage(
    val id: String,
    val act: Int,
    val title: String,
    val ask: String,
    /** How deep the hull allows, as a height above the tank floor. */
    val floor: Double,
    val battery: Double,
    val goals: List<() -> Goal>,
    /** The stars: this much battery left. */
    val par: Double,
)

sealed interface Ev {
    data class Note(val text: String) : Ev
    data class Photo(val key: String, val first: Boolean) : Ev
    data class Sample(val key: String) : Ev
    data class Lit(val key: String) : Ev
    data class Bump(val at: P) : Ev
    data class ZoneIn(val zone: Zone) : Ev
    data object Goal : Ev
    data object Low : Ev
    data object Won : Ev
    data object Lost : Ev
}

class Sub(var at: P) {
    var target: P = at
    var heading = 1.0
    var speed = 0.0
    var lamp = true
}

/** A dive: the sub, the things in the sea, the battery and the goals. */
class Dive(val stage: Stage, private val seen: (String) -> Boolean = { false }) {
    val sub = Sub(START)
    val things = ArrayList<Thing>()
    val goals = stage.goals.map { it() }
    var battery = stage.battery; private set
    var time = 0.0; private set
    var over = false; private set
    var won = false; private set
    /** What the sub is on its way to do when it arrives. */
    var job: Pair<Thing, Boolean>? = null; private set
    /**
     * The animal just photographed, which the sub now keeps up with at photo range (device test 2026-11-12: from inside
     * the sub there is no overview to find an animal, so it is found and photographed in the overview, then followed, and
     * the cabin view is for watching it). Cleared by any new order.
     */
    var follow: Thing? = null; private set

    /** Test hook: follow [key] at once, as if just photographed. */
    fun debugFollow(key: String) { thing(key)?.let { follow = it; job = null; it.photographed = true } }
    private val events = ArrayList<Ev>()
    private var zone = Sea.zoneAt(START.y)
    private var warned = false
    private var lastThing: Thing? = null
    private var darkSaid = false
    private var shySaid = false
    private var lampOn = 1.0

    init {
        for ((id, ps) in Sea.PLACES) ps.forEachIndexed { k, p -> things += Thing("$id$k", Sea.species(id), p) }
        // What the sun reaches is lit from the first moment, before the dive begins.
        light()
        events.clear()
    }

    fun thing(key: String) = things.firstOrNull { it.key == key }

    private fun clamp(p: P) = P(p.x.coerceIn(-Sea.W / 2 + 0.05, Sea.W / 2 - 0.05), p.y.coerceIn(stage.floor, Sea.H - 0.05))

    /** A pinch on open water: go there. */
    fun steer(p: P) {
        if (over) return
        job = null
        follow = null
        val c = clamp(p)
        if (p.y < stage.floor - 0.03) events += Ev.Note("船体只能下到这里：水压太大，下一章换耐压舱。")
        sub.target = c
    }

    /** A pinch on a thing: go near it and do what can be done, one pinch for all of it. */
    fun act(key: String) {
        if (over) return
        val t = thing(key) ?: return
        if (t.at.y < stage.floor - RANGE) {
            events += Ev.Note("${t.sp.name}在更深的地方，这个耐压舱下不去。")
            return
        }
        // A sample for what a goal wants sampled, a photograph for all else.
        val wantsSample = goals.any { it.kind == GoalKind.SAMPLE && !it.met && t.key.startsWith(it.target) }
        follow = null
        job = t to wantsSample
        if (!near(t)) sub.target = standoff(t)
    }

    fun near(t: Thing) = (t.at - sub.at).len() <= RANGE + t.sp.size * 0.25

    private fun standoff(t: Thing): P {
        val d = sub.at - t.at
        val l = d.len().coerceAtLeast(1e-6)
        return clamp(t.at + d * ((RANGE * 0.7 + t.sp.size * 0.25) / l))
    }

    fun toggleLamp() {
        if (over) return
        sub.lamp = !sub.lamp
        events += Ev.Note(if (sub.lamp) "探照灯开。" else "探照灯关：省电，也不吓跑胆小的动物。发光的生物反而看得更清楚。")
    }

    fun step(dt0: Double, between: (() -> Unit)? = null): List<Ev> {
        var left = dt0
        while (left > 1e-9 && !over) {
            val dt = minOf(left, STEP)
            between?.invoke()
            tick(dt)
            left -= dt
        }
        return events.toList().also { events.clear() }
    }

    private fun tick(dt: Double) {
        time += dt
        lampOn = if (sub.lamp) lampOn + dt else 0.0
        moveSub(dt)
        for (t in things) moveThing(t, dt)
        light()
        doJob()
        // Battery: the motor, the lamp, and more the deeper the pressure pumps work.
        val deep = (Sea.H - sub.at.y) / Sea.H
        battery -= dt * (0.35 + sub.speed / MAX_SPEED * 0.55 + (if (sub.lamp) 0.35 else 0.0) + deep * 0.25)
        if (battery < stage.battery * 0.2 && !warned) {
            warned = true
            events += Ev.Low
            events += Ev.Note("电量不多了，留够回程的电！")
        }
        val z = Sea.zoneAt(sub.at.y)
        if (z != zone) {
            zone = z
            events += Ev.ZoneIn(z)
        }
        for (g in goals) if (!g.met) when (g.kind) {
            GoalKind.REACH -> if (sub.at.y <= g.target.toDouble() + 0.02) meet(g)
            GoalKind.SURFACE -> if (goals.all { it === g || it.met } && sub.at.y >= Sea.H - 0.06) meet(g)
            else -> {}
        }
        if (goals.all { it.met }) {
            over = true
            won = true
            events += Ev.Won
        } else if (battery <= 0) {
            battery = 0.0
            over = true
            events += Ev.Lost
        }
    }

    /** What the goals still want: the view marks these, so the player knows where to go. */
    fun wanted(t: Thing) = goals.any { g -> !g.met && (g.kind == GoalKind.PHOTO || g.kind == GoalKind.SAMPLE) && t.key.startsWith(g.target) && !(g.kind == GoalKind.PHOTO && t.photographed) }

    val wantsSurface get() = goals.any { it.kind == GoalKind.SURFACE && !it.met } && goals.all { it.met || it.kind == GoalKind.SURFACE }

    private fun meet(g: Goal) {
        g.got = g.n
        events += Ev.Goal
    }

    private fun moveSub(dt: Double) {
        val d = sub.target - sub.at
        val l = d.len()
        // Slows down coming in, so it doesn't overshoot; turns by itself.
        val want = (l * 1.2).coerceAtMost(MAX_SPEED)
        sub.speed += (want - sub.speed) * (dt * 3).coerceAtMost(1.0)
        if (l > 1e-3) {
            val s = minOf(sub.speed * dt, l)
            sub.at = sub.at + d * (s / l)
            if (abs(d.x) > 0.005) sub.heading = if (d.x > 0) 1.0 else -1.0
        } else sub.speed = 0.0
    }

    private fun moveThing(t: Thing, dt: Double) {
        if (t.flash > 0) t.flash -= dt
        if (t.sp.swim <= 0) return
        t.phase += dt * t.sp.swim / 0.12
        // A slow circle around home.
        val r = 0.07
        val goal = t.home + P(cos(t.phase) * r * 1.6, sin(t.phase * 1.3) * r * 0.7)
        var at = t.at + (goal - t.at) * (dt * 0.8).coerceAtMost(1.0)
        // Shy ones move off from the lit lamp, and tire.
        val d = t.at - sub.at
        val l = d.len().coerceAtLeast(1e-6)
        // A moment's startle when the lamp comes on: that is the instant to take the picture.
        if (t.sp.shy && sub.lamp && lampOn > STARTLE && l < FLEE) t.fleeing = 1.5
        if (t.fleeing > 0) {
            t.fleeing -= dt
            at = t.at + d * (dt * FLEE_SPEED / l)
        }
        val before = t.at.x
        t.at = P(at.x.coerceIn(-Sea.W / 2 + 0.08, Sea.W / 2 - 0.08), at.y.coerceIn(0.1, Sea.H - 0.06))
        if (abs(t.at.x - before) > 1e-5) t.heading = if (t.at.x > before) 1.0 else -1.0
    }

    /** What can be seen: the sunlit zones always, in the dark only what glows or what the lamp reaches. */
    private fun light() {
        for (t in things) {
            val l = (t.at - sub.at).len()
            val reach = if (sub.lamp) LAMP else 0.0
            val daylight = t.at.y > Sea.ZONE_H * 2
            val now = daylight || l < reach + t.sp.size * 0.3 || (t.sp.glows && !sub.lamp && l < reach + 0.32)
            if (now && !t.lit && !daylight) events += Ev.Lit(t.key)
            t.lit = now
        }
    }

    private fun doJob() {
        val f = follow
        if (job == null && f != null) {
            // Keep up with the followed animal, at photo range, without crowding it.
            if (!near(f)) sub.target = standoff(f)
            return
        }
        val (t, sample) = job ?: return
        // The target moves: keep after it, at photo range.
        if (!near(t)) {
            sub.target = standoff(t)
            if (t.sp.shy && sub.lamp && t.fleeing > 0 && !shySaid) {
                shySaid = true
                events += Ev.Note("它怕光，开着灯追不上：关灯靠近，到了再开灯。")
            }
            return
        }
        if (!t.lit) {
            // Stays on the job: the shot goes off as soon as the lamp comes on.
            if (!darkSaid) events += Ev.Note("到了，但太黑看不见：打开探照灯就能拍。")
            darkSaid = true
            sub.target = sub.at
            return
        }
        darkSaid = false
        job = null
        sub.target = sub.at
        lastThing = t
        if (sample) doSample(t) else doPhoto(t)
    }

    private fun doPhoto(t: Thing) {
        val first = !seen(t.sp.id) && !t.photographed
        t.photographed = true
        t.flash = 0.35
        if (t.sp.kind == Kind.ANIMAL) follow = t
        events += Ev.Photo(t.key, first)
        for (g in goals) if (g.kind == GoalKind.PHOTO && !g.met && t.key.startsWith(g.target) && !g.counted(t)) {
            g.mark(t)
            g.got++
            if (g.met) events += Ev.Goal
        }
    }

    private fun doSample(t: Thing) {
        t.sampled = true
        t.flash = 0.35
        events += Ev.Sample(t.key)
        for (g in goals) if (g.kind == GoalKind.SAMPLE && !g.met && t.key.startsWith(g.target)) {
            g.got++
            if (g.met) events += Ev.Goal
        }
    }

    private val counted = HashSet<String>()
    private fun Goal.counted(t: Thing) = (text + t.key) in counted
    private fun Goal.mark(t: Thing) { counted += text + t.key }

    fun stars(): Int {
        if (!won) return 0
        return if (battery >= stage.par) 3 else if (battery >= stage.par * 0.45) 2 else 1
    }

    companion object {
        val START = P(0.36, Sea.H - 0.08)
        const val STEP = 1.0 / 30
        const val MAX_SPEED = 0.05
        const val RANGE = 0.09
        const val LAMP = 0.27
        const val FLEE = 0.2
        const val FLEE_SPEED = 0.035
        const val STARTLE = 0.5
    }
}

/** Plays a dive as a careful player would: one goal at a time, the nearest first, then home. */
class Solver(private val d: Dive) {
    private var cooldown = 0.0

    /** Off in daylight, on in the dark; dark while creeping up on a shy one, on again when close enough. */
    private fun lamp() {
        val job = d.job?.first
        val want = when {
            job != null && job.sp.shy -> d.near(job)
            job != null && d.near(job) -> true
            else -> d.sub.at.y < Sea.ZONE_H * 2 + 0.06
        }
        if (d.sub.lamp != want) d.toggleLamp()
    }

    fun update(dt: Double) {
        if (d.over) return
        lamp()
        cooldown -= dt
        if (cooldown > 0 || d.job != null) return
        cooldown = 0.5
        val g = d.goals.firstOrNull { !it.met && it.kind != GoalKind.SURFACE }
        when (g?.kind) {
            GoalKind.PHOTO, GoalKind.SAMPLE -> {
                val t = d.things.filter { it.key.startsWith(g.target) && (g.kind == GoalKind.SAMPLE || !it.photographed) }
                    .minByOrNull { (it.at - d.sub.at).len() } ?: d.things.first { it.key.startsWith(g.target) }
                d.act(t.key)
            }
            GoalKind.REACH -> d.steer(P(d.sub.at.x, g.target.toDouble()))
            else -> d.steer(P(d.sub.at.x, Sea.H))
        }
    }
}
