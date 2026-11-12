package me.rerere.rikkahub.ui.components.sticker

// 第二批：原来那些表情里的后 25 个（钳子比心换成了破防了）

private val B_MOUSE_KICK = art(".GG...GG", ".GpGGGpG", ".GGKGKGG", ".GGGpGGG", "..GGGGG.", "GGG...G.")
private val B_STAR = art(".Y.", "YYY", ".Y.")
private val B_KISS = art("RR.RR", ".RRR.")
private val B_KISS_BIG = art(".RRR.RRR.", "RRRRRRRRR", ".RRRRRRR.", "...RRR...")
private val B_WALL = PixArt(List(14) { "Cccccccc" })
private val B_PHONE = art("gggg", "gBBg", "gBBg", "gBWg", "gggg")
private val B_PHONES = art(".gggggggggggg.", "g............g", "R............R", "R............R")
private val B_PUD1 = art("..TTTTTTTTTT..")
private val B_PUD2 = art(".TTTTTTTTTTTT.", "TTTTTTTTTTTTTT")
private val B_PUD3 = art(".TTTTTTTTTTTTTTTT.", "TTTTTTTTTTTTTTTTTT")
private val B_TV1 = art("gggggg", "gBBBBg", "gBSBBg", "gBBBBg", "gggggg", "..gg..")
private val B_TV2 = art("gggggg", "gSBBBg", "gBBBSg", "gBSBBg", "gggggg", "..gg..")
private val B_PAD = art(".GGGGGG.", "GKGGGGRG", "GGGGGGGG", ".GG..GG.")
private val B_CHIPS = art(".RR.", "RYYR", "RRRR", "RRRR")
private val B_BOWL = art("..WWW..", ".WWWWW.", "OOOOOOO", ".OOOOO.", "..OOO..")
private val B_BOWL_H = art(".......", "..WWW..", "OOOOOOO", ".OOOOO.", "..OOO..")
private val B_BOWL_E = art(".......", ".......", "OOOOOOO", ".OOOOO.", "..OOO..")
private val B_CHOP = art("C..", ".C.", "..C")
private val B_STEAM = art("G")
private val B_DOTS = art("K.K.K")
private val B_CLOCK = art("R...R", ".RRR.", "RWWWR", "RWKWR", "RWWKR", ".RRR.")
private val B_BLANKET = art(
    "bbbbbbbbbbbbbbbbbbbb",
    "BBWBBBBWBBBBWBBBBWBB",
    "BBBBBBBBBBBBBBBBBBBB",
    "BBBBWBBBBWBBBBWBBBBW",
    "BBBBBBBBBBBBBBBBBBBB",
    "BBWBBBBWBBBBWBBBBWBB",
    "BBBBBBBBBBBBBBBBBBBB",
)
private val B_PILLOW = art("..SSSSSSSSSSSSSS..", ".SSSSSSSSSSSSSSSS.", "SSSSSSSSSSSSSSSSSS")
private val B_SPEED = art("GGG")
private val B_BOX = art("RRRRRR", "RrrrrR", "RRRRRR", "RRRRRR")
private val B_LID = art("rrrrrr")
private val B_BOX_OPEN = art("RWYYWR", "RRRRRR", "RRRRRR")
private val B_RING = art("..B..", ".YYY.", "Y...Y", "Y...Y", ".YYY.")
private val B_BOLT = art("..YY", ".YY.", "YYYY", ".YY.", "YY..", "Y...")
private val B_CRACK = art(".K.", "..K", ".K.", "K..", ".K.", "..K", ".K.")
private val B_HEART_BROKEN = art(".RR.RR.", "RRRKRRR", "RRKRRRR", ".RRKRR.", "..RKR..", "...R...")

// 横着来回跑，两只钳子一上一下
private fun crabWalk(): List<SK> {
    val xs = listOf(-1, -2, -3, -4, -3, -2, -1, 0, 1, 2, 3, 4, 3, 2, 1, 0)
    return xs.mapIndexed { i, x ->
        val a = i % 2 == 0
        SK(
            140, dx = x, eyes = "happy",
            legs = if (a) "walkA" else "walkB",
            cl = if (a) "up" else "low",
            cr = if (a) "low" else "up",
        )
    } + listOf(
        SK(700, eyes = "open", blush = true, props = listOf(SP(SPARK, 15, -3))),
        SK(160, eyes = "shut", blush = true),
        SK(400, eyes = "happy", blush = true),
    )
}

