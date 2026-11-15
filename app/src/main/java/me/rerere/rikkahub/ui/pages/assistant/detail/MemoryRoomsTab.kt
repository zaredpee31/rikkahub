package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Link01
import me.rerere.hugeicons.stroke.Package
import me.rerere.rikkahub.companion.DottedLine
import me.rerere.rikkahub.data.db.dao.PalaceNodeLite
import me.rerere.rikkahub.data.db.entity.MemoryPlateEntity
import me.rerere.rikkahub.data.db.entity.PalacePlateEntryEntity
import me.rerere.rikkahub.data.palace.PalaceCore
import me.rerere.rikkahub.data.palace.PalaceMood
import me.rerere.rikkahub.data.palace.PalaceDigestion
import me.rerere.rikkahub.data.palace.PalacePlate
import me.rerere.rikkahub.data.palace.PalaceRoom
import me.rerere.rikkahub.data.palace.PalaceSource
import me.rerere.rikkahub.ui.components.ui.ItemAction
import me.rerere.rikkahub.ui.components.ui.ItemActionMenu
import me.rerere.ui.components.RikkaConfirmDialog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal const val PALACE_PAGE = 100

/** 编辑中的记忆；[id] 为 null 是新增 */
data class PalaceDraft(
    val id: Long?,
    val room: String,
    val content: String,
    val importance: String,
    val mood: String,
    val tags: String,
    val date: String,
)

private data class EntryDraft(val plate: String, val id: Long?, val content: String, val tag: String)

