package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import me.rerere.hugeicons.stroke.Edit02
import me.rerere.hugeicons.stroke.Package
import me.rerere.rikkahub.data.db.dao.PalaceNodeLite
import me.rerere.rikkahub.data.db.entity.PalaceBoxEntity
import me.rerere.rikkahub.data.palace.PalaceBoxes
import me.rerere.rikkahub.data.palace.PalaceRoom
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.ItemAction
import me.rerere.rikkahub.ui.components.ui.ItemActionMenu
import me.rerere.ui.components.RikkaConfirmDialog
import java.time.Instant
import java.time.ZoneId

private data class BoxMetaDraft(val box: PalaceBoxEntity, val name: String, val tags: String)

private fun boxDate(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toString()

private fun boxTagsOf(box: PalaceBoxEntity) = box.tags.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

/** 事件盒：同一件事的记忆绑在一起。看总结、近期片段、已合入总结的旧片段，改名、复活、移出、重新整合 */
@Composable
internal fun MemoryBoxesTab(
    boxes: List<PalaceBoxEntity>,
    nodes: List<PalaceNodeLite>,
    regenerating: Long?,
    onSaveMeta: (box: PalaceBoxEntity, name: String, tags: String, done: (Boolean) -> Unit) -> Unit,
    onRevive: (PalaceNodeLite) -> Unit,
    onRemove: (PalaceNodeLite) -> Unit,
    onUnbindAllLive: (PalaceBoxEntity) -> Unit,
    onRegenerate: (PalaceBoxEntity) -> Unit,
) {
    var expanded by remember { mutableStateOf<Long?>(null) }
    var metaDraft by remember { mutableStateOf<BoxMetaDraft?>(null) }
    var saving by remember { mutableStateOf(false) }
    var pendingRegen by remember { mutableStateOf<PalaceBoxEntity?>(null) }
    var pendingUnbind by remember { mutableStateOf<PalaceBoxEntity?>(null) }
    var pendingRemove by remember { mutableStateOf<PalaceNodeLite?>(null) }

    val byBox = nodes.filter { it.boxId > 0 }.groupBy { it.boxId }
    fun sourcesOf(box: PalaceBoxEntity) = PalaceBoxes.regenSources(byBox[box.id].orEmpty(), box.id, box.summaryNodeId)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "按同一件事自动装在一起的记忆，共 ${boxes.size} 个。点开能看整合回忆、近期片段和已合入总结的旧片段",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )

        if (boxes.isEmpty()) {
            Text(
                text = "还没有事件盒。聊天里出现同一件事，或者在记忆宫殿里手动把两条绑成一件事时会自动建",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        }

        boxes.forEach { box ->
            val members = byBox[box.id].orEmpty()
            val summary = members.firstOrNull { it.id == box.summaryNodeId }
            val live = members.filter { it.id != box.summaryNodeId && !it.archived }.sortedBy { it.createdAt }
            val archived = members.filter { it.id != box.summaryNodeId && it.archived }.sortedBy { it.createdAt }
            val open = expanded == box.id
            val busy = regenerating == box.id

            CardGroup(title = { Text(box.name.ifBlank { "未命名" } + if (box.isSealed) " · 已封盒" else "") }) {
                item(
                    onClick = { expanded = if (open) null else box.id },
                    headlineContent = {
                        Text(
                            text = summary?.content ?: "还没写整合回忆",
                            maxLines = if (open) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = {
                        Text(
                            text = buildString {
                                append("近期片段 ${live.size} · 已合入 ${archived.size} · 整合过 ${box.compressionCount} 次 · ${boxDate(box.updatedAt)} 更新")
                                if (busy) append(" · 正在重新整合…")
                                val tags = boxTagsOf(box)
                                if (tags.isNotEmpty()) append("\n" + tags.joinToString("、"))
                                if (!open) append("\n点一下展开")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    },
                    trailingContent = {
                        ItemActionMenu(
                            actions = buildList {
                                add(ItemAction(text = "改名字和关键词", icon = HugeIcons.Edit02) {
                                    metaDraft = BoxMetaDraft(box, box.name, boxTagsOf(box).joinToString("，"))
                                })
                                add(ItemAction(text = "重新整合", icon = HugeIcons.Package, enabled = regenerating == null) { pendingRegen = box })
                                if (live.isNotEmpty()) {
                                    add(ItemAction(text = "移出全部近期片段", icon = HugeIcons.Delete01, destructive = true) { pendingUnbind = box })
                                }
                            }
                        )
                    },
                )
                if (open) {
                    summary?.let { s ->
                        item(
                            overlineContent = { Text("整合回忆") },
                            headlineContent = { Text(s.content) },
                            supportingContent = { Text(memberLine(s), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline) },
                            trailingContent = {
                                ItemActionMenu(actions = listOf(ItemAction(text = "移出盒子", icon = HugeIcons.Delete01, destructive = true) { pendingRemove = s }))
                            },
                        )
                    }
                    live.forEach { n ->
                        item(
                            overlineContent = { Text("近期片段") },
                            headlineContent = { Text(n.content, maxLines = 6, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(memberLine(n), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline) },
                            trailingContent = {
                                ItemActionMenu(actions = listOf(ItemAction(text = "移出盒子", icon = HugeIcons.Delete01, destructive = true) { pendingRemove = n }))
                            },
                        )
                    }
                    archived.forEach { n ->
                        item(
                            overlineContent = { Text("已合入总结") },
                            headlineContent = { Text(n.content, maxLines = 6, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.outline) },
                            supportingContent = { Text(memberLine(n), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline) },
                            trailingContent = {
                                ItemActionMenu(
                                    actions = listOf(
                                        ItemAction(text = "复活", icon = HugeIcons.Package) { onRevive(n) },
                                        ItemAction(text = "移出盒子", icon = HugeIcons.Delete01, destructive = true) { pendingRemove = n },
                                    )
                                )
                            },
                        )
                    }
                    if (summary == null && live.isEmpty() && archived.isEmpty()) {
                        item(headlineContent = { Text("盒子里没有记忆", color = MaterialTheme.colorScheme.outline) })
                    }
                }
            }
        }
    }

    metaDraft?.let { draft ->
        AlertDialog(
            onDismissRequest = { metaDraft = null },
            title = { Text("事件盒名字和关键词") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        value = draft.name,
                        onValueChange = { metaDraft = draft.copy(name = it) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("名字，留空就是「${PalaceBoxes.UNNAMED}」") },
                    )
                    TextField(
                        value = draft.tags,
                        onValueChange = { metaDraft = draft.copy(tags = it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("关键词，用逗号隔开，最多 ${PalaceBoxes.MAX_TAGS} 个") },
                    )
                    Text(
                        "只影响显示的抬头，不影响召回",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (!saving) {
                        saving = true
                        onSaveMeta(draft.box, draft.name, draft.tags) { ok -> saving = false; if (ok) metaDraft = null }
                    }
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { metaDraft = null }) { Text("取消") } },
        )
    }

    RikkaConfirmDialog(
        show = pendingRegen != null,
        title = "重新整合「${pendingRegen?.name?.ifBlank { "未命名事件" }.orEmpty()}」？",
        confirmText = "重新整合",
        dismissText = "取消",
        onConfirm = { pendingRegen?.let(onRegenerate); pendingRegen = null },
        onDismiss = { pendingRegen = null },
        text = {
            val count = pendingRegen?.let { sourcesOf(it).size } ?: 0
            Text(
                "会重新读盒子里全部 $count 条原始记忆，不参考现在的整合回忆，写一段新的。" +
                    "成功后这些近期片段合入总结（原文保留），累计合入满 ${PalaceBoxes.SEAL_AT} 条会封盒；失败就保持原样。"
            )
        },
    )

    RikkaConfirmDialog(
        show = pendingUnbind != null,
        title = "把「${pendingUnbind?.name?.ifBlank { "未命名" }.orEmpty()}」里的近期片段全部移出？",
        confirmText = "移出",
        dismissText = "取消",
        onConfirm = { pendingUnbind?.let(onUnbindAllLive); pendingUnbind = null },
        onDismiss = { pendingUnbind = null },
        text = { Text("这些记忆不会删，只是离开这个事件盒，变回普通记忆。整合回忆和已合入总结的旧片段不动。") },
    )

    RikkaConfirmDialog(
        show = pendingRemove != null,
        title = "把这条移出事件盒？",
        confirmText = "移出",
        dismissText = "取消",
        onConfirm = { pendingRemove?.let(onRemove); pendingRemove = null },
        onDismiss = { pendingRemove = null },
        text = { Text(text = (pendingRemove?.content.orEmpty()) + "\n\n不会删除，会变回一条普通记忆，重新参与召回。", maxLines = 10, overflow = TextOverflow.Ellipsis) },
    )
}

private fun memberLine(n: PalaceNodeLite) =
    "${PalaceRoom.label(n.room)} · ${boxDate(n.createdAt)} · 重要性 ${n.importance} · ${n.mood}"
