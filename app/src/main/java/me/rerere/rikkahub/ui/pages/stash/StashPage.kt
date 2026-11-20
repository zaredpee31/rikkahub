package me.rerere.rikkahub.ui.pages.stash

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.FolderAdd
import me.rerere.hugeicons.stroke.Link01
import me.rerere.hugeicons.stroke.Package01
import me.rerere.rikkahub.companion.MonoText
import me.rerere.rikkahub.ui.components.ui.PalaceConfirmDialog
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceBackdrop
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceBanner
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceChip
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceEmptyState
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceField
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceGlass
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceSearchField
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceSection
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceTextButton
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceTopBar
import me.rerere.rikkahub.ui.theme.palaceColors

/** 格子的四种淡底色，名字跟服务器 stash.py 的 COLORS 一致。 */
internal val STASH_COLORS = listOf("rose", "lilac", "peach", "sage")

internal fun stashTint(color: String): Color = when (color) {
    "lilac" -> Color(0xFFEDE7F5)
    "peach" -> Color(0xFFF9ECE2)
    "sage" -> Color(0xFFE6EFE7)
    else -> Color(0xFFF7E6E9)
}

internal fun stashColorName(color: String) = when (color) {
    "lilac" -> "丁香"
    "peach" -> "蜜桃"
    "sage" -> "青草"
    else -> "玫瑰"
}

internal const val DEFAULT_FOLDER_ICON = "📁"

private fun whoLabel(by: String) = if (by == "ai") "阿辞" else "我"

private fun shortTime(iso: String) =
    if (iso.length >= 16) iso.substring(5, 16).replace('T', ' ') else iso

private fun hostOf(url: String) =
    runCatching { Uri.parse(url).host?.removePrefix("www.") }.getOrNull() ?: url

/** 页面上开着的弹窗，一次只开一个。 */
internal sealed interface StashDialog {
    data object NewFolder : StashDialog
    data class EditFolder(val folder: StashFolder) : StashDialog
    data object NewItem : StashDialog
    data class EditItem(val item: StashItem) : StashDialog
    data class Move(val item: StashItem) : StashDialog
    data class DeleteFolder(val folder: StashFolder) : StashDialog
    data class DeleteItem(val item: StashItem) : StashDialog
    data class DeleteComment(val comment: StashComment) : StashDialog
}

