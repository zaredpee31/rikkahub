package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import me.rerere.rikkahub.ui.components.ai.GlassShadow
import me.rerere.rikkahub.ui.components.ai.flatGlass
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.chrisbanes.haze.rememberHazeState
import me.rerere.rikkahub.ui.components.ai.LocalGlassHazeState
import me.rerere.rikkahub.ui.components.ai.liquidGlass
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.datetime.toJavaLocalDateTime
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Edit01
import me.rerere.hugeicons.stroke.FavouriteCircle
import me.rerere.hugeicons.stroke.GitFork
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.hugeicons.stroke.Share04
import me.rerere.hugeicons.stroke.TextSelection
import me.rerere.hugeicons.stroke.WebDesign01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.ui.components.ai.GridTile
import me.rerere.rikkahub.ui.components.ai.TileGrid
import me.rerere.rikkahub.ui.components.ui.PalaceConfirmDialog
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceGlass
import me.rerere.rikkahub.ui.theme.palaceColors
import me.rerere.rikkahub.utils.copyMessageToClipboard
import me.rerere.rikkahub.utils.toLocalString

@Composable
fun ColumnScope.ChatMessageActionButtons(
    message: UIMessage,
    node: MessageNode,
    onUpdate: (MessageNode) -> Unit,
    onRegenerate: () -> Unit,
    onOpenActionSheet: () -> Unit,
    floor: Int? = null,
) {
    val context = LocalContext.current
    val settings = LocalSettings.current
    var isPendingDelete by remember { mutableStateOf(false) }
    var showRegenerateConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(isPendingDelete) {
        if (isPendingDelete) {
            delay(3000) // 3秒后自动取消
            isPendingDelete = false
        }
    }

    val isUser = message.role == MessageRole.USER
    val onCopy: () -> Unit = { context.copyMessageToClipboard(message) }
    val onRegenerateClick: () -> Unit = {
        if (isUser) {
            showRegenerateConfirm = true
        } else {
            onRegenerate()
        }
    }

    if (isUser) {
        // 用户侧：NOIR .mes[is_user] .mes_buttons 小胶囊，右对齐在气泡下面，不显示楼层、时间、统计
        Row(
            modifier = Modifier.align(Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ChatMessageBranchLabel(node = node)
            Row(
                modifier = Modifier
                    .flatGlass(CircleShape, fillAlpha = 0.28f, edgeAlpha = 0.90f)
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ActionPillContent(
                    node = node,
                    onUpdate = onUpdate,
                    onCopy = onCopy,
                    onRegenerate = onRegenerateClick,
                    onOpenActionSheet = onOpenActionSheet,
                )
            }
        }
    } else {
        // 助手横条：48dp 高、圆角 20、liquidGlass（底 0.56、描边 1.00、阴影 0 1 3 黑 0.04，不传渐变）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .liquidGlass(
                    hazeState = LocalGlassHazeState.current ?: rememberHazeState(),
                    shape = RoundedCornerShape(20.dp),
                    fillAlpha = 0.56f,
                    edgeAlpha = 1.0f,
                    shadows = listOf(GlassShadow(1.dp, 3.dp, Color.Black.copy(alpha = 0.04f))),
                )
                .padding(start = 18.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (floor != null) {
                        Text(
                            text = "#$floor",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Normal,
                            letterSpacing = 0.4.sp,
                            color = ActionMuteColor,
                            maxLines = 1,
                        )
                        Spacer(Modifier.width(5.dp))
                    }
                    if (settings.displaySetting.showDateTimeInMessage) {
                        Text(
                            text = message.createdAt.toJavaLocalDateTime().toEnglishMessageTime(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ActionTimeColor,
                            maxLines = 1,
                        )
                    }
                }
                ChatMessageNerdLine(message = message)
            }

            ChatMessageBranchLabel(node = node)
            Spacer(Modifier.width(6.dp))

            // 复制 / 重新生成 / 三个点收进一颗玻璃胶囊：高 26、padding 0/10、间距 4
            Row(
                modifier = Modifier
                    .height(26.dp)
                    .flatGlass(CircleShape, fillAlpha = 0.28f, edgeAlpha = 0.90f)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ActionPillContent(
                    node = node,
                    onUpdate = onUpdate,
                    onCopy = onCopy,
                    onRegenerate = onRegenerateClick,
                    onOpenActionSheet = onOpenActionSheet,
                )
            }
        }
    }

    // Regenerate confirmation dialog
    PalaceConfirmDialog(
        show = showRegenerateConfirm,
        title = stringResource(R.string.regenerate),
        text = stringResource(R.string.regenerate_confirm_message),
        confirmText = stringResource(R.string.confirm),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            showRegenerateConfirm = false
            onRegenerate()
        },
        onDismiss = { showRegenerateConfirm = false },
    )
}

