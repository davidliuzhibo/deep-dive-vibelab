package com.vrgames.deepsea.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiveTest {
    private fun solve(s: Stage): Dive {
        val d = Dive(s)
        val bot = Solver(d)
        var t = 0.0
        while (!d.over && t < 900) { d.step(Dive.STEP) { bot.update(Dive.STEP) }; t += Dive.STEP }
        return d
    }

    @Test
    fun everyLevelCanBeSolvedWithThreeStars() {
        val bad = ArrayList<String>()
        for (s in Stages.ALL) {
            val d = solve(s)
            println("${s.id}: won=${d.won} stars=${d.stars()} battery=${d.battery.toInt()}/${s.battery.toInt()} par=${s.par.toInt()} time=${d.time.toInt()} goals=${d.goals.map { it.text + ":" + it.got }}")
            if (d.stars() < 3) bad += "${s.id} won=${d.won} stars=${d.stars()} battery=${d.battery.toInt()} par=${s.par} ${d.goals.filter { !it.met }.map { it.text }}"
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    @Test
    fun aPinchOnWaterSteers() {
        val d = Dive(Stages.byId("a1"))
        d.steer(P(0.2, 0.8))
        repeat(600) { d.step(Dive.STEP) }
        assertEquals(0.2, d.sub.at.x, 0.01)
        assertEquals(0.8, d.sub.at.y, 0.01)
    }

    @Test
    fun aPinchOnACreatureGoesAndPhotographsInOneGo() {
        val d = Dive(Stages.byId("a1"))
        d.act("manta0")
        var photo = false
        repeat(900) { if (d.step(Dive.STEP).any { it is Ev.Photo }) photo = true }
        assertTrue(photo)
        d.steer(P(d.sub.at.x, Sea.H))
        repeat(900) { d.step(Dive.STEP) }
        assertTrue(d.won)
    }

    @Test
    fun theHullStopsAtItsDepth() {
        val d = Dive(Stages.byId("a1"))
        d.steer(P(0.0, 0.0))
        repeat(900) { d.step(Dive.STEP) }
        assertTrue(d.sub.at.y >= d.stage.floor - 1e-6)
    }

    @Test
    fun aThingTooDeepSaysSoAndTheSubStays() {
        val d = Dive(Stages.byId("a1"))
        d.act("vent0")
        assertEquals(null, d.job)
        assertTrue(d.step(0.01).any { it is Ev.Note })
    }

    @Test
    fun theDarkHidesUntilTheLampReaches() {
        val d = Dive(Stages.byId("b1"))
        d.step(0.1)
        assertFalse("too far to see", d.thing("wreck0")!!.lit)
        val a = d.thing("angler0")!!
        assertFalse(a.lit)
        d.act(a.key)
        repeat(1500) { d.step(Dive.STEP) }
        assertTrue(a.photographed)
    }

    @Test
    fun shyOnesFleeTheLampAndComeIfItIsOff() {
        fun chase(lamp: Boolean): Double {
            val d = Dive(Stages.byId("a2"))
            if (!lamp) d.toggleLamp()
            val t = d.thing("turtle0")!!
            d.steer(t.at)
            repeat(200) { d.step(Dive.STEP) }
            return (t.at - t.home).len()
        }
        assertTrue("runs from the light", chase(true) > chase(false))
    }

    @Test
    fun aShyOneIsCaughtWithTheLampOffAndTheGameSaysSo() {
        // Lamp on: it keeps away, and the game tells the player what to do.
        val on = Dive(Stages.byId("b2"))
        on.act("squid0")
        var told = false
        repeat(1800) { if (on.step(Dive.STEP).any { it is Ev.Note }) told = true }
        assertTrue("the hint", told)
        assertFalse(on.thing("squid0")!!.photographed)
        // Lamp off, on again on arrival: the one pinch still stands, and the shot goes off by itself.
        val off = Dive(Stages.byId("b2"))
        off.toggleLamp()
        val t = off.thing("squid0")!!
        off.act(t.key)
        var clock = 0.0
        while (!off.near(t) && clock < 120) { off.step(Dive.STEP); clock += Dive.STEP }
        assertTrue("got close at $clock", off.near(t))
        assertFalse("not in the dark", t.photographed)
        off.toggleLamp()
        repeat(30) { off.step(Dive.STEP) }
        assertTrue("the shot goes off", t.photographed)
    }

    @Test
    fun anEmptyBatteryEndsTheDive() {
        val d = Dive(Stages.byId("a1"))
        d.steer(P(0.4, 0.6))
        var evs = listOf<Ev>()
        repeat(40000) { if (!d.over) evs = d.step(Dive.STEP) }
        assertTrue(d.over)
        assertFalse(d.won)
        assertTrue(evs.any { it is Ev.Lost })
    }

    @Test
    fun theGaugeReadsTheZones() {
        assertEquals(0, Sea.meters(Sea.H))
        assertEquals(200, Sea.meters(Sea.ZONE_H * 3))
        assertEquals(1000, Sea.meters(Sea.ZONE_H * 2))
        assertEquals(6000, Sea.meters(0.0))
    }

    /** From the seat, eyes and low hands: nothing that can be pinched is wholly behind something else, or too small to aim at. */
    @Test
    fun everyThingCanBePinchedFromTheSeat() {
        class Box(val key: String, val cx: Double, val cy: Double, val w: Double, val h: Double)
        val sizes = mapOf(
            "manta" to (0.17 to 0.08), "turtle" to (0.13 to 0.07), "coral" to (0.12 to 0.11), "lantern" to (0.07 to 0.05), "jelly" to (0.08 to 0.1), "squid" to (0.2 to 0.09),
            "angler" to (0.12 to 0.08), "wreck" to (0.28 to 0.11), "whale" to (0.26 to 0.09), "vent" to (0.13 to 0.17), "tube" to (0.1 to 0.1), "chest" to (0.07 to 0.05),
        )
        val d = Dive(Stages.byId("c4"))
        val boxes = d.things.map { t ->
            val (w, h) = sizes[t.sp.id]!!
            Box(t.key, t.at.x, t.at.y + if (t.sp.kind == Kind.THING) h / 2 else 0.0, w * 1.1, h * 1.1)
        }
        // The boxes all stand in one plane, 0.8 m ahead: a straight ray reaches any free point of one.
        for (b in boxes) {
            val free = listOf(b.cx to b.cy, b.cx - b.w * 0.35 to b.cy, b.cx + b.w * 0.35 to b.cy).any { (x, y) ->
                boxes.none { o -> o !== b && kotlin.math.abs(x - o.cx) < o.w / 2 && kotlin.math.abs(y - o.cy) < o.h / 2 }
            }
            assertTrue("${b.key} is covered by another box", free)
            assertTrue("${b.key} is too small to aim at", Math.toDegrees(kotlin.math.atan(b.w / 0.8)) >= 3.0)
        }
    }
}

class VoicesTest {
    @org.junit.Test fun everyThingInTheSeaHasNarrationOfTheRightLength() {
        for (s in Sea.SPECIES) {
            val t = Voices.TEXT[s.id]
            org.junit.Assert.assertNotNull(s.id, t)
            org.junit.Assert.assertTrue("${s.id} ${t!!.length}", t.length in 55..110)
        }
    }
}
