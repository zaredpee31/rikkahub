package me.rerere.rikkahub.ui.pages.stash

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceDialog
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceField
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceTextButton
import me.rerere.rikkahub.ui.theme.palaceColors
import me.rerere.rikkahub.webpage.WebPages

/** 弹窗里的出错提示：本地校验的和服务器回的都显示在这，弹窗不关。 */
@Composable
private fun DialogError(local: String?, c: StashController) {
    val pc = palaceColors()
    val msg = local ?: c.error
    if (msg != null) Text(msg, fontSize = 12.sp, color = pc.error)
}

/** 「放在 / 挪到」用的格子列表：首页（待归仓 / 一级）+ 按树缩进的格子，选中的那行深一点。 */
@Composable
private fun FolderPicker(
    c: StashController,
    selected: String?,
    rootLabel: String,
    exclude: String? = null,
    onPick: (String?) -> Unit,
) {
    val pc = palaceColors()
    val shape = RoundedCornerShape(12.dp)
    @Composable
    fun row(id: String?, label: String, depth: Int, tint: androidx.compose.ui.graphics.Color?) {
        val on = id == selected
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(if (on) pc.chip else tint?.copy(alpha = 0.5f) ?: pc.glassStrong)
                .border(0.5.dp, if (on) pc.ink2 else pc.border, shape)
                .clickable { onPick(id) }
                .padding(start = (12 + depth * 16).dp, end = 12.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label, fontSize = 13.sp, color = pc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (on) FontWeight.Medium else FontWeight.Normal, modifier = Modifier.weight(1f),
            )
            if (on) Text("✓", fontSize = 13.sp, color = pc.ink2)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        row(null, rootLabel, 0, null)
        c.flatTree(exclude).forEach { (f, depth) ->
            row(f.id, "${f.icon ?: DEFAULT_FOLDER_ICON} ${f.name}", depth, stashTint(f.color))
        }
    }
}

/** 新建 / 编辑格子。parent 是默认放在哪；编辑时可以挪到别的格子下面（不含自己和子孙）。 */
@Composable
internal fun StashFolderDialog(
    c: StashController,
    editing: StashFolder?,
    parent: String?,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val pc = palaceColors()
    var name by remember { mutableStateOf(editing?.name.orEmpty()) }
    var icon by remember { mutableStateOf(editing?.icon.orEmpty()) }
    var color by remember { mutableStateOf(editing?.color ?: "rose") }
    var desc by remember { mutableStateOf(editing?.description.orEmpty()) }
    var at by remember { mutableStateOf(parent) }
    var local by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { c.error = null }

    PalaceDialog(
        title = if (editing == null) "新格子" else "改格子",
        onDismiss = onDismiss,
        confirmText = if (c.busy) "保存中…" else "保存",
        confirmEnabled = !c.busy,
        onConfirm = {
            local = when {
                name.isBlank() -> "格子总得有个名字"
                name.trim().length > 60 -> "名字太长了，60 字以内"
                icon.trim().length > 8 -> "图标只放一个 emoji"
                else -> null
            }
            if (local == null) c.saveFolder(editing?.id, name, icon, color, desc, at, onDismiss)
        },
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            PalaceField(icon, { icon = it }, Modifier.size(width = 64.dp, height = 64.dp), label = "图标", placeholder = DEFAULT_FOLDER_ICON, singleLine = true)
            Box(Modifier.size(10.dp))
            PalaceField(name, { name = it }, Modifier.weight(1f), label = "名字", placeholder = "比如「想看的书」", singleLine = true)
        }
        Text("底色", fontSize = 11.sp, color = pc.mute, modifier = Modifier.padding(start = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            STASH_COLORS.forEach { col ->
                val on = col == color
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { color = col }) {
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(stashTint(col))
                            .border(if (on) 1.5.dp else 0.5.dp, if (on) pc.ink2 else pc.border, CircleShape),
                    )
                    Text(stashColorName(col), fontSize = 10.sp, color = if (on) pc.ink else pc.mute)
                }
            }
        }
        PalaceField(desc, { desc = it }, label = "说明（可不写）", placeholder = "这格放什么", maxLines = 3)
        Text("放在", fontSize = 11.sp, color = pc.mute, modifier = Modifier.padding(start = 4.dp))
        FolderPicker(c, at, rootLabel = "囤粮处（一级格子）", exclude = editing?.id) { at = it }
        DialogError(local, c)
        if (onDelete != null) {
            PalaceTextButton("拆掉这个格子", onClick = onDelete, color = pc.error)
        }
    }
}