/** 囤粮处：取代原来的「收藏」。格子一层层往里点，首页下面的粮食就是待归仓。 */
@Composable
fun StashPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = remember { StashController(context, scope) }
    val pc = palaceColors()
    var dialog by remember { mutableStateOf<StashDialog?>(null) }

    LaunchedEffect(Unit) { c.refresh() }
    LaunchedEffect(c.notice) {
        if (c.notice != null) {
            delay(2500)
            c.notice = null
        }
    }

    val here = c.folder(c.current)
    val searching = c.keyword.isNotBlank()
    fun goUp() {
        c.current = here?.parentId
    }

    BackHandler {
        when {
            dialog != null -> dialog = null
            searching -> c.keyword = ""
            c.current != null -> goUp()
            else -> onBack()
        }
    }

    PalaceBackdrop {
        Column(Modifier.fillMaxSize()) {
            PalaceTopBar(
                title = here?.let { "${it.icon ?: DEFAULT_FOLDER_ICON} ${it.name}" } ?: "囤粮处",
                sub = "STASH",
                onBack = { if (c.current != null) goUp() else onBack() },
                rightIcon = HugeIcons.Add01,
                onRight = { dialog = StashDialog.NewItem },
            )
            val shown = c.visibleItems()
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().navigationBarsPadding(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                c.error?.let { e ->
                    item(key = "error") { PalaceBanner(e, onClick = { c.error = null }, warn = true) }
                }
                if (c.offline) {
                    item(key = "offline") { PalaceBanner("连不上服务器，这是上次的样子，只能看不能改", warn = true) }
                }
                c.notice?.let { n -> item(key = "notice") { PalaceBanner(n) } }

                item(key = "head") {
                    Column {
                        if (c.current == null) {
                            val total = c.items.size
                            val eaten = c.items.count { it.eaten }
                            val inbox = c.items.count { it.folderId == null }
                            MonoText(
                                if (!c.loaded) "加载中…" else "共 $total 条 · 囤着 ${total - eaten} · 吃掉 $eaten · 待归仓 $inbox",
                                size = 9.5f, color = pc.mute,
                            )
                        } else {
                            Breadcrumb(c)
                        }
                        Spacer(Modifier.height(10.dp))
                        PalaceSearchField(c.keyword, { c.keyword = it }, "搜全部：标题、正文、链接、标签")
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StashFilter.entries.forEach { f ->
                                PalaceChip(f.label, c.filter == f) { c.filter = f }
                            }
                        }
                    }
                }

                if (!searching) {
                    val subs = c.children(c.current)
                    item(key = "folders-title") {
                        PalaceSection(tag = "FOLDERS", title = "格子 · ${subs.size}") {
                            PalaceTextButton("新格子", onClick = { dialog = StashDialog.NewFolder }, icon = HugeIcons.FolderAdd)
                        }
                    }
                    if (subs.isEmpty() && c.loaded) {
                        item(key = "folders-empty") {
                            Text(
                                if (c.current == null) "还没有格子，先建一个，比如「想看的书」「想去的地方」。" else "这格里还没有子格子。",
                                fontSize = 12.sp, color = pc.mute, modifier = Modifier.padding(horizontal = 6.dp),
                            )
                        }
                    }
                    items(subs, key = { "f-" + it.id }) { f ->
                        FolderCard(c, f, onOpen = { c.current = f.id }, onEdit = { dialog = StashDialog.EditFolder(f) })
                    }
                }

                item(key = "items-title") {
                    val title = when {
                        searching -> "搜到的 · ${shown.size}"
                        c.current == null -> "待归仓 · ${shown.size}"
                        else -> "这格里的 · ${shown.size}"
                    }
                    PalaceSection(tag = if (c.current == null && !searching) "INBOX" else "ITEMS", title = title)
                }
                if (shown.isEmpty() && c.loaded) {
                    item(key = "items-empty") {
                        PalaceEmptyState(
                            text = when {
                                searching -> "没搜到"
                                c.filter != StashFilter.All -> "这个状态下没有东西"
                                c.current == null -> "拿不准放哪的，先丢这里"
                                else -> "这格里还是空的"
                            },
                            icon = HugeIcons.Package01,
                            buttonText = if (searching) null else "囤一条",
                            onButton = { dialog = StashDialog.NewItem },
                        )
                    }
                }
                items(shown, key = { "i-" + it.id }) { i ->
                    ItemCard(
                        c = c, item = i, showPath = searching,
                        onOpenUrl = { url ->
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }.onFailure { c.error = "打不开这个链接" }
                        },
                        onEdit = { dialog = StashDialog.EditItem(i) },
                        onMove = { dialog = StashDialog.Move(i) },
                        onDelete = { dialog = StashDialog.DeleteItem(i) },
                        onDeleteComment = { dialog = StashDialog.DeleteComment(it) },
                    )
                }
            }
        }
    }

    val close = { dialog = null }
    when (val d = dialog) {
        null -> Unit
        StashDialog.NewFolder -> StashFolderDialog(c, editing = null, parent = c.current, onDelete = null, onDismiss = close)
        is StashDialog.EditFolder -> StashFolderDialog(
            c, editing = d.folder, parent = d.folder.parentId,
            onDelete = { dialog = StashDialog.DeleteFolder(d.folder) }, onDismiss = close,
        )
        StashDialog.NewItem -> StashItemDialog(c, editing = null, folderId = c.current, onDismiss = close)
        is StashDialog.EditItem -> StashItemDialog(c, editing = d.item, folderId = d.item.folderId, onDismiss = close)
        is StashDialog.Move -> StashMoveDialog(c, d.item, onDismiss = close)
        is StashDialog.DeleteFolder -> {
            val (own, _) = c.counts(d.folder.id)
            val subs = c.children(d.folder.id).size
            PalaceConfirmDialog(
                show = true, title = "拆掉格子「${d.folder.name}」？",
                text = "里面的 $own 条和 $subs 个子格子都会升到上一级，一条都不丢。",
                confirmText = "拆掉", dismissText = "取消", danger = true,
                onConfirm = { c.deleteFolder(d.folder); dialog = null }, onDismiss = close,
            )
        }
        is StashDialog.DeleteItem -> PalaceConfirmDialog(
            show = true, title = "丢掉「${d.item.title.take(20)}」？",
            text = "这条和它下面的留言会彻底删掉，找不回来。",
            confirmText = "丢掉", dismissText = "取消", danger = true,
            onConfirm = { c.deleteItem(d.item); dialog = null }, onDismiss = close,
        )
        is StashDialog.DeleteComment -> PalaceConfirmDialog(
            show = true, title = "删掉这条留言？",
            text = "「${d.comment.content.take(40)}${if (d.comment.content.length > 40) "…" else ""}」",
            confirmText = "删掉", dismissText = "取消", danger = true,
            onConfirm = { c.deleteComment(d.comment); dialog = null }, onDismiss = close,
        )
    }
}

