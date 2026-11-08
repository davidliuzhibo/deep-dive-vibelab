package com.vrgames.deepsea.content

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.PhysicalLengthConverter
import com.vrgames.deepsea.GUIDE_ID
import com.vrgames.deepsea.LEVELS_ID
import com.vrgames.deepsea.ALBUM_ID
import com.vrgames.deepsea.core.Dive
import com.vrgames.deepsea.core.Sea
import com.vrgames.deepsea.core.Stages
import com.vrgames.deepsea.game.Game

const val PANEL_CARD = "card"
const val PANEL_INTRO = "intro"
const val PANEL_HUD = "hud"
const val PANEL_RESULT = "result"
const val PANEL_SHOW = "show"
const val PANEL_MEETING = "meeting"
const val PANEL_MENU = "menu"
const val PANEL_NOTE = "note"

val Ink = Color(0xFFEAF6FF)
val Lamp = Color(0xFFFFD86B)
val Sky = Color(0xFF7FD0F0)
val Mint = Color(0xFF9FE3A8)
val Dim = Color(0xFFA8BCCC)
val Alarm = Color(0xFFFF7B5C)
private val PanelBg = Color(0xEA081A2C)
/** Window backdrop: the system glass can be light, and every text color here is meant for dark. */
val WindowBg = Color(0xF2081A2C)

@Composable
fun PhysicalLengthConverter.m(meters: Float): Dp = lengthToDp(meters, LengthUnit.Meters)

@Composable
fun PhysicalLengthConverter.sz(meters: Float) = m(meters).value.sp

@Composable
private fun PanelBox(c: PhysicalLengthConverter, width: Float, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(c.m(0.016f))
    Column(
        Modifier.width(c.m(width)).clip(shape).background(PanelBg).border(c.m(0.0015f), Color(0x8878C8F0), shape).padding(c.m(0.016f)),
        verticalArrangement = Arrangement.spacedBy(c.m(0.008f)),
    ) { content() }
}

/** The step that moves the game on. */
@Composable
fun CallToAction(c: PhysicalLengthConverter, text: String, color: Color = Lamp, onClick: () -> Unit) {
    val shape = RoundedCornerShape(c.m(0.014f))
    Text(
        text,
        Modifier.clip(shape).background(color).border(c.m(0.002f), Color(0xFFFFF4E0), shape)
            .clickable { onClick() }.padding(horizontal = c.m(0.024f), vertical = c.m(0.013f)),
        fontSize = c.sz(0.024f),
        color = Color(0xFF1A1420),
    )
}

@Composable
fun Pill(c: PhysicalLengthConverter, text: String, color: Color = Dim, onClick: () -> Unit) {
    val shape = RoundedCornerShape(c.m(0.012f))
    Text(
        text,
        Modifier.clip(shape).border(c.m(0.0015f), color, shape).clickable { onClick() }.padding(horizontal = c.m(0.014f), vertical = c.m(0.008f)),
        fontSize = c.sz(0.017f),
        color = color,
    )
}

/** Text on the lit display of a riveted instrument panel (the frame model is added by the stage). */
@Composable
private fun Gauge(c: PhysicalLengthConverter, width: Float, height: Float, content: @Composable () -> Unit) {
    Column(
        Modifier.width(c.m(width)).height(c.m(height)).padding(horizontal = c.m(0.016f), vertical = c.m(0.006f)),
        verticalArrangement = Arrangement.spacedBy(c.m(0.006f), Alignment.CenterVertically),
    ) { content() }
}

/** First meeting with a creature: its name, what it is, and the recording. */
@Composable
fun MeetingPanel(c: PhysicalLengthConverter) {
    val sp = Game.meeting ?: return
    Gauge(c, 0.56f, 0.26f) {
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.012f)), verticalAlignment = Alignment.CenterVertically) {
            Text(sp.name, fontSize = c.sz(0.03f), color = Lamp)
            if (Game.speaking == sp.id) CallToAction(c, "■ 停止") { Game.stopNarration() } else CallToAction(c, "▶ 听讲解") { Game.speak(sp.id) }
            Pill(c, "收起") { Game.stopNarration(); Game.meeting = null }
        }
        Text(com.vrgames.deepsea.core.Voices.TEXT[sp.id] ?: sp.fact, fontSize = c.sz(0.0165f), color = Ink)
    }
}

private fun stars(n: Int) = "★".repeat(n) + "☆".repeat(3 - n)

private fun bar(v: Double, full: Double): String {
    val n = (v / full * 10).toInt().coerceIn(0, 10)
    return "▮".repeat(n) + "▯".repeat(10 - n)
}

