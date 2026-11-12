package me.rerere.rikkahub.ui.components.sticker

// 第一批：原来那 50 个里的前 25 个

private val CHEESE = art("....YY", "..YYYY", "YYYyYY", "YyYYYY", "YYYYyY")
private val CHEESE_B1 = art("......", "..YYY.", "YYYyYY", "YyYYYY", "YYYYyY")
private val CHEESE_B2 = art("......", "......", "YYYy..", "YyYYY.", "YYYYyY")
private val CRUMB = art("Y")
private val CRUMB_D = art("y")
private val KISS = art("RR.RR", ".RRR.")
private val NO_SIGN = art(".RRRRR.", "RWRRRWR", "RRWRWRR", "RRRWRRR", "RRWRWRR", "RWRRRWR", ".RRRRR.", "...C...", "...C...")
private val OK_SIGN = art(".NNNNN.", "NNNNNWN", "NNNNWNN", "NWNWNNN", "NNWNNNN", ".NNNNN.", "...C...", "...C...")
private val FLAG_A = art("CRRRR", "CRRRR", "CRRR.", "C....", "C....", "C....", "C....", "C....", "C....")
private val FLAG_B = art("CRRR.", "CRRRR", "CRRRR", "C....", "C....", "C....", "C....", "C....", "C....")
private val SPEED = art("GGG")
private val SPROUT_A = art("NN.NN.", ".NnN..", "..n...", "..n...")
private val SPROUT_B = art(".NN.NN", "..NnN.", "..n...", "..n...")
private val PHONE = art("gggg", "gBBg", "gBBg", "gBWg", "gggg")
private val HG_A = art("CCC", "GYG", ".Y.", "G.G", "CCC")
private val HG_B = art("CCC", "G.G", ".Y.", "GYG", "CCC")
private val DOTS1 = art("K")
private val DOTS2 = art("K.K")
private val DOTS3 = art("K.K.K")
private val PARTY_HAT = art("..Y..", "..R..", ".RYR.", ".YRY.", "RYRYR")
private val HALO = art(".YYYY.", "Y....Y")
private val WING_L_UP = art("TT..", ".TTT", "..TT")
private val WING_L_DN = art("..TT", ".TTT", "TT..")
private val WING_R_UP = art("..TT", "TTT.", "TT..")
private val WING_R_DN = art("TT..", "TTT.", "..TT")
private val STAR = art("..Y..", ".YYY.", "YYYYY", ".YYY.", ".Y.Y.")
private val BLANKET = art(
    "bbbbbbbbbbbbbbbbbbbb",
    "BBWBBBBWBBBBWBBBBWBB",
    "BBBBBBBBBBBBBBBBBBBB",
    "BBBBWBBBBWBBBBWBBBBW",
    "BBBBBBBBBBBBBBBBBBBB",
    "BBWBBBBWBBBBWBBBBWBB",
    "BBBBBBBBBBBBBBBBBBBB",
)
private val PILLOW = art("..SSSSSSSSSSSSSS..", ".SSSSSSSSSSSSSSSS.", "SSSSSSSSSSSSSSSSSS")
private val MOON = art(".YY", "YY.", "Y..", "YY.", ".YY")
private val SUN = art("Y.Y.Y", ".YYY.", "YYOYY", ".YYY.", "Y.Y.Y")
private val LAPTOP = art(".GGGGGGG.", ".GGGXGGG.", ".GGXXXGG.", ".GGGXGGG.", "ggggggggg")
private val MUG = art("BBB.", "BBBb", "BBB.")
private val STEAM = art("G")
private val ICEPACK = art("BBBBBBBB", "BSSSSSSB")
private val THERMO = art("GGGR")
private val BUBBLE_OK = art("GGGGGGG", "GWWWWNG", "GNWWNWG", "GWNNWWG", "GGGGGGG", ".G.....")
private val THOUGHT1 = art("G")
private val THOUGHT2 = art("GG", "GG")

