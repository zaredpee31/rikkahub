package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.combinedClickable

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import kotlin.uuid.Uuid
import kotlinx.coroutines.Job
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.hugeicons.stroke.Bug01
import me.rerere.hugeicons.stroke.Camera01
import me.rerere.hugeicons.stroke.Codesandbox
import me.rerere.hugeicons.stroke.ComputerTerminal01
import me.rerere.hugeicons.stroke.Files02
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.Idea01
import me.rerere.hugeicons.stroke.McpServer
import me.rerere.hugeicons.stroke.Image02
import me.rerere.hugeicons.stroke.MusicNote03
import me.rerere.hugeicons.stroke.Package
import me.rerere.hugeicons.stroke.Package01
import me.rerere.hugeicons.stroke.PaintBoard
import me.rerere.hugeicons.stroke.Settings02
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.hugeicons.stroke.Video01
import me.rerere.hugeicons.stroke.Voice
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.datastore.ClawdPetMode
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.lastContextTokens
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.ui.components.ui.ExtensionSelector
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceDialog
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceDivider
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceGlass
import me.rerere.rikkahub.ui.theme.palaceColors
import me.rerere.rikkahub.utils.formatNumber
import me.rerere.workspace.WorkspaceShellStatus
import org.koin.compose.koinInject

// 顺序决定切换方向：往后是进入下一级，往前是返回
private enum class FilesPickerPage {
    MAIN,
    MCP,
    EXTENSIONS,
}

private val FilesPickerPage.parent: FilesPickerPage
    get() = FilesPickerPage.MAIN

