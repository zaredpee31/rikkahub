package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.rikkahub.data.db.dao.MemoryNodeSummary
import me.rerere.rikkahub.data.db.entity.MemoryPlateEntity
import me.rerere.rikkahub.data.model.MemoryPlateKind
import me.rerere.rikkahub.data.model.MemoryRoom
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.ItemAction
import me.rerere.rikkahub.ui.components.ui.ItemActionMenu
import me.rerere.ui.components.RikkaConfirmDialog
import java.time.Instant
import java.time.ZoneId

// 一次最多画这么多条，再多先用搜索或房间筛
private const val MAX_SHOWN = 300

private data class NodeDraft(
    val node: MemoryNodeSummary?,
    val room: String,
    val content: String,
)

private data class PlateDraft(val kind: String, val content: String)

private fun plateTitle(kind: String) = when (kind) {
    MemoryPlateKind.USER -> "用户门牌"
    MemoryPlateKind.SELF -> "自我门牌"
    else -> kind
}

private fun indexStatus(node: MemoryNodeSummary, currentKey: String?) = when {
    currentKey == null -> "没配向量模型"
    node.embeddingDims > 0 && node.embeddingKey == currentKey -> "有向量"
    node.embeddingError.isNotEmpty() -> "向量失败"
    node.embeddingDims > 0 -> "旧模型的向量，待重算"
    else -> "待算向量"
}

private fun dateOf(millis: Long) =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toString()

