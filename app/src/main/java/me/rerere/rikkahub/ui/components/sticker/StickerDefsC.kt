package me.rerere.rikkahub.ui.components.sticker

// 第三批：新加的 20 个

private val C_DOT1 = art("G")
private val C_DOT2 = art("GG", "GG")
private val C_DOTS = art("K.K.K")
private val C_BOOM = art(
    "....Y....Y....",
    ".Y..YY..YY..Y.",
    "..YYOOYYOOYY..",
    "..YOORROORROY.",
    ".YOORRRRRRROOY",
    "YYORRWWWWRRROY",
    ".YORRWWWWWRROY",
    "..YORRWWWRROY.",
    ".YOORRRRRROOY.",
    "..YYOORROOYY..",
    ".Y..YYOOYY..Y.",
    "....Y....Y....",
)
private val C_SOOT = art(
    "g...K....g..",
    "..K...g..K..",
    "K....g.....g",
    "...g..K.....",
    ".K......g..K",
    "....K..g....",
)
private val C_POW = art("Y.Y.Y", ".YRY.", "YRWRY", ".YRY.", "Y.Y.Y")
private val C_TEA = art("...C.", "...C.", "GGGGG", "wcccw", "wcKcw", "wKccw", ".www.")
private val C_SOUL = art(".GGG.", "GWWWG", "GKWKG", "GWWWG", ".G.G.")
private val C_WFLAG_A = art("CGGGGG", "CWWWWG", "CWWWWG", "CGGGG.", "C.....", "C.....", "C.....", "C.....")
private val C_WFLAG_B = art("CGGGG.", "CWWWWG", "CWWWWG", "CGGGGG", "C.....", "C.....", "C.....", "C.....")
private val C_BRICK = art("rRRRrRRR", "RRrRRRrR", "rRRRrRRR")
private val C_BALL = art(".WW.", "WRRW", "WRRW", ".WW.")
private val C_SNOUT = art("PPP", "KPK")
private val C_LAPTOP = art(".GGGGGGG.", ".GGGXGGG.", ".GGXXXGG.", ".GGGXGGG.", "ggggggggg")
private val C_LAPTOP_SHUT = art("ggggggggg", "GGGGGGGGG")
private val C_SPEED = art("GGG")
private val C_FISH = art("..BBB...", ".BKBBB.B", "BBBBBBBB", ".BBBBB.B", "..BBB...")
private val C_CROWN = art("Y.Y.Y", "YYYYY", "YRYBY")