internal fun palaceDateOf(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toString()

/** 列表里的日期：第一次发生和最近进展同一天只显示一个，不同天显示「起 … 最近 …」 */
internal fun palaceSpanOf(first: Long, last: Long): String {
    val a = palaceDateOf(first)
    val b = palaceDateOf(last)
    return if (b > a) "$a 起，最近 $b" else a
}

internal fun parseDraftDate(text: String): Long? = runCatching {
    LocalDate.parse(text.trim()).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}.getOrNull()

internal fun coreTitle(kind: String) = if (kind == PalaceCore.USER) "关于小葵" else "关于你"

private fun indexStatus(node: PalaceNodeLite, currentKey: String?) = when {
    currentKey == null -> "没配向量"
    node.hasEmbedding && node.embeddingKey == currentKey -> "有向量"
    node.embeddingError.isNotEmpty() -> "向量失败"
    node.hasEmbedding -> "旧模型向量，待重算"
    else -> "待算向量"
}

internal fun newDraft(room: String) = PalaceDraft(null, room, "", "5", "neutral", "", LocalDate.now().toString())

private fun draftOf(n: PalaceNodeLite) = PalaceDraft(
    n.id, n.room, n.content, n.importance.toString(), n.mood,
    n.tags.split('\n').filter { it.isNotBlank() }.joinToString("，"), palaceDateOf(n.createdAt),
)

/** 记忆的增删改、绑成一件事、移出事件盒，这几页共用 */
internal class PalaceNodeActions(
    val allNodes: List<PalaceNodeLite>,
    val currentKey: String?,
    val onSaveNode: (PalaceDraft, (Boolean) -> Unit) -> Unit,
    val onDeleteNode: (PalaceNodeLite) -> Unit,
    val onBind: (a: PalaceNodeLite, b: PalaceNodeLite) -> Unit,
    val onUnbox: (PalaceNodeLite) -> Unit,
)

/**
 * 一串记忆卡片，带分页。点一下编辑，菜单里删除、绑成一件事、移出事件盒。
 * [editRequest] 不为 null 时直接打开这份草稿（比如「新增」按钮）。
 */
@Composable
internal fun PalaceNodeList(
    nodes: List<PalaceNodeLite>,
    actions: PalaceNodeActions,
    showRoom: Boolean,
    editRequest: PalaceDraft? = null,
    onEditRequestHandled: () -> Unit = {},
) {
    val c = palaceColors()
    var shown by remember(nodes.size) { mutableIntStateOf(PALACE_PAGE) }
    var nodeDraft by remember { mutableStateOf<PalaceDraft?>(null) }
    var saving by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<PalaceNodeLite?>(null) }
    var bindFrom by remember { mutableStateOf<PalaceNodeLite?>(null) }
    var bindQuery by remember { mutableStateOf("") }
    var pendingUnbox by remember { mutableStateOf<PalaceNodeLite?>(null) }

    androidx.compose.runtime.LaunchedEffect(editRequest) {
        if (editRequest != null) {
            nodeDraft = editRequest
            onEditRequestHandled()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        nodes.take(shown).forEach { n ->
            PalaceGlass(tint = if (n.archived) c.chip else null) {
                Row(
                    Modifier.fillMaxWidth().clickable { nodeDraft = draftOf(n) }.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            n.content, fontSize = 13.5.sp, lineHeight = 20.sp, maxLines = 4, overflow = TextOverflow.Ellipsis,
                            color = if (n.archived) c.mute else c.ink,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = listOfNotNull(
                                PalaceRoom.label(n.room).takeIf { showRoom },
                                palaceSpanOf(n.createdAt, n.lastEventAt),
                                "重要性 ${n.importance}",
                                n.mood,
                                "想起 ${n.accessCount} 次".takeIf { n.accessCount > 0 },
                                indexStatus(n, actions.currentKey),
                                when (n.antStatus) {
                                    PalaceDigestion.ANT_ANCHOR -> "盼了很久的锚点"
                                    PalaceDigestion.ANT_FULFILLED -> "实现了"
                                    PalaceDigestion.ANT_DISAPPOINTED -> "落空了"
                                    else -> null
                                },
                                when {
                                    n.source == PalaceSource.BOX -> "事件盒总结"
                                    n.archived -> "已合入事件盒总结"
                                    n.boxId > 0 -> "在事件盒里"
                                    else -> null
                                },
                            ).joinToString(" · ") + (if (n.embeddingError.isNotEmpty()) "\n${n.embeddingError}" else ""),
                            fontSize = 10.5.sp, lineHeight = 15.sp, color = c.mute,
                        )
                    }
                    ItemActionMenu(
                        actions = buildList {
                            if (n.source != PalaceSource.BOX) add(ItemAction(text = "和另一条绑成一件事", icon = HugeIcons.Link01) { bindFrom = n; bindQuery = "" })
                            if (n.boxId > 0) add(ItemAction(text = "移出事件盒", icon = HugeIcons.Package) { pendingUnbox = n })
                            add(ItemAction(text = "删除", icon = HugeIcons.Delete01, destructive = true) { pendingDelete = n })
                        }
                    )
                }
            }
        }
        if (nodes.size > shown) {
            PalaceTextButton("再显示 ${minOf(PALACE_PAGE, nodes.size - shown)} 条（共 ${nodes.size} 条）", onClick = { shown += PALACE_PAGE })
        }
    }
    nodeDraft?.let { draft ->
        PalaceDialog(
            title = if (draft.id == null) "新增记忆" else "编辑记忆",
            onDismiss = { nodeDraft = null },
            confirmEnabled = draft.content.isNotBlank() && parseDraftDate(draft.date) != null,
            onConfirm = {
                if (!saving) {
                    saving = true
                    actions.onSaveNode(draft) { ok -> saving = false; if (ok) nodeDraft = null }
                }
            },
        ) {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PalaceRoom.all.forEach { room ->
                    PalaceChip(PalaceRoom.label(room), draft.room == room) { nodeDraft = draft.copy(room = room) }
                }
            }
            PalaceField(draft.content, { nodeDraft = draft.copy(content = it) }, minLines = 4, placeholder = "写一件事")
            PalaceField(draft.date, { nodeDraft = draft.copy(date = it) }, label = "日期 YYYY-MM-DD", singleLine = true, isError = parseDraftDate(draft.date) == null)
            PalaceField(draft.importance, { nodeDraft = draft.copy(importance = it.filter(Char::isDigit).take(2)) }, label = "重要性 1-10", singleLine = true, number = true)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PalaceMood.extractionMoods.forEach { mood ->
                    PalaceChip(mood, draft.mood == mood) { nodeDraft = draft.copy(mood = mood) }
                }
            }
            PalaceField(draft.tags, { nodeDraft = draft.copy(tags = it) }, label = "标签，用逗号隔开", singleLine = true)
        }
    }

    RikkaConfirmDialog(
        show = pendingDelete != null,
        title = "删除这条记忆？",
        confirmText = "删除",
        dismissText = "取消",
        onConfirm = { pendingDelete?.let(actions.onDeleteNode); pendingDelete = null },
        onDismiss = { pendingDelete = null },
        text = { Text(text = pendingDelete?.content.orEmpty(), maxLines = 8, overflow = TextOverflow.Ellipsis) },
    )

    bindFrom?.let { from ->
        val bq = bindQuery.trim()
        val candidates = actions.allNodes.filter { c ->
            c.id != from.id && c.source != PalaceSource.BOX &&
                (bq.isEmpty() || c.content.contains(bq, ignoreCase = true) || c.tags.contains(bq, ignoreCase = true))
        }
        val c = palaceColors()
        PalaceDialog(title = "和哪一条是同一件事？", onDismiss = { bindFrom = null }, confirmText = null) {
            Text(from.content, maxLines = 3, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = c.mute)
            PalaceField(bindQuery, { bindQuery = it }, singleLine = true, placeholder = "搜索另一条")
            if (candidates.isEmpty()) Text("没有符合的记忆", fontSize = 12.5.sp, color = c.mute)
            candidates.take(PALACE_PAGE).forEach { cand ->
                Text(
                    "${PalaceRoom.label(cand.room)} · ${palaceSpanOf(cand.createdAt, cand.lastEventAt)}" + (if (cand.boxId > 0) " · 在事件盒里" else "") + "\n" + cand.content,
                    fontSize = 12.5.sp, lineHeight = 18.sp, color = c.ink,
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.glassStrong)
                        .clickable { actions.onBind(from, cand); bindFrom = null }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            Text(
                "已经封盒、或者已经在别的事件盒里的，按自动装盒的规则处理：能合就合，封了的另开新盒",
                fontSize = 10.5.sp, color = c.mute,
            )
        }
    }

    RikkaConfirmDialog(
        show = pendingUnbox != null,
        title = "把这条移出事件盒？",
        confirmText = "移出",
        dismissText = "取消",
        onConfirm = { pendingUnbox?.let(actions.onUnbox); pendingUnbox = null },
        onDismiss = { pendingUnbox = null },
        text = { Text(text = pendingUnbox?.content.orEmpty() + "\n\n不会删除，会变回一条普通记忆。", maxLines = 10, overflow = TextOverflow.Ellipsis) },
    )
}

