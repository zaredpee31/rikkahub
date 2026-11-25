package me.rerere.rikkahub.companion

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.ui.components.ui.UIAvatar

// MUSI 皮肤（照《M U S I》粉色 by 蹲）：只在皮肤 layout 写 "musi" / avatarFrame 写 "polaroid" 时用到，冷淡风不经过这里
private val MusiRim = Color(0xFF8E98A0)
private val MusiPinkRim = Color(0xFFAC96A2)
private val MusiInk = Color(0xFF32383C)
private val MusiSub = Color(0xFF8E98A0)
private val MusiLabel = Color(0xFF5A646C)

/** 灰白 / 粉白圆钮：底色 + 上半截白色高光 + 细描边。给了 iconUrl 就直接用图。 */
@Composable
fun MusiRoundButton(
    label: String,
    pink: Boolean,
    size: Float = 30f,
    iconUrl: String? = null,
    onClick: (() -> Unit)? = null,
) {
    if (iconUrl != null) {
        SkinIconImage(iconUrl, size, label, onClick = onClick ?: {})
        return
    }
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(size.dp)
            .shadow(1.5.dp, CircleShape, clip = false)
            .background(if (pink) Color(0xFFFAF0F5) else Color(0xFFF0F0F0), CircleShape)
            .background(Brush.verticalGradient(listOf(Color.White, Color.White.copy(alpha = 0f))), CircleShape)
            .border(1.dp, if (pink) MusiPinkRim else MusiRim, CircleShape)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                ) else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = (size * 0.42f).sp, fontWeight = FontWeight.Bold, color = Color(0xFF463A40))
    }
}

/** 一根播放进度条：左右两段小字，中间细轨道 + 圆点 */
@Composable
fun MusiProgress(left: String, right: String, modifier: Modifier = Modifier, at: Float = 0.32f) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(left, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = MusiLabel, maxLines = 1)
        Canvas(modifier = Modifier.weight(1f).height(10.dp)) {
            val h = 5.dp.toPx()
            val y = (size.height - h) / 2
            drawRoundRect(color = Color.White, topLeft = Offset(0f, y), size = Size(size.width, h), cornerRadius = CornerRadius(h / 2))
            drawRoundRect(color = MusiRim, topLeft = Offset(0f, y), size = Size(size.width, h), cornerRadius = CornerRadius(h / 2), style = Stroke(width = 0.8.dp.toPx()))
            val c = Offset(size.width * at, size.height / 2)
            drawCircle(color = Color.White, radius = 4.5.dp.toPx(), center = c)
            drawCircle(color = MusiRim, radius = 4.5.dp.toPx(), center = c, style = Stroke(width = 1.dp.toPx()))
            drawCircle(color = MusiRim, radius = 1.6.dp.toPx(), center = c)
        }
        Text(right, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = MusiLabel, maxLines = 1)
    }
}

/** 头像外面套一层白色拍立得框（底边宽一点），歪一点 */
fun Modifier.musiFrame(tilt: Float = 4f): Modifier = this
    .rotate(tilt)
    .shadow(2.dp, RoundedCornerShape(3.dp), clip = false)
    .background(Color.White, RoundedCornerShape(3.dp))
    .border(1.dp, MusiRim, RoundedCornerShape(3.dp))
    .padding(start = 3.dp, top = 3.dp, end = 3.dp, bottom = 8.dp)

/** 拍立得头像：白框 + 底下一行小字；stack 后面再垫一张歪的，badge 右下角一个 + 小圆 */
@Composable
fun MusiPolaroid(
    name: String,
    avatar: Avatar,
    size: Float,
    label: String,
    loading: Boolean = false,
    tilt: Float = -4f,
    onClick: (() -> Unit)? = null,
    stack: Boolean = false,
    badge: Boolean = false,
) {
    Box {
        if (stack) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .rotate(tilt - 8f)
                    .background(Color.White, RoundedCornerShape(3.dp))
                    .border(1.dp, MusiRim, RoundedCornerShape(3.dp)),
            )
        }
        Column(
            modifier = Modifier
                .rotate(tilt)
                .shadow(2.dp, RoundedCornerShape(3.dp), clip = false)
                .background(Color.White, RoundedCornerShape(3.dp))
                .border(1.dp, MusiRim, RoundedCornerShape(3.dp))
                .padding(start = 3.dp, top = 3.dp, end = 3.dp, bottom = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            UIAvatar(
                name = name,
                value = avatar,
                modifier = Modifier.size(size.dp),
                loading = loading,
                shape = RoundedCornerShape(2.dp),
                onClick = onClick,
            )
            if (label.isNotEmpty()) {
                Text(label, fontSize = (size / 8f).coerceAtLeast(6f).sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = MusiLabel, maxLines = 1)
            }
        }
        if (badge) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 5.dp, y = 3.dp)
                    .size(16.dp)
                    .background(Color(0xFFF4F4F4), CircleShape)
                    .border(1.dp, MusiRim, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("+", fontSize = 11.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold, color = MusiInk)
            }
        }
    }
}