/** 囤一条 / 编辑这条。链接和正文至少一样；没链接得写标题。 */
@Composable
internal fun StashItemDialog(
    c: StashController,
    editing: StashItem?,
    folderId: String?,
    onDismiss: () -> Unit,
) {
    val pc = palaceColors()
    var url by remember { mutableStateOf(editing?.url.orEmpty()) }
    var title by remember { mutableStateOf(editing?.title.orEmpty()) }
    var content by remember { mutableStateOf(editing?.content.orEmpty()) }
    var tags by remember { mutableStateOf(editing?.tags?.joinToString("，").orEmpty()) }
    var at by remember { mutableStateOf(folderId) }
    var local by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { c.error = null }

    PalaceDialog(
        title = if (editing == null) "囤一条" else "编辑这条",
        onDismiss = onDismiss,
        confirmText = if (c.reading) "读链接中…" else if (c.busy) "保存中…" else if (editing == null) "囤起来" else "保存",
        confirmEnabled = !c.busy && !c.reading,
        onConfirm = {
            val tagList = parseTags(tags)
            // 链接框里贴的可能是整段分享文字：抠出第一个链接当 url，链接前面那段文字在标题空着时当标题
            val link = WebPages.findUrls(url).firstOrNull()
            val realUrl = link ?: url.trim()
            val realTitle = if (title.isBlank() && link != null) {
                url.substringBefore(link).trim().take(200)
            } else {
                title
            }
            local = when {
                realUrl.isBlank() && content.isBlank() -> "链接和正文至少要有一样"
                realUrl.isBlank() && realTitle.isBlank() -> "没有链接时得写个标题"
                realTitle.trim().length > 200 -> "标题太长了，200 字以内"
                tagList.size > 20 -> "标签最多 20 个"
                else -> null
            }
            if (local == null) c.saveItem(editing?.id, realUrl, realTitle, content, tagList, at, onDismiss)
        },
    ) {
        PalaceField(url, { url = it }, label = "链接（可不填）", placeholder = "https://… 粘贴就行，追踪参数会自动去掉", singleLine = true)
        PalaceField(title, { title = it }, label = "标题", placeholder = "有链接可以不写", singleLine = true)
        PalaceField(content, { content = it }, label = "正文（可不填）", placeholder = "摘一段、写两句为什么囤，没链接就把内容贴进来", minLines = 3, maxLines = 8)
        PalaceField(tags, { tags = it }, label = "标签（逗号隔开，可不填）", placeholder = "刑侦，想看", singleLine = true)
        Text("放在", fontSize = 11.sp, color = pc.mute, modifier = Modifier.padding(start = 4.dp))
        FolderPicker(c, at, rootLabel = "📥 待归仓") { at = it }
        DialogError(local, c)
    }
}

/** 挪到：点一行就挪过去，当前所在那格打勾。 */
@Composable
internal fun StashMoveDialog(c: StashController, item: StashItem, onDismiss: () -> Unit) {
    LaunchedEffect(Unit) { c.error = null }
    PalaceDialog(
        title = "「${item.title.take(16)}」挪到",
        onDismiss = onDismiss,
        confirmText = null,
        dismissText = "取消",
    ) {
        FolderPicker(c, item.folderId, rootLabel = "📥 待归仓") { to ->
            if (to != item.folderId) c.move(item, to)
            onDismiss()
        }
        DialogError(null, c)
    }
}
