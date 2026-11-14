package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.rikkahub.companion.MonoText
import me.rerere.rikkahub.data.db.dao.PalaceNodeLite
import me.rerere.rikkahub.data.db.entity.PalaceBoxEntity
import me.rerere.rikkahub.data.palace.PalaceRoom

/** 关键词、标签、情绪里任一个包含 [q] */
private fun PalaceNodeLite.matches(q: String) =
    content.contains(q, ignoreCase = true) || tags.contains(q, ignoreCase = true) || mood.contains(q, ignoreCase = true)

@Composable
private fun PalaceEmpty(text: String) {
    val c = palaceColors()
    Text(
        text, fontSize = 12.5.sp, lineHeight = 18.sp, color = c.mute, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
    )
}

/** 记忆宫殿主页：统计、两个入口、搜索、七个房间 */
@Composable
internal fun PalaceHomePage(
    nodes: List<PalaceNodeLite>,
    boxes: List<PalaceBoxEntity>,
    embeddingConfigured: Boolean,
    actions: PalaceNodeActions,
    onBack: () -> Unit,
    onOpenRoom: (String) -> Unit,
    onOpenAll: () -> Unit,
    onOpenBoxes: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenEmbedding: () -> Unit,
) {
    val c = palaceColors()
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val counts = remember(nodes) { nodes.groupingBy { it.room }.eachCount() }
    val hopes = counts[PalaceRoom.WINDOWSILL] ?: 0

    PalaceBackdrop {
        Column(Modifier.fillMaxSize()) {
            PalaceTopBar("记忆宫殿", "MEMORY PALACE", onBack = onBack, rightIcon = HugeIcons.Settings03, onRight = onOpenSettings)
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "${nodes.size} 条记忆 · ${boxes.size} 个事件盒 · $hopes 个期盼",
                    fontSize = 12.5.sp, color = c.ink2, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HomeButton("查看全部记忆", Modifier.weight(1f), onOpenAll)
                    HomeButton("查看事件盒", Modifier.weight(1f), onOpenBoxes)
                }
                PalaceSearchField(query, { query = it }, "搜索记忆（关键词、标签、情绪）")
                if (!embeddingConfigured) {
                    PalaceBanner("尚未配置 Embedding 模型，点这里配置", onClick = onOpenEmbedding, warn = true)
                }

                if (q.length >= 2) {
                    val hits = nodes.filter { it.matches(q) }
                    PalaceSection("SEARCH", "搜到 ${hits.size} 条")
                    if (hits.isEmpty()) PalaceEmpty("没有找到包含「$q」的记忆")
                    else PalaceNodeList(hits, actions, showRoom = true)
                } else {
                    if (q.isNotEmpty()) {
                        Text("再多打一个字开始搜", fontSize = 11.5.sp, color = c.mute, modifier = Modifier.padding(start = 6.dp))
                    }
                    PalaceSection("ROOMS", "房间")
                    PalaceRoom.all.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { room ->
                                RoomTile(room, counts[room] ?: 0, Modifier.weight(1f)) { onOpenRoom(room) }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    if (nodes.isEmpty()) {
                        PalaceEmpty("宫殿还是空的。聊得多了，阿辞整理消息时会把记忆放进各个房间")
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    val c = palaceColors()
    PalaceGlass(modifier, shape = RoundedCornerShape(16.dp)) {
        Text(
            text, fontSize = 13.5.sp, color = c.ink, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp),
        )
    }
}

@Composable
private fun RoomTile(room: String, count: Int, modifier: Modifier, onClick: () -> Unit) {
    val c = palaceColors()
    val cap = PalaceRoom.capacity(room)
    PalaceGlass(modifier, tint = c.roomTint(room)) {
        Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp)) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(c.glassStrong), contentAlignment = Alignment.Center) {
                Icon(c.roomIcon(room), null, Modifier.size(16.dp), tint = c.ink2)
            }
            Spacer(Modifier.height(10.dp))
            Text(PalaceRoom.label(room), fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = c.ink)
            Spacer(Modifier.height(2.dp))
            Text(PalaceRoom.description(room), fontSize = 11.sp, lineHeight = 15.sp, color = c.mute, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            MonoText(if (cap != null) "$count / $cap" else "$count 条", size = 10f, color = c.ink2)
        }
    }
}

