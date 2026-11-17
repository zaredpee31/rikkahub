package me.rerere.rikkahub.ui.components.ai

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import kotlin.uuid.Uuid
import kotlinx.coroutines.Job
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderSetting
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.Camera01
import me.rerere.hugeicons.stroke.Codesandbox
import me.rerere.hugeicons.stroke.ComputerTerminal01
import me.rerere.hugeicons.stroke.Files02
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.Idea01
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
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.lastContextTokens
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.ui.components.sticker.StickerSmileIcon
import me.rerere.rikkahub.ui.components.ui.ExtensionSelector
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.pages.assistant.detail.DetailNavRow
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceDialog
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceDivider
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceGlass
import me.rerere.rikkahub.ui.pages.assistant.detail.palaceColors
import me.rerere.rikkahub.utils.formatNumber
import me.rerere.workspace.WorkspaceShellStatus
import org.koin.compose.koinInject

// 顺序决定切换方向：往后是进入下一级，往前是返回
private enum class FilesPickerPage {
    MAIN,
    MCP,
    EXTENSIONS,
    SEARCH,
    SEARCH_PROVIDER,
}

private val FilesPickerPage.parent: FilesPickerPage
    get() = when (this) {
        FilesPickerPage.SEARCH_PROVIDER -> FilesPickerPage.SEARCH
        else -> FilesPickerPage.MAIN
    }