@Composable
internal fun FilesPicker(
    conversation: Conversation,
    // 会话视角下的助手和模型：会话开始后以会话上固定的配置为准
    assistant: Assistant,
    chatModel: Model?,
    state: ChatInputState,
    mcpManager: McpManager,
    onCompressContext: (additionalPrompt: String, keepRecentMessages: Int) -> Job,
    onUpdateChatModel: (Model) -> Unit,
    onUpdateAssistant: (Assistant) -> Unit,
    onUpdateWorkspaceCwd: (String?) -> Unit,
    showCompressDialog: Boolean,
    onShowCompressDialogChange: (Boolean) -> Unit,
    // 桌宠崽崽三档：点一下循环切换
    petMode: ClawdPetMode,
    onPetModeChange: (ClawdPetMode) -> Unit,
    onDismiss: () -> Unit,
    onTakePic: () -> Unit,
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onPickAudio: () -> Unit,
    onPickFile: () -> Unit,
    onSketch: () -> Unit,
    onStartVoiceMode: (() -> Unit)? = null,
    // 摇骰子（从输入栏挪进来的），不传就不显示
    onRollDice: (() -> Unit)? = null,
) {
    val settings = LocalSettings.current
    val provider = chatModel?.findProvider(providers = settings.providers)
    val navController = LocalNavController.current
    val workspaceRepository: WorkspaceRepository = koinInject()
    val workspaces by workspaceRepository.listFlow().collectAsState(initial = emptyList())

    val boundWorkspace = remember(workspaces, assistant.workspaceId) {
        workspaces.find { it.id == assistant.workspaceId?.toString() }
    }
    val showCwd = boundWorkspace != null && boundWorkspace.shellStatus == WorkspaceShellStatus.READY.name
    var showCwdSheet by remember { mutableStateOf(false) }

    var page by remember { mutableStateOf(FilesPickerPage.MAIN) }
    // 在子页面时，返回键回到上一级而不是关闭 sheet
    BackHandler(enabled = page != FilesPickerPage.MAIN) {
        page = page.parent
    }
    AnimatedContent(
        targetState = page,
        transitionSpec = {
            if (targetState > initialState) {
                slideInHorizontally { it } + fadeIn() togetherWith
                    slideOutHorizontally { -it } + fadeOut()
            } else {
                slideInHorizontally { -it } + fadeIn() togetherWith
                    slideOutHorizontally { it } + fadeOut()
            }
        },
        label = "FilesPickerPage"
    ) { currentPage ->
        when (currentPage) {
            FilesPickerPage.MCP -> McpPickerPage(
                assistant = assistant,
                servers = settings.mcpServers,
                mcpManager = mcpManager,
                onUpdateAssistant = onUpdateAssistant,
                onBack = { page = FilesPickerPage.MAIN },
            )

            FilesPickerPage.EXTENSIONS -> ExtensionPickerPage(
                assistant = assistant,
                settings = settings,
                onUpdateAssistant = onUpdateAssistant,
                onBack = { page = FilesPickerPage.MAIN },
                onDismissAll = onDismiss,
            )

            FilesPickerPage.MAIN -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val showWorkspace = workspaces.isNotEmpty()
                val showMcp = settings.mcpServers.isNotEmpty()
                // 限制了上下文消息数就是滚动上下文，旧消息会自动移出，不需要压缩
                val showCompress = assistant.contextMessageLimit <= 0
                val showReasoning = chatModel?.abilities?.contains(ModelAbility.REASONING) == true
                var showReasoningPicker by remember { mutableStateOf(false) }
                var showWorkspaceSheet by remember { mutableStateOf(false) }
                val modelListState = rememberModelListState(
                    modelId = chatModel?.id,
                    providers = settings.providers,
                    type = ModelType.CHAT,
                )

                // 最上面一行：当前模型，完整显示不截断
                CurrentModelRow(
                    modelName = chatModel?.displayName ?: stringResource(R.string.model_list_select_model),
                    onClick = { modelListState.open() },
                )

                val mcpCount = settings.mcpServers.count {
                    it.commonOptions.enable && assistant.mcpServers.contains(it.id)
                }
                val extensionCount =
                    assistant.modeInjectionIds.size +
                        assistant.lorebookIds.size +
                        assistant.enabledSkills.size
                val contextTokens = remember(conversation.messageNodes) { conversation.lastContextTokens() }
                val contextLength = chatModel?.contextLength?.takeIf { it > 0 }

                val tiles = buildList {
                    add(GridTile(HugeIcons.Camera01, stringResource(R.string.take_picture), null, onClick = onTakePic))
                    add(GridTile(HugeIcons.Image02, stringResource(R.string.photo), null, onClick = onPickImage))
                    add(GridTile(HugeIcons.PaintBoard, stringResource(R.string.sketch), null, onClick = onSketch))
                    add(GridTile(HugeIcons.Files02, stringResource(R.string.upload_file), null, onClick = onPickFile))
                    onRollDice?.let { roll -> add(GridTile(ThinDiceIcon, "骰子", null, onClick = roll)) }
                    if (provider != null && provider is ProviderSetting.Google) {
                        add(GridTile(HugeIcons.Video01, stringResource(R.string.video), null, onClick = onPickVideo))
                        add(GridTile(HugeIcons.MusicNote03, stringResource(R.string.audio), null, onClick = onPickAudio))
                    }
                    onStartVoiceMode?.let { start ->
                        add(GridTile(HugeIcons.Voice, stringResource(R.string.chat_page_voice_title), null, onClick = start))
                    }
                    if (showReasoning) {
                        add(
                            GridTile(
                                icon = HugeIcons.Idea01,
                                title = "思考深度",
                                status = assistant.reasoningLevel.label(),
                                onClick = { showReasoningPicker = true },
                            )
                        )
                    }
                    if (showWorkspace) {
                        add(
                            GridTile(
                                icon = HugeIcons.Codesandbox,
                                title = stringResource(R.string.assistant_page_workspace),
                                status = boundWorkspace?.name ?: stringResource(R.string.assistant_page_workspace_unbound),
                                onClick = { showWorkspaceSheet = true },
                            )
                        )
                        if (boundWorkspace != null) {
                            add(
                                GridTile(
                                    icon = HugeIcons.Settings02,
                                    title = stringResource(R.string.workspace_detail),
                                    status = null,
                                    onClick = {
                                        onDismiss()
                                        navController.navigate(Screen.WorkspaceDetail(boundWorkspace.id))
                                    },
                                )
                            )
                            if (boundWorkspace.supportsTerminal()) {
                                add(
                                    GridTile(
                                        icon = HugeIcons.ComputerTerminal01,
                                        title = stringResource(R.string.workspace_terminal),
                                        status = null,
                                        onClick = {
                                            onDismiss()
                                            navController.navigate(Screen.WorkspaceTerminal(boundWorkspace.id))
                                        },
                                    )
                                )
                            }
                        }
                        if (showCwd) {
                            add(
                                GridTile(
                                    icon = HugeIcons.Folder01,
                                    title = stringResource(R.string.workspace_cwd_select_directory),
                                    status = (conversation.workspaceCwd ?: boundWorkspace?.workspaceDir().orEmpty())
                                        .trimEnd('/')
                                        .substringAfterLast('/')
                                        .ifBlank { null },
                                    onClick = { showCwdSheet = true },
                                )
                            )
                        }
                    }
                    if (showMcp) {
                        add(
                            GridTile(
                                icon = HugeIcons.McpServer,
                                title = stringResource(R.string.mcp_picker_title),
                                status = mcpCount.takeIf { it > 0 }?.toString(),
                                onClick = { page = FilesPickerPage.MCP },
                            )
                        )
                    }
                    add(
                        GridTile(
                            icon = HugeIcons.Package,
                            title = stringResource(R.string.assistant_page_tab_extensions),
                            status = extensionCount.takeIf { it > 0 }?.toString(),
                            onClick = { page = FilesPickerPage.EXTENSIONS },
                        )
                    )
                    // 桌宠崽崽：全开 → 仅大只 → 关，点一下切下一档，立即生效
                    add(
                        GridTile(
                            icon = HugeIcons.Bug01,
                            title = "桌宠",
                            status = when (petMode) {
                                ClawdPetMode.ALL -> "全开"
                                ClawdPetMode.BIG_ONLY -> "仅大只"
                                ClawdPetMode.OFF -> "关"
                            },
                            onClick = {
                                onPetModeChange(
                                    when (petMode) {
                                        ClawdPetMode.ALL -> ClawdPetMode.BIG_ONLY
                                        ClawdPetMode.BIG_ONLY -> ClawdPetMode.OFF
                                        ClawdPetMode.OFF -> ClawdPetMode.ALL
                                    }
                                )
                            },
                        )
                    )
                    if (showCompress) {
                        add(
                            GridTile(
                                icon = HugeIcons.Package01,
                                title = stringResource(R.string.chat_page_compress_context),
                                status = buildString {
                                    if (conversation.messageNodes.isNotEmpty()) {
                                        append("${conversation.messageNodes.size} 条")
                                    }
                                    if (contextTokens != null && contextLength != null) {
                                        if (isNotEmpty()) append(" · ")
                                        append("${contextTokens.formatNumber()}/${contextLength.formatNumber()}")
                                    }
                                }.ifBlank { null },
                                progress = if (contextTokens != null && contextLength != null) {
                                    contextTokens.toFloat() / contextLength
                                } else null,
                                onClick = { onShowCompressDialogChange(true) },
                            )
                        )
                    }
                }
                TileGrid(tiles = tiles)

                if (showReasoningPicker) {
                    ReasoningLevelPickerDialog(
                        current = assistant.reasoningLevel,
                        onSelect = { level ->
                            showReasoningPicker = false
                            onUpdateAssistant(assistant.copy(reasoningLevel = level))
                        },
                        onDismiss = { showReasoningPicker = false },
                    )
                }

                if (showWorkspaceSheet) {
                    WorkspaceSelectSheet(
                        assistant = assistant,
                        workspaces = workspaces,
                        onSelect = { workspaceId ->
                            val newId = workspaceId?.let { Uuid.parse(it) }
                            if (newId != assistant.workspaceId) {
                                // 会话的工作目录随工作区一并重置
                                onUpdateAssistant(assistant.copy(workspaceId = newId))
                            }
                            showWorkspaceSheet = false
                        },
                        onManage = {
                            showWorkspaceSheet = false
                            onDismiss()
                            navController.navigate(Screen.Workspaces)
                        },
                        onDismiss = { showWorkspaceSheet = false },
                    )
                }

                ModelListSheet(
                    state = modelListState,
                    onSelect = onUpdateChatModel,
                )
            }
        }
    }

    if (showCwdSheet && boundWorkspace != null) {
        WorkspaceCwdPickerSheet(
            workspaceId = boundWorkspace.id,
            workspaceDir = boundWorkspace.workspaceDir(),
            currentCwd = conversation.workspaceCwd,
            onSelectCwd = { newCwd ->
                onUpdateWorkspaceCwd(newCwd)
            },
            onDismiss = { showCwdSheet = false },
        )
    }

    // Compress Context Dialog
    if (showCompressDialog) {
        CompressContextDialog(onDismiss = {
            onShowCompressDialogChange(false)
            onDismiss()
        }, onConfirm = onCompressContext)
    }
}