internal fun stickerDefsB(): Map<String, StickerDef> = mapOf(

    // 小老鼠冲过来一脚把崽崽踹上天，崽崽掉下来摔成饼，头上冒星星
    "一脚踢飞" to StickerDef(
        keys = listOf(
            SK(500, eyes = "happy", blush = true, props = listOf(SP(MOUSE_RUN, 22, 4))),
            SK(220, eyes = "happy", blush = true, props = listOf(SP(MOUSE, 19, 4), SP(ANGER, 19, -2))),
            SK(140, body = "leanL", dx = -1, eyes = "squint", cl = "up", cr = "up", props = listOf(SP(B_MOUSE_KICK, 15, 4), SP(B_STAR, 13, 3, 2))),
            SK(110, body = "turnL", dx = -4, dy = -4, eyes = "squint", legs = "flailA", cl = "rUp", cr = "rUp", props = listOf(SP(B_MOUSE_KICK, 15, 4), SP(MOTION, 4, 3))),
            SK(110, body = "turnR", dx = -7, dy = -8, eyes = "squint", legs = "flailB", cl = "rUp", cr = "rUp", props = listOf(SP(MOUSE, 16, 4), SP(MOTION, 1, -1))),
            SK(110, body = "turnL", dx = -10, dy = -12, eyes = "squint", legs = "flailC", props = listOf(SP(MOUSE, 16, 4))),
            SK(500, hide = true, props = listOf(SP(MOUSE_HAPPY, 16, 4), SP(B_STAR, -5, -8))),
            SK(300, hide = true, props = listOf(SP(MOUSE_HAPPY, 16, 4))),
            SK(120, dx = -1, dy = -6, eyes = "wide", legs = "dangle", cl = "rUp", cr = "rUp", props = listOf(SP(MOUSE_HAPPY, 16, 4))),
            SK(700, body = "flat", ey = 3, eyes = "squint", legs = "tuck", cl = "low", ly = 1, cr = "low", ry = 1, props = listOf(SP(MOUSE_HAPPY, 16, 4), SP(SPARK, 2, 1), SP(SPARK_P, 11, 0))),
            SK(700, body = "flat", ey = 3, eyes = "squint", legs = "tuck", cl = "low", ly = 1, cr = "low", ry = 1, props = listOf(SP(MOUSE_HAPPY, 16, 4), SP(SPARK_P, 4, -1), SP(SPARK, 9, 1))),
            SK(400, eyes = "half", blush = true, props = listOf(SP(MOUSE, 16, 4))),
        ),
    ),

    // 踮脚嘟嘴，啵一下，唇印飞出来
    "亲你一口" to StickerDef(
        keys = listOf(
            SK(400),
            SK(260, body = "tall", ey = -1, dy = -1, legs = "dangle", eyes = "shut", blush = true),
            SK(200, body = "squash", ey = 1, eyes = "shut", blush = true, props = listOf(SP(B_KISS, 6, 4, 2))),
            SK(200, eyes = "shut", blush = true, props = listOf(SP(B_KISS_BIG, 3, -3, 2))),
            SK(320, eyes = "happy", blush = true, cl = "up", cr = "up", props = listOf(SP(B_KISS_BIG, 3, -6, 2))),
            SK(420, eyes = "happy", blush = true),
        ),
        fx = listOf(
            SFx(HEART_S, -2f, 1f, 6f, 1600, drift = -1f, sway = .7f),
            SFx(HEART_S, 15f, 0f, 6f, 1600, 800, drift = 1f, sway = .7f),
        ),
    ),

    // 躲在墙后，先露一只眼，被发现了吓一跳又缩回去
    "偷偷探头" to StickerDef(
        keys = listOf(
            SK(500, dx = 12, props = listOf(SP(B_WALL, 14, -2, 2))),
            SK(300, dx = 10, eyes = "half", props = listOf(SP(B_WALL, 14, -2, 2))),
            SK(500, dx = 9, ex = -1, props = listOf(SP(B_WALL, 14, -2, 2))),
            SK(150, dx = 9, eyes = "shut", props = listOf(SP(B_WALL, 14, -2, 2))),
            SK(500, dx = 9, ex = -1, props = listOf(SP(B_WALL, 14, -2, 2), SP(QMARK, 8, -5))),
            SK(220, dx = 7, eyes = "wide", ex = -1, cl = "up", props = listOf(SP(B_WALL, 14, -2, 2), SP(EXCL, 7, -6))),
            SK(260, dx = 12, props = listOf(SP(B_WALL, 14, -2, 2), SP(MOTION, 12, 2))),
        ),
    ),

    // 捧着手机傻笑，一抬眼赶紧藏到身后，假装吹口哨
    "偷看手机" to StickerDef(
        keys = listOf(
            SK(500, ey = 1, blush = true, cl = "front", lx = 3, cr = "front", rx = -3, props = listOf(SP(B_PHONE, 6, 4))),
            SK(400, ey = 1, eyes = "happy", blush = true, cl = "front", lx = 3, cr = "front", rx = -3, props = listOf(SP(B_PHONE, 6, 4), SP(HEART_S, 11, -1))),
            SK(220, ex = 1, ey = -1, eyes = "wide", cl = "front", lx = 3, cr = "front", rx = -3, props = listOf(SP(B_PHONE, 6, 4))),
            SK(160, eyes = "wide", props = listOf(SP(B_PHONE, 0, 5, 0), SP(MOTION, 1, 1))),
            SK(600, ex = -1, props = listOf(SP(B_PHONE, 0, 5, 0), SP(NOTE, 15, -3))),
            SK(400, ex = 1, props = listOf(SP(B_PHONE, 0, 5, 0), SP(NOTE, 16, -5))),
        ),
    ),

    // 钳子托着下巴左右歪头，眨眨眼，最后蹦一下
    "卖萌" to StickerDef(
        keys = listOf(
            SK(320, body = "leanL", eyes = "happy", blush = true, cl = "front", lx = 3, ly = -1, cr = "front", rx = -3, ry = -1),
            SK(150, body = "leanL", eyes = "shut", blush = true, cl = "front", lx = 3, ly = -1, cr = "front", rx = -3, ry = -1),
            SK(320, body = "leanR", eyes = "happy", blush = true, cl = "front", lx = 3, ly = -1, cr = "front", rx = -3, ry = -1),
            SK(400, body = "leanR", ex = 1, blush = true, cl = "front", lx = 3, ly = -1, cr = "front", rx = -3, ry = -1, props = listOf(SP(SPARK, 16, -2))),
            SK(200, body = "squash", ey = 1, eyes = "happy", blush = true, cl = "front", lx = 3, cr = "front", rx = -3),
            SK(320, body = "tall", ey = -1, dy = -1, legs = "dangle", eyes = "happy", blush = true, cl = "rUp", cr = "rUp"),
        ),
        fx = listOf(
            SFx(SPARK_P, -3f, -2f, 2f, 1200),
            SFx(HEART_S, 15f, -3f, 4f, 1500, 600),
        ),
    ),

    // 戴着耳机闭眼点头，左右晃，脚打拍子
    "听歌" to StickerDef(
        keys = listOf(
            SK(250, eyes = "shut", props = listOf(SP(B_PHONES, 1, -1, 2))),
            SK(250, body = "squash", ey = 1, eyes = "shut", legs = "walkA", props = listOf(SP(B_PHONES, 1, 0, 2))),
            SK(250, body = "leanL", eyes = "shut", props = listOf(SP(B_PHONES, 1, -1, 2))),
            SK(250, body = "squash", ey = 1, eyes = "happy", legs = "walkB", props = listOf(SP(B_PHONES, 1, 0, 2))),
            SK(250, body = "leanR", eyes = "shut", cr = "up", props = listOf(SP(B_PHONES, 1, -1, 2))),
            SK(250, body = "squash", ey = 1, eyes = "happy", cr = "up", props = listOf(SP(B_PHONES, 1, 0, 2))),
        ),
        fx = listOf(
            SFx(NOTE, -4f, 2f, 7f, 1400, drift = -1f, sway = .8f),
            SFx(NOTE, 17f, 1f, 7f, 1400, 700, drift = 1f, sway = .8f),
        ),
    ),

    // 眼泪往两边喷，水坑越积越大
    "哭成小水坑" to StickerDef(
        keys = listOf(
            SK(300, eyes = "shut", props = listOf(SP(B_PUD1, 1, 10, 0))),
            SK(300, body = "squash", ey = 1, eyes = "shut", props = listOf(SP(B_PUD1, 1, 10, 0))),
            SK(300, eyes = "shut", props = listOf(SP(B_PUD2, 1, 10, 0))),
            SK(300, body = "squash", ey = 1, eyes = "shut", props = listOf(SP(B_PUD2, 1, 10, 0))),
            SK(300, eyes = "shut", cl = "front", lx = 3, ly = -2, cr = "front", rx = -3, ry = -2, props = listOf(SP(B_PUD3, -1, 10, 0))),
            SK(300, body = "squash", ey = 1, eyes = "shut", cl = "front", lx = 3, ly = -1, cr = "front", rx = -3, ry = -1, props = listOf(SP(B_PUD3, -1, 10, 0))),
        ),
        fx = listOf(
            SFx(TEAR, 3f, 3f, -7f, 500, drift = -3f),
            SFx(TEAR, 12f, 3f, -7f, 500, 250, drift = 3f),
            SFx(TEAR, 2f, 3f, -7f, 600, 300, drift = -5f),
            SFx(TEAR, 13f, 3f, -7f, 600, 0, drift = 5f),
        ),
    ),

    // 缩成一团，两只钳子在身前戳戳戳，憋着一颗眼泪
    "委屈到掉泪" to StickerDef(
        keys = listOf(
            SK(500, body = "squash2", ey = 2, eyes = "wide", legs = "tuck", cl = "front", lx = 5, cr = "front", rx = -5),
            SK(200, body = "squash2", ey = 2, eyes = "wide", legs = "tuck", cl = "front", lx = 6, cr = "front", rx = -6),
            SK(200, body = "squash2", ey = 2, eyes = "wide", legs = "tuck", cl = "front", lx = 5, cr = "front", rx = -5),
            SK(200, body = "squash2", ey = 2, eyes = "wide", legs = "tuck", cl = "front", lx = 6, cr = "front", rx = -6),
            SK(600, body = "squash2", ey = 2, eyes = "half", legs = "tuck", cl = "front", lx = 5, cr = "front", rx = -5),
            SK(160, body = "squash2", ey = 2, eyes = "shut", legs = "tuck", cl = "front", lx = 5, cr = "front", rx = -5),
        ),
        fx = listOf(
            SFx(TEAR, 4f, 6f, -4f, 1600),
            SFx(TEAR, 11f, 6f, -4f, 1600, 800),
        ),
    ),

    // 对着电视狂按手柄，旁边一包薯片，赢了就扭两下
    "宅在家里我好开心" to StickerDef(
        keys = listOf(
            SK(160, body = "squash", ey = 1, ex = -1, legs = "tuck", cl = "front", lx = 3, ly = 1, cr = "front", rx = -3, props = listOf(SP(B_TV1, -7, 1, 0), SP(B_PAD, 4, 5), SP(B_CHIPS, 16, 6, 0))),
            SK(160, body = "squash", ey = 1, ex = -1, legs = "tuck", cl = "front", lx = 3, cr = "front", rx = -3, ry = 1, props = listOf(SP(B_TV2, -7, 1, 0), SP(B_PAD, 4, 5), SP(B_CHIPS, 16, 6, 0))),
            SK(160, body = "squash", ey = 1, ex = -1, legs = "tuck", cl = "front", lx = 3, ly = 1, cr = "front", rx = -3, props = listOf(SP(B_TV1, -7, 1, 0), SP(B_PAD, 4, 5), SP(B_CHIPS, 16, 6, 0))),
            SK(160, body = "squash", ey = 1, ex = -1, legs = "tuck", cl = "front", lx = 3, cr = "front", rx = -3, ry = 1, props = listOf(SP(B_TV2, -7, 1, 0), SP(B_PAD, 4, 5), SP(B_CHIPS, 16, 6, 0))),
            SK(400, body = "leanL", eyes = "happy", blush = true, cl = "front", lx = 3, cr = "front", rx = -3, props = listOf(SP(B_TV1, -7, 1, 0), SP(B_PAD, 4, 5), SP(B_CHIPS, 16, 6, 0), SP(HEART_S, 14, -2))),
            SK(400, body = "leanR", eyes = "happy", blush = true, cl = "front", lx = 3, cr = "front", rx = -3, props = listOf(SP(B_TV2, -7, 1, 0), SP(B_PAD, 4, 5), SP(B_CHIPS, 16, 6, 0), SP(HEART_S, 15, -3))),
        ),
    ),

    // 钳子敲着要饭，夹起来一口一口吃光
    "开饭" to StickerDef(
        keys = listOf(
            SK(160, eyes = "wide", cl = "up", cr = "up", props = listOf(SP(B_BOWL, 5, 6))),
            SK(160, eyes = "happy", cl = "low", cr = "low", props = listOf(SP(B_BOWL, 5, 6))),
            SK(160, eyes = "wide", cl = "up", cr = "up", props = listOf(SP(B_BOWL, 5, 6))),
            SK(160, eyes = "happy", cl = "low", cr = "low", props = listOf(SP(B_BOWL, 5, 6))),
            SK(300, body = "squash", ey = 1, eyes = "shut", cr = "front", rx = -4, props = listOf(SP(B_BOWL_H, 5, 6), SP(B_CHOP, 9, 2, 2))),
            SK(300, eyes = "happy", blush = true, props = listOf(SP(B_BOWL_H, 5, 6))),
            SK(300, body = "squash", ey = 1, eyes = "shut", cr = "front", rx = -4, props = listOf(SP(B_BOWL_E, 5, 6), SP(B_CHOP, 9, 2, 2))),
            SK(500, eyes = "happy", blush = true, cl = "rUp", cr = "rUp", props = listOf(SP(B_BOWL_E, 5, 6), SP(HEART_S, 14, -2))),
        ),
        fx = listOf(
            SFx(B_STEAM, 7f, 4f, 4f, 1200, sway = .8f),
            SFx(B_STEAM, 9f, 4f, 4f, 1200, 600, sway = .8f),
        ),
    ),

    // 小老鼠想跑，一钳子拽回来抱住
    "强行在一起" to StickerDef(
        keys = listOf(
            SK(300, ex = 1, props = listOf(SP(MOUSE_RUN, 17, 4))),
            SK(200, body = "leanR", eyes = "wide", ex = 1, cr = "reach2", props = listOf(SP(MOUSE_RUN, 19, 4))),
            SK(300, body = "leanR", eyes = "squint", ex = 1, cr = "reach2", legs = "walkA", props = listOf(SP(MOUSE, 19, 4), SP(SWEAT, 18, 1))),
            SK(260, body = "leanL", eyes = "squint", cr = "reach1", legs = "walkB", props = listOf(SP(MOUSE, 16, 4), SP(SWEAT, 18, 1))),
            SK(800, eyes = "happy", blush = true, cr = "front", rx = -2, props = listOf(SP(MOUSE, 13, 4), SP(SWEAT, 19, 2), SP(HEART, 9, -4))),
        ),
    ),

    // 横着来回跑，钳子一上一下
    "我只是一只小螃蟹呀" to StickerDef(keys = crabWalk()),

    // 钳子捂住眼睛扭，偷偷放下一只往外看
    "捂脸害羞" to StickerDef(
        keys = listOf(
            SK(500, body = "leanL", eyes = "shut", blush = true, cl = "front", lx = 4, ly = -3, cr = "front", rx = -3, ry = -3),
            SK(500, body = "leanR", eyes = "shut", blush = true, cl = "front", lx = 4, ly = -3, cr = "front", rx = -3, ry = -3),
            SK(400, blush = true, cl = "front", lx = 4, ly = -3),
            SK(150, eyes = "wide", blush = true, cl = "front", lx = 4, ly = -3),
            SK(500, body = "squash", ey = 1, eyes = "shut", blush = true, legs = "tuck", cl = "front", lx = 4, ly = -2, cr = "front", rx = -3, ry = -2),
        ),
        fx = listOf(SFx(HEART_S, 15f, -1f, 5f, 1500)),
    ),

    // 伸长钳子够你，一蹦一蹦等你牵
    "牵我吧" to StickerDef(
        keys = listOf(
            SK(300, ex = 1, blush = true, cr = "reach1"),
            SK(400, body = "leanR", ex = 1, blush = true, cr = "reach2"),
            SK(200, body = "leanR", dy = -1, legs = "dangle", ex = 1, eyes = "happy", blush = true, cr = "reach2"),
            SK(200, body = "leanR", ex = 1, eyes = "happy", blush = true, cr = "reach2"),
            SK(200, body = "leanR", dy = -1, legs = "dangle", ex = 1, eyes = "happy", blush = true, cr = "reach2"),
            SK(500, body = "leanR", ex = 1, eyes = "half", blush = true, cr = "reach2", props = listOf(SP(HEART_S, 19, 2))),
        ),
        fx = listOf(SFx(HEART_S, 19f, 2f, 6f, 1500, 300, sway = .6f)),
    ),

    // 戳戳戳，没人理就缩起来，再蹦起来挥钳子
    "理理我" to StickerDef(
        keys = listOf(
            SK(200, body = "leanR", ex = 1, cr = "reach1"),
            SK(150, ex = 1),
            SK(200, body = "leanR", ex = 1, cr = "reach1"),
            SK(150, ex = 1),
            SK(700, body = "squash2", ey = 2, eyes = "half", ex = 1, cl = "front", lx = 4, cr = "front", rx = -4, props = listOf(SP(B_DOTS, 15, -1))),
            SK(260, body = "tall", ey = -1, dy = -1, legs = "dangle", eyes = "wide", cl = "rUp", cr = "rUp", props = listOf(SP(EXCL, 16, -6))),
            SK(250, body = "leanL", eyes = "wide", cl = "up", cr = "rUp", legs = "walkA"),
            SK(250, body = "leanR", eyes = "wide", cl = "rUp", cr = "up", legs = "walkB"),
        ),
    ),

    // 一点点塌下去，叹口气，摊成一张饼，再勉强鼓起来
    "疲惫" to StickerDef(
        keys = listOf(
            SK(500, eyes = "half"),
            SK(500, body = "squash", ey = 1, eyes = "half", legs = "crouch"),
            SK(500, body = "squash2", ey = 2, eyes = "shut", legs = "crouch", cl = "low", cr = "low", props = listOf(SP(SMOKE_S, -3, 3))),
            SK(900, body = "flat", ey = 3, eyes = "half", legs = "tuck", cl = "low", ly = 1, cr = "low", ry = 1),
            SK(300, body = "squash2", ey = 2, eyes = "half", legs = "crouch"),
            SK(300, body = "squash", ey = 1, eyes = "half", legs = "crouch"),
        ),
        fx = listOf(SFx(SWEAT, 13f, 0f, -3f, 1600)),
    ),

    // 左右歪头挠头，问号越冒越多，脑子冒烟
    "看不懂" to StickerDef(
        keys = listOf(
            SK(500, body = "leanL", eyes = "squint", ex = -1, cl = "rUp", props = listOf(SP(QMARK, -3, -6))),
            SK(150, body = "leanL", eyes = "squint", ex = -1, cl = "up", props = listOf(SP(QMARK, -3, -6))),
            SK(150, body = "leanL", eyes = "squint", ex = -1, cl = "rUp", props = listOf(SP(QMARK, -3, -6))),
            SK(500, body = "leanR", eyes = "wide", ex = 1, cr = "rUp", props = listOf(SP(QMARK, -3, -6), SP(QMARK, 16, -6))),
            SK(500, body = "squash", ey = 1, eyes = "squint", cl = "front", lx = 3, cr = "front", rx = -3, props = listOf(SP(QMARK, -3, -5), SP(QMARK, 16, -5), SP(SMOKE_S, 7, -3))),
        ),
    ),

    // 一惊跳起来，凑过去看，再眯眼怀疑
    "真的假的？" to StickerDef(
        keys = listOf(
            SK(400),
            SK(160, body = "tall", ey = -1, dy = -1, legs = "dangle", eyes = "wide", cl = "up", cr = "up", props = listOf(SP(EXCL, 7, -6))),
            SK(300, body = "leanR", eyes = "wide", ex = 1, props = listOf(SP(EXCL, 16, -5))),
            SK(320, body = "leanR", eyes = "squint", ex = 1, props = listOf(SP(QMARK, 16, -6))),
            SK(320, body = "leanL", dx = -1, eyes = "squint", ex = -1, props = listOf(SP(QMARK, -4, -6))),
            SK(420, body = "leanR", dx = 1, eyes = "wide", ex = 1, cr = "reach1", props = listOf(SP(EXCL, 17, -5), SP(QMARK, 13, -7))),
        ),
    ),

    // 闹钟一直响，伸出钳子拍掉，接着睡
    "赖床" to StickerDef(
        keys = listOf(
            SK(300, eyes = "shut", props = listOf(SP(B_PILLOW, -1, -2, 0), SP(B_CLOCK, 17, 2), SP(B_BLANKET, -2, 4, 2))),
            SK(120, eyes = "shut", props = listOf(SP(B_PILLOW, -1, -2, 0), SP(B_CLOCK, 18, 2), SP(MOTION, 16, 1), SP(B_BLANKET, -2, 4, 2))),
            SK(120, eyes = "shut", props = listOf(SP(B_PILLOW, -1, -2, 0), SP(B_CLOCK, 17, 2), SP(MOTION, 23, 1), SP(B_BLANKET, -2, 4, 2))),
            SK(120, eyes = "shut", props = listOf(SP(B_PILLOW, -1, -2, 0), SP(B_CLOCK, 18, 2), SP(MOTION, 16, 1), SP(B_BLANKET, -2, 4, 2))),
            SK(220, eyes = "half", props = listOf(SP(B_PILLOW, -1, -2, 0), SP(B_CLOCK, 17, 2), SP(B_BLANKET, -2, 4, 2))),
            SK(160, eyes = "squint", cr = "rUp", rx = 2, ry = -4, props = listOf(SP(B_PILLOW, -1, -2, 0), SP(B_CLOCK, 17, 3), SP(B_STAR, 20, 0), SP(B_BLANKET, -2, 4, 2))),
            SK(1000, eyes = "shut", props = listOf(SP(B_PILLOW, -1, -2, 0), SP(B_CLOCK, 17, 3), SP(B_BLANKET, -2, 4, 2))),
        ),
        fx = listOf(SFx(ZZ, 12f, -2f, 6f, 2400, 1200, drift = 3f)),
    ),

    // 左右扭、蹲下、蹦起来、转身，钳子轮流举
    "跳舞" to StickerDef(
        keys = listOf(
            SK(220, body = "leanL", eyes = "happy", cl = "rUp", cr = "low", legs = "walkA"),
            SK(220, body = "squash", ey = 1, eyes = "happy", cl = "front", lx = 2, cr = "front", rx = -2, legs = "crouch"),
            SK(220, body = "leanR", eyes = "happy", cl = "low", cr = "rUp", legs = "walkB"),
            SK(220, body = "squash", ey = 1, eyes = "happy", cl = "front", lx = 2, cr = "front", rx = -2, legs = "crouch"),
            SK(240, body = "tall", ey = -1, dy = -1, legs = "dangle", eyes = "happy", blush = true, cl = "rUp", cr = "rUp"),
            SK(200, body = "turnL", dx = -1, eyes = "happy", cl = "up"),
            SK(200, body = "turnR", dx = 1, eyes = "happy", cr = "up"),
            SK(240, body = "tall", ey = -1, dy = -1, legs = "dangle", eyes = "happy", blush = true, cl = "rUp", cr = "rUp"),
        ),
        fx = listOf(
            SFx(NOTE, -4f, 2f, 7f, 1300, drift = -1f, sway = .8f),
            SFx(NOTE, 17f, 1f, 7f, 1300, 650, drift = 1f, sway = .8f),
            SFx(SPARK, 7f, -6f, 1f, 900, 300),
        ),
    ),

    // 摊成饼躺着，伸钳子够薯片，够不着就算了，叹口气接着躺
    "躺平摆烂" to StickerDef(
        keys = listOf(
            SK(900, body = "flat", ey = 3, eyes = "half", legs = "tuck", cl = "low", ly = 1, cr = "low", ry = 1, props = listOf(SP(B_CHIPS, 20, 6))),
            SK(400, body = "flat", ey = 3, eyes = "half", ex = 1, legs = "tuck", cl = "low", ly = 1, cr = "reach1", ry = 1, props = listOf(SP(B_CHIPS, 20, 6))),
            SK(600, body = "flat", ey = 3, eyes = "squint", ex = 1, legs = "tuck", cl = "low", ly = 1, cr = "reach2", ry = 1, props = listOf(SP(B_CHIPS, 20, 6))),
            SK(300, body = "flat", ey = 3, eyes = "shut", legs = "tuck", cl = "low", ly = 1, cr = "low", ry = 1, props = listOf(SP(B_CHIPS, 20, 6), SP(SMOKE_S, -3, 6))),
            SK(1000, body = "flat", ey = 3, eyes = "shut", legs = "tuck", cl = "low", ly = 1, cr = "low", ry = 1, props = listOf(SP(B_CHIPS, 20, 6))),
        ),
    ),

    // 追着小老鼠跑，钳子往前伸，小老鼠满头大汗
    "追赶" to StickerDef(
        keys = listOf(-6, -5, -4, -3, -2, -1, 0, 1).mapIndexed { i, x ->
            val a = i % 2 == 0
            SK(
                140, body = "leanR", dx = x, ex = 1, eyes = if (i > 5) "wide" else "squint",
                legs = if (a) "walkA" else "walkB", dy = if (a) 0 else -1,
                cr = if (i > 5) "reach2" else "reach1", cl = "up",
                props = listOf(
                    SP(if (a) MOUSE_RUN else MOUSE, 18 + i / 2, 4),
                    SP(SWEAT, 24 + i / 2, 1),
                    SP(B_SPEED, x - 4, if (a) 3 else 5),
                ),
            )
        },
    ),

    // 捧出小红盒，打开是戒指，再把戒指举高高
    "送你戒指" to StickerDef(
        keys = listOf(
            SK(500, blush = true, cl = "front", lx = 3, cr = "front", rx = -3, props = listOf(SP(B_BOX, 5, 4))),
            SK(300, eyes = "wide", blush = true, cl = "front", lx = 3, cr = "front", rx = -3, props = listOf(SP(B_BOX, 5, 4))),
            SK(500, eyes = "happy", blush = true, cl = "front", lx = 3, cr = "front", rx = -3, props = listOf(SP(B_LID, 5, 2), SP(B_BOX_OPEN, 5, 5), SP(SPARK, 7, -1))),
            SK(260, eyes = "happy", blush = true, cl = "front", lx = 3, cr = "rUp", props = listOf(SP(B_BOX_OPEN, 5, 5), SP(B_RING, 15, -3))),
            SK(800, eyes = "happy", blush = true, cl = "front", lx = 3, cr = "rUp", props = listOf(SP(B_BOX_OPEN, 5, 5), SP(B_RING, 15, -5), SP(SPARK, 19, -7), SP(SPARK_P, 12, -6))),
        ),
        fx = listOf(SFx(HEART_S, 0f, 0f, 5f, 1600, sway = .6f)),
    ),

    // 抱着小爱心，往前一递，爱心变大，再举高高
    "送你爱心" to StickerDef(
        keys = listOf(
            SK(400, blush = true, cl = "front", lx = 3, cr = "front", rx = -3, props = listOf(SP(HEART, 5, 4))),
            SK(200, body = "squash", ey = 1, eyes = "shut", blush = true, cl = "front", lx = 3, ly = 1, cr = "front", rx = -3, ry = 1, props = listOf(SP(HEART, 5, 5))),
            SK(260, body = "leanR", ex = 1, blush = true, cr = "reach1", props = listOf(SP(HEART, 16, 3))),
            SK(500, body = "leanR", ex = 1, eyes = "happy", blush = true, cr = "reach2", props = listOf(SP(HEART_BIG, 18, 0))),
            SK(700, body = "tall", ey = -1, eyes = "happy", blush = true, cl = "rUp", cr = "rUp", props = listOf(SP(HEART_BIG, 4, -9))),
        ),
        fx = listOf(
            SFx(HEART_S, -3f, 0f, 6f, 1800, sway = .8f),
            SFx(HEART_S, 16f, -1f, 6f, 1800, 900, sway = .8f),
        ),
    ),

    // 本来笑眯眯，一道闪电劈下来，身上裂开，抖两下，眼泪冒出来，瘫成一张饼
    "破防了" to StickerDef(
        keys = listOf(
            SK(600, eyes = "happy"),
            SK(120, eyes = "wide", props = listOf(SP(B_BOLT, 6, -8, 2))),
            SK(140, body = "squash", ey = 1, eyes = "wide", cl = "up", cr = "up", props = listOf(SP(B_BOLT, 6, -5, 2), SP(SPARK, 3, -2))),
            SK(400, eyes = "wide", cl = "low", cr = "low", props = listOf(SP(B_CRACK, 7, 0, 2))),
            SK(110, dx = 1, eyes = "wide", cl = "low", cr = "low", props = listOf(SP(B_CRACK, 8, 0, 2))),
            SK(110, dx = -1, eyes = "wide", cl = "low", cr = "low", props = listOf(SP(B_CRACK, 6, 0, 2))),
            SK(110, dx = 1, eyes = "wide", cl = "low", cr = "low", props = listOf(SP(B_CRACK, 8, 0, 2))),
            SK(500, body = "squash2", ey = 2, eyes = "shut", legs = "crouch", cl = "low", cr = "low", props = listOf(SP(B_CRACK, 7, 2, 2), SP(B_HEART_BROKEN, 15, -5), SP(TEAR, 3, 5), SP(TEAR, 12, 5))),
            SK(1000, body = "flat", ey = 3, eyes = "shut", legs = "tuck", cl = "low", ly = 1, cr = "low", ry = 1, props = listOf(SP(B_HEART_BROKEN, 15, -3), SP(TEAR, 2, 8), SP(TEAR, 13, 8))),
        ),
    ),
)