/** 路径条：囤粮处 › 一级 › 二级，每段都能点回去。 */
@Composable
private fun Breadcrumb(c: StashController) {
    val pc = palaceColors()
    Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
        Text("囤粮处", fontSize = 12.sp, color = pc.ink2, modifier = Modifier.clickable { c.current = null })
        c.trail(c.current).forEach { f ->
            Text("  ›  ", fontSize = 12.sp, color = pc.faint)
            val last = f.id == c.current
            Text(
                f.name, fontSize = 12.sp, color = if (last) pc.ink else pc.ink2,
                fontWeight = if (last) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier.clickable(enabled = !last) { c.current = f.id },
            )
        }
    }
}

@Composable
private fun FolderCard(c: StashController, f: StashFolder, onOpen: () -> Unit, onEdit: () -> Unit) {
    val pc = palaceColors()
    val (own, all) = c.counts(f.id)
    val stashed = c.items.count { it.folderId == f.id && !it.eaten }
    PalaceGlass(tint = stashTint(f.color).copy(alpha = 0.85f)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) { Text(f.icon ?: DEFAULT_FOLDER_ICON, fontSize = 18.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(f.name, fontSize = 14.5.sp, color = pc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(
                    "$own 条" + (if (all != own) "（连子格子 $all）" else "") + " · 囤着 $stashed",
                    fontSize = 11.sp, color = pc.mute,
                )
                f.description?.takeIf { it.isNotBlank() }?.let {
                    Text(it, fontSize = 11.sp, color = pc.mute, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            PalaceTextButton("改", onClick = onEdit, color = pc.mute)
        }
    }
}

@Composable
private fun ItemCard(
    c: StashController,
    item: StashItem,
    showPath: Boolean,
    onOpenUrl: (String) -> Unit,
    onEdit: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onDeleteComment: (StashComment) -> Unit,
) {
    val pc = palaceColors()
    val list = c.commentsOf(item.id)
    val open = item.id in c.openComments
    PalaceGlass {
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.title, fontSize = 14.5.sp, fontWeight = FontWeight.Medium,
                    color = if (item.eaten) pc.mute else pc.ink,
                    textDecoration = if (item.eaten) TextDecoration.LineThrough else null,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                if (item.eaten) {
                    Spacer(Modifier.width(6.dp))
                    MonoText("吃掉了", size = 9f, color = pc.mute)
                }
            }
            item.url?.let { url ->
                Spacer(Modifier.height(4.dp))
                Row(Modifier.clickable { onOpenUrl(url) }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(HugeIcons.Link01, null, Modifier.size(12.dp), tint = pc.mute)
                    Spacer(Modifier.width(4.dp))
                    Text(hostOf(url), fontSize = 11.5.sp, color = pc.mute, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            item.content?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, fontSize = 13.sp, lineHeight = 19.sp, color = pc.ink2, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            if (item.tags.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(item.tags.joinToString("  ") { "#$it" }, fontSize = 11.5.sp, color = pc.ink2)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${whoLabel(item.addedBy)}囤的 · ${shortTime(item.created)}" +
                    (if (showPath) " · ${c.pathLabel(item.folderId)}" else ""),
                fontSize = 10.5.sp, color = pc.faint,
            )
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 2.dp)) {
                PalaceTextButton(if (item.eaten) "囤回去" else "吃掉", onClick = { c.toggleEaten(item) })
                PalaceTextButton("挪到", onClick = onMove)
                PalaceTextButton("编辑", onClick = onEdit)
                PalaceTextButton("留言" + if (list.isNotEmpty()) " · ${list.size}" else "", onClick = { c.toggleComments(item.id) })
                PalaceTextButton("丢掉", onClick = onDelete, color = pc.error)
            }
            if (open) Comments(c, item, list, onDeleteComment)
        }
    }
}

@Composable
private fun Comments(c: StashController, item: StashItem, list: List<StashComment>, onDelete: (StashComment) -> Unit) {
    val pc = palaceColors()
    var draft by remember(item.id) { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)) {
        list.forEach { cm ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("${whoLabel(cm.author)} · ${shortTime(cm.created)}", fontSize = 10.5.sp, color = pc.faint)
                    Text(cm.content, fontSize = 13.sp, lineHeight = 19.sp, color = pc.ink)
                }
                if (cm.author != "ai") PalaceTextButton("删", onClick = { onDelete(cm) }, color = pc.mute)
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PalaceField(
                draft, { draft = it }, Modifier.weight(1f),
                placeholder = "留一句：为什么囤、看完怎么样", maxLines = 4,
            )
            PalaceTextButton(
                "留言", enabled = draft.isNotBlank() && !c.busy,
                onClick = { c.comment(item, draft) { draft = "" } },
            )
        }
    }
}
