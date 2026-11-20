package me.rerere.rikkahub.ui.pages.stash

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import me.rerere.rikkahub.companion.StashApi
import me.rerere.rikkahub.webpage.WebReader

/** 格子：自引用树，parentId 空=一级。color 是淡底色 rose/lilac/peach/sage。 */
internal data class StashFolder(
    val id: String,
    val parentId: String?,
    val name: String,
    val icon: String?,
    val color: String,
    val description: String?,
    val sort: Int,
    val created: String,
)

/** 粮食：folderId 空=待归仓。addedBy 是 her / ai。 */
internal data class StashItem(
    val id: String,
    val folderId: String?,
    val title: String,
    val url: String?,
    val content: String?,
    val tags: List<String>,
    val addedBy: String,
    val src: String,
    val status: String,
    val eatenAt: String?,
    val created: String,
) {
    val eaten get() = status == "eaten"
}

internal data class StashComment(
    val id: String,
    val itemId: String,
    val author: String,
    val content: String,
    val created: String,
)

internal enum class StashFilter(val label: String) { All("全部"), Stashed("囤着"), Eaten("吃掉了") }

private fun JsonObject.s(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
private fun JsonObject.arr(k: String): List<JsonObject> =
    (this[k] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

private fun folderOf(o: JsonObject) = StashFolder(
    id = o.s("id").orEmpty(), parentId = o.s("parent_id"), name = o.s("name").orEmpty(),
    icon = o.s("icon"), color = o.s("color") ?: "rose", description = o.s("description"),
    sort = o.s("sort")?.toIntOrNull() ?: 0, created = o.s("created").orEmpty(),
)

private fun itemOf(o: JsonObject) = StashItem(
    id = o.s("id").orEmpty(), folderId = o.s("folder_id"), title = o.s("title").orEmpty(),
    url = o.s("url"), content = o.s("content"),
    tags = (o["tags"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty(),
    addedBy = o.s("added_by") ?: "her", src = o.s("src") ?: "app", status = o.s("status") ?: "stashed",
    eatenAt = o.s("eaten_at"), created = o.s("created").orEmpty(),
)

private fun commentOf(o: JsonObject) = StashComment(
    id = o.s("id").orEmpty(), itemId = o.s("item_id").orEmpty(), author = o.s("author") ?: "her",
    content = o.s("content").orEmpty(), created = o.s("created").orEmpty(),
)

/**
 * 囤粮处页面的状态和动作。数据只在服务器，每次改完重新拉整份；
 * 拉不到就用上次的缓存，offline=true，页面只能看不能改。
 */
internal class StashController(private val context: Context, private val scope: CoroutineScope) {
    var folders by mutableStateOf<List<StashFolder>>(emptyList()); private set
    var items by mutableStateOf<List<StashItem>>(emptyList()); private set
    var comments by mutableStateOf<List<StashComment>>(emptyList()); private set
    var loaded by mutableStateOf(false); private set
    var offline by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    /** 囤一条前正在读链接（要十几秒），弹窗按钮显示「读链接中…」。 */
    var reading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null)
    var notice by mutableStateOf<String?>(null)

    /** 当前在哪个格子里，null=囤粮处首页（首页下面的粮食就是待归仓）。 */
    var current by mutableStateOf<String?>(null)
    var keyword by mutableStateOf("")
    var filter by mutableStateOf(StashFilter.All)
    /** 留言展开的那几条。 */
    var openComments by mutableStateOf<Set<String>>(emptySet())

    private fun apply(o: JsonObject) {
        folders = o.arr("folders").map(::folderOf)
        items = o.arr("items").map(::itemOf)
        comments = o.arr("comments").map(::commentOf)
        if (current != null && folders.none { it.id == current }) current = null
    }

    fun refresh() = scope.launch {
        runCatching { StashApi.all(context) }
            .onSuccess { apply(it); offline = false; error = null }
            .onFailure { e ->
                val cache = StashApi.cached(context)
                if (cache != null) {
                    apply(cache)
                    offline = true
                    error = null
                } else {
                    error = "囤粮处没拉下来：${e.message ?: e.javaClass.simpleName}"
                }
            }
        loaded = true
    }

    /** 改动统一走这里：断网不让改；成功后重新拉整份，onOk 拿服务器回的 JSON。 */
    private fun post(path: String, body: JsonObject, onOk: (JsonObject) -> Unit = {}) {
        if (offline) {
            error = "现在连不上服务器，只能看不能改"
            return
        }
        if (busy) return
        busy = true
        scope.launch {
            runCatching { StashApi.call("POST", path, body) }
                .onSuccess { r ->
                    onOk(r)
                    runCatching { StashApi.all(context) }.onSuccess { apply(it) }
                }
                .onFailure { e -> error = e.message ?: e.javaClass.simpleName }
            busy = false
        }
    }

    // ── 查 ──────────────────────────────────────────────────────────────────

    fun folder(id: String?) = id?.let { f -> folders.firstOrNull { it.id == f } }

    fun children(parent: String?) =
        folders.filter { it.parentId == parent }.sortedWith(compareBy({ it.sort }, { it.created }))

    /** 从根到这个格子，断链就停。 */
    fun trail(id: String?): List<StashFolder> {
        val out = ArrayList<StashFolder>()
        var cur = folder(id)
        var n = 0
        while (cur != null && n < 64) {
            out.add(0, cur)
            cur = folder(cur.parentId)
            n++
        }
        return out
    }

    fun pathLabel(id: String?) = trail(id).joinToString(" › ") { it.name }.ifEmpty { "待归仓" }

    fun descendants(id: String): Set<String> {
        val out = HashSet<String>()
        val queue = ArrayDeque(listOf(id))
        while (queue.isNotEmpty()) {
            val p = queue.removeFirst()
            for (c in folders) if (c.parentId == p && out.add(c.id)) queue.add(c.id)
        }
        return out
    }

    /** 挪到 / 放在 用的扁平树：先序，depth 用来缩进。exclude 的格子和它的子孙不出现。 */
    fun flatTree(exclude: String? = null): List<Pair<StashFolder, Int>> {
        val ban = exclude?.let { descendants(it) + it }.orEmpty()
        val out = ArrayList<Pair<StashFolder, Int>>()
        fun visit(parent: String?, depth: Int) {
            if (depth > 64) return
            for (f in children(parent)) if (f.id !in ban) {
                out.add(f to depth)
                visit(f.id, depth + 1)
            }
        }
        visit(null, 0)
        return out
    }

    /** 这格自己的条数和连子格子一共的条数。 */
    fun counts(id: String): Pair<Int, Int> {
        val own = items.count { it.folderId == id }
        val all = descendants(id) + id
        return own to items.count { it.folderId in all }
    }

    private fun passFilter(i: StashItem) = when (filter) {
        StashFilter.All -> true
        StashFilter.Stashed -> !i.eaten
        StashFilter.Eaten -> i.eaten
    }

    /** 搜索时搜全部；不搜就是当前这格的（首页=待归仓）。新囤的在前。 */
    fun visibleItems(): List<StashItem> {
        val k = keyword.trim().lowercase()
        val base = if (k.isEmpty()) items.filter { it.folderId == current }
        else items.filter { i ->
            i.title.lowercase().contains(k) || (i.content ?: "").lowercase().contains(k) ||
                (i.url ?: "").lowercase().contains(k) || i.tags.any { it.lowercase().contains(k) }
        }
        return base.filter(::passFilter).sortedByDescending { it.created }
    }

    fun commentsOf(itemId: String) = comments.filter { it.itemId == itemId }.sortedBy { it.created }

    // ── 格子 ────────────────────────────────────────────────────────────────

    fun saveFolder(id: String?, name: String, icon: String, color: String, desc: String, parent: String?, done: () -> Unit) {
        val body = buildJsonObject {
            put("name", name.trim()); put("icon", icon.trim()); put("color", color)
            put("description", desc.trim()); put("parent_id", parent ?: "inbox"); put("by", "her")
        }
        post(if (id == null) "stash/folders" else "stash/folders/$id", body) {
            notice = if (id == null) "格子「${name.trim()}」建好了" else "格子「${name.trim()}」改好了"
            done()
        }
    }

    fun deleteFolder(f: StashFolder) = post("stash/folders/${f.id}/delete", JsonObject(emptyMap())) {
        if (current == f.id || current in descendants(f.id)) current = f.parentId
        notice = "格子「${f.name}」拆掉了，里面的东西升了一级"
    }

    // ── 粮食 ────────────────────────────────────────────────────────────────

    /**
     * 囤一条 / 保存编辑。新建时有链接、标题或正文又有一样是空的，先用 WebReader 读链接补上空着的
     * （她自己填的不覆盖，url 换成跳转后的地址）；读失败不报错，照原样存。编辑已有的不读。
     */
    fun saveItem(
        id: String?, url: String, title: String, content: String, tags: List<String>, folderId: String?,
        done: () -> Unit,
    ) {
        val link = url.trim()
        val needRead = id == null && !offline && link.startsWith("http") && (title.isBlank() || content.isBlank())
        if (!needRead) {
            submitItem(id, url, title, content, tags, folderId, done)
            return
        }
        if (reading || busy) return
        reading = true
        scope.launch {
            var finalUrl = link
            var t = title
            var c = content
            val page = runCatching { WebReader.read(context, link) }.getOrNull()
            if (page != null) {
                finalUrl = page.finalUrl ?: link
                val pt = page.title
                if (pt != null && !page.needLogin) {
                    if (t.isBlank()) t = pt.take(200)
                    if (c.isBlank() && page.text.isNotBlank()) c = page.text.take(20000)
                }
            }
            reading = false
            submitItem(id, finalUrl, t, c, tags, folderId, done)
        }
    }

    private fun submitItem(
        id: String?, url: String, title: String, content: String, tags: List<String>, folderId: String?,
        done: () -> Unit,
    ) {
        val body = buildJsonObject {
            put("url", url.trim()); put("title", title.trim()); put("content", content.trim())
            put("tags", JsonArray(tags.map { JsonPrimitive(it) })); put("folder_id", folderId ?: "inbox")
            if (id == null) { put("by", "her"); put("src", "app") }
        }
        post(if (id == null) "stash/items" else "stash/items/$id", body) { r ->
            val dup = (r["dup"] as? JsonPrimitive)?.contentOrNull == "true"
            val t = (r["item"] as? JsonObject)?.s("title") ?: title
            notice = when {
                dup -> "这条早囤过了：「$t」在 ${pathLabel((r["item"] as? JsonObject)?.s("folder_id"))}"
                id != null -> "「$t」改好了"
                folderId == null -> "「$t」先放待归仓"
                else -> "「$t」囤进格子了"
            }
            done()
        }
    }

    fun toggleEaten(i: StashItem) = post("stash/items/${i.id}", buildJsonObject {
        put("status", if (i.eaten) "stashed" else "eaten")
    }) { notice = if (i.eaten) "「${i.title}」囤回去了" else "「${i.title}」吃掉了" }

    fun move(i: StashItem, to: String?) = post("stash/items/${i.id}", buildJsonObject {
        put("folder_id", to ?: "inbox")
    }) { notice = "「${i.title}」挪到 ${pathLabel(to)}" }

    fun deleteItem(i: StashItem) = post("stash/items/${i.id}/delete", JsonObject(emptyMap())) {
        notice = "「${i.title}」丢掉了"
    }

    // ── 留言 ────────────────────────────────────────────────────────────────

    fun comment(i: StashItem, text: String, done: () -> Unit) = post("stash/items/${i.id}/comments", buildJsonObject {
        put("content", text.trim()); put("by", "her"); put("src", "app")
    }) { done() }

    fun deleteComment(c: StashComment) = post("stash/comments/${c.id}/delete", JsonObject(emptyMap())) {
        notice = "留言删掉了"
    }

    fun toggleComments(itemId: String) {
        openComments = if (itemId in openComments) openComments - itemId else openComments + itemId
    }
}

/** 把标签输入框里的字拆成标签：逗号、顿号、换行都算分隔。 */
internal fun parseTags(s: String): List<String> =
    s.split(',', '，', '、', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