@Composable
fun CardPanel(c: PhysicalLengthConverter) {
    Gauge(c, 0.64f, 0.34f) {
        Game.version // Read in here: the same arguments, so the box would skip its content.
        Text("深海潜航", fontSize = c.sz(0.046f), color = Lamp)
        Text("把一片海装进玻璃缸。开着小潜水器一层层往下，拍下它们，把样本带回来。", fontSize = c.sz(0.018f), color = Ink)
        Text("怎么玩", fontSize = c.sz(0.02f), color = Mint)
        Text("· 看向水里捏一下：潜水器开过去。\n· 捏一条鱼：它自己靠近、拍照。\n· 越深越黑：只有灯照到才看得见。电量用完就结束。", fontSize = c.sz(0.0165f), color = Ink)
        if (Game.stars > 0) Text("已得 ${Game.stars} / ${Game.maxStars} 颗星，图鉴 ${Game.seen.size} / ${Sea.SPECIES.size}", fontSize = c.sz(0.016f), color = Mint)
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.01f)), verticalAlignment = Alignment.CenterVertically) {
            CallToAction(c, if (Game.stars > 0) "接着下潜 →" else "下水 →") { Game.start() }
            if (Game.gold) Pill(c, "万灯之海", Lamp) { Game.show() }
        }
    }
}

@Composable
fun IntroPanel(c: PhysicalLengthConverter) {
    Gauge(c, 0.6f, 0.36f) {
        Game.version
        val s = Game.stage
        Text("第 ${Stages.ALL.indexOf(s) + 1} 次下潜 · ${s.title}", fontSize = c.sz(0.032f), color = Lamp)
        Text(Stages.ACTS[s.act] + "　能下到 ${Sea.meters(s.floor)} 米", fontSize = c.sz(0.016f), color = Sky)
        Text(s.ask, fontSize = c.sz(0.0185f), color = Ink)
        for (g in s.goals) Text("○ ${g().text}", fontSize = c.sz(0.0175f), color = Mint)
        Text("三星：回来时电量还剩 ${(s.par / s.battery * 100).toInt()}% 以上。", fontSize = c.sz(0.015f), color = Dim)
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.01f)), verticalAlignment = Alignment.CenterVertically) {
            CallToAction(c, "开始 →") { Game.begin() }
            Pill(c, "返回") { Game.toCard() }
        }
    }
}

@Composable
private fun Goals(c: PhysicalLengthConverter, p: Dive) {
    // Read here: the Dive is the same object as it changes, so without this the list skips recomposing.
    Game.version
    for (g in p.goals) Text((if (g.met) "✔ " else "○ ") + g.text + if (g.n > 1) "　${g.got}/${g.n}" else "", fontSize = c.sz(0.0165f), color = if (g.met) Mint else Ink)
}

@Composable
fun HudPanel(c: PhysicalLengthConverter, toggle: (String) -> Unit) {
    Gauge(c, 0.34f, 0.34f) {
        Game.version
        val p = Game.play ?: return@Gauge
        Text("第 ${Stages.ALL.indexOf(p.stage) + 1} 次 · ${p.stage.title}", fontSize = c.sz(0.02f), color = Lamp)
        val z = Sea.zoneAt(p.sub.at.y)
        Text("${Sea.meters(p.sub.at.y)} 米　${z.title}", fontSize = c.sz(0.024f), color = Sky)
        val low = p.battery < p.stage.battery * 0.2
        Text("电量 " + bar(p.battery, p.stage.battery), fontSize = c.sz(0.0175f), color = if (low) Alarm else Mint)
        Goals(c, p)
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.006f))) {
            Pill(c, if (p.sub.lamp) "探照灯：开" else "探照灯：关", if (p.sub.lamp) Lamp else Dim) { Game.lamp() }
            Pill(c, "重来") { Game.retry() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.006f))) {
            Pill(c, "图鉴", Lamp) { toggle(ALBUM_ID) }
            Pill(c, "关卡") { toggle(LEVELS_ID) }
        }
    }
}

