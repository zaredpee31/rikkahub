package me.rerere.rikkahub.ui.pages.assistant.detail

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.ai.MemoryConsolidationStatus
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.MemoryFile
import me.rerere.rikkahub.data.model.groupByDirectory
import me.rerere.rikkahub.data.model.memoryTitleOf
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.ItemAction
import me.rerere.rikkahub.ui.components.ui.ItemActionMenu
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.pages.memory.resultText
import me.rerere.ui.components.RikkaConfirmDialog
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/** 记忆页里的子页面；null 是主页 */
private sealed interface PalaceView {
    data class Room(val room: String) : PalaceView
    data object All : PalaceView
    data object Boxes : PalaceView
    data object Settings : PalaceView
    data object Legacy : PalaceView
}

@Composable
fun AssistantMemoryPage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(
        parameters = {
            parametersOf(id)
        }
    )
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val memories by vm.memories.collectAsStateWithLifecycle()
    val consolidation by vm.memoryConsolidation.collectAsStateWithLifecycle()
    val memoryNodes by vm.memoryNodes.collectAsStateWithLifecycle()
    val memoryCore by vm.memoryCore.collectAsStateWithLifecycle()
    val memoryPlateEntries by vm.memoryPlateEntries.collectAsStateWithLifecycle()
    val memoryError by vm.memoryError.collectAsStateWithLifecycle()
    val embeddingKey by vm.embeddingKey.collectAsStateWithLifecycle()
    val memoryBoxes by vm.memoryBoxes.collectAsStateWithLifecycle()
    val regeneratingBox by vm.regeneratingBox.collectAsStateWithLifecycle()
    val memoryNotice by vm.memoryNotice.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    // 子页面的返回栈，主页在最底下
    val stack = remember { mutableStateListOf<PalaceView>() }
    val current = stack.lastOrNull()
    fun open(v: PalaceView) { stack.add(v) }
    fun back() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) else navController.popBackStack() }
    BackHandler(enabled = stack.isNotEmpty()) { back() }

    memoryError?.let { message ->
        AlertDialog(
            onDismissRequest = { vm.dismissMemoryError() },
            title = { Text("没有保存成功") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { vm.dismissMemoryError() }) { Text("知道了") }
            },
        )
    }

    memoryNotice?.let { message ->
        AlertDialog(
            onDismissRequest = { vm.dismissMemoryNotice() },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { vm.dismissMemoryNotice() }) { Text("知道了") }
            },
        )
    }

    val actions = PalaceNodeActions(
        allNodes = memoryNodes,
        currentKey = embeddingKey,
        onSaveNode = { draft, done -> vm.saveMemoryNode(draft, done) },
        onDeleteNode = { vm.deleteMemoryNode(it) },
        onBind = { a, b -> vm.bindMemories(a, b) },
        onUnbox = { vm.removeFromBox(it) },
    )
    val openEmbeddingSettings = { navController.navigate(Screen.SettingModels) }

    when (val v = current) {
        null -> PalaceHomePage(
            nodes = memoryNodes,
            boxes = memoryBoxes,
            embeddingConfigured = embeddingKey != null,
            actions = actions,
            onBack = { back() },
            onOpenRoom = { open(PalaceView.Room(it)) },
            onOpenAll = { open(PalaceView.All) },
            onOpenBoxes = { open(PalaceView.Boxes) },
            onOpenSettings = { open(PalaceView.Settings) },
            onOpenEmbedding = openEmbeddingSettings,
        )

        is PalaceView.Room -> PalaceRoomPage(
            room = v.room,
            nodes = memoryNodes,
            core = memoryCore,
            entries = memoryPlateEntries,
            actions = actions,
            onBack = { back() },
            onSaveCore = { kind, content, done -> vm.saveMemoryCore(kind, content, done) },
            onSaveEntry = { plate, eid, content, done -> vm.saveMemoryPlateEntry(plate, eid, content, done) },
            onDeleteEntry = { vm.deleteMemoryPlateEntry(it) },
        )

        PalaceView.All -> PalaceAllPage(
            nodes = memoryNodes,
            actions = actions,
            onBack = { back() },
            onReindex = { vm.reindexMemory() },
        )

        PalaceView.Boxes -> PalaceSubPage("事件盒", "EVENT BOXES · ${memoryBoxes.size}", onBack = { back() }) {
            MemoryBoxesTab(
                boxes = memoryBoxes,
                nodes = memoryNodes,
                regenerating = regeneratingBox,
                onSaveMeta = { box, name, tags, done -> vm.saveBoxMeta(box, name, tags, done) },
                onRevive = { vm.reviveArchived(it) },
                onRemove = { vm.removeFromBox(it) },
                onUnbindAllLive = { vm.unbindAllLive(it) },
                onRegenerate = { vm.regenerateBox(it) },
            )
        }

        PalaceView.Settings -> PalaceSubPage("记忆设置", "SETTINGS", onBack = { back() }) {
            MemorySettingsTab(
                assistant = assistant,
                consolidation = consolidation.takeIf { assistant.enableMemory },
                embeddingConfigured = embeddingKey != null,
                onOrganize = { vm.organizeMemory() },
                onOpenEmbedding = openEmbeddingSettings,
                onOpenLegacy = { open(PalaceView.Legacy) },
                onUpdateAssistant = { vm.update(it) },
            )
        }

        PalaceView.Legacy -> PalaceSubPage("旧记忆备份", "LEGACY · READ ONLY", onBack = { back() }) {
            MemoryFilesTab(
                memories = memories,
                onOpenMemory = { path -> navController.navigate(Screen.MemoryFile(vm.memoryId, path)) },
                onDeleteMemory = { vm.deleteMemory(it) },
            )
        }
    }
}

