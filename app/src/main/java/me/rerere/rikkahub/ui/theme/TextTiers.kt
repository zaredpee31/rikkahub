package me.rerere.rikkahub.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// NOIR 的字号字重层级。只管辅助信息，正文字号不走这里。
// 颜色不放进样式，各处用 palaceColors() 的 ink / ink2 / mute / faint 四档。

/** 名字、主标题：16 / 600 / 字距 0.3，配 ink2。 */
internal val TitleStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp)

/** 横条主行里的加粗部分（时间）：13 / 700，配 ink。 */
internal val LabelStrongStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp)

/** 横条主行里的弱化部分（楼层号）：13 / 400，配 mute。 */
internal val LabelWeakStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.2.sp)

/** 副标题：11 / 400，配 mute。 */
internal val CaptionStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.3.sp)

/** 时间戳：9.5 / 600 / 字距 0.3，配 mute。 */
internal val TimeStyle = TextStyle(fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp)

/** 元信息小字（步数、tok/s、上下文、已缓存）：9 / 500 / 字距 0.4，配 mute 或 faint。 */
internal val MetaStyle = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp)

/** 英文小标（TEMP / NOW / WIND）：11.5 / 800 / 字距 1.6。 */
internal val TagStyle = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.6.sp)

/** 消息里头像旁的名字：11 / 500 / 字距 0.3，颜色在调用处统一用 #8E8E93。 */
internal val NameStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp)