@Composable
fun ResultPanel(c: PhysicalLengthConverter, toggle: (String) -> Unit) {
    Gauge(c, 0.6f, 0.4f) {
        Game.version
        val p = Game.play ?: return@Gauge
        if (p.won) {
            Text("${p.stage.title}　${stars(p.stars())}", fontSize = c.sz(0.034f), color = Lamp)
            Text("电量还剩 ${(p.battery / p.stage.battery * 100).toInt()}%。", fontSize = c.sz(0.018f), color = Ink)
            if (p.stars() < 3) Text("三星：电量还剩 ${(p.stage.par / p.stage.battery * 100).toInt()}% 以上。少开灯，走直线。", fontSize = c.sz(0.015f), color = Dim)
        } else {
            Text("${p.stage.title}　电用完了", fontSize = c.sz(0.034f), color = Alarm)
            Text("潜水器抛掉压载，慢慢浮了上来。再来一次吧。", fontSize = c.sz(0.018f), color = Ink)
        }
        Goals(c, p)
        val t = Game.trophies
        if (t > Game.trophiesBefore) Text(
            when (t) {
                1 -> "得到铜奖杯！十二次下潜全部完成。"
                2 -> "得到银奖杯！"
                else -> "得到金奖杯！解锁「万灯之海」。"
            },
            fontSize = c.sz(0.019f), color = Mint,
        )
        Text("总星数 ${Game.stars} / ${Game.maxStars}", fontSize = c.sz(0.015f), color = Dim)
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.01f)), verticalAlignment = Alignment.CenterVertically) {
            if (p.won && Stages.ALL.indexOf(p.stage) < Stages.ALL.size - 1) CallToAction(c, "下一次 →") { Game.next() }
            Pill(c, "再来一次", Lamp) { Game.retry() }
            Pill(c, "图鉴", Sky) { toggle(ALBUM_ID) }
            if (Game.gold) Pill(c, "万灯之海", Lamp) { Game.show() }
        }
    }
}

@Composable
fun ShowPanel(c: PhysicalLengthConverter) {
    Gauge(c, 0.32f, 0.17f) {
        Text("万灯之海", fontSize = c.sz(0.03f), color = Lamp)
        Text("十二次下潜全部三星。整片海都为你亮了。", fontSize = c.sz(0.017f), color = Ink)
        Pill(c, "结束") { Game.endShow() }
    }
}

@Composable
fun NotePanel(c: PhysicalLengthConverter) {
    val n = Game.note ?: return
    val shape = RoundedCornerShape(c.m(0.012f))
    Text(n, Modifier.widthIn(max = c.m(0.7f)).clip(shape).background(PanelBg).padding(c.m(0.012f)), fontSize = c.sz(0.024f), color = Lamp)
}

@Composable
fun MenuPanel(c: PhysicalLengthConverter, toggle: (String) -> Unit, faceMe: () -> Unit, quit: () -> Unit, version: String) {
    Game.version
    if (!Game.menuOpen) {
        Pill(c, "菜单", Lamp) { Game.menuOpen = true }
        return
    }
    PanelBox(c, 0.28f) {
        Game.version
        Pill(c, if (Game.window == LEVELS_ID) "收起关卡" else "关卡（选关、奖杯）", Lamp) { toggle(LEVELS_ID) }
        Pill(c, if (Game.window == ALBUM_ID) "收起图鉴" else "深海图鉴") { toggle(ALBUM_ID) }
        Pill(c, if (Game.window == GUIDE_ID) "收起说明" else "潜航说明") { toggle(GUIDE_ID) }
        if (Game.gold) Pill(c, "万灯之海", Lamp) { Game.menuOpen = false; Game.show() }
        Pill(c, "把水缸摆到我正前方") { faceMe() }
        Pill(c, if (Game.musicOn) "音乐：开" else "音乐：关") { Game.toggleMusic() }
        RestartPill(c)
        Pill(c, "退出") { quit() }
        Text("进度自动保存。版本 $version", fontSize = c.sz(0.0125f), color = Dim)
        Pill(c, "收起菜单") { Game.menuOpen = false }
    }
}

@Composable
private fun RestartPill(c: PhysicalLengthConverter) {
    if (Game.confirmRestart) {
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.006f))) {
            Pill(c, "确定清空进度", Alarm) { Game.confirmRestart = false; Game.menuOpen = false; Game.restart() }
            Pill(c, "算了") { Game.confirmRestart = false }
        }
    } else Pill(c, "清空进度从头开始") { Game.confirmRestart = true }
}

@Composable
fun WindowHeader(id: String, title: String, size: TextUnit) {
    val copy = remember { Any() }
    DisposableEffect(copy) {
        Game.windowShown(id, copy)
        onDispose { Game.windowGone(id, copy) }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = size, color = Lamp)
        Spacer(Modifier.weight(1f))
        Button(onClick = { Game.closeWindow(id) }) { Text("关闭", fontSize = 22.sp) }
    }
}