/** 记忆房间：门牌和节点的查看、搜索、增删改、粘贴导入 */
@Composable
internal fun MemoryRoomsTab(
    nodes: List<MemoryNodeSummary>,
    plates: List<MemoryPlateEntity>,
    currentKey: String?,
    // 最后一个参数是完成回调：true 才关对话框，失败时草稿留着，错误另有弹窗
    onSaveNode: (node: MemoryNodeSummary?, room: String, content: String, done: (Boolean) -> Unit) -> Unit,
    onDeleteNode: (MemoryNodeSummary) -> Unit,
    onSavePlate: (kind: String, content: String, done: (Boolean) -> Unit) -> Unit,
    onImport: (room: String, text: String, done: (Boolean) -> Unit) -> Unit,
    onReindex: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    // 正在保存时不响应再次点击，免得同一条存两遍
    var saving by remember { mutableStateOf(false) }
    var roomFilter by remember { mutableStateOf<String?>(null) }
    var nodeDraft by remember { mutableStateOf<NodeDraft?>(null) }
    var plateDraft by remember { mutableStateOf<PlateDraft?>(null) }
    var importDraft by remember { mutableStateOf<NodeDraft?>(null) }
    var pendingDelete by remember { mutableStateOf<MemoryNodeSummary?>(null) }

    val q = query.trim()
    val filtered = nodes.filter { node ->
        (roomFilter == null || node.room == roomFilter) &&
            (q.isEmpty() || node.content.contains(q, ignoreCase = true) || node.keywords.contains(q, ignoreCase = true))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CardGroup(title = { Text("门牌") }) {
            MemoryPlateKind.all.forEach { kind ->
                val content = plates.firstOrNull { it.kind == kind }?.content.orEmpty()
                item(
                    onClick = { plateDraft = PlateDraft(kind, content) },
                    headlineContent = { Text(plateTitle(kind)) },
                    supportingContent = {
                        Text(
                            text = content.ifBlank { "（空，点一下编辑）" },
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }

        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("搜索记忆") },
        )

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = roomFilter == null,
                onClick = { roomFilter = null },
                label = { Text("全部 ${nodes.size}") },
            )
            MemoryRoom.all.forEach { room ->
                FilterChip(
                    selected = roomFilter == room,
                    onClick = { roomFilter = room },
                    label = { Text("${MemoryRoom.label(room)} ${nodes.count { it.room == room }}") },
                )
            }
        }

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(onClick = {
                nodeDraft = NodeDraft(null, roomFilter ?: MemoryRoom.LIVING, "")
            }) { Text("新增") }
            FilledTonalButton(onClick = {
                importDraft = NodeDraft(null, roomFilter ?: MemoryRoom.LIVING, "")
            }) { Text("粘贴导入") }
            FilledTonalButton(onClick = onReindex) { Text("补算向量") }
        }

        if (filtered.isEmpty()) {
            Text(
                text = if (nodes.isEmpty()) "还没有记忆节点" else "没有符合的记忆",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        } else {
            CardGroup(
                title = { Text(roomFilter?.let { MemoryRoom.label(it) } ?: "全部房间") },
            ) {
                filtered.take(MAX_SHOWN).forEach { node ->
                    item(
                        onClick = { nodeDraft = NodeDraft(node, node.room, node.content) },
                        trailingContent = {
                            ItemActionMenu(
                                actions = listOf(
                                    ItemAction(
                                        text = "删除",
                                        icon = HugeIcons.Delete01,
                                        destructive = true,
                                        onClick = { pendingDelete = node },
                                    ),
                                )
                            )
                        },
                        headlineContent = {
                            Text(text = node.content, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = {
                            val date = if (node.happenedAt > 0) " · " + dateOf(node.happenedAt) else ""
                            Text(
                                text = "${MemoryRoom.label(node.room)}$date · ${indexStatus(node, currentKey)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        },
                    )
                }
            }
            if (filtered.size > MAX_SHOWN) {
                Text(
                    text = "只显示前 $MAX_SHOWN 条，共 ${filtered.size} 条，用搜索缩小范围",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }

    nodeDraft?.let { draft ->
        NodeDialog(
            title = if (draft.node == null) "新增记忆" else "编辑记忆",
            draft = draft,
            contentHint = "写一件事",
            onChange = { nodeDraft = it },
            onDismiss = { nodeDraft = null },
            onConfirm = {
                if (!saving) {
                    saving = true
                    onSaveNode(draft.node, draft.room, draft.content) { ok ->
                        saving = false
                        if (ok) nodeDraft = null
                    }
                }
            },
        )
    }

    importDraft?.let { draft ->
        NodeDialog(
            title = "粘贴导入",
            draft = draft,
            contentHint = "粘贴旧记忆。每个列表项、每段空行隔开的文字各成一条",
            onChange = { importDraft = it },
            onDismiss = { importDraft = null },
            onConfirm = {
                if (!saving) {
                    saving = true
                    onImport(draft.room, draft.content) { ok ->
                        saving = false
                        if (ok) importDraft = null
                    }
                }
            },
        )
    }

    plateDraft?.let { draft ->
        AlertDialog(
            onDismissRequest = { plateDraft = null },
            title = { Text(plateTitle(draft.kind)) },
            text = {
                TextField(
                    value = draft.content,
                    onValueChange = { plateDraft = draft.copy(content = it) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 6,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (!saving) {
                        saving = true
                        onSavePlate(draft.kind, draft.content) { ok ->
                            saving = false
                            if (ok) plateDraft = null
                        }
                    }
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { plateDraft = null }) { Text("取消") }
            },
        )
    }

    RikkaConfirmDialog(
        show = pendingDelete != null,
        title = "删除这条记忆？",
        confirmText = "删除",
        dismissText = "取消",
        onConfirm = {
            pendingDelete?.let(onDeleteNode)
            pendingDelete = null
        },
        onDismiss = { pendingDelete = null },
        text = {
            Text(text = pendingDelete?.content.orEmpty(), maxLines = 8, overflow = TextOverflow.Ellipsis)
        },
    )
}

@Composable
private fun NodeDialog(
    title: String,
    draft: NodeDraft,
    contentHint: String,
    onChange: (NodeDraft) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MemoryRoom.all.forEach { room ->
                        FilterChip(
                            selected = draft.room == room,
                            onClick = { onChange(draft.copy(room = room)) },
                            label = { Text(MemoryRoom.label(room)) },
                        )
                    }
                }
                TextField(
                    value = draft.content,
                    onValueChange = { onChange(draft.copy(content = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    placeholder = { Text(contentHint) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = draft.content.isNotBlank()) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