internal fun stickerDefsA(): Map<String, StickerDef> = mapOf(

    // 蹲下去一蹦老高，举着钳子喊
    "宝宝！" to StickerDef(
        keys = listOf(
            SK(220, body = "squash", ey = 1, legs = "crouch", dy = 1, gy = -1, cl = "low", cr = "low", eyes = "wide"),
            SK(260, body = "tall", ey = -1, legs = "dangle", dy = -3, eyes = "happy", cl = "rUp", cr = "rUp", props = listOf(SP(EXCL, 17, -6))),
            SK(160, legs = "dangle", dy = -2, eyes = "happy", cl = "up", cr = "up", props = listOf(SP(EXCL, 17, -5))),
            SK(140, body = "squash", ey = 1, eyes = "happy", cl = "low", cr = "low"),
            SK(360, eyes = "happy", blush = true, cl = "rUp", cr = "up"),
            SK(360, eyes = "happy", blush = true, cl = "up", cr = "rUp"),
        ),
        fx = listOf(
            SFx(HEART, -3f, -1f, 6f, 1800, 0, drift = -1f, sway = .8f),
            SFx(HEART_S, 15f, 0f, 6f, 1800, 900, drift = 1f, sway = .8f),
            SFx(HEART_S, 6f, -4f, 4f, 1800, 500),
        ),
    ),

    // 看见了，往前扑着伸钳子，最后举起一颗大爱心
    "老公！" to StickerDef(
        keys = listOf(
            SK(300, ex = 1),
            SK(200, body = "leanR", eyes = "wide", ex = 1, cr = "reach1"),
            SK(180, body = "leanR", eyes = "wide", ex = 1, cr = "reach2", cl = "up", legs = "walkA", props = listOf(SP(HEART_S, 19, 1))),
            SK(180, body = "leanR", eyes = "wide", ex = 1, cr = "reach2", cl = "up", legs = "walkB", dy = -1, props = listOf(SP(HEART, 18, -1))),
            SK(180, body = "leanR", eyes = "wide", ex = 1, cr = "reach2", cl = "up", legs = "walkA", props = listOf(SP(HEART, 18, -2))),
            SK(260, body = "squash", ey = 1, eyes = "happy", cl = "low", cr = "low"),
            SK(560, body = "tall", ey = -1, eyes = "happy", blush = true, cl = "rUp", cr = "rUp", props = listOf(SP(HEART_BIG, 4, -9))),
        ),
    ),

    // 两只钳子捧着奶酪，一口一口啃，越啃越少
    "抱着奶酪啃" to StickerDef(
        keys = listOf(
            SK(320, cl = "front", lx = 4, cr = "front", rx = -4, props = listOf(SP(CHEESE, 5, 4))),
            SK(180, body = "squash", ey = 1, eyes = "shut", cl = "front", lx = 4, ly = 1, cr = "front", rx = -4, ry = 1, props = listOf(SP(CHEESE, 5, 5))),
            SK(260, eyes = "happy", cl = "front", lx = 4, cr = "front", rx = -4, props = listOf(SP(CHEESE_B1, 5, 4))),
            SK(180, body = "squash", ey = 1, eyes = "shut", cl = "front", lx = 4, ly = 1, cr = "front", rx = -4, ry = 1, props = listOf(SP(CHEESE_B1, 5, 5))),
            SK(260, eyes = "happy", blush = true, cl = "front", lx = 4, cr = "front", rx = -4, props = listOf(SP(CHEESE_B2, 5, 4))),
            SK(180, body = "squash", ey = 1, eyes = "shut", cl = "front", lx = 4, ly = 1, cr = "front", rx = -4, ry = 1, props = listOf(SP(CHEESE_B2, 5, 5))),
            SK(520, eyes = "happy", blush = true, cl = "front", lx = 4, cr = "front", rx = -4, props = listOf(SP(CHEESE_B2, 5, 4))),
        ),
        fx = listOf(
            SFx(CRUMB, 5f, 7f, -3f, 800, drift = -2f),
            SFx(CRUMB_D, 10f, 7f, -3f, 950, 400, drift = 2f),
            SFx(HEART_S, 13f, -1f, 4f, 2000),
        ),
    ),

    // 脸上一个口红印，晕得左右晃，头顶两颗星星绕圈
    "被亲晕了" to StickerDef(
        keys = listOf(
            SK(200, body = "leanL", eyes = "squint", blush = true, legs = "walkA", props = listOf(SP(KISS, 11, 5), SP(SPARK, -1, -3), SP(SPARK_P, 14, -3))),
            SK(200, eyes = "squint", blush = true, props = listOf(SP(KISS, 11, 5), SP(SPARK, 3, -6), SP(SPARK_P, 10, -1))),
            SK(200, body = "leanR", eyes = "squint", blush = true, legs = "walkB", props = listOf(SP(KISS, 11, 5), SP(SPARK, 7, -7), SP(SPARK_P, 6, -1))),
            SK(200, eyes = "squint", blush = true, props = listOf(SP(KISS, 11, 5), SP(SPARK, 11, -6), SP(SPARK_P, 2, -1))),
        ),
    ),

    // 小老鼠想跑，崽崽一钳子抓住往回拽
    "别想跑" to StickerDef(
        keys = listOf(
            SK(250, body = "leanL", eyes = "squint", cr = "reach1", legs = "walkA", props = listOf(SP(MOUSE_RUN, 16, 4), SP(SWEAT, 21, 1))),
            SK(250, body = "leanL", eyes = "squint", cr = "reach1", legs = "walkB", props = listOf(SP(MOUSE, 16, 4), SP(SMOKE_S, 21, 8))),
            SK(250, body = "leanL", eyes = "squint", cr = "reach1", legs = "walkA", props = listOf(SP(MOUSE_RUN, 16, 4), SP(SWEAT, 21, 0))),
            SK(400, body = "leanL", dx = -1, eyes = "shut", cr = "reach1", props = listOf(SP(MOUSE, 15, 4), SP(SWEAT, 0, -1))),
        ),
    ),

    // 举着红叉牌子使劲摇头
    "不对不对" to StickerDef(
        keys = listOf(
            SK(200, body = "turnL", eyes = "squint", ex = -1, cl = "up", props = listOf(SP(NO_SIGN, -4, -6), SP(MOTION, 17, 1))),
            SK(200, body = "turnR", eyes = "squint", ex = 1, cl = "up", props = listOf(SP(NO_SIGN, -5, -6), SP(MOTION, -6, 1))),
            SK(200, body = "turnL", eyes = "squint", ex = -1, cl = "up", props = listOf(SP(NO_SIGN, -4, -6), SP(MOTION, 17, 1))),
            SK(200, body = "turnR", eyes = "squint", ex = 1, cl = "up", props = listOf(SP(NO_SIGN, -5, -6), SP(MOTION, -6, 1))),
            SK(500, eyes = "shut", cl = "up", props = listOf(SP(NO_SIGN, -4, -6))),
        ),
    ),

    // 缩成一团闭着眼，左右拧着不肯
    "不要不想" to StickerDef(
        keys = listOf(
            SK(160, body = "squash2", ey = 2, eyes = "shut", cl = "front", lx = 3, cr = "front", rx = -3, legs = "tuck", dx = -1, props = listOf(SP(MOTION, -4, 3))),
            SK(160, body = "squash2", ey = 2, eyes = "shut", cl = "front", lx = 3, cr = "front", rx = -3, legs = "tuck", dx = 1, props = listOf(SP(MOTION, 18, 3))),
            SK(160, body = "squash2", ey = 2, eyes = "shut", cl = "front", lx = 3, cr = "front", rx = -3, legs = "tuck", dx = -1, props = listOf(SP(MOTION, -4, 3))),
            SK(160, body = "squash2", ey = 2, eyes = "shut", cl = "front", lx = 3, cr = "front", rx = -3, legs = "tuck", dx = 1, props = listOf(SP(MOTION, 18, 3))),
            SK(560, body = "squash2", ey = 2, eyes = "squint", cl = "front", lx = 3, cr = "front", rx = -3, legs = "tuck", props = listOf(SP(SMOKE_S, -3, 3))),
        ),
    ),

    // 扛着小红旗往前冲
    "出发" to StickerDef(
        keys = listOf(
            SK(180, body = "leanR", ex = 1, cr = "up", legs = "walkA", props = listOf(SP(FLAG_A, 15, -6), SP(SPEED, -5, 3))),
            SK(180, body = "leanR", ex = 1, cr = "up", legs = "walkB", dy = -1, props = listOf(SP(FLAG_B, 15, -7), SP(SPEED, -6, 6))),
            SK(180, body = "leanR", ex = 1, cr = "up", legs = "walkA", props = listOf(SP(FLAG_A, 15, -6), SP(SPEED, -5, 2))),
            SK(180, body = "leanR", ex = 1, eyes = "squint", cr = "up", legs = "walkB", dy = -1, props = listOf(SP(FLAG_B, 15, -7), SP(SPEED, -6, 5))),
        ),
    ),

    // 头顶一棵小芽晃来晃去，发呆，被自己吓一跳
    "呆萌" to StickerDef(
        keys = listOf(
            SK(600, props = listOf(SP(SPROUT_A, 5, -4))),
            SK(600, props = listOf(SP(SPROUT_B, 5, -4))),
            SK(120, eyes = "half", props = listOf(SP(SPROUT_A, 5, -4))),
            SK(500, ex = 1, props = listOf(SP(SPROUT_A, 5, -4))),
            SK(500, ex = -1, props = listOf(SP(SPROUT_B, 5, -4))),
            SK(200, dy = -1, legs = "dangle", eyes = "wide", cl = "up", cr = "up", props = listOf(SP(SPROUT_B, 5, -5))),
            SK(400, blush = true, props = listOf(SP(SPROUT_A, 5, -4))),
        ),
    ),

    // 捧着手机盯着看，脚一下一下点地，沙漏翻过来
    "等消息中" to StickerDef(
        keys = listOf(
            SK(420, ey = 1, cl = "front", lx = 3, cr = "front", rx = -3, props = listOf(SP(PHONE, 6, 4), SP(HG_A, -5, 3), SP(DOTS1, 16, -2))),
            SK(420, ey = 1, cl = "front", lx = 3, cr = "front", rx = -3, legs = "walkA", props = listOf(SP(PHONE, 6, 4), SP(HG_A, -5, 3), SP(DOTS2, 16, -2))),
            SK(420, ey = 1, cl = "front", lx = 3, cr = "front", rx = -3, props = listOf(SP(PHONE, 6, 4), SP(HG_A, -5, 3), SP(DOTS3, 16, -2))),
            SK(260, ey = 1, eyes = "half", cl = "front", lx = 3, cr = "front", rx = -3, legs = "walkA", props = listOf(SP(PHONE, 6, 4), SP(HG_B, -5, 3), SP(DOTS3, 16, -2))),
            SK(420, ey = 1, eyes = "half", cl = "front", lx = 3, cr = "front", rx = -3, props = listOf(SP(PHONE, 6, 4), SP(HG_B, -5, 3))),
        ),
    ),

    // 举着绿勾牌子用力点头
    "对的对的" to StickerDef(
        keys = listOf(
            SK(200, eyes = "happy", cr = "up", props = listOf(SP(OK_SIGN, 12, -6))),
            SK(200, body = "squash", ey = 1, eyes = "shut", cr = "up", ry = 1, props = listOf(SP(OK_SIGN, 12, -5))),
            SK(200, eyes = "happy", cr = "up", props = listOf(SP(OK_SIGN, 12, -6))),
            SK(200, body = "squash", ey = 1, eyes = "shut", cr = "up", ry = 1, props = listOf(SP(OK_SIGN, 12, -5))),
            SK(450, eyes = "happy", blush = true, cr = "up", props = listOf(SP(OK_SIGN, 12, -6), SP(SPARK, 19, -8))),
        ),
    ),

    // 戴尖尖帽，两只钳子轮流举，彩纸往下飘
    "恭喜" to StickerDef(
        keys = listOf(
            SK(260, eyes = "happy", cl = "rUp", cr = "up", props = listOf(SP(PARTY_HAT, 5, -5))),
            SK(260, eyes = "happy", cl = "up", cr = "rUp", dy = -1, legs = "dangle", props = listOf(SP(PARTY_HAT, 5, -6))),
            SK(260, eyes = "happy", blush = true, cl = "rUp", cr = "up", props = listOf(SP(PARTY_HAT, 5, -5))),
            SK(260, eyes = "happy", blush = true, cl = "up", cr = "rUp", dy = -1, legs = "dangle", props = listOf(SP(PARTY_HAT, 5, -6))),
        ),
        fx = listOf(
            SFx(CONF_R, -4f, -8f, -18f, 1600, 0, sway = 1f),
            SFx(CONF_Y, 2f, -8f, -18f, 1900, 500, sway = 1f),
            SFx(CONF_B, 9f, -9f, -18f, 1700, 900, sway = 1f),
            SFx(CONF_N, 14f, -8f, -18f, 2000, 300, sway = 1f),
            SFx(CONF_P, 18f, -9f, -18f, 1800, 1200, sway = 1f),
            SFx(CONF_V, 5f, -9f, -18f, 2100, 700, sway = 1f),
        ),
    ),

    // 捂着肚子笑得一抖一抖，笑出眼泪，再左右笑倒
    "哈哈哈" to StickerDef(
        keys = listOf(
            SK(120, eyes = "happy", cl = "front", lx = 2, ly = 1, cr = "front", rx = -2, ry = 1),
            SK(120, body = "squash", ey = 1, eyes = "happy", cl = "front", lx = 2, ly = 2, cr = "front", rx = -2, ry = 2),
            SK(120, eyes = "happy", cl = "front", lx = 2, ly = 1, cr = "front", rx = -2, ry = 1),
            SK(120, body = "squash", ey = 1, eyes = "happy", cl = "front", lx = 2, ly = 2, cr = "front", rx = -2, ry = 2),
            SK(120, eyes = "happy", cl = "front", lx = 2, ly = 1, cr = "front", rx = -2, ry = 1),
            SK(120, body = "squash", ey = 1, eyes = "happy", cl = "front", lx = 2, ly = 2, cr = "front", rx = -2, ry = 2),
            SK(300, body = "leanL", eyes = "happy", cl = "rDown", cr = "rDown", legs = "walkA"),
            SK(300, body = "leanR", eyes = "happy", cl = "rDown", cr = "rDown", legs = "walkB"),
        ),
        fx = listOf(
            SFx(TEAR, 2f, 3f, 2f, 700, drift = -3f),
            SFx(TEAR, 13f, 3f, 2f, 700, 350, drift = 3f),
        ),
    ),

    // 长了小翅膀顶着光圈，哭着往上飘
    "含泪升天" to StickerDef(
        keys = listOf(
            SK(400, eyes = "shut", legs = "dangle", props = listOf(SP(HALO, 5, -4), SP(WING_L_UP, -3, 1, 0), SP(WING_R_UP, 15, 1, 0))),
            SK(400, eyes = "shut", legs = "dangle", dy = -1, props = listOf(SP(HALO, 5, -5), SP(WING_L_DN, -3, 1, 0), SP(WING_R_DN, 15, 1, 0))),
            SK(400, eyes = "shut", legs = "dangle", dy = -2, props = listOf(SP(HALO, 5, -6), SP(WING_L_UP, -3, 0, 0), SP(WING_R_UP, 15, 0, 0))),
            SK(400, eyes = "shut", legs = "dangle", dy = -1, props = listOf(SP(HALO, 5, -5), SP(WING_L_DN, -3, 1, 0), SP(WING_R_DN, 15, 1, 0))),
        ),
        fx = listOf(
            SFx(TEAR, 4f, 4f, -5f, 900),
            SFx(TEAR, 11f, 4f, -5f, 900, 450),
            SFx(SPARK, 0f, -6f, 2f, 1500),
            SFx(SPARK_P, 15f, -7f, 2f, 1500, 750),
        ),
    ),

    // 举着钳子蹦，头顶大星星，四周闪
    "好棒" to StickerDef(
        keys = listOf(
            SK(200, eyes = "happy", cl = "up", cr = "up", props = listOf(SP(STAR, 5, -7))),
            SK(200, eyes = "happy", cl = "rUp", cr = "rUp", dy = -1, legs = "dangle", props = listOf(SP(STAR, 5, -8))),
            SK(200, eyes = "happy", blush = true, cl = "up", cr = "up", props = listOf(SP(STAR, 5, -7))),
            SK(200, eyes = "happy", blush = true, cl = "rUp", cr = "rUp", dy = -1, legs = "dangle", props = listOf(SP(STAR, 5, -8))),
        ),
        fx = listOf(
            SFx(SPARK, -3f, -3f, 1f, 900),
            SFx(SPARK_P, 17f, -2f, 1f, 900, 450),
            SFx(SPARK, 15f, -7f, 1f, 1200, 300),
        ),
    ),

    // 快快地点头加小蹦，两只钳子轮流举
    "好呀好呀" to StickerDef(
        keys = listOf(
            SK(150, eyes = "happy", cl = "up"),
            SK(150, eyes = "happy", cr = "up", dy = -1, legs = "dangle"),
            SK(150, eyes = "happy", cl = "up"),
            SK(150, body = "squash", ey = 1, eyes = "shut"),
            SK(150, eyes = "happy", blush = true, cr = "up"),
            SK(150, eyes = "happy", blush = true, cl = "up", dy = -1, legs = "dangle"),
            SK(150, eyes = "happy", blush = true, cr = "up"),
            SK(150, body = "squash", ey = 1, eyes = "shut", blush = true),
        ),
        fx = listOf(SFx(HEART_S, 14f, -1f, 5f, 1200)),
    ),

    // 扭过身子抱着钳子，哼一声喷出一口气
    "哼！" to StickerDef(
        keys = listOf(
            SK(600, body = "turnL", eyes = "shut", ex = -1, cl = "front", lx = 4, cr = "front", rx = -4, props = listOf(SP(ANGER, 13, -3))),
            SK(200, body = "squash", ey = 1, eyes = "shut", ex = -1, cl = "front", lx = 4, ly = 1, cr = "front", rx = -4, ry = 1, props = listOf(SP(ANGER, 13, -2), SP(SMOKE, -4, 4))),
            SK(600, body = "turnL", eyes = "shut", ex = -1, cl = "front", lx = 4, cr = "front", rx = -4, props = listOf(SP(ANGER, 13, -3), SP(SMOKE_S, -5, 3))),
        ),
    ),

    // 盖着小被子睡，一起一伏，飘 Z
    "晚安" to StickerDef(
        keys = listOf(
            SK(1000, eyes = "shut", props = listOf(SP(PILLOW, -1, -2, 0), SP(MOON, -6, -8, 0), SP(BLANKET, -2, 4, 2))),
            SK(1000, body = "squash", ey = 1, eyes = "shut", props = listOf(SP(PILLOW, -1, -2, 0), SP(MOON, -6, -8, 0), SP(BLANKET, -2, 4, 2))),
        ),
        fx = listOf(
            SFx(ZZ, 12f, -2f, 6f, 2400, drift = 3f),
            SFx(ZZ, 14f, -1f, 6f, 2400, 1200, drift = 3f),
            SFx(SPARK, 18f, -7f, 0f, 1600, 800),
        ),
    ),

    // 太阳慢慢升起来，崽崽打哈欠、伸懒腰、挥钳子
    "早安" to StickerDef(
        keys = listOf(
            SK(400, eyes = "half", props = listOf(SP(SUN, 16, 0, 0))),
            SK(400, body = "squash", ey = 1, eyes = "shut", props = listOf(SP(SUN, 16, -2, 0))),
            SK(500, body = "tall", ey = -1, eyes = "shut", cl = "rUp", cr = "rUp", props = listOf(SP(SUN, 16, -4, 0))),
            SK(300, cl = "rUp", cr = "rUp", props = listOf(SP(SUN, 16, -5, 0))),
            SK(400, eyes = "happy", blush = true, cr = "up", props = listOf(SP(SUN, 16, -6, 0))),
            SK(400, eyes = "happy", blush = true, cr = "rUp", props = listOf(SP(SUN, 16, -6, 0))),
        ),
    ),

    // 对着电脑噼里啪啦敲键盘，旁边一杯热饮，偶尔困得眨眼
    "上班中" to StickerDef(
        keys = listOf(
            SK(120, ey = 1, cl = "front", lx = 3, ly = 2, cr = "front", rx = -3, ry = 1, props = listOf(SP(LAPTOP, 3, 5), SP(MUG, -5, 6))),
            SK(120, ey = 1, cl = "front", lx = 3, ly = 1, cr = "front", rx = -3, ry = 2, props = listOf(SP(LAPTOP, 3, 5), SP(MUG, -5, 6))),
            SK(120, ey = 1, cl = "front", lx = 3, ly = 2, cr = "front", rx = -3, ry = 1, props = listOf(SP(LAPTOP, 3, 5), SP(MUG, -5, 6))),
            SK(120, ey = 1, cl = "front", lx = 3, ly = 1, cr = "front", rx = -3, ry = 2, props = listOf(SP(LAPTOP, 3, 5), SP(MUG, -5, 6))),
            SK(120, ey = 1, cl = "front", lx = 3, ly = 2, cr = "front", rx = -3, ry = 1, props = listOf(SP(LAPTOP, 3, 5), SP(MUG, -5, 6))),
            SK(120, ey = 1, cl = "front", lx = 3, ly = 1, cr = "front", rx = -3, ry = 2, props = listOf(SP(LAPTOP, 3, 5), SP(MUG, -5, 6))),
            SK(400, ey = 1, eyes = "half", cl = "front", lx = 3, ly = 1, cr = "front", rx = -3, ry = 1, props = listOf(SP(LAPTOP, 3, 5), SP(MUG, -5, 6))),
            SK(160, ey = 1, eyes = "shut", cl = "front", lx = 3, ly = 1, cr = "front", rx = -3, ry = 1, props = listOf(SP(LAPTOP, 3, 5), SP(MUG, -5, 6))),
        ),
        fx = listOf(
            SFx(STEAM, -4f, 4f, 4f, 1400, sway = .8f),
            SFx(STEAM, -3f, 4f, 4f, 1400, 700, sway = .8f),
        ),
    ),

    // 气得原地跺脚，钳子乱挥，头顶冒烟
    "生气冒烟" to StickerDef(
        keys = listOf(
            SK(120, body = "squash", ey = 1, eyes = "squint", cl = "low", cr = "low", legs = "walkA", props = listOf(SP(ANGER, 13, -3))),
            SK(120, eyes = "squint", cl = "rUp", cr = "rUp", legs = "walkB", props = listOf(SP(ANGER, 13, -4))),
            SK(120, body = "squash", ey = 1, eyes = "squint", cl = "low", cr = "low", legs = "walkB", props = listOf(SP(ANGER, 13, -3))),
            SK(120, eyes = "squint", cl = "rUp", cr = "rUp", legs = "walkA", props = listOf(SP(ANGER, 13, -4))),
        ),
        fx = listOf(
            SFx(SMOKE, 4f, -2f, 5f, 900, drift = -2f),
            SFx(SMOKE, 9f, -2f, 5f, 900, 450, drift = 2f),
            SFx(SMOKE_S, 7f, -1f, 6f, 1100, 200),
        ),
    ),

    // 头顶冰袋、嘴里叼体温计，冷得一抖一抖
    "生病了" to StickerDef(
        keys = listOf(
            SK(400, eyes = "half", props = listOf(SP(ICEPACK, 4, -2), SP(THERMO, 7, 4))),
            SK(120, eyes = "half", dx = 1, props = listOf(SP(ICEPACK, 5, -2), SP(THERMO, 8, 4))),
            SK(120, eyes = "half", props = listOf(SP(ICEPACK, 4, -2), SP(THERMO, 7, 4))),
            SK(120, eyes = "half", dx = 1, props = listOf(SP(ICEPACK, 5, -2), SP(THERMO, 8, 4))),
            SK(500, eyes = "shut", props = listOf(SP(ICEPACK, 4, -2), SP(THERMO, 7, 4))),
        ),
        fx = listOf(SFx(SWEAT, 13f, 0f, -3f, 1600)),
    ),

    // 一跳起来敬个礼，冒出一个打勾的小气泡
    "收到" to StickerDef(
        keys = listOf(
            SK(300),
            SK(180, dy = -1, legs = "dangle", eyes = "wide", cr = "rUp"),
            SK(700, eyes = "happy", cr = "rUp", props = listOf(SP(BUBBLE_OK, 15, -6))),
            SK(400, eyes = "happy", blush = true, cr = "rUp", props = listOf(SP(BUBBLE_OK, 15, -6))),
        ),
    ),

    // 歪着身子托腮往上看，冒小泡泡，最后冒出问号
    "思考" to StickerDef(
        keys = listOf(
            SK(700, body = "leanR", ex = 1, ey = -1, cr = "front", rx = -3, ry = -1, props = listOf(SP(THOUGHT1, 15, -1))),
            SK(700, body = "leanR", ex = 1, ey = -1, cr = "front", rx = -3, ry = -1, props = listOf(SP(THOUGHT1, 15, -1), SP(THOUGHT2, 16, -3))),
            SK(900, body = "leanR", ex = 1, ey = -1, cr = "front", rx = -3, ry = -1, props = listOf(SP(THOUGHT1, 15, -1), SP(THOUGHT2, 16, -3), SP(QMARK, 17, -8))),
            SK(160, body = "leanR", eyes = "half", ex = 1, ey = -1, cr = "front", rx = -3, ry = -1, props = listOf(SP(QMARK, 17, -8))),
            SK(600, body = "leanL", ex = -1, ey = -1, cl = "front", lx = 3, ly = -1, props = listOf(SP(QMARK, -4, -7))),
        ),
    ),

    // 和小老鼠挨着蹭，两个都眯眼，中间冒爱心
    "贴贴" to StickerDef(
        keys = listOf(
            SK(500, body = "leanR", eyes = "happy", ex = 1, cr = "front", rx = -1, props = listOf(SP(MOUSE, 16, 4))),
            SK(600, body = "leanR", dx = 1, eyes = "happy", blush = true, cr = "front", rx = -1, props = listOf(SP(MOUSE_HAPPY, 15, 4))),
            SK(400, body = "leanR", dx = 1, eyes = "happy", blush = true, cr = "front", rx = -1, legs = "walkA", props = listOf(SP(MOUSE_HAPPY, 15, 4))),
        ),
        fx = listOf(
            SFx(HEART, 13f, -2f, 5f, 1600),
            SFx(HEART_S, 16f, -1f, 5f, 1600, 800),
        ),
    ),
)