private val ActionMuteColor = Color(0xFF8E8E93)
private val ActionTimeColor = Color(0xFF3C3C3C)
private val ActionIconInk = Color(0xFF636366)

// 胶囊里的内容：分支箭头（多于 1 条才有）+ 复制 + 重新生成 + 三个点
@Composable
private fun ActionPillContent(
    node: MessageNode,
    onUpdate: (MessageNode) -> Unit,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    onOpenActionSheet: () -> Unit,
) {
    ChatMessageBranchArrows(node = node, onUpdate = onUpdate, tint = ActionIconInk)
    ChatMessageActionButton(
        icon = HugeIcons.Copy01,
        contentDescription = stringResource(R.string.copy),
        onClick = onCopy,
    )
    ChatMessageActionButton(
        icon = HugeIcons.Refresh03,
        contentDescription = stringResource(R.string.regenerate),
        onClick = onRegenerate,
    )
    ChatMessageActionButton(
        icon = HugeIcons.MoreVertical,
        contentDescription = stringResource(R.string.more_options),
        onClick = onOpenActionSheet,
    )
}

private val MessageTimeLocale: Locale = Locale.ENGLISH
private val WeekdayTimeFormatter = DateTimeFormatter.ofPattern("EEEE, h:mm a", MessageTimeLocale)
private val DateTimeShortFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a", MessageTimeLocale)

// 7 天内 "Tuesday, 2:28 PM"，更早 "Nov 15, 2:28 PM"
private fun LocalDateTime.toEnglishMessageTime(): String {
    val days = ChronoUnit.DAYS.between(toLocalDate(), LocalDate.now())
    return format(if (days in 0..6) WeekdayTimeFormatter else DateTimeShortFormatter)
}

// 胶囊里的图标：15dp 放在 22dp 的点击框里
private val ActionIconSize = 15.dp
private val ActionClickSize = 22.dp

@Composable
internal fun ChatMessageActionButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = ActionIconInk,
) {
    Box(
        modifier = modifier
            .size(ActionClickSize)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(ActionIconSize),
        )
    }
}

@Composable
fun ChatMessageActionsSheet(
    message: UIMessage,
    model: Model?,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onFork: () -> Unit,
    onSelectAndCopy: () -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onWebViewPreview: () -> Unit,
    onDismissRequest: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
    ) {
        // WebView Preview (only show if message has text content)
        val hasTextContent = message.parts.filterIsInstance<UIMessagePart.Text>()
            .any { it.text.isNotBlank() }
        val c = palaceColors()
        // 复用加号面板的方块（GridTile）：圆底图标 + 名称，纯按钮没有状态小字；点了先关菜单再执行
        fun tile(icon: ImageVector, label: String, tint: Color? = null, onClick: () -> Unit) =
            GridTile(
                icon = icon,
                title = label,
                status = null,
                tint = tint,
                onClick = {
                    onDismissRequest()
                    onClick()
                },
            )
        val tiles = buildList {
            add(tile(HugeIcons.TextSelection, stringResource(R.string.select_and_copy), onClick = onSelectAndCopy))
            if (hasTextContent) {
                add(tile(HugeIcons.WebDesign01, stringResource(R.string.render_with_webview), onClick = onWebViewPreview))
            }
            add(tile(HugeIcons.Edit01, stringResource(R.string.edit), onClick = onEdit))
            add(tile(HugeIcons.Share04, stringResource(R.string.share), onClick = onShare))
            add(tile(HugeIcons.GitFork, stringResource(R.string.create_fork), onClick = onFork))
            if (onToggleFavorite != null) {
                add(
                    tile(
                        icon = HugeIcons.FavouriteCircle,
                        label = stringResource(
                            if (isFavorite) R.string.chat_message_remove_favorite
                            else R.string.chat_message_add_favorite
                        ),
                        onClick = onToggleFavorite,
                    )
                )
            }
            // 删除：方块底色和别的一样，只有图标和文字用低饱和红（c.error）
            add(tile(HugeIcons.Delete01, stringResource(R.string.delete), tint = c.error, onClick = onDelete))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 菜单容器：磨砂面板，里面是每行 4 个的方块网格
            PalaceGlass {
                TileGrid(
                    tiles = tiles,
                    modifier = Modifier.padding(10.dp),
                )
            }

            // Message Info：字号颜色和已改过页面的副标一致
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    message.createdAt.toJavaLocalDateTime().toLocalString(),
                    fontSize = 11.sp,
                    color = c.mute,
                )
                if (model != null) {
                    Text(model.displayName, fontSize = 11.sp, color = c.mute)
                }
            }
        }
    }
}