@Composable
private fun MemorySettingsTab(
    assistant: Assistant,
    // 为 null 时（没开记忆）不显示整理入口
    consolidation: MemoryConsolidationStatus?,
    embeddingConfigured: Boolean,
    onOrganize: () -> Unit,
    onOpenEmbedding: () -> Unit,
    onOpenLegacy: () -> Unit,
    onUpdateAssistant: (Assistant) -> Unit,
) {
    var showTimeReminderIntervalDialog by remember(assistant.id) { mutableStateOf(false) }
    var timeReminderIntervalInput by remember(assistant.id) { mutableStateOf("") }

    if (showTimeReminderIntervalDialog) {
        val interval = timeReminderIntervalInput.toIntOrNull()?.takeIf { it > 0 }
        AlertDialog(
            onDismissRequest = { showTimeReminderIntervalDialog = false },
            title = { Text(stringResource(R.string.assistant_page_time_reminder_interval)) },
            text = {
                TextField(
                    value = timeReminderIntervalInput,
                    onValueChange = { timeReminderIntervalInput = it },
                    label = { Text(stringResource(R.string.assistant_page_time_reminder_interval_label)) },
                    supportingText = { Text(stringResource(R.string.assistant_page_time_reminder_interval_hint)) },
                    isError = interval == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = interval != null,
                    onClick = {
                        interval?.let {
                            onUpdateAssistant(assistant.copy(timeReminderIntervalMinutes = it))
                        }
                        showTimeReminderIntervalDialog = false
                    },
                ) {
                    Text(stringResource(R.string.assistant_page_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimeReminderIntervalDialog = false }) {
                    Text(stringResource(R.string.assistant_page_cancel))
                }
            },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (consolidation != null) {
            MemoryConsolidationSection(status = consolidation, onOrganize = onOrganize)
        }
        if (!embeddingConfigured) {
            PalaceBanner("尚未配置 Embedding 模型，召回只能按关键词。点这里去配置", onClick = onOpenEmbedding, warn = true)
        }
        CardGroup {
            switchItem(
                checked = assistant.enableMemory,
                onCheckedChange = {
                    onUpdateAssistant(
                        assistant.copy(
                            enableMemory = it
                        )
                    )
                },
                supportingContent = { Text(stringResource(R.string.assistant_page_memory_desc)) },
                headlineContent = { Text(stringResource(R.string.assistant_page_memory)) },
            )
            switchItem(
                checked = assistant.useGlobalMemory,
                onCheckedChange = {
                    onUpdateAssistant(
                        assistant.copy(
                            useGlobalMemory = it
                        )
                    )
                },
                enabled = assistant.enableMemory,
                supportingContent = { Text(stringResource(R.string.assistant_page_global_memory_desc)) },
                headlineContent = { Text(stringResource(R.string.assistant_page_global_memory)) },
            )
            switchItem(
                checked = assistant.autoConsolidateMemory,
                onCheckedChange = {
                    onUpdateAssistant(
                        assistant.copy(
                            autoConsolidateMemory = it
                        )
                    )
                },
                enabled = assistant.enableMemory,
                supportingContent = { Text(stringResource(R.string.assistant_page_memory_auto_dream_desc)) },
                headlineContent = { Text(stringResource(R.string.assistant_page_memory_auto_dream)) },
            )
            switchItem(
                checked = assistant.enableRecentChatsReference,
                onCheckedChange = {
                    onUpdateAssistant(
                        assistant.copy(
                            enableRecentChatsReference = it
                        )
                    )
                },
                supportingContent = { Text(stringResource(R.string.assistant_page_recent_chats_desc)) },
                headlineContent = { Text(stringResource(R.string.assistant_page_recent_chats)) },
            )
        }

        CardGroup {
            switchItem(
                checked = assistant.enableTimeReminder,
                onCheckedChange = {
                    onUpdateAssistant(
                        assistant.copy(
                            enableTimeReminder = it
                        )
                    )
                },
                supportingContent = { Text(stringResource(R.string.assistant_page_time_reminder_desc)) },
                headlineContent = { Text(stringResource(R.string.assistant_page_time_reminder)) },
            )
            if (assistant.enableTimeReminder) {
                item(
                    headlineContent = { Text(stringResource(R.string.assistant_page_time_reminder_interval)) },
                    supportingContent = { Text(stringResource(R.string.assistant_page_time_reminder_interval_desc)) },
                    trailingContent = {
                        Text(
                            text = stringResource(
                                R.string.assistant_page_time_reminder_interval_value,
                                assistant.timeReminderIntervalMinutes
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    onClick = {
                        timeReminderIntervalInput = assistant.timeReminderIntervalMinutes.toString()
                        showTimeReminderIntervalDialog = true
                    },
                )
            }
        }

        CardGroup {
            item(
                onClick = onOpenLegacy,
                headlineContent = { Text("旧记忆备份") },
                supportingContent = { Text("旧版文件记忆，已经搬进记忆宫殿，只读备份") },
            )
        }
    }
}

@Composable
private fun MemoryFilesTab(
    memories: List<MemoryFile>,
    onOpenMemory: (path: String) -> Unit,
    onDeleteMemory: (MemoryFile) -> Unit,
) {
    var pendingDeleteMemory by remember { mutableStateOf<MemoryFile?>(null) }
    val memoryGroups = remember(memories) { memories.groupByDirectory() }
    val c = palaceColors()

    if (memoryGroups.isEmpty()) {
        Text(
            "没有旧记忆文件",
            fontSize = 13.sp, color = c.mute,
            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
            textAlign = TextAlign.Center,
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "这里是旧版文件记忆，已经搬进记忆宫殿，现在只当备份留着，聊天不再读它。改这里不会影响宫殿。",
            fontSize = 12.sp,
            color = c.mute,
        )

        memoryGroups.fastForEach { (directory, files) ->
            CardGroup(
                title = { Text(memorySectionTitle(directory)) },
            ) {
                files.fastForEach { memory ->
                    item(
                        onClick = { onOpenMemory(memory.path) },
                        trailingContent = {
                            ItemActionMenu(
                                actions = listOf(
                                    ItemAction(
                                        text = stringResource(R.string.delete),
                                        icon = HugeIcons.Delete01,
                                        destructive = true,
                                        onClick = { pendingDeleteMemory = memory },
                                    ),
                                )
                            )
                        },
                        headlineContent = {
                            Text(
                                text = memory.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                if (memory.description.isNotEmpty()) {
                                    Text(
                                        text = memory.description,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    text = stringResource(
                                        R.string.assistant_page_memory_updated_at,
                                        relativeTimeOf(memory.updatedAt),
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    RikkaConfirmDialog(
        show = pendingDeleteMemory != null,
        title = stringResource(R.string.confirm_delete),
        confirmText = stringResource(R.string.confirm),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            pendingDeleteMemory?.let(onDeleteMemory)
            pendingDeleteMemory = null
        },
        onDismiss = { pendingDeleteMemory = null },
        text = {
            Text(
                text = pendingDeleteMemory?.path.orEmpty(),
                maxLines = 8,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}

/** 后台整理的状态和手动入口 */
@Composable
private fun MemoryConsolidationSection(
    status: MemoryConsolidationStatus,
    onOrganize: () -> Unit,
) {
    val lastRun = status.lastRun
    CardGroup {
        item(
            headlineContent = { Text(stringResource(R.string.memory_dream_title)) },
            supportingContent = { Text(memoryConsolidationSummary(status)) },
            trailingContent = {
                if (status.running) {
                    ContainedLoadingIndicator(modifier = Modifier.size(32.dp))
                } else {
                    FilledTonalButton(onClick = onOrganize) {
                        Text(
                            stringResource(
                                if (lastRun?.error != null) {
                                    R.string.assistant_page_memory_dream_retry
                                } else {
                                    R.string.assistant_page_memory_dream_now
                                }
                            )
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun memoryConsolidationSummary(status: MemoryConsolidationStatus): String {
    val lastRun = status.lastRun
    return when {
        status.running -> stringResource(R.string.memory_dreaming)
        lastRun?.error != null -> lastRun.resultText()
        else -> listOfNotNull(
            status.pendingTurns.takeIf { it > 0 }?.let {
                stringResource(R.string.assistant_page_memory_dream_pending, it)
            },
            lastRun?.let { "${relativeTimeOf(it.finishedAt)}: ${it.resultText()} (${durationTextOf(it.duration)})" },
        ).joinToString("\n").ifEmpty { stringResource(R.string.assistant_page_memory_dream_hint) }
    }
}

// 按整秒显示成 "42s"、"1m 5s"
private fun durationTextOf(duration: Duration): String = duration.inWholeSeconds.coerceAtLeast(1).seconds.toString()

// 根目录下是 profile 和 preferences，都是关于用户本人的；记忆格式约定的几个目录有译名，其余的从目录名推
@Composable
private fun memorySectionTitle(directory: String): String = when (directory) {
    "/" -> stringResource(R.string.assistant_page_memory_section_you)
    "/topics" -> stringResource(R.string.assistant_page_memory_section_topics)
    "/areas" -> stringResource(R.string.assistant_page_memory_section_areas)
    "/people" -> stringResource(R.string.assistant_page_memory_section_people)
    else -> memoryTitleOf(directory.removePrefix("/"))
}

// 一周内显示"41 分钟前"这样的相对时间，更早的显示日期
private fun relativeTimeOf(time: Long): String = DateUtils.getRelativeTimeSpanString(
    time,
    System.currentTimeMillis(),
    DateUtils.MINUTE_IN_MILLIS,
    DateUtils.FORMAT_ABBREV_MONTH,
).toString()
