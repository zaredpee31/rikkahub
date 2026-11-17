package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.layout.Arrangement
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
import me.rerere.rikkahub.ui.theme.LabelStrongStyle
import me.rerere.rikkahub.ui.theme.LabelWeakStyle
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import me.rerere.rikkahub.utils.toMessageTimeString

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

    val palace = palaceColors()
    // 横条卡片：48dp 高、圆角 20、liquidGlass（底 0.56、描边 1.00），内部从左到右：
    // 楼层号（弱）+ 时间（加粗）/ 统计行，右边是分支切换和三个小按钮
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .liquidGlass(
                hazeState = LocalGlassHazeState.current ?: rememberHazeState(),
                shape = RoundedCornerShape(20.dp),
                fillAlpha = 0.56f,
                edgeAlpha = 1.0f,
            )
            .padding(start = 16.dp, end = 8.dp),
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
                        style = LabelWeakStyle,
                        color = palace.mute,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                if (settings.displaySetting.showDateTimeInMessage) {
                    Text(
                        text = message.createdAt.toJavaLocalDateTime().toMessageTimeString(),
                        style = LabelStrongStyle,
                        color = palace.ink,
                        maxLines = 1,
                    )
                }
            }
            ChatMessageNerdLine(message = message)
        }

        ChatMessageBranchSelector(
            node = node,
            onUpdate = onUpdate,
        )

        ChatMessageActionButton(
            icon = HugeIcons.Copy01,
            contentDescription = stringResource(R.string.copy),
            onClick = { context.copyMessageToClipboard(message) },
        )

        ChatMessageActionButton(
            icon = HugeIcons.Refresh03,
            contentDescription = stringResource(R.string.regenerate),
            onClick = {
                if (message.role == MessageRole.USER) {
                    showRegenerateConfirm = true
                } else {
                    onRegenerate()
                }
            },
        )

        ChatMessageActionButton(
            icon = HugeIcons.MoreVertical,
            contentDescription = stringResource(R.string.more_options),
            onClick = onOpenActionSheet,
        )
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

// 横条里的图标：比之前小一档，色用 mute
private val ActionIconSize = 14.dp

// 消息下方的小号图标按钮，按下时圆形收成方角
@Composable
internal fun ChatMessageActionButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    // 按钮排得密，不让 48dp 的最小触控尺寸把间距撑开
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        IconButton(
            onClick = onClick,
            shapes = IconButtonDefaults.shapes(),
            modifier = modifier.size(IconButtonDefaults.extraSmallContainerSize()),
            enabled = enabled,
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = palaceColors().mute,
            ),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(ActionIconSize),
            )
        }
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
