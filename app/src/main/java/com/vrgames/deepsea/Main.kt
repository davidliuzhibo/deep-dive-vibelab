package com.vrgames.deepsea

import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultWindowContainer
import com.pico.spatial.ui.foundation.dsl.Stage
import com.pico.spatial.ui.foundation.dsl.Form
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.spatial.ui.foundation.dsl.WindowContainer
import com.pico.spatial.ui.foundation.dsl.WindowContainerSize
import com.vrgames.deepsea.content.AlbumWindow
import com.vrgames.deepsea.content.ConsoleWindow
import com.vrgames.deepsea.content.StartWindow
import com.vrgames.deepsea.content.TankWindow
import com.vrgames.deepsea.content.DeepStage
import com.vrgames.deepsea.content.GuideWindow
import com.vrgames.deepsea.content.LevelsWindow

const val LEVELS_ID = "Levels"
const val GUIDE_ID = "Guide"
const val ALBUM_ID = "Album"

const val TANK_ID = "Tank"
/** The app's first window (manifest id), a start card that opens the tank and the console and closes itself. */
const val START_ID = "Start"
const val CONSOLE_ID = "Console"
const val DIVE_ID = "Dive"

/**
 * v0.5 (PICO 1118: 「把水箱的场景做成 sharedspace，潜水器场景做成 fullspace，支持手势与 shared/full 切换」): the lab is in
 * Shared Space — the 潜航控制台 window (the app's main window) and the glass tank beside it in a volume of its own, next to
 * whatever else is open. Diving (the sub pulled out of the tank toward oneself, or 潜入深海) opens the deep sea all round
 * in Full Space; pulling the hatch handle overhead (or 浮出水面) comes back up to the lab. They share
 * [com.vrgames.deepsea.game.Game].
 */
fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        // v0.5.1 (device 2026-11-19): the tank in the middle, the console to its left. The system puts an app's first window
        // straight ahead, so the first window is a small start card that opens the tank (in its place) and the console
        // beside it, then closes itself.
        DefaultWindowContainer {
            PicoTheme { StartWindow() }
        }
        WindowContainer(TANK_ID, Form.Volumetric, {
            worldScale = com.pico.spatial.ui.foundation.dsl.WorldScale.Dynamic
            defaultSize = WindowContainerSize(0.8f, 0.92f, 0.42f, com.pico.spatial.ui.platform.LengthUnit.Meters)
            placement = {
                containers.firstOrNull { it.id == START_ID }?.let { com.pico.spatial.ui.foundation.dsl.Placement.placement(it, com.pico.spatial.ui.foundation.dsl.Placement.Orientation.None, 0.dp) }
                    ?: containers.firstOrNull { it.id == CONSOLE_ID }?.let { com.pico.spatial.ui.foundation.dsl.Placement.right(it, 60.dp) }
                    ?: com.pico.spatial.ui.foundation.dsl.Placement.none()
            }
        }) {
            PicoTheme { TankWindow() }
        }
        WindowContainer(CONSOLE_ID, Form.Planar, {
            defaultSize = WindowContainerSize(880.dp, 1100.dp)
            placement = { containers.firstOrNull { it.id == TANK_ID }?.let { com.pico.spatial.ui.foundation.dsl.Placement.left(it, 60.dp) } ?: com.pico.spatial.ui.foundation.dsl.Placement.none() }
        }) {
            PicoTheme { ConsoleWindow() }
        }
        Stage(DIVE_ID) {
            PicoTheme { DeepStage() }
        }
        WindowContainer(LEVELS_ID, Form.Planar, { defaultSize = WindowContainerSize(820.dp, 1000.dp) }) {
            PicoTheme { LevelsWindow() }
        }
        WindowContainer(ALBUM_ID, Form.Planar, { defaultSize = WindowContainerSize(860.dp, 1000.dp) }) {
            PicoTheme { AlbumWindow() }
        }
        WindowContainer(GUIDE_ID, Form.Planar, { defaultSize = WindowContainerSize(820.dp, 1000.dp) }) {
            PicoTheme { GuideWindow() }
        }
    }
