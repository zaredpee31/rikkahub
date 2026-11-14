package me.rerere.rikkahub.ui.pages.assistant.detail

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import me.rerere.rikkahub.companion.DottedLine
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
    val palaceModelName by vm.palaceModelName.collectAsStateWithLifecycle()
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
        PalaceDialog(title = "没有保存成功", onDismiss = { vm.dismissMemoryError() }, confirmText = null, dismissText = "知道了") {
            Text(message, fontSize = 13.sp, color = palaceColors().ink)
        }
    }

    memoryNotice?.let { message ->
        PalaceDialog(title = "提示", onDismiss = { vm.dismissMemoryNotice() }, confirmText = null, dismissText = "知道了") {
            Text(message, fontSize = 13.sp, color = palaceColors().ink)
        }
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
                modelName = palaceModelName,
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
    modelName: String?,
    onOrganize: () -> Unit,
    onOpenEmbedding: () -> Unit,
    onOpenLegacy: () -> Unit,
    onUpdateAssistant: (Assistant) -> Unit,
) {
    val c = palaceColors()
    var showIntervalDialog by remember(assistant.id) { mutableStateOf(false) }
    var intervalInput by remember(assistant.id) { mutableStateOf("") }

    if (showIntervalDialog) {
        val interval = intervalInput.toIntOrNull()?.takeIf { it > 0 }
        PalaceDialog(
            title = "提醒间隔",
            onDismiss = { showIntervalDialog = false },
            confirmEnabled = interval != null,
            onConfirm = {
                interval?.let { onUpdateAssistant(assistant.copy(timeReminderIntervalMinutes = it)) }
                showIntervalDialog = false
            },
        ) {
            PalaceField(intervalInput, { intervalInput = it.filter(Char::isDigit) }, label = "间隔（分钟）", singleLine = true, isError = interval == null, number = true)
            Text("距离上次发消息超过这个时间，才在消息里带上当前时间", fontSize = 11.sp, color = c.mute)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!embeddingConfigured) {
            PalaceBanner("尚未配置 Embedding 模型，召回只能按关键词。点这里去配置", onClick = onOpenEmbedding, warn = true)
        }
        PalaceSection("STATUS", "状态")
        PalaceGlass {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("整理模型：${modelName ?: "没有可用的模型，先在模型设置里选一个"}", fontSize = 13.sp, color = c.ink)
                Text(
                    "向量模型：" + if (embeddingConfigured) "已配置" else "没配置",
                    fontSize = 13.sp, color = c.ink,
                )
                Text("整理模型的选法：记忆模型，没设就用快速模型，再没有就用这个助手的对话模型", fontSize = 11.sp, lineHeight = 16.sp, color = c.mute)
            }
        }
        if (consolidation != null) {
            MemoryConsolidationSection(status = consolidation, onOrganize = onOrganize)
        }

        PalaceSection("SWITCH", "记忆")
        PalaceGlass {
            PalaceSwitchRow(
                title = "记忆宫殿",
                desc = "打开后，门牌和两块核心背景在所有窗口常驻。普通窗口里模型可以用记忆工具搜索、读写，但只有你明确要求时才会写。工具需要模型支持工具调用；消息页的自动整理和召回不需要",
                checked = assistant.enableMemory,
                onChange = { onUpdateAssistant(assistant.copy(enableMemory = it)) },
            )
            DottedLine(Modifier.fillMaxWidth().padding(horizontal = 16.dp), color = c.dot)
            PalaceSwitchRow(
                title = "全局记忆",
                desc = "所有开了这个选项的助手共用同一座宫殿，不开则这个助手自己一座。同一座宫殿里，消息页和所有普通窗口是同一个阿辞",
                checked = assistant.useGlobalMemory,
                onChange = { onUpdateAssistant(assistant.copy(useGlobalMemory = it)) },
                enabled = assistant.enableMemory,
            )
            DottedLine(Modifier.fillMaxWidth().padding(horizontal = 16.dp), color = c.dot)
            PalaceSwitchRow(
                title = "自动整理消息页",
                desc = "消息页攒够 100 条新消息后自动整理成记忆，最近 200 条留在上下文里不动。关掉后只在点「现在整理」时整理。普通窗口不会自动整理",
                checked = assistant.autoConsolidateMemory,
                onChange = { onUpdateAssistant(assistant.copy(autoConsolidateMemory = it)) },
                enabled = assistant.enableMemory,
            )
        }

        PalaceSection("CHAT", "聊天")
        PalaceGlass {
            PalaceSwitchRow(
                title = "参考历史聊天记录",
                desc = "允许助手在过去的对话中搜索相关信息，需要模型支持工具调用",
                checked = assistant.enableRecentChatsReference,
                onChange = { onUpdateAssistant(assistant.copy(enableRecentChatsReference = it)) },
            )
            DottedLine(Modifier.fillMaxWidth().padding(horizontal = 16.dp), color = c.dot)
            PalaceSwitchRow(
                title = "时间提醒",
                desc = "隔一段时间再发消息时，在消息里带上当前时间",
                checked = assistant.enableTimeReminder,
                onChange = { onUpdateAssistant(assistant.copy(enableTimeReminder = it)) },
            )
            if (assistant.enableTimeReminder) {
                DottedLine(Modifier.fillMaxWidth().padding(horizontal = 16.dp), color = c.dot)
                PalaceNavRow(
                    title = "提醒间隔",
                    desc = "当前 ${assistant.timeReminderIntervalMinutes} 分钟",
                    onClick = {
                        intervalInput = assistant.timeReminderIntervalMinutes.toString()
                        showIntervalDialog = true
                    },
                )
            }
        }

        PalaceSection("BACKUP", "备份")
        PalaceGlass {
            PalaceNavRow(title = "旧记忆备份", desc = "旧版文件记忆，已经搬进记忆宫殿，只读备份", onClick = onOpenLegacy)
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
    val c = palaceColors()
    val lastRun = status.lastRun
    PalaceGlass {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("整理消息页", fontSize = 14.sp, color = c.ink)
                Spacer(Modifier.height(2.dp))
                Text(memoryConsolidationSummary(status), fontSize = 11.5.sp, lineHeight = 16.sp, color = c.mute)
            }
            Spacer(Modifier.width(12.dp))
            if (status.running) {
                ContainedLoadingIndicator(modifier = Modifier.size(32.dp))
            } else {
                PalaceTextButton(if (lastRun?.error != null) "重试" else "现在整理", onClick = onOrganize)
            }
        }
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