/** 输入框左边：歪着的我的小拍立得（底下写 ME）+ 后面露半张 CD，点了是加号的功能 */
@Composable
fun MusiPlusButton(name: String, avatar: Avatar, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 46.dp, height = 42.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    ) {
        Canvas(modifier = Modifier.align(Alignment.CenterEnd).size(24.dp)) {
            val r = size.minDimension / 2
            drawCircle(Color.White, r)
            drawCircle(MusiRim, r - 0.5.dp.toPx(), style = Stroke(1.dp.toPx()))
            drawCircle(Color(0xFFD6CED4), r * 0.55f, style = Stroke(1.dp.toPx()))
            drawCircle(MusiRim, r * 0.18f, style = Stroke(1.dp.toPx()))
        }
        Box(modifier = Modifier.align(Alignment.CenterStart)) {
            MusiPolaroid(name, avatar, 22f, "ME", tilt = -4f)
        }
    }
}

/** MUSI 顶栏：一排圆钮 / 拍立得头像 + 名字 + 签名卡 / 进度条 */
@Composable
fun MusiTopBar(
    top: SkinTopBar,
    assistantName: String,
    assistantAvatar: Avatar,
    onMenu: (() -> Unit)?,
    onNewChat: () -> Unit,
    onClickAvatar: () -> Unit,
    mcpText: String,
    onMcp: () -> Unit,
) {
    val ink = skinColor(top.text) ?: MusiInk
    val sub = skinColor(top.subText) ?: MusiSub
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(top.fill?.let { Modifier.skinFill(it) } ?: Modifier)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = top.padX.dp)
            .padding(top = 4.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (onMenu != null) MusiRoundButton("<", pink = false, size = top.iconSize, iconUrl = top.leftIcon, onClick = onMenu)
            MusiRoundButton(">", pink = true, size = top.iconSize, iconUrl = top.rightIcon, onClick = onNewChat)
            Spacer(Modifier.weight(1f))
            MusiRoundButton("M", pink = false, size = top.iconSize, onClick = onMcp)
            MusiRoundButton("U", pink = true, size = top.iconSize)
            MusiRoundButton("S", pink = false, size = top.iconSize)
            MusiRoundButton("I", pink = true, size = top.iconSize, onClick = onClickAvatar)
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MusiPolaroid(assistantName, assistantAvatar, top.leftAvatarSize, top.coverLabel.orEmpty(), tilt = 0f, onClick = onClickAvatar, badge = true)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(assistantName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Box(modifier = Modifier.size(15.dp).border(1.dp, sub, CircleShape), contentAlignment = Alignment.Center) {
                        Text("✓", fontSize = 9.sp, lineHeight = 9.sp, color = sub)
                    }
                    Text(top.handle.orEmpty(), fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = sub, maxLines = 1)
                    Spacer(Modifier.weight(1f))
                    Text(mcpText, fontSize = 9.sp, color = sub, modifier = Modifier.clickable(onClick = onMcp))
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color(0xFFFDFDFE), Color(0xFFF7EEF2))), RoundedCornerShape(10.dp))
                        .border(0.8.dp, Color(0xFFA2ACB4), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text("❝ " + top.quote.orEmpty(), fontSize = 11.sp, lineHeight = 15.sp, color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    top.stats?.let {
                        Text("$it   ❞", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = sub, maxLines = 1, modifier = Modifier.align(Alignment.End))
                    }
                }
            }
        }
        MusiProgress(top.progressLeft ?: "00:32", top.progressRight ?: "04:15", Modifier.fillMaxWidth())
    }
}