internal fun stickerDefsC(): Map<String, StickerDef> = mapOf(

    // 小老鼠站在崽崽头上一下一下拍，崽崽被拍得一压一压，笑眯了眼
    "摸摸头" to StickerDef(
        keys = listOf(
            SK(300, props = listOf(SP(MOUSE, 4, -6, 2))),
            SK(180, body = "squash", ey = 1, eyes = "shut", blush = true, props = listOf(SP(MOUSE, 4, -4, 2))),
            SK(180, eyes = "happy", blush = true, props = listOf(SP(MOUSE, 4, -6, 2))),
            SK(180, body = "squash", ey = 1, eyes = "shut", blush = true, props = listOf(SP(MOUSE, 4, -4, 2))),
            SK(180, eyes = "happy", blush = true, props = listOf(SP(MOUSE, 4, -6, 2))),
            SK(600, eyes = "happy", blush = true, cl = "front", lx = 3, cr = "front", rx = -3, props = listOf(SP(MOUSE_HAPPY, 4, -6, 2))),
        ),
        fx = listOf(
            SFx(HEART_S, -3f, 0f, 5f, 1600, sway = .7f),
            SFx(HEART_S, 16f, 1f, 5f, 1600, 800, sway = .7f),
        ),
    ),

    // 抬头发呆，冒出想念的泡泡，里面是小老鼠，然后抱住自己
    "好想你" to StickerDef(
        keys = listOf(
            SK(500, eyes = "half", ex = 1, ey = -1),
            SK(400, eyes = "half", ex = 1, ey = -1, props = listOf(SP(C_DOT1, 14, -1))),
            SK(400, eyes = "half", ex = 1, ey = -1, props = listOf(SP(C_DOT1, 14, -1), SP(C_DOT2, 15, -4))),
            SK(900, eyes = "happy", blush = true, ex = 1, ey = -1, props = listOf(SP(C_DOT1, 14, -1), SP(C_DOT2, 15, -4), SP(MOUSE_HAPPY, 15, -9), SP(HEART_S, 19, -3))),
            SK(700, eyes = "shut", blush = true, cl = "front", lx = 4, cr = "front", rx = -4, props = listOf(SP(HEART, 5, -5))),
        ),
        fx = listOf(SFx(TEAR, 12f, 4f, -3f, 1800)),
    ),

    // 越气越鼓，抖抖抖，砰一下炸开，烟散了站在原地被炸黑
    "气到爆炸" to StickerDef(
        keys = listOf(
            SK(300, eyes = "squint", props = listOf(SP(ANGER, 13, -3))),
            SK(200, body = "tall", ey = -1, eyes = "squint", cl = "up", cr = "up", props = listOf(SP(ANGER, 13, -4))),
            SK(100, body = "tall", ey = -1, dx = 1, eyes = "squint", cl = "rUp", cr = "rUp", props = listOf(SP(ANGER, 14, -4))),
            SK(100, body = "tall", ey = -1, dx = -1, eyes = "squint", cl = "rUp", cr = "rUp", props = listOf(SP(ANGER, 12, -4))),
            SK(100, body = "tall", ey = -1, dx = 1, eyes = "wide", cl = "rUp", cr = "rUp", props = listOf(SP(ANGER, 14, -4))),
            SK(100, body = "tall", ey = -1, dx = -1, eyes = "wide", cl = "rUp", cr = "rUp", props = listOf(SP(ANGER, 12, -4))),
            SK(160, hide = true, props = listOf(SP(C_BOOM, 1, -3, 2))),
            SK(300, hide = true, props = listOf(SP(SMOKE, 3, 2, 2), SP(SMOKE, 9, 0, 2), SP(SMOKE_S, 6, 5, 2), SP(SMOKE_S, 12, 4, 2))),
            SK(1100, eyes = "half", cl = "low", cr = "low", props = listOf(SP(C_SOOT, 2, 0, 2), SP(SMOKE_S, 6, -3))),
        ),
    ),

    // 往后抡钳子蓄力，一拳打出去，砰，打完还挺得意
    "给你一拳" to StickerDef(
        keys = listOf(
            SK(300, eyes = "squint", props = listOf(SP(ANGER, 13, -3))),
            SK(260, body = "leanL", eyes = "squint", cr = "rDown", legs = "walkB", props = listOf(SP(ANGER, 13, -3))),
            SK(100, body = "leanR", dx = 1, eyes = "squint", cr = "reach2", legs = "walkA", props = listOf(SP(C_POW, 22, 2), SP(MOTION, 12, 3))),
            SK(400, body = "leanR", dx = 1, eyes = "squint", cr = "reach2", props = listOf(SP(C_POW, 22, 2))),
            SK(500, eyes = "happy", blush = true, cr = "up"),
        ),
    ),

    // 盯着奶茶流口水，伸钳子够过来，捧着吸，一口一口
    "想喝奶茶" to StickerDef(
        keys = listOf(
            SK(400, ex = 1, eyes = "wide", props = listOf(SP(C_TEA, 19, 3), SP(TEAR, 9, 5))),
            SK(300, body = "leanR", ex = 1, eyes = "wide", cr = "reach1", props = listOf(SP(C_TEA, 19, 3), SP(TEAR, 9, 5))),
            SK(300, body = "leanR", ex = 1, eyes = "wide", cr = "reach2", props = listOf(SP(C_TEA, 19, 3))),
            SK(300, eyes = "happy", cl = "front", lx = 3, cr = "front", rx = -3, props = listOf(SP(C_TEA, 6, 3))),
            SK(250, eyes = "shut", blush = true, cl = "front", lx = 3, ly = 1, cr = "front", rx = -3, ry = 1, props = listOf(SP(C_TEA, 6, 4))),
            SK(250, eyes = "happy", blush = true, cl = "front", lx = 3, cr = "front", rx = -3, props = listOf(SP(C_TEA, 6, 3))),
            SK(250, eyes = "shut", blush = true, cl = "front", lx = 3, ly = 1, cr = "front", rx = -3, ry = 1, props = listOf(SP(C_TEA, 6, 4))),
            SK(600, eyes = "happy", blush = true, cl = "front", lx = 3, cr = "front", rx = -3, props = listOf(SP(C_TEA, 6, 3), SP(HEART_S, 14, -2))),
        ),
    ),

    // 拖着腿一步一晃走过来，扑通趴倒，魂飘出来
    "累瘫了" to StickerDef(
        keys = listOf(
            SK(320, dx = -3, eyes = "half", legs = "walkA", cl = "low", cr = "low"),
            SK(320, dx = -2, dy = 1, eyes = "half", legs = "walkB", cl = "low", cr = "low"),
            SK(320, dx = -1, eyes = "half", legs = "walkA", cl = "low", cr = "low"),
            SK(200, body = "squash2", ey = 2, eyes = "shut", legs = "crouch", cl = "low", cr = "low"),
            SK(1400, body = "flat", ey = 3, eyes = "shut", legs = "tuck", cl = "low", ly = 1, cr = "low", ry = 1),
        ),
        fx = listOf(SFx(C_SOUL, 6f, 0f, 7f, 1600, sway = .6f)),
    ),

    // 两只钳子举高，挥小白旗，最后跪下
    "投降了" to StickerDef(
        keys = listOf(
            SK(250, eyes = "squint", cl = "rUp", cr = "rUp", props = listOf(SP(C_WFLAG_A, 16, -6))),
            SK(250, eyes = "squint", cl = "rUp", cr = "rUp", props = listOf(SP(C_WFLAG_B, 16, -6))),
            SK(250, eyes = "squint", cl = "rUp", cr = "rUp", props = listOf(SP(C_WFLAG_A, 16, -6))),
            SK(250, eyes = "squint", cl = "rUp", cr = "rUp", props = listOf(SP(C_WFLAG_B, 16, -6))),
            SK(700, body = "squash2", ey = 2, eyes = "shut", legs = "crouch", cl = "rUp", cr = "rUp", props = listOf(SP(C_WFLAG_A, 16, -5))),
        ),
        fx = listOf(SFx(SWEAT, 0f, -1f, -3f, 1200)),
    ),

    // 头顶一摞砖来回走，放下砖擦擦汗
    "认真搬砖" to StickerDef(
        keys = listOf(-2, -1, 0, 1, 2, 1, 0, -1).mapIndexed { i, x ->
            val a = i % 2 == 0
            SK(
                170, dx = x, dy = if (a) 0 else -1, eyes = "squint", cl = "up", cr = "up",
                legs = if (a) "walkA" else "walkB",
                props = listOf(SP(C_BRICK, 4 + x, if (a) -3 else -4, 2)),
            )
        } + listOf(
            SK(260, body = "squash", ey = 1, eyes = "shut", cl = "low", cr = "low", props = listOf(SP(C_BRICK, 18, 7))),
            SK(600, eyes = "happy", cr = "front", rx = -3, ry = -3, props = listOf(SP(C_BRICK, 18, 7), SP(SWEAT, 14, 0))),
        ),
        fx = listOf(SFx(SWEAT, 14f, 0f, -3f, 1000)),
    ),

    // 左右扭屁股，钳子一上一下，扭完害羞
    "扭扭扭" to StickerDef(
        keys = listOf(
            SK(160, body = "leanL", dx = -1, eyes = "happy", cl = "rUp", cr = "low", legs = "walkA"),
            SK(160, body = "leanR", dx = 1, eyes = "happy", cl = "low", cr = "rUp", legs = "walkB"),
            SK(160, body = "leanL", dx = -1, eyes = "happy", cl = "rUp", cr = "low", legs = "walkA"),
            SK(160, body = "leanR", dx = 1, eyes = "happy", cl = "low", cr = "rUp", legs = "walkB"),
            SK(160, body = "leanL", dx = -1, eyes = "happy", cl = "rUp", cr = "low", legs = "walkA"),
            SK(160, body = "leanR", dx = 1, eyes = "happy", cl = "low", cr = "rUp", legs = "walkB"),
            SK(500, body = "squash", ey = 1, eyes = "happy", blush = true, cl = "front", lx = 3, cr = "front", rx = -3),
        ),
        fx = listOf(
            SFx(NOTE, -4f, 2f, 7f, 1200, drift = -1f, sway = .8f),
            SFx(NOTE, 17f, 1f, 7f, 1200, 600, drift = 1f, sway = .8f),
        ),
    ),

    // 眼睛往旁边瞟，两只钳子戳来戳去，偷偷看你一眼又躲开
    "内个内个" to StickerDef(
        keys = listOf(
            SK(300, ex = -1, blush = true, cl = "front", lx = 5, cr = "front", rx = -5),
            SK(200, ex = -1, blush = true, cl = "front", lx = 6, cr = "front", rx = -6),
            SK(200, ex = -1, blush = true, cl = "front", lx = 5, cr = "front", rx = -5),
            SK(200, ex = -1, blush = true, cl = "front", lx = 6, cr = "front", rx = -6),
            SK(500, ex = 1, ey = -1, blush = true, cl = "front", lx = 5, cr = "front", rx = -5, props = listOf(SP(C_DOTS, 15, -2))),
            SK(400, ex = -1, eyes = "shut", blush = true, cl = "front", lx = 5, cr = "front", rx = -5, props = listOf(SP(C_DOTS, 15, -2))),
        ),
        fx = listOf(SFx(SWEAT, 13f, 0f, -3f, 1500)),
    ),

    // 举着球一蹦一蹦，扔给你，等你接
    "陪我玩！" to StickerDef(
        keys = listOf(
            SK(200, body = "squash", ey = 1, eyes = "happy", cl = "rUp", cr = "rUp", props = listOf(SP(C_BALL, 6, -3))),
            SK(200, body = "tall", ey = -1, dy = -1, legs = "dangle", eyes = "happy", cl = "rUp", cr = "rUp", props = listOf(SP(C_BALL, 6, -6))),
            SK(200, body = "squash", ey = 1, eyes = "happy", cl = "rUp", cr = "rUp", props = listOf(SP(C_BALL, 6, -3))),
            SK(200, body = "tall", ey = -1, dy = -1, legs = "dangle", eyes = "happy", cl = "rUp", cr = "rUp", props = listOf(SP(C_BALL, 6, -6))),
            SK(160, body = "leanR", eyes = "wide", cr = "reach2", props = listOf(SP(C_BALL, 19, 0))),
            SK(160, body = "leanR", eyes = "wide", cr = "reach2", props = listOf(SP(C_BALL, 21, -4))),
            SK(500, eyes = "happy", blush = true, cl = "up", cr = "up", props = listOf(SP(EXCL, 16, -5))),
        ),
    ),

    // 指着戴猪鼻子的小老鼠笑，捂嘴偷乐，小老鼠气鼓鼓
    "大笨猪" to StickerDef(
        keys = listOf(
            SK(300, body = "leanR", eyes = "happy", cr = "reach1", props = listOf(SP(MOUSE, 17, 4), SP(C_SNOUT, 19, 7, 2))),
            SK(150, body = "squash", ey = 1, eyes = "happy", cr = "reach1", props = listOf(SP(MOUSE, 17, 4), SP(C_SNOUT, 19, 7, 2))),
            SK(150, body = "leanR", eyes = "happy", cr = "reach1", props = listOf(SP(MOUSE, 17, 4), SP(C_SNOUT, 19, 7, 2))),
            SK(150, body = "squash", ey = 1, eyes = "happy", cr = "reach1", props = listOf(SP(MOUSE, 17, 4), SP(C_SNOUT, 19, 7, 2))),
            SK(400, eyes = "happy", blush = true, cl = "front", lx = 2, cr = "front", rx = -2, props = listOf(SP(MOUSE, 17, 4), SP(C_SNOUT, 19, 7, 2), SP(ANGER, 18, -2))),
            SK(300, body = "squash", ey = 1, eyes = "happy", blush = true, cl = "front", lx = 2, ly = 1, cr = "front", rx = -2, ry = 1, props = listOf(SP(MOUSE, 17, 4), SP(C_SNOUT, 19, 7, 2), SP(ANGER, 18, -2))),
        ),
    ),

    // 合上电脑，蹦一下，一溜烟跑出画面
    "下班啦" to StickerDef(
        keys = listOf(
            SK(400, ey = 1, eyes = "half", cl = "front", lx = 3, ly = 2, cr = "front", rx = -3, ry = 1, props = listOf(SP(C_LAPTOP, 3, 5))),
            SK(220, eyes = "wide", props = listOf(SP(C_LAPTOP_SHUT, 3, 8), SP(EXCL, 16, -5))),
            SK(260, body = "tall", ey = -1, dy = -1, legs = "dangle", eyes = "happy", cl = "rUp", cr = "rUp", props = listOf(SP(C_LAPTOP_SHUT, 3, 8))),
            SK(140, body = "leanR", dx = 2, eyes = "happy", legs = "walkA", props = listOf(SP(C_LAPTOP_SHUT, 3, 8), SP(C_SPEED, -1, 3))),
            SK(140, body = "leanR", dx = 5, dy = -1, eyes = "happy", legs = "walkB", props = listOf(SP(C_LAPTOP_SHUT, 3, 8), SP(C_SPEED, 2, 5))),
            SK(140, body = "leanR", dx = 8, eyes = "happy", legs = "walkA", props = listOf(SP(C_LAPTOP_SHUT, 3, 8), SP(C_SPEED, 5, 3))),
            SK(140, body = "leanR", dx = 11, dy = -1, eyes = "happy", legs = "walkB", props = listOf(SP(C_LAPTOP_SHUT, 3, 8), SP(C_SPEED, 8, 5))),
            SK(140, body = "leanR", dx = 15, eyes = "happy", legs = "walkA", props = listOf(SP(C_LAPTOP_SHUT, 3, 8), SP(C_SPEED, 12, 3))),
            SK(600, dx = 30, hide = true, props = listOf(SP(C_LAPTOP_SHUT, 3, 8), SP(SPARK, 19, 2))),
        ),
    ),

    // 抱着一条鱼摸摸摸，突然左右看有没有人，又接着摸
    "摸鱼中" to StickerDef(
        keys = listOf(
            SK(400, ey = 1, eyes = "happy", cl = "front", lx = 3, cr = "front", rx = -3, props = listOf(SP(C_FISH, 4, 4))),
            SK(250, ey = 1, eyes = "happy", cl = "front", lx = 3, cr = "front", rx = -3, ry = -1, props = listOf(SP(C_FISH, 4, 4))),
            SK(250, ey = 1, eyes = "happy", cl = "front", lx = 3, cr = "front", rx = -3, props = listOf(SP(C_FISH, 4, 4))),
            SK(250, ey = 1, eyes = "happy", cl = "front", lx = 3, cr = "front", rx = -3, ry = -1, props = listOf(SP(C_FISH, 4, 4))),
            SK(250, eyes = "wide", ex = -1, cl = "front", lx = 3, cr = "front", rx = -3, props = listOf(SP(C_FISH, 4, 4), SP(EXCL, -3, -4))),
            SK(250, eyes = "wide", ex = 1, cl = "front", lx = 3, cr = "front", rx = -3, props = listOf(SP(C_FISH, 4, 4), SP(EXCL, 18, -4))),
            SK(500, ey = 1, eyes = "half", cl = "front", lx = 3, cr = "front", rx = -3, props = listOf(SP(C_FISH, 4, 4))),
        ),
    ),

    // 靠在小老鼠身上上下蹭蹭蹭
    "蹭蹭" to StickerDef(
        keys = listOf(
            SK(180, body = "leanR", dx = 2, eyes = "shut", blush = true, props = listOf(SP(MOUSE, 17, 4))),
            SK(180, body = "leanR", dx = 2, dy = -1, eyes = "shut", blush = true, legs = "dangle2", props = listOf(SP(MOUSE, 17, 4))),
            SK(180, body = "leanR", dx = 2, eyes = "shut", blush = true, props = listOf(SP(MOUSE, 17, 4))),
            SK(180, body = "leanR", dx = 2, dy = -1, eyes = "shut", blush = true, legs = "dangle2", props = listOf(SP(MOUSE, 17, 4))),
            SK(180, body = "leanR", dx = 2, eyes = "shut", blush = true, props = listOf(SP(MOUSE, 17, 4))),
            SK(180, body = "leanR", dx = 2, dy = -1, eyes = "shut", blush = true, legs = "dangle2", props = listOf(SP(MOUSE, 17, 4))),
            SK(600, body = "leanR", dx = 2, eyes = "happy", blush = true, props = listOf(SP(MOUSE_HAPPY, 17, 4), SP(HEART, 14, -3))),
        ),
        fx = listOf(SFx(HEART_S, 16f, 0f, 5f, 1400)),
    ),

    // 一把抓住小老鼠举过头顶，再抱进怀里，冒爱心
    "你是我的" to StickerDef(
        keys = listOf(
            SK(300, ex = 1, eyes = "squint", props = listOf(SP(MOUSE, 17, 4))),
            SK(250, body = "leanR", ex = 1, eyes = "wide", cr = "reach2", props = listOf(SP(MOUSE_RUN, 18, 4))),
            SK(300, eyes = "happy", cl = "rUp", cr = "rUp", props = listOf(SP(MOUSE, 4, -7, 2))),
            SK(250, eyes = "happy", blush = true, cl = "rUp", cr = "rUp", props = listOf(SP(MOUSE, 4, -8, 2), SP(SWEAT, 11, -9))),
            SK(900, eyes = "happy", blush = true, cl = "front", lx = 3, cr = "front", rx = -3, props = listOf(SP(MOUSE_HAPPY, 5, 4, 2), SP(HEART, 14, -3), SP(HEART_S, -2, -1))),
        ),
    ),

    // 小老鼠戴着王冠站在崽崽头上，崽崽在底下拼命鼓掌
    "世界第一聪明鼠" to StickerDef(
        keys = listOf(
            SK(200, eyes = "happy", cl = "front", lx = 2, cr = "front", rx = -2, props = listOf(SP(MOUSE_HAPPY, 4, -6, 2), SP(C_CROWN, 5, -9, 2))),
            SK(150, eyes = "happy", cl = "front", lx = 4, cr = "front", rx = -4, props = listOf(SP(MOUSE_HAPPY, 4, -6, 2), SP(C_CROWN, 5, -9, 2), SP(SPARK, -2, 2))),
            SK(200, eyes = "happy", cl = "front", lx = 2, cr = "front", rx = -2, props = listOf(SP(MOUSE_HAPPY, 4, -6, 2), SP(C_CROWN, 5, -9, 2))),
            SK(150, eyes = "happy", cl = "front", lx = 4, cr = "front", rx = -4, props = listOf(SP(MOUSE_HAPPY, 4, -6, 2), SP(C_CROWN, 5, -9, 2), SP(SPARK_P, 15, 2))),
            SK(200, eyes = "happy", cl = "front", lx = 2, cr = "front", rx = -2, props = listOf(SP(MOUSE_HAPPY, 4, -6, 2), SP(C_CROWN, 5, -9, 2))),
            SK(150, eyes = "happy", cl = "front", lx = 4, cr = "front", rx = -4, props = listOf(SP(MOUSE_HAPPY, 4, -6, 2), SP(C_CROWN, 5, -9, 2), SP(SPARK, -2, 2))),
            SK(600, body = "squash", ey = 1, eyes = "happy", blush = true, cl = "up", cr = "up", props = listOf(SP(MOUSE_HAPPY, 4, -4, 2), SP(C_CROWN, 5, -7, 2))),
        ),
        fx = listOf(
            SFx(CONF_Y, -4f, -8f, -18f, 1600, 0, sway = 1f),
            SFx(CONF_R, 17f, -8f, -18f, 1800, 600, sway = 1f),
            SFx(CONF_B, 0f, -9f, -18f, 1700, 1100, sway = 1f),
            SFx(CONF_P, 20f, -9f, -18f, 1900, 300, sway = 1f),
        ),
    ),

    // 钳子并拢一下一下鞠躬，眼睛亮晶晶，憋出眼泪
    "求求你了" to StickerDef(
        keys = listOf(
            SK(250, eyes = "wide", blush = true, cl = "front", lx = 4, cr = "front", rx = -4),
            SK(250, body = "squash", ey = 1, eyes = "wide", blush = true, cl = "front", lx = 4, ly = 1, cr = "front", rx = -4, ry = 1),
            SK(250, eyes = "wide", blush = true, cl = "front", lx = 4, cr = "front", rx = -4),
            SK(250, body = "squash", ey = 1, eyes = "wide", blush = true, cl = "front", lx = 4, ly = 1, cr = "front", rx = -4, ry = 1),
            SK(700, eyes = "wide", blush = true, cl = "front", lx = 4, cr = "front", rx = -4, props = listOf(SP(SPARK, 0, -2), SP(SPARK_P, 14, -3))),
            SK(200, eyes = "half", blush = true, cl = "front", lx = 4, cr = "front", rx = -4),
        ),
        fx = listOf(
            SFx(TEAR, 4f, 4f, -3f, 1400),
            SFx(TEAR, 11f, 4f, -3f, 1400, 700),
        ),
    ),

    // 从左边冲过来，压扁往前滑，正好停在你跟前趴着，再抬头可怜巴巴看你
    "滑跪" to StickerDef(
        keys = listOf(
            SK(110, body = "leanR", dx = -14, eyes = "squint", legs = "walkA"),
            SK(110, body = "leanR", dx = -11, dy = -1, eyes = "squint", legs = "walkB"),
            SK(110, body = "leanR", dx = -8, eyes = "squint", legs = "walkA"),
            SK(110, body = "squash2", ey = 2, dx = -6, eyes = "shut", legs = "crouch"),
            SK(100, body = "flat", ey = 3, dx = -4, eyes = "shut", legs = "tuck", cl = "reach1", cr = "reach1", props = listOf(SP(C_SPEED, -8, 6))),
            SK(100, body = "flat", ey = 3, dx = -2, eyes = "shut", legs = "tuck", cl = "reach1", cr = "reach1", props = listOf(SP(C_SPEED, -6, 7))),
            SK(100, body = "flat", ey = 3, dx = -1, eyes = "shut", legs = "tuck", cl = "reach1", cr = "reach1", props = listOf(SP(C_SPEED, -5, 6))),
            SK(300, body = "flat", ey = 3, eyes = "shut", legs = "tuck", cl = "front", lx = 3, cr = "front", rx = -3, props = listOf(SP(SMOKE_S, -3, 7), SP(SMOKE_S, 17, 7))),
            SK(700, body = "flat", ey = 3, eyes = "shut", legs = "tuck", cl = "front", lx = 3, cr = "front", rx = -3, props = listOf(SP(SWEAT, 14, 2))),
            SK(700, body = "squash2", ey = 2, eyes = "wide", blush = true, legs = "crouch", cl = "front", lx = 3, cr = "front", rx = -3, props = listOf(SP(HEART_S, 15, 0))),
        ),
    ),

    // 侧过身眼睛往一边瞟，小老鼠戳它，它往旁边挪，再戳再挪，抱着钳子就是不理
    "不想理你" to StickerDef(
        keys = listOf(
            SK(500, body = "turnR", ex = 1, eyes = "half", props = listOf(SP(MOUSE, -6, 4), SP(C_DOTS, 15, -2))),
            SK(200, body = "turnR", ex = 1, eyes = "half", props = listOf(SP(MOUSE_RUN, -5, 4), SP(MOTION, 1, 4))),
            SK(200, body = "turnR", dx = 1, ex = 1, eyes = "shut", props = listOf(SP(MOUSE, -5, 4))),
            SK(500, body = "turnR", dx = 1, ex = 1, eyes = "half", props = listOf(SP(MOUSE, -4, 4))),
            SK(200, body = "turnR", dx = 2, ex = 1, eyes = "shut", props = listOf(SP(MOUSE_RUN, -3, 4), SP(ANGER, -3, -2))),
            SK(800, body = "turnR", dx = 2, ex = 1, eyes = "shut", cl = "front", lx = 3, cr = "front", rx = -3, props = listOf(SP(MOUSE, -3, 4), SP(ANGER, -3, -2), SP(SMOKE_S, 18, 3))),
        ),
    ),
)