/** 一个房间：说明、对应的门牌或核心背景、这间的记忆，可以新增 */
@Composable
internal fun PalaceRoomPage(
    room: String,
    nodes: List<PalaceNodeLite>,
    core: List<me.rerere.rikkahub.data.db.entity.MemoryPlateEntity>,
    entries: List<me.rerere.rikkahub.data.db.entity.PalacePlateEntryEntity>,
    actions: PalaceNodeActions,
    onBack: () -> Unit,
    onSaveCore: (kind: String, content: String, done: (Boolean) -> Unit) -> Unit,
    onSaveEntry: (plate: String, id: Long?, content: String, tag: String, done: (Boolean) -> Unit) -> Unit,
    onDeleteEntry: (me.rerere.rikkahub.data.db.entity.PalacePlateEntryEntity) -> Unit,
) {
    val c = palaceColors()
    val mine = remember(nodes, room) { nodes.filter { it.room == room } }
    val cap = PalaceRoom.capacity(room)
    var newRequest by remember { mutableStateOf<PalaceDraft?>(null) }
    val coreKind = when (room) {
        PalaceRoom.USER -> me.rerere.rikkahub.data.palace.PalaceCore.USER
        PalaceRoom.SELF -> me.rerere.rikkahub.data.palace.PalaceCore.SELF
        else -> null
    }
    val plate = me.rerere.rikkahub.data.palace.PalaceDigestion.plateOfRoom(room)

    PalaceSubPage(PalaceRoom.label(room), room.uppercase() + " · " + (if (cap != null) "${mine.size} / $cap" else "${mine.size}"), onBack = onBack) {
        PalaceGlass(tint = c.roomTint(room)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(c.glassStrong), contentAlignment = Alignment.Center) {
                    Icon(c.roomIcon(room), null, Modifier.size(18.dp), tint = c.ink2)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(PalaceRoom.description(room), fontSize = 13.sp, color = c.ink)
                    Text(roomNote(room), fontSize = 11.sp, lineHeight = 15.sp, color = c.mute)
                }
            }
        }
        if (coreKind != null) {
            Spacer(Modifier.height(4.dp))
            PalaceCoreCard(coreKind, core, onSaveCore)
        }
        if (plate != null) {
            Spacer(Modifier.height(4.dp))
            PalacePlateCard(plate, entries, onSaveEntry, onDeleteEntry)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { PalaceSection("NODES", "这间的记忆 · ${mine.size}") }
            PalaceRoundIcon(HugeIcons.Add01, size = 30.dp) { newRequest = newDraft(room) }
        }
        if (mine.isEmpty()) PalaceEmpty("这间还没有记忆。可以点右上的加号手动写一条")
        PalaceNodeList(mine, actions, showRoom = false, editRequest = newRequest, onEditRequestHandled = { newRequest = null })
    }
}

private fun roomNote(room: String) = when (room) {
    PalaceRoom.LIVING -> "最多放 ${PalaceRoom.capacity(room)} 条，忘得最快，满了先挤掉旧的不重要的"
    PalaceRoom.ATTIC -> "消化时阿辞会反复翻这里，想通了会搬走"
    PalaceRoom.WINDOWSILL -> "实现了或者放下了会搬走"
    else -> "忘得很慢"
}

/** 全部记忆：按房间筛选、搜索、补算向量 */
@Composable
internal fun PalaceAllPage(
    nodes: List<PalaceNodeLite>,
    actions: PalaceNodeActions,
    onBack: () -> Unit,
    onReindex: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var roomFilter by remember { mutableStateOf<String?>(null) }
    val q = query.trim()
    val filtered = remember(nodes, q, roomFilter) {
        nodes.filter { (roomFilter == null || it.room == roomFilter) && (q.isEmpty() || it.matches(q)) }
    }
    val missing = nodes.count { !it.hasEmbedding || it.embeddingKey != actions.currentKey }

    PalaceSubPage("全部记忆", "ALL · ${nodes.size}", onBack = onBack) {
        PalaceSearchField(query, { query = it }, "搜索记忆（关键词、标签、情绪）")
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterPill("全部", roomFilter == null) { roomFilter = null }
            PalaceRoom.all.forEach { r -> FilterPill(PalaceRoom.label(r), roomFilter == r) { roomFilter = if (roomFilter == r) null else r } }
        }
        if (actions.currentKey != null && missing > 0) {
            Spacer(Modifier.height(10.dp))
            PalaceBanner("有 $missing 条还没算向量，点这里补算", onClick = onReindex)
        }
        PalaceSection("NODES", "${filtered.size} 条")
        if (filtered.isEmpty()) PalaceEmpty(if (nodes.isEmpty()) "还没有记忆" else "没有符合条件的记忆")
        PalaceNodeList(filtered, actions, showRoom = roomFilter == null)
    }
}

@Composable
private fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = palaceColors()
    Text(
        text, fontSize = 12.sp,
        color = if (selected) c.onAccent else c.ink2,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) c.accent else c.glassStrong)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
