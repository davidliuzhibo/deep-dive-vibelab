package com.vrgames.deepsea.content

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Text
import com.vrgames.deepsea.ALBUM_ID
import com.vrgames.deepsea.GUIDE_ID
import com.vrgames.deepsea.LEVELS_ID
import com.vrgames.deepsea.core.Sea
import com.vrgames.deepsea.core.Stages
import com.vrgames.deepsea.core.Zone
import com.vrgames.deepsea.game.Game

private val CardBg = Color(0x26EAF6FF)

/** 关卡: every dive with its stars (pick one to play), and the three trophies. */
@Composable
fun LevelsWindow() {
    Game.version
    Column(Modifier.fillMaxSize().background(WindowBg).padding(28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        WindowHeader(LEVELS_ID, "关卡", 32.sp)
        Text("总星数 ${Game.stars} / ${Game.maxStars}。点一次下潜就能玩。", fontSize = 22.sp, color = Ink)
        Text(
            (if (Game.bronze) "🥉 铜奖杯：十二次全完成　" else "铜奖杯：十二次全完成（未得）　") +
                (if (Game.silver) "🥈 银奖杯　" else "银奖杯：三分之二的星（未得）　") +
                (if (Game.gold) "🥇 金奖杯：万灯之海" else "金奖杯：全部三星（未得）"),
            fontSize = 21.sp, color = Mint,
        )
        for ((k, s) in Stages.ALL.withIndex()) {
            if (k == 0 || Stages.ALL[k - 1].act != s.act) Text(Stages.ACTS[s.act], fontSize = 25.sp, color = Sky)
            val open = Game.open(s)
            val st = Game.best[s.id] ?: 0
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardBg)
                    .clickable(enabled = open) { Game.closeWindow(LEVELS_ID); Game.intro(s) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("${k + 1}. ${s.title}", fontSize = 24.sp, color = if (open) Ink else Dim)
                    Text("最深 ${Sea.meters(s.floor)} 米　" + s.goals.joinToString("，") { it().text }, fontSize = 19.sp, color = Dim)
                }
                Spacer(Modifier.weight(1f))
                Text(if (open) "★".repeat(st) + "☆".repeat(3 - st) else "未开放", fontSize = 23.sp, color = Lamp)
            }
        }
    }
}

private val ZONE_COLOR = mapOf(Zone.SUN to Color(0xFF4FB6D8), Zone.TWILIGHT to Color(0xFF1F6FA8), Zone.MIDNIGHT to Color(0xFF123A6A), Zone.ABYSS to Color(0xFF0A1A30))

/** 深海图鉴: by zone, top to bottom, like the tank. A species shows its story once it has been photographed. */
@Composable
fun AlbumWindow() {
    Game.version
    Column(Modifier.fillMaxSize().background(WindowBg).padding(28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        WindowHeader(ALBUM_ID, "深海图鉴", 32.sp)
        Text("已拍到 ${Game.seen.size} / ${Sea.SPECIES.size} 种", fontSize = 22.sp, color = Ink)
        for (z in Zone.entries) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ZONE_COLOR[z]!!).padding(12.dp)) {
                Text("${z.title}　${z.meters}", fontSize = 25.sp, color = Color.White)
                Text(z.color, fontSize = 19.sp, color = Color(0xFFD8ECFF))
            }
            for (s in Sea.SPECIES.filter { it.zone == z }) {
                val got = s.id in Game.seen
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardBg).padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (got) s.name else "？？？", fontSize = 24.sp, color = if (got) Lamp else Dim)
                        Spacer(Modifier.weight(1f))
                        if (got) Text(s.figure, fontSize = 20.sp, color = Mint)
                    }
                    Text(if (got) s.fact else "还没拍到。去${z.title}找一找。", fontSize = 20.sp, color = if (got) Ink else Dim)
                }
            }
        }
    }
}

/** 潜航说明: how to drive, and the sea. */
@Composable
fun GuideWindow() {
    Game.version
    Column(Modifier.fillMaxSize().background(WindowBg).padding(28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WindowHeader(GUIDE_ID, "潜航说明", 32.sp)
        for ((t, d) in GUIDE) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardBg).padding(14.dp)) {
                Text(t, fontSize = 24.sp, color = Lamp)
                Text(d, fontSize = 20.sp, color = Ink)
            }
        }
    }
}

private val GUIDE = listOf(
    "开船" to "看向水里捏一下，潜水器就开过去，你不用一直控制。看着一条鱼、一块石头捏一下，潜水器会自己靠过去，到了就拍照或采样。手指到的东西会变亮。金色小圈是任务要找的；移动的金圈是潜水器正要去的地方；任务做完后，海面会出现金色光带，开上去就算回来了。",
    "探照灯" to "灯开着，附近的东西才看得见，但更费电，胆小的动物（棱皮龟、大王乌贼）也会躲开。关灯时，发光的生物反而更容易找。",
    "电量" to "开得越快、下得越深、灯开得越久，电用得越快。电量用完这次下潜就失败。任务里有「回到海面」就要留够回程的电。",
    "耐压舱" to "红线以下这个舱下不去。第二章能下到午夜带，第三章能到海底。",
    "星星" to "完成任务一星，电量剩得多就两星、三星。关灯、走直线、少绕路。",
    "奖杯" to "十二次下潜全部完成：铜奖杯。拿到三分之二的星：银奖杯。全部三星：金奖杯，解锁「万灯之海」。",
)