/** 一块门牌：条目列表，点一下编辑，菜单删除；空的时候写清楚什么时候会有 */
@Composable
internal fun PalacePlateCard(
    plate: String,
    entries: List<PalacePlateEntryEntity>,
    onSaveEntry: (plate: String, id: Long?, content: String, tag: String, done: (Boolean) -> Unit) -> Unit,
    onDeleteEntry: (PalacePlateEntryEntity) -> Unit,
) {
    val c = palaceColors()
    val items = entries.filter { it.plate == plate }
    var entryDraft by remember { mutableStateOf<EntryDraft?>(null) }
    var pendingDelete by remember { mutableStateOf<PalacePlateEntryEntity?>(null) }
    var saving by remember { mutableStateOf(false) }

    PalaceSection("PLATE", "门牌 · ${PalacePlate.label(plate)}  ${items.size}/${PalacePlate.capacity(plate)}")
    PalaceGlass {
        if (items.isEmpty()) {
            Text(
                "还没有内容，消化或者回填门牌以后会出现",
                fontSize = 12.5.sp, color = c.mute,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }
        items.forEachIndexed { i, e ->
            if (i > 0) DottedLine(Modifier.fillMaxWidth().padding(horizontal = 16.dp), color = c.dot)
            Row(
                Modifier.fillMaxWidth().clickable { entryDraft = EntryDraft(plate, e.id, e.content, e.tag) }.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(e.content, fontSize = 13.5.sp, lineHeight = 19.sp, color = c.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(e.tag.takeIf { it.isNotEmpty() }, "${palaceDateOf(e.firstLearnedAt)} 起", "印证 ${e.sourceCount} 次").joinToString(" · "),
                        fontSize = 10.5.sp, color = c.mute)
                }
                ItemActionMenu(actions = listOf(ItemAction(text = "删除", icon = HugeIcons.Delete01, destructive = true) { pendingDelete = e }))
            }
        }
        if (items.size < PalacePlate.capacity(plate)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)) {
                PalaceTextButton("手动加一条", onClick = { entryDraft = EntryDraft(plate, null, "", "") })
            }
        }
    }

    entryDraft?.let { draft ->
        PalaceDialog(
            title = PalacePlate.label(draft.plate),
            onDismiss = { entryDraft = null },
            confirmEnabled = draft.content.isNotBlank(),
            onConfirm = {
                if (!saving) {
                    saving = true
                    onSaveEntry(draft.plate, draft.id, draft.content, draft.tag) { ok -> saving = false; if (ok) entryDraft = null }
                }
            },
        ) {
            PalaceField(draft.content, { entryDraft = draft.copy(content = it) }, minLines = 3)
            Spacer(Modifier.height(8.dp))
            PalaceField(
                draft.tag, { entryDraft = draft.copy(tag = it.take(PalaceDigestion.TAG_MAX)) },
                label = "分类标签（可不填）", placeholder = "比如 家庭、默契", singleLine = true,
            )
        }
    }

    RikkaConfirmDialog(
        show = pendingDelete != null,
        title = "删除这条门牌？",
        confirmText = "删除",
        dismissText = "取消",
        onConfirm = { pendingDelete?.let(onDeleteEntry); pendingDelete = null },
        onDismiss = { pendingDelete = null },
        text = { Text(text = pendingDelete?.content.orEmpty(), maxLines = 8, overflow = TextOverflow.Ellipsis) },
    )
}

