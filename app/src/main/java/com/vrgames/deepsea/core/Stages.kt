package com.vrgames.deepsea.core

object Stages {
    val ACTS = listOf("第一章 · 阳光和暮光", "第二章 · 午夜带", "第三章 · 深渊")

    private fun photo(id: String, name: String, n: Int = 1): () -> Goal = { Goal(GoalKind.PHOTO, id, if (n > 1) "拍 $n 条$name" else "拍到$name", n) }
    private fun sample(id: String, name: String): () -> Goal = { Goal(GoalKind.SAMPLE, id, "采集$name") }
    private fun reach(y: Double, text: String): () -> Goal = { Goal(GoalKind.REACH, "$y", text) }
    private val home: () -> Goal = { Goal(GoalKind.SURFACE, "", "回到海面") }

    private const val TWILIGHT_FLOOR = Sea.ZONE_H * 2 + 0.02
    private const val MIDNIGHT_FLOOR = Sea.ZONE_H + 0.02
    private const val ABYSS_FLOOR = 0.06

    val ALL = listOf(
        Stage("a1", 0, "第一次下水", "看向水里捏一下，潜水器就开过去。捏一条鱼，它会自己靠近并拍照。去拍下蝠鲼，再回到海面。", TWILIGHT_FLOOR, 40.0, listOf(photo("manta", "蝠鲼"), home), 15.0),
        Stage("a2", 0, "胆小的海龟", "棱皮龟怕探照灯，灯开着它就躲。先关灯（面板上的按钮）再靠近，它就不跑了。", TWILIGHT_FLOOR, 40.0, listOf(photo("turtle", "棱皮龟"), photo("manta", "蝠鲼"), home), 15.0),
        Stage("a3", 0, "往下一层", "下到暮光带。这里的光开始变蓝。拍三条灯笼鱼：看它们肚子上的光点。", TWILIGHT_FLOOR, 50.0, listOf(photo("lantern", "灯笼鱼", 3), home), 18.0),
        Stage("a4", 0, "水母的光", "拍两只水母，再采一块冷水珊瑚。", TWILIGHT_FLOOR, 55.0, listOf(photo("jelly", "水母", 2), sample("coral", "冷水珊瑚"), home), 15.0),
        Stage("b1", 1, "彻底的黑", "新耐压舱能下到午夜带。这里只有灯照到的地方才看得见。路上拍一条灯笼鱼，再拍鮟鱇鱼头上的灯。", MIDNIGHT_FLOOR, 60.0, listOf(photo("lantern", "灯笼鱼"), photo("angler", "鮟鱇鱼"), home), 16.0),
        Stage("b2", 1, "大王乌贼", "它胆子小，游得也快。关灯慢慢靠近，到了再开灯，马上就拍。", MIDNIGHT_FLOOR, 70.0, listOf(photo("squid", "大王乌贼"), photo("angler", "鮟鱇鱼"), home), 16.0),
        Stage("b3", 1, "一路拍下去", "从上到下，每层都拍。注意电量。", MIDNIGHT_FLOOR, 95.0, listOf(photo("manta", "蝠鲼"), photo("jelly", "水母", 2), photo("angler", "鮟鱇鱼"), photo("squid", "大王乌贼"), home), 22.0),
        Stage("b4", 1, "省电", "这次电池小。少开灯，走直线。", MIDNIGHT_FLOOR, 45.0, listOf(photo("lantern", "灯笼鱼", 3), photo("angler", "鮟鱇鱼"), home), 7.0),
        Stage("c1", 2, "到海底", "深渊舱到了：一路下到海底。看，那是一艘沉船。回来时带一块珊瑚。", ABYSS_FLOOR, 110.0, listOf(reach(ABYSS_FLOOR, "下到海底"), photo("wreck", "沉船"), sample("coral", "冷水珊瑚"), home), 25.0),
        Stage("c2", 2, "鲸落", "一头鲸沉到了海底。拍下它的骨架，再找找旁边的管虫和上次留下的样本舱。", ABYSS_FLOOR, 100.0, listOf(photo("whale", "鲸落"), photo("tube", "巨型管虫"), photo("chest", "样本舱"), photo("jelly", "水母"), home), 24.0),
        Stage("c3", 2, "热液喷口", "采集喷口的硫化物和管虫的样本，回收样本舱，上来时再拍一次大王乌贼。", ABYSS_FLOOR, 95.0, listOf(sample("vent", "热液样本"), sample("tube", "管虫样本"), sample("chest", "样本舱"), photo("squid", "大王乌贼"), home), 25.0),
        Stage("c4", 2, "一次全程", "最后一次：从海面到海底，每一带都带回照片和样本。", ABYSS_FLOOR, 130.0, listOf(photo("turtle", "棱皮龟"), sample("coral", "冷水珊瑚"), photo("squid", "大王乌贼"), photo("angler", "鮟鱇鱼"), sample("vent", "热液样本"), photo("whale", "鲸落"), home), 28.0),
    )

    fun byId(id: String) = ALL.first { it.id == id }
}
