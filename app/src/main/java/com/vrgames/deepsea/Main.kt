package com.vrgames.deepsea

import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.Form
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.spatial.ui.foundation.dsl.WindowContainer
import com.pico.spatial.ui.foundation.dsl.WindowContainerSize
import com.vrgames.deepsea.content.AlbumWindow
import com.vrgames.deepsea.content.DeepStage
import com.vrgames.deepsea.content.GuideWindow
import com.vrgames.deepsea.content.LevelsWindow

const val LEVELS_ID = "Levels"
const val GUIDE_ID = "Guide"
const val ALBUM_ID = "Album"

/**
 * Four linked spaces: the tank in front of the seated player (a full-immersion Stage), the 关卡, 图鉴
 * and 说明 windows. They share [com.vrgames.deepsea.game.Game].
 */
fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultStage {
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