// 扩展选择页，作为子页面嵌在加号 sheet 里
@Composable
internal fun ExtensionPickerPage(
    assistant: Assistant,
    settings: Settings,
    onUpdateAssistant: (Assistant) -> Unit,
    onBack: () -> Unit,
    onDismissAll: () -> Unit,
) {
    val navController = LocalNavController.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.75f)
            .padding(horizontal = 16.dp),
    ) {
        SheetHeader(
            title = stringResource(R.string.assistant_page_tab_extensions),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(HugeIcons.ArrowLeft01, contentDescription = stringResource(R.string.back))
                }
            },
        )
        ExtensionSelector(
            assistant = assistant,
            settings = settings,
            onUpdate = onUpdateAssistant,
            modifier = Modifier.weight(1f),
            onNavigateToPrompts = {
                onDismissAll()
                navController.navigate(Screen.Prompts)
            },
            onNavigateToSkills = {
                onDismissAll()
                navController.navigate(Screen.Skills)
            })

        Spacer(modifier = Modifier.height(16.dp))
    }
}

internal class GridTile(
    val icon: ImageVector,
    val title: String,
    // 状态小字，没有就不显示
    val status: String?,
    // 0..1，不为空时在方块底部画一条细进度条
    val progress: Float? = null,
    // 图标和名称的颜色，不传用默认；消息菜单里「删除」用低饱和红
    val tint: Color? = null,
    // 圆底里放别的东西（表情包的图）；传了就替换图标
    val leading: (@Composable () -> Unit)? = null,
    val onClick: () -> Unit,
    // 长按，不传就没有
    val onLongClick: (() -> Unit)? = null,
)