@Composable
internal fun FilesPicker(
    conversation: Conversation,
    // 会话视角下的助手和模型：会话开始后以会话上固定的配置为准
    assistant: Assistant,
    chatModel: Model?,
    state: ChatInputState,
    mcpManager: McpManager,
    onCompressContext: (additionalPrompt: String, keepRecentMessages: Int) -> Job,
    onUpdateAssistant: (Assistant) -> Unit,
    onUpdateWorkspaceCwd: (String?) -> Unit,
    onUpdateSearchMode: (SearchMode) -> Unit,
    onUpdateSearchService: (Int) -> Unit,
    showCompressDialog: Boolean,
    onShowCompressDialogChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onTakePic: () -> Unit,
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onPickAudio: () -> Unit,
    onPickFile: () -> Unit,
    onSketch: () -> Unit,
    onStartVoiceMode: (() -> Unit)? = null,
    // 崽崽表情包入口，不传就不显示
    onOpenStickers: (() -> Unit)? = null,
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

            FilesPickerPage.SEARCH -> SearchPickerPage(
                enableSearch = assistant.enableWebSearch,
                settings = settings,
                model = chatModel,
                onUpdateSearchMode = onUpdateSearchMode,
                onSelectProvider = { page = FilesPickerPage.SEARCH_PROVIDER },
                onBack = { page = FilesPickerPage.MAIN },
                onDismissAll = onDismiss,
            )

            FilesPickerPage.SEARCH_PROVIDER -> SearchProviderPicker(
                settings = settings,
                onUpdateSearchService = { index ->
                    onUpdateSearchService(index)
                    page = FilesPickerPage.SEARCH
                },
                onBack = { page = FilesPickerPage.SEARCH },
            )

            FilesPickerPage.MAIN -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 上面那排快捷入口：拍照、照片、草图、上传文件，表情包提到这一排
                AttachmentGrid(
                    actions = buildList {
                        add(AttachmentAction(HugeIcons.Camera01, R.string.take_picture, onTakePic))
                        add(AttachmentAction(HugeIcons.Image02, R.string.photo, onPickImage))
                        add(AttachmentAction(HugeIcons.PaintBoard, R.string.sketch, onSketch))
                        add(AttachmentAction(HugeIcons.Files02, R.string.upload_file, onPickFile))
                        onOpenStickers?.let { open -> add(AttachmentAction(StickerSmileIcon, R.string.files_picker_stickers, open)) }
                        if (provider != null && provider is ProviderSetting.Google) {
                            add(AttachmentAction(HugeIcons.Video01, R.string.video, onPickVideo))
                            add(AttachmentAction(HugeIcons.MusicNote03, R.string.audio, onPickAudio))
                        }
                        onStartVoiceMode?.let { start ->
                            add(AttachmentAction(HugeIcons.Voice, R.string.chat_page_voice_title, start))
                        }
                    }
                )

                // 下面的列表：助手详情页同款圆角卡，行间点线分隔
                val showWorkspace = workspaces.isNotEmpty()
                val showMcp = settings.mcpServers.isNotEmpty()
                // 限制了上下文消息数就是滚动上下文，旧消息会自动移出，不需要压缩
                val showCompress = assistant.contextMessageLimit <= 0
                val showReasoning = chatModel?.abilities?.contains(ModelAbility.REASONING) == true
                var showReasoningPicker by remember { mutableStateOf(false) }
                PalaceGlass {
                    var first = true
                    @Composable
                    fun row() {
                        if (!first) PalaceDivider()
                        first = false
                    }
                    row()
                    SearchPickerListItem(
                        enableSearch = assistant.enableWebSearch,
                        settings = settings,
                        model = chatModel,
                        onClick = { page = FilesPickerPage.SEARCH },
                    )
                    if (showReasoning) {
                        row()
                        DetailNavRow(
                            icon = HugeIcons.Idea01,
                            title = stringResource(R.string.reasoning_picker_title),
                            desc = assistant.reasoningLevel.label(),
                            onClick = { showReasoningPicker = true },
                        )
                    }
                    if (showWorkspace) {
                        row()
                        WorkspacePickerListItem(
                            assistant = assistant,
                            workspaces = workspaces,
                            onUpdateAssistant = onUpdateAssistant,
                            onNavigateToDetail = { id ->
                                onDismiss()
                                navController.navigate(Screen.WorkspaceDetail(id))
                            },
                            onNavigateToTerminal = { id ->
                                onDismiss()
                                navController.navigate(Screen.WorkspaceTerminal(id))
                            },
                            onNavigateToManage = {
                                onDismiss()
                                navController.navigate(Screen.Workspaces)
                            },
                        )
                        if (showCwd) {
                            row()
                            DetailNavRow(
                                icon = HugeIcons.Folder01,
                                title = stringResource(R.string.workspace_cwd_select_directory),
                                desc = conversation.workspaceCwd ?: boundWorkspace?.workspaceDir().orEmpty(),
                                onClick = { showCwdSheet = true },
                            )
                        }
                    }
                    if (showMcp) {
                        row()
                        McpPickerListItem(
                            assistant = assistant,
                            servers = settings.mcpServers,
                            mcpManager = mcpManager,
                            onClick = { page = FilesPickerPage.MCP },
                        )
                    }
                    row()
                    // 扩展管理（提示词注入 + 技能）
                    val activeCount =
                        assistant.modeInjectionIds.size +
                            assistant.lorebookIds.size +
                            assistant.enabledSkills.size
                    DetailNavRow(
                        icon = HugeIcons.Package,
                        title = stringResource(R.string.assistant_page_tab_extensions),
                        desc = null,
                        onClick = { page = FilesPickerPage.EXTENSIONS },
                        trailing = { if (activeCount > 0) CountBadge(count = activeCount) },
                    )
                    if (showCompress) {
                        row()
                        val contextTokens = remember(conversation.messageNodes) {
                            conversation.lastContextTokens()
                        }
                        val contextLength = chatModel?.contextLength?.takeIf { it > 0 }
                        DetailNavRow(
                            icon = HugeIcons.Package01,
                            title = stringResource(R.string.chat_page_compress_context),
                            desc = buildString {
                                if (conversation.messageNodes.isNotEmpty()) {
                                    append(stringResource(R.string.chat_page_message_count, conversation.messageNodes.size))
                                }
                                if (contextTokens != null && contextLength != null) {
                                    if (isNotEmpty()) append(" · ")
                                    append("${contextTokens.formatNumber()} / ${contextLength.formatNumber()}")
                                }
                            }.ifBlank { null },
                            onClick = { onShowCompressDialogChange(true) },
                            trailing = {
                                if (contextTokens != null && contextLength != null) {
                                    ContextUsageRing(fraction = contextTokens.toFloat() / contextLength)
                                }
                            },
                        )
                    }
                }
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

@Composable
private fun WorkspacePickerListItem(
    assistant: Assistant,
    workspaces: List<WorkspaceEntity>,
    onUpdateAssistant: (Assistant) -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToTerminal: (String) -> Unit,
    onNavigateToManage: () -> Unit,
) {
    var showSheet by remember { mutableStateOf(false) }
    val boundWorkspace = remember(workspaces, assistant.workspaceId) {
        workspaces.find { it.id == assistant.workspaceId?.toString() }
    }

    DetailNavRow(
        icon = HugeIcons.Codesandbox,
        title = stringResource(R.string.assistant_page_workspace),
        desc = boundWorkspace?.name ?: stringResource(R.string.assistant_page_workspace_unbound),
        onClick = { showSheet = true },
        trailing = {
            if (boundWorkspace != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PickerTrailingIcon(
                        icon = HugeIcons.Settings02,
                        description = stringResource(R.string.workspace_detail),
                        onClick = { onNavigateToDetail(boundWorkspace.id) },
                    )
                    if (boundWorkspace.supportsTerminal()) {
                        PickerTrailingIcon(
                            icon = HugeIcons.ComputerTerminal01,
                            description = stringResource(R.string.workspace_terminal),
                            onClick = { onNavigateToTerminal(boundWorkspace.id) },
                        )
                    }
                }
            }
        },
    )

    if (showSheet) {
        WorkspaceSelectSheet(
            assistant = assistant,
            workspaces = workspaces,
            onSelect = { workspaceId ->
                val newId = workspaceId?.let { Uuid.parse(it) }
                if (newId != assistant.workspaceId) {
                    // 会话的工作目录随工作区一并重置
                    onUpdateAssistant(assistant.copy(workspaceId = newId))
                }
                showSheet = false
            },
            onManage = {
                showSheet = false
                onNavigateToManage()
            },
            onDismiss = { showSheet = false },
        )
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

private class AttachmentAction(
    val icon: ImageVector,
    @param:StringRes val label: Int,
    val onClick: () -> Unit,
)

// 上面那排快捷入口：一行最多四个，再多就均分成两行
@Composable
private fun AttachmentGrid(
    actions: List<AttachmentAction>,
    modifier: Modifier = Modifier,
) {
    val perRow = if (actions.size <= 4) actions.size else 4
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        actions.chunked(perRow).fastForEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.fastForEach { action ->
                    AttachmentTile(
                        action = action,
                        modifier = Modifier.weight(1f),
                    )
                }
                // 不满一行的补空位，保持每个格子一样宽
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun AttachmentTile(
    action: AttachmentAction,
    modifier: Modifier = Modifier,
) {
    val c = palaceColors()
    Column(
        modifier = modifier.clickable(onClick = action.onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(c.chip),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = c.ink2,
            )
        }
        Text(
            text = stringResource(action.label),
            fontSize = 11.5.sp,
            lineHeight = 14.sp,
            color = c.ink2,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

// 行尾的小按钮，工作区那行的设置和终端用
@Composable
private fun PickerTrailingIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    val c = palaceColors()
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, Modifier.size(14.dp), tint = c.mute)
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

// 上下文使用量进度圈，接近上限时用警告色提醒压缩
@Composable
private fun ContextUsageRing(fraction: Float) {
    val c = palaceColors()
    val color = when {
        fraction >= 0.9f -> c.error
        fraction >= 0.7f -> c.warnInk
        else -> c.ink2
    }
    CircularProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = Modifier.size(20.dp),
        color = color,
        strokeWidth = 2.5.dp,
        trackColor = c.chip,
        strokeCap = StrokeCap.Round,
    )
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

@Preview(showBackground = true)
@Composable
private fun AttachmentGridPreview() {
    AttachmentGrid(
        actions = listOf(
            AttachmentAction(HugeIcons.Camera01, R.string.take_picture) {},
            AttachmentAction(HugeIcons.Image02, R.string.photo) {},
            AttachmentAction(HugeIcons.Video01, R.string.video) {},
            AttachmentAction(HugeIcons.MusicNote03, R.string.audio) {},
            AttachmentAction(HugeIcons.Files02, R.string.upload_file) {},
        ),
        modifier = Modifier.padding(16.dp),
    )
}