/** 一块核心背景：整段长文本，点一下编辑。自动整理不会动它 */
@Composable
internal fun PalaceCoreCard(
    kind: String,
    core: List<MemoryPlateEntity>,
    onSaveCore: (kind: String, content: String, done: (Boolean) -> Unit) -> Unit,
) {
    val c = palaceColors()
    val content = core.firstOrNull { it.kind == kind }?.content.orEmpty()
    var draft by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    PalaceSection("CORE", "${coreTitle(kind)} · 所有窗口常驻，自动整理不会动")
    PalaceGlass {
        Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                content.ifBlank { "还没有内容。这里只有你手动写，或者明确让阿辞改的时候才会变" },
                fontSize = 13.sp, lineHeight = 20.sp,
                color = if (content.isBlank()) c.mute else c.ink,
                maxLines = if (expanded) Int.MAX_VALUE else 6,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)) {
            if (content.isNotBlank()) PalaceTextButton(if (expanded) "收起" else "展开全文", onClick = { expanded = !expanded })
            PalaceTextButton("编辑", onClick = { draft = content })
        }
    }

    draft?.let { text ->
        PalaceDialog(
            title = coreTitle(kind),
            onDismiss = { draft = null },
            onConfirm = {
                if (!saving) {
                    saving = true
                    onSaveCore(kind, text) { ok -> saving = false; if (ok) draft = null }
                }
            },
        ) {
            PalaceField(text, { draft = it }, minLines = 8)
        }
    }
}