// 当前模型一整行，模型名完整显示，放不下就换行
@Composable
private fun CurrentModelRow(modelName: String, onClick: () -> Unit) {
    val c = palaceColors()
    PalaceGlass {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(c.chip),
                contentAlignment = Alignment.Center,
            ) {
                Icon(HugeIcons.Brain02, null, Modifier.size(16.dp), tint = c.ink2)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = "当前模型：$modelName",
                modifier = Modifier.weight(1f),
                fontSize = 14.sp,
                lineHeight = 19.sp,
                color = c.ink,
            )
            Spacer(Modifier.width(8.dp))
            Icon(HugeIcons.ArrowRight01, null, Modifier.size(14.dp), tint = c.faint)
        }
    }
}

// 方块网格：每行 4 个，不满一行补空位保持一样宽
@Composable
internal fun TileGrid(tiles: List<GridTile>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tiles.chunked(4).fastForEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.fastForEach { tile ->
                    GridTileItem(tile = tile, modifier = Modifier.weight(1f))
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
internal fun GridTileItem(tile: GridTile, modifier: Modifier = Modifier) {
    val c = palaceColors()
    Box(
        modifier
            .height(84.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(c.chip)
            .combinedClickable(onClick = tile.onClick, onLongClick = tile.onLongClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (tile.leading != null) {
                // 放图片的方块：圆底只当背景，不裁内容，像素图不会被切掉
                Box(
                    Modifier
                        .size(48.dp)
                        .background(c.glassStrong, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    tile.leading.invoke()
                }
            } else {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(c.glassStrong),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(tile.icon, null, Modifier.size(16.dp), tint = tile.tint ?: c.ink2)
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = tile.title,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                color = tile.tint ?: c.ink2,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            if (tile.status != null) {
                Text(
                    text = tile.status,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    color = c.mute,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (tile.progress != null) {
            // 上下文用量，接近上限时用警告色提醒压缩
            val fraction = tile.progress.coerceIn(0f, 1f)
            val color = when {
                fraction >= 0.9f -> c.error
                fraction >= 0.7f -> c.warnInk
                else -> c.ink2
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 5.dp)
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(c.glassStrong),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .background(color)
                )
            }
        }
    }
}

// 列表行尾的已启用数量，颜色跟助手详情页的浅底一致，不用 Material 默认徽章色
@Composable
internal fun CountBadge(count: Int) {
    val c = palaceColors()
    Box(
        Modifier
            .heightIn(min = 18.dp)
            .clip(CircleShape)
            .background(c.chip)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(count.toString(), fontSize = 11.sp, color = c.ink2, maxLines = 1)
    }
}

// 模型思考程度的档位选择，样式跟助手详情页的弹窗一致
@Composable
private fun ReasoningLevelPickerDialog(
    current: ReasoningLevel,
    onSelect: (ReasoningLevel) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = palaceColors()
    PalaceDialog(
        title = stringResource(R.string.reasoning_picker_title),
        onDismiss = onDismiss,
        confirmText = null,
        dismissText = "关闭",
    ) {
        Column {
            ReasoningLevel.entries.forEachIndexed { index, level ->
                if (index > 0) PalaceDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(level) }
                        .padding(horizontal = 4.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(level.chatInputIcon(), null, Modifier.size(16.dp), tint = c.ink2)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(level.label(), fontSize = 14.sp, color = c.ink, maxLines = 1)
                        Spacer(Modifier.height(2.dp))
                        Text(level.desc(), fontSize = 11.5.sp, lineHeight = 16.sp, color = c.mute)
                    }
                    if (level == current) {
                        Icon(HugeIcons.Tick01, null, Modifier.size(16.dp), tint = c.ink)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReasoningLevel.label(): String = when (this) {
    ReasoningLevel.OFF -> stringResource(R.string.reasoning_off)
    ReasoningLevel.AUTO -> stringResource(R.string.reasoning_auto)
    ReasoningLevel.LOW -> stringResource(R.string.reasoning_light)
    ReasoningLevel.MEDIUM -> stringResource(R.string.reasoning_medium)
    ReasoningLevel.HIGH -> stringResource(R.string.reasoning_heavy)
    ReasoningLevel.XHIGH -> stringResource(R.string.reasoning_xhigh)
    ReasoningLevel.MAX -> stringResource(R.string.reasoning_max)
}

@Composable
private fun ReasoningLevel.desc(): String = when (this) {
    ReasoningLevel.OFF -> stringResource(R.string.reasoning_off_desc)
    ReasoningLevel.AUTO -> stringResource(R.string.reasoning_auto_desc)
    ReasoningLevel.LOW -> stringResource(R.string.reasoning_light_desc)
    ReasoningLevel.MEDIUM -> stringResource(R.string.reasoning_medium_desc)
    ReasoningLevel.HIGH -> stringResource(R.string.reasoning_heavy_desc)
    ReasoningLevel.XHIGH, ReasoningLevel.MAX -> stringResource(R.string.reasoning_heavy_desc)
}
