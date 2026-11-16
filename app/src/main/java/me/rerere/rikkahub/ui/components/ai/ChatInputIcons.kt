package me.rerere.rikkahub.ui.components.ai

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import me.rerere.ai.core.ReasoningLevel

// 普通会话换皮：小葵气泡和输入框共用的浅色底色 #f5f5f5（只在浅色模式用，深色模式各自保持原样）
internal val ChatSkinSurface = Color(0xFFF5F5F5)

// 普通会话输入框专用的细线图标，24x24 画布，统一 1.4 线宽，圆头圆角，颜色由 Icon 的 tint 决定
private const val STROKE_WIDTH = 1.4f

private fun thinIcon(
    name: String,
    vararg paths: String,
    filledPaths: List<String> = emptyList(),
): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    filledPaths.forEach { data ->
        builder.addPath(
            pathData = addPathNodes(data),
            fill = SolidColor(Color.Black),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE_WIDTH,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    paths.forEach { data ->
        builder.addPath(
            pathData = addPathNodes(data),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE_WIDTH,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return builder.build()
}

private fun roundedSquare(x: Float, y: Float, size: Float = 6f, r: Float = 1.5f): String {
    val x2 = x + size
    val y2 = y + size
    return "M${x + r},$y H${x2 - r} A$r,$r 0 0 1 $x2,${y + r} V${y2 - r} " +
        "A$r,$r 0 0 1 ${x2 - r},$y2 H${x + r} A$r,$r 0 0 1 $x,${y2 - r} " +
        "V${y + r} A$r,$r 0 0 1 ${x + r},$y Z"
}

// 输入区左边的小声波，纯装饰
internal val ThinWaveIcon: ImageVector by lazy {
    thinIcon(
        "ThinWave",
        "M5,10 V14", "M8.5,7 V17", "M12,4 V20", "M15.5,8 V16", "M19,10.5 V13.5",
    )
}

// 输入区右边的全屏编辑：对角两个直角箭头，线宽和声波一致
internal val ThinExpandIcon: ImageVector by lazy {
    thinIcon(
        "ThinExpand",
        "M14,4 H20 V10", "M20,4 L13.5,10.5", "M10,20 H4 V14", "M4,20 L10.5,13.5",
    )
}

// Menu：四格
internal val ThinMenuIcon: ImageVector by lazy {
    thinIcon(
        "ThinMenu",
        roundedSquare(4f, 4f), roundedSquare(14f, 4f),
        roundedSquare(4f, 14f), roundedSquare(14f, 14f),
    )
}

// 鼠鼠：由 app 图标的气泡鼠简化而来，两只耳朵 + 圆角头 + 左下小尾巴 + 眼睛鼻子
internal val ThinMouseIcon: ImageVector by lazy {
    thinIcon(
        "ThinMouse",
        // 头
        "M10.3,8 H13.7 A5,5 0 0 1 18.7,13 A5,5 0 0 1 13.7,18 H10.3 A5,5 0 0 1 5.3,13 A5,5 0 0 1 10.3,8 Z",
        // 左右耳
        "M5.2,6.6 A2.4,2.4 0 1 1 10,6.6 A2.4,2.4 0 1 1 5.2,6.6 Z",
        "M14,6.6 A2.4,2.4 0 1 1 18.8,6.6 A2.4,2.4 0 1 1 14,6.6 Z",
        // 气泡尾巴
        "M7.6,17 L9.8,17.6 L6.4,19.6 Z",
        // 眼睛、鼻子
        "M9.9,12.4 H9.95", "M14.05,12.4 H14.1", "M11.4,14.3 H12.6",
    )
}

// 摇骰子：圆角方块 + 三个点
internal val ThinDiceIcon: ImageVector by lazy {
    thinIcon(
        "ThinDice",
        roundedSquare(4f, 4f, size = 16f, r = 3.5f),
        "M8.8,8.8 H8.85", "M12,12 H12.05", "M15.2,15.2 H15.25",
    )
}

// 按住说话：话筒
internal val ThinMicIcon: ImageVector by lazy {
    thinIcon(
        "ThinMic",
        "M9,7 A3,3 0 0 1 15,7 V11 A3,3 0 0 1 9,11 Z",
        "M6,11 A6,6 0 0 0 18,11",
        "M12,17 V20.5",
    )
}

// 切回打字：键盘
internal val ThinKeyboardIcon: ImageVector by lazy {
    thinIcon(
        "ThinKeyboard",
        "M5.5,6.5 H18.5 A1.5,1.5 0 0 1 20,8 V16 A1.5,1.5 0 0 1 18.5,17.5 H5.5 A1.5,1.5 0 0 1 4,16 V8 A1.5,1.5 0 0 1 5.5,6.5 Z",
        "M8,10.5 H8.05", "M12,10.5 H12.05", "M16,10.5 H16.05", "M8.5,14 H15.5",
    )
}

private const val BULB_BODY =
    "M12,3 A6,6 0 0 0 8.4,13.8 C9.1,14.4 9.3,15 9.3,16 H14.7 C14.7,15 14.9,14.4 15.6,13.8 A6,6 0 0 0 12,3 Z"
private const val BULB_BASE = "M10,18.5 H14"
private const val BULB_BASE2 = "M10.8,20.8 H13.2"

private val ThinBulbOff by lazy {
    // 关：空心灯泡 + 斜线
    thinIcon("ThinBulbOff", BULB_BODY, BULB_BASE, BULB_BASE2, "M5,19 L19,5")
}
private val ThinBulbAuto by lazy {
    thinIcon("ThinBulbAuto", BULB_BODY, BULB_BASE, BULB_BASE2, "M12,9 H12.05")
}
private val ThinBulbLow by lazy {
    thinIcon("ThinBulbLow", BULB_BODY, BULB_BASE, BULB_BASE2, "M10.5,10 H13.5")
}
private val ThinBulbMedium by lazy {
    thinIcon("ThinBulbMedium", BULB_BODY, BULB_BASE, BULB_BASE2, "M10.5,8.2 H13.5", "M10.5,11.2 H13.5")
}
private val ThinBulbHigh by lazy {
    // 高档及以上：实心灯泡
    thinIcon("ThinBulbHigh", BULB_BASE, BULB_BASE2, filledPaths = listOf(BULB_BODY))
}

// 思考：关 = 斜线空心；自动 = 空心加点；低/中 = 1/2 条横线；高及以上 = 实心
internal fun ReasoningLevel.chatInputIcon(): ImageVector = when (this) {
    ReasoningLevel.OFF -> ThinBulbOff
    ReasoningLevel.AUTO -> ThinBulbAuto
    ReasoningLevel.LOW -> ThinBulbLow
    ReasoningLevel.MEDIUM -> ThinBulbMedium
    ReasoningLevel.HIGH, ReasoningLevel.XHIGH, ReasoningLevel.MAX -> ThinBulbHigh
}

internal val ThinMicIcon: ImageVector by lazy {
    thinIcon(
        "ThinMic",
        "M12,3.5 A3,3 0 0 1 15,6.5 V11.5 A3,3 0 0 1 9,11.5 V6.5 A3,3 0 0 1 12,3.5 Z",
        "M6.5,11 A5.5,5.5 0 0 0 17.5,11",
        "M12,16.5 V20.5",
    )
}

internal val ThinEnvelopeIcon: ImageVector by lazy {
    thinIcon(
        "ThinEnvelope",
        "M5.5,5.5 H18.5 A1.5,1.5 0 0 1 20,7 V17 A1.5,1.5 0 0 1 18.5,18.5 H5.5 A1.5,1.5 0 0 1 4,17 V7 A1.5,1.5 0 0 1 5.5,5.5 Z",
        "M4.5,7.5 L12,13 L19.5,7.5",
    )
}

internal val ThinStopIcon: ImageVector by lazy {
    thinIcon("ThinStop", roundedSquare(7f, 7f, size = 10f, r = 1.5f))
}

// 顶栏数据条圆里的雪花：三条穿心线，上下两端各一个小 V
internal val ThinSnowflakeIcon: ImageVector by lazy {
    thinIcon(
        "ThinSnowflake",
        "M12,3 V21",
        "M4.2,7.5 L19.8,16.5",
        "M4.2,16.5 L19.8,7.5",
        "M9.5,5.2 L12,7.7 L14.5,5.2",
        "M9.5,18.8 L12,16.3 L14.5,18.8",
    )
}
