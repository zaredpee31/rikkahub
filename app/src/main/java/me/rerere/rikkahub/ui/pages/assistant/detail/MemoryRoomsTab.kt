package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.rikkahub.data.ai.MemoryConsolidationStatus
import me.rerere.rikkahub.data.db.dao.PalaceNodeLite
import me.rerere.rikkahub.data.db.entity.MemoryPlateEntity
import me.rerere.rikkahub.data.db.entity.PalacePlateEntryEntity
import me.rerere.rikkahub.data.palace.PalaceCore
import me.rerere.rikkahub.data.palace.PalaceMood
import me.rerere.rikkahub.data.palace.PalacePlate
import me.rerere.rikkahub.data.palace.PalaceRoom
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.ItemAction
import me.rerere.rikkahub.ui.components.ui.ItemActionMenu
import me.rerere.ui.components.RikkaConfirmDialog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val PAGE = 100

/** 编辑中的记忆；[id] 为 null 是新增 */
internal data class PalaceDraft(
    val id: Long?,
    val room: String,
    val content: String,
    val importance: String,
    val mood: String,
    val tags: String,
    val date: String,
)

private data class CoreDraft(val kind: String, val content: String)
private data class EntryDraft(val plate: String, val id: Long?, val content: String)

private fun dateOf(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toString()

internal fun parseDraftDate(text: String): Long? = runCatching {
    LocalDate.parse(text.trim()).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}.getOrNull()

private fun coreTitle(kind: String) = if (kind == PalaceCore.USER) "关于小葵" else "关于你"

private fun indexStatus(node: PalaceNodeLite, currentKey: String?) = when {
    currentKey == null -> "没配向量"
    node.hasEmbedding && node.embeddingKey == currentKey -> "有向量"
    node.embeddingError.isNotEmpty() -> "向量失败"
    node.hasEmbedding -> "旧模型向量，待重算"
    else -> "待算向量"
}

/** 记忆宫殿：门牌、七个房间的记忆、整理和向量 */
@Composable
internal fun MemoryRoomsTab(
    nodes: List<PalaceNodeLite>,
    core: List<MemoryPlateEntity>,
    entries: List<PalacePlateEntryEntity>,
    currentKey: String?,
    consolidation: MemoryConsolidationStatus?,
    onOrganize: () -> Unit,
    onSaveNode: (PalaceDraft, (Boolean) -> Unit) -> Unit,
    onDeleteNode: (PalaceNodeLite) -> Unit,
    onSaveCore: (kind: String, content: String, done: (Boolean) -> Unit) -> Unit,
    onSaveEntry: (plate: String, id: Long?, content: String, done: (Boolean) -> Unit) -> Unit,
    onDeleteEntry: (PalacePlateEntryEntity) -> Unit,
    onReindex: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var roomFilter by remember { mutableStateOf<String?>(null) }
    var shown by remember { mutableIntStateOf(PAGE) }
    var nodeDraft by remember { mutableStateOf<PalaceDraft?>(null) }
    var coreDraft by remember { mutableStateOf<CoreDraft?>(null) }
    var entryDraft by remember { mutableStateOf<EntryDraft?>(null) }
    var pendingDelete by remember { mutableStateOf<PalaceNodeLite?>(null) }
    var pendingEntryDelete by remember { mutableStateOf<PalacePlateEntryEntity?>(null) }

    val q = query.trim()
    val filtered = nodes.filter { n ->
        (roomFilter == null || n.room == roomFilter) &&
            (q.isEmpty() || n.content.contains(q, ignoreCase = true) || n.tags.contains(q, ignoreCase = true))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        consolidation?.let { status ->
            CardGroup(title = { Text("整理") }) {
                item(
                    onClick = { if (!status.running) onOrganize() },
                    headlineContent = { Text(if (status.running) "正在整理…" else "现在整理消息页") },
                    supportingContent = {
                        val last = status.lastRun
                        Text(
                            text = when {
                                last?.error != null -> "上次失败：${last.error}"
                                status.pendingTurns > 0 -> "缓冲里有 ${status.pendingTurns} 条消息待整理。平时攒够 100 条自动整理，最近 200 条留在上下文里不动"
                                else -> "平时攒够 100 条自动整理，最近 200 条留在上下文里不动"
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                )
            }
        }

        CardGroup(title = { Text("核心背景（所有窗口常驻，自动整理不会动）") }) {
            listOf(PalaceCore.USER, PalaceCore.SELF).forEach { kind ->
                val content = core.firstOrNull { it.kind == kind }?.content.orEmpty()
                item(
                    onClick = { coreDraft = CoreDraft(kind, content) },
                    headlineContent = { Text(coreTitle(kind)) },
                    supportingContent = {
                        Text(text = content.ifBlank { "（空，点一下编辑）" }, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    },
                )
            }
        }

        PalacePlate.all.forEach { plate ->
            val items = entries.filter { it.plate == plate }
            CardGroup(title = { Text("门牌 · ${PalacePlate.label(plate)}（${items.size}/${PalacePlate.capacity(plate)}）") }) {
                items.forEach { e ->
                    item(
                        onClick = { entryDraft = EntryDraft(plate, e.id, e.content) },
                        trailingContent = {
                            ItemActionMenu(
                                actions = listOf(
                                    ItemAction(text = "删除", icon = HugeIcons.Delete01, destructive = true, onClick = { pendingEntryDelete = e }),
                                )
                            )
                        },
                        headlineContent = { Text(e.content, maxLines = 3, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text(
                                "${dateOf(e.firstLearnedAt)} 起 · 印证 ${e.sourceCount} 次",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        },
                    )
                }
                item(
                    onClick = { entryDraft = EntryDraft(plate, null, "") },
                    headlineContent = { Text("添加一条") },
                )
            }
        }

        TextField(
            value = query,
            onValueChange = { query = it; shown = PAGE },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("搜索记忆") },
        )

        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = roomFilter == null, onClick = { roomFilter = null; shown = PAGE }, label = { Text("全部 ${nodes.size}") })
            PalaceRoom.all.forEach { room ->
                FilterChip(
                    selected = roomFilter == room,
                    onClick = { roomFilter = room; shown = PAGE },
                    label = { Text("${PalaceRoom.label(room)} ${nodes.count { it.room == room }}") },
                )
            }
        }

        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = {
                nodeDraft = PalaceDraft(null, roomFilter ?: PalaceRoom.LIVING, "", "5", "neutral", "", LocalDate.now().toString())
            }) { Text("新增") }
            FilledTonalButton(onClick = onReindex) { Text("补算向量") }
        }

        if (filtered.isEmpty()) {
            Text(
                text = if (nodes.isEmpty()) "宫殿里还没有记忆" else "没有符合的记忆",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        } else {
            CardGroup(title = { Text(roomFilter?.let { PalaceRoom.label(it) } ?: "全部房间") }) {
                filtered.take(shown).forEach { n ->
                    item(
                        onClick = {
                            nodeDraft = PalaceDraft(
                                n.id, n.room, n.content, n.importance.toString(), n.mood,
                                n.tags.split('\n').filter { it.isNotBlank() }.joinToString("，"), dateOf(n.createdAt),
                            )
                        },
                        trailingContent = {
                            ItemActionMenu(
                                actions = listOf(
                                    ItemAction(text = "删除", icon = HugeIcons.Delete01, destructive = true, onClick = { pendingDelete = n }),
                                )
                            )
                        },
                        headlineContent = { Text(n.content, maxLines = 4, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text(
                                text = "${PalaceRoom.label(n.room)} · ${dateOf(n.createdAt)} · 重要性 ${n.importance} · ${n.mood}" +
                                    (if (n.accessCount > 0) " · 想起 ${n.accessCount} 次" else "") +
                                    " · ${indexStatus(n, currentKey)}" +
                                    (if (n.embeddingError.isNotEmpty()) "\n${n.embeddingError}" else ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        },
                    )
                }
            }
            if (filtered.size > shown) {
                TextButton(onClick = { shown += PAGE }) { Text("再显示 ${minOf(PAGE, filtered.size - shown)} 条（共 ${filtered.size} 条）") }
            }
        }
    }

    nodeDraft?.let { draft ->
        AlertDialog(
            onDismissRequest = { nodeDraft = null },
            title = { Text(if (draft.id == null) "新增记忆" else "编辑记忆") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PalaceRoom.all.forEach { room ->
                            FilterChip(selected = draft.room == room, onClick = { nodeDraft = draft.copy(room = room) }, label = { Text(PalaceRoom.label(room)) })
                        }
                    }
                    TextField(
                        value = draft.content,
                        onValueChange = { nodeDraft = draft.copy(content = it) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        placeholder = { Text("写一件事") },
                    )
                    TextField(
                        value = draft.date,
                        onValueChange = { nodeDraft = draft.copy(date = it) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("日期 YYYY-MM-DD") },
                        isError = parseDraftDate(draft.date) == null,
                    )
                    TextField(
                        value = draft.importance,
                        onValueChange = { nodeDraft = draft.copy(importance = it.filter(Char::isDigit).take(2)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("重要性 1-10") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PalaceMood.extractionMoods.forEach { mood ->
                            FilterChip(selected = draft.mood == mood, onClick = { nodeDraft = draft.copy(mood = mood) }, label = { Text(mood) })
                        }
                    }
                    TextField(
                        value = draft.tags,
                        onValueChange = { nodeDraft = draft.copy(tags = it) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("标签，用逗号隔开") },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = draft.content.isNotBlank() && parseDraftDate(draft.date) != null,
                    onClick = {
                        if (!saving) {
                            saving = true
                            onSaveNode(draft) { ok -> saving = false; if (ok) nodeDraft = null }
                        }
                    },
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { nodeDraft = null }) { Text("取消") } },
        )
    }

    coreDraft?.let { draft ->
        AlertDialog(
            onDismissRequest = { coreDraft = null },
            title = { Text(coreTitle(draft.kind)) },
            text = {
                TextField(
                    value = draft.content,
                    onValueChange = { coreDraft = draft.copy(content = it) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 8,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (!saving) {
                        saving = true
                        onSaveCore(draft.kind, draft.content) { ok -> saving = false; if (ok) coreDraft = null }
                    }
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { coreDraft = null }) { Text("取消") } },
        )
    }

    entryDraft?.let { draft ->
        AlertDialog(
            onDismissRequest = { entryDraft = null },
            title = { Text(PalacePlate.label(draft.plate)) },
            text = {
                TextField(
                    value = draft.content,
                    onValueChange = { entryDraft = draft.copy(content = it) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            },
            confirmButton = {
                TextButton(enabled = draft.content.isNotBlank(), onClick = {
                    if (!saving) {
                        saving = true
                        onSaveEntry(draft.plate, draft.id, draft.content) { ok -> saving = false; if (ok) entryDraft = null }
                    }
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { entryDraft = null }) { Text("取消") } },
        )
    }

    RikkaConfirmDialog(
        show = pendingDelete != null,
        title = "删除这条记忆？",
        confirmText = "删除",
        dismissText = "取消",
        onConfirm = { pendingDelete?.let(onDeleteNode); pendingDelete = null },
        onDismiss = { pendingDelete = null },
        text = { Text(text = pendingDelete?.content.orEmpty(), maxLines = 8, overflow = TextOverflow.Ellipsis) },
    )

    RikkaConfirmDialog(
        show = pendingEntryDelete != null,
        title = "删除这条门牌？",
        confirmText = "删除",
        dismissText = "取消",
        onConfirm = { pendingEntryDelete?.let(onDeleteEntry); pendingEntryDelete = null },
        onDismiss = { pendingEntryDelete = null },
        text = { Text(text = pendingEntryDelete?.content.orEmpty(), maxLines = 8, overflow = TextOverflow.Ellipsis) },
    )
}
