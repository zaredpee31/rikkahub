package me.rerere.rikkahub.companion

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.activity.compose.BackHandler
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

import kotlin.uuid.Uuid

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessagePart

import me.rerere.asr.ASRStatus

import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Alert02
import me.rerere.hugeicons.stroke.ArrowUp02
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.FavouriteCircle
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.Image02
import me.rerere.hugeicons.stroke.Menu03
import me.rerere.hugeicons.stroke.Mic01
import me.rerere.hugeicons.stroke.Moon02
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.QuillWrite01
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Sun01
import me.rerere.hugeicons.stroke.TextSelection
import me.rerere.hugeicons.stroke.Tick02

import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.NodeFavoriteTarget
import me.rerere.rikkahub.data.model.getAssistantOf
import me.rerere.rikkahub.data.repository.FavoriteRepository

import me.rerere.rikkahub.service.ChatService

import me.rerere.rikkahub.ui.components.ai.McpPickerSheet
import me.rerere.rikkahub.ui.components.ai.MediaFileInputRow
import me.rerere.rikkahub.ui.components.ai.rememberChatAttachmentPickerActions
import me.rerere.rikkahub.ui.components.sticker.StickerImage
import me.rerere.rikkahub.ui.components.sticker.StickerPickerSheet
import me.rerere.rikkahub.ui.components.sticker.StickerSmileIcon
import me.rerere.rikkahub.ui.components.sticker.stickerTag
import me.rerere.rikkahub.ui.components.ui.TTSControllerGate
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.PermissionRecordAudio
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState

import me.rerere.rikkahub.ui.context.LocalASRState
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.ui.context.LocalToaster

import me.rerere.rikkahub.ui.hooks.ChatInputState

import me.rerere.rikkahub.webpage.WebPages

import me.rerere.ui.toast.ToastType

import org.koin.compose.koinInject

import java.time.LocalDate
import java.time.LocalDateTime

/* ---------------- 列表里的一行 ---------------- */

private sealed interface InboxRow { val key: String }
private data class DayRow(override val key: String, val label: String) : InboxRow
private data class LineRow(
    override val key: String,
    val msgId: String,
    val firstOfMsg: Boolean,
    val lastOfMsg: Boolean,
    val mine: Boolean,
    val text: String,
    val time: String,
    val first: Boolean,
    val last: Boolean,
    val typing: Boolean,
    val fresh: Boolean,
    val note: String? = null,
) : InboxRow

private val WEEK = arrayOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

private fun dayLabel(date: String): String = runCatching {
    val d = LocalDate.parse(date)
    val today = LocalDate.now()
    val tail = "%02d.%02d %s".format(d.monthValue, d.dayOfMonth, WEEK[d.dayOfWeek.value - 1])
    when (d) {
        today -> "TODAY · $tail"
        today.minusDays(1) -> "YESTERDAY · $tail"
        else -> tail
    }
}.getOrDefault(date)

private class Msg(val id: String, val mine: Boolean, val lines: List<String>, val at: LocalDateTime, val date: String, val time: String, val typing: Boolean, val fresh: Boolean, val note: String? = null)

/** 同一个人、隔不到 5 分钟的连着发算一串，时间只在最后一条下面写一次。 */
private fun buildRows(msgs: List<Msg>): List<InboxRow> {
    val rows = mutableListOf<InboxRow>()
    var lastDate = ""
    msgs.forEachIndexed { i, m ->
        if (m.date != lastDate) { rows += DayRow("day-${m.date}", dayLabel(m.date)); lastDate = m.date }
        val prev = msgs.getOrNull(i - 1)
        val next = msgs.getOrNull(i + 1)
        val joinPrev = prev != null && prev.mine == m.mine && prev.date == m.date &&
            java.time.Duration.between(prev.at, m.at).toMinutes() < 5
        val joinNext = next != null && next.mine == m.mine && next.date == m.date &&
            java.time.Duration.between(m.at, next.at).toMinutes() < 5
        val lines = if (m.typing && m.lines.isEmpty()) listOf("") else m.lines
        lines.forEachIndexed { j, line ->
            rows += LineRow(
                key = "${m.id}-$j", msgId = m.id, firstOfMsg = j == 0, lastOfMsg = j == lines.lastIndex, mine = m.mine, text = line, time = m.time,
                first = !joinPrev && j == 0, last = !joinNext && j == lines.lastIndex,
                typing = m.typing && line.isEmpty(), fresh = m.fresh,
                note = if (j == lines.lastIndex) m.note else null,
            )
        }
    }
    return rows
}

@Composable
fun CompanionInbox(conversationId: Uuid, onSettings: () -> Unit, onOpenDrawer: () -> Unit = {}) {
    val context = LocalContext.current
    val chat: ChatService = koinInject()
    val settingsStore: SettingsStore = koinInject()
    val settings = LocalSettings.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val stored by chat.getCompanionConversationFlow().collectAsStateWithLifecycle()
    val job by remember(conversationId) { chat.getGenerationJobStateFlow(conversationId) }.collectAsStateWithLifecycle(initialValue = null)
    val errors by chat.errors.collectAsStateWithLifecycle()
    val generating = job?.isActive == true
    val list = rememberLazyListState()
    var ready by remember(conversationId) { mutableStateOf(false) }
    var modelName by remember(conversationId) { mutableStateOf("") }
    var draft by rememberSaveable(conversationId.toString()) { mutableStateOf("") }
    var nudging by remember { mutableStateOf(false) }
    var weather by remember { mutableStateOf<JsonObject?>(null) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var depth by remember { mutableFloatStateOf(0f) }

    DisposableEffect(conversationId) {
        chat.addConversationReference(conversationId)
        onDispose { chat.removeConversationReference(conversationId) }
    }
    LaunchedEffect(conversationId) {
        try {
            chat.openCompanionConversation(conversationId)
            val active = settingsStore.awaitLoaded()
            val conv = chat.getCompanionConversation()
            modelName = conv?.config?.chatModelId?.let(active::findModelById)?.displayName.orEmpty()
            ready = true
            if (conv != null && modelName.isNotBlank()) {
                CompanionSync.activate(context, conv.id, conv.assistantId)
                runCatching { ContextCompat.startForegroundService(context, Intent(context, CompanionReceiverService::class.java)) }
                runCatching { CompanionSync.sync(context) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ready = false
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            runCatching { CompanionSync.weather(context) }.getOrNull()?.let { weather = it }
            delay(20 * 60_000L)
        }
    }
    LaunchedEffect(Unit) { while (true) { now = LocalDateTime.now(); delay(20_000) } }
    // 积雪：回复的时候慢慢积，停了慢慢化
    LaunchedEffect(generating) {
        while (true) {
            depth = if (generating) (depth + 0.012f).coerceAtMost(1f) else (depth - 0.004f).coerceAtLeast(0f)
            if (!generating && depth <= 0f) break
            delay(120)
        }
    }

    val conversation = stored
    val assistant = conversation?.let { settings.getAssistantById(it.assistantId) } ?: settings.getCurrentAssistant()
    val aiName = assistant.name.ifBlank { "阿辞" }
    val myName = settings.displaySetting.userNickname.ifBlank { "小葵" }
    val openedAt = remember { LocalDateTime.now() }
    val msgs = remember(conversation, generating) {
        val all = conversation?.currentMessages.orEmpty().filter { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT }
        all.mapIndexed { i, m ->
            val raw = m.createdAt.toString()
            val at = runCatching { LocalDateTime.parse(raw) }.getOrElse { LocalDateTime.now() }
            // 网页正文只发给模型：气泡里只显示她的原话，下面一行小字写「已读取」
            val fullText = m.toText()
            val body = WebPages.strip(fullText)
            val attached = buildList {
                m.parts.count { it is UIMessagePart.Image }.takeIf { it > 0 }?.let { add("附了 $it 张图片") }
                m.parts.filterIsInstance<UIMessagePart.Document>().forEach { add("附了文件：${it.fileName}") }
            }
            val note = (attached + WebPages.statuses(fullText)).takeIf { it.isNotEmpty() }?.joinToString("\n")
            val mine = m.role == MessageRole.USER
            val hasProcess = m.parts.any { it is UIMessagePart.Tool || it is UIMessagePart.ServerTool }
            val lines = if (mine) listOf(body.ifBlank { if (attached.isNotEmpty()) "（图片或文件）" else "（非文字消息）" })
            else body.lines().map(String::trim).filter(String::isNotEmpty).flatMap(::splitSpecial)
                .ifEmpty { if (hasProcess) listOf("（调用了工具，点上面的胶囊看过程）") else emptyList() }
            Msg(m.id.toString(), mine, lines, at, raw.substringBefore('T'), raw.substringAfter('T').take(5),
                typing = generating && !mine && i == all.lastIndex && lines.isEmpty(), fresh = at.isAfter(openedAt),
                note = if (mine) note else null)
        }.filter { it.lines.isNotEmpty() || it.typing }
    }
    val rows = remember(msgs) { buildRows(msgs) }
    val byId = remember(conversation) { conversation?.currentMessages.orEmpty().associateBy { it.id.toString() } }
    val chatAssistant = remember(conversation, settings) { conversation?.let { settings.getAssistantOf(it) } }
    var openTrace by remember { mutableStateOf(setOf<String>()) }
    var showMcp by remember { mutableStateOf(false) }
    val mcpCount = chatAssistant?.mcpServers?.count { id -> settings.mcpServers.any { it.id == id && it.commonOptions.enable } } ?: 0
    val lastError = errors.lastOrNull { it.conversationId == conversationId }

    // 进页面第一次定位：不带动画直接到底，定位完成前列表不可见，避免先看到顶部再滚下去
    var positioned by remember(conversationId) { mutableStateOf(false) }
    LaunchedEffect(conversationId, rows.isEmpty(), conversation != null) {
        if (positioned) return@LaunchedEffect
        if (rows.isNotEmpty()) {
            list.scrollToItem(rows.size + 2)
            positioned = true
        } else if (conversation != null) {
            // 会话已加载但确实没有消息，不用定位
            positioned = true
        }
    }
    // 之后有新消息或回复变长：已经在底部附近、或最后一条是她自己刚发的，才动画滚到底；
    // 她往上翻着看旧消息时不拉她
    LaunchedEffect(rows.size, rows.lastOrNull()?.let { (it as? LineRow)?.text?.length }) {
        if (!positioned || rows.isEmpty()) return@LaunchedEffect
        val info = list.layoutInfo
        val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
        val nearBottom = lastVisible >= info.totalItemsCount - 3
        val sentByMe = (rows.lastOrNull() as? LineRow)?.mine == true
        if (nearBottom || sentByMe) list.animateScrollToItem(rows.size + 2)
    }

    // 键盘弹起、收起时列表保持贴底（像微信），最后一条始终露在输入栏上方
    val imeBottom = WindowInsets.ime
    val density = LocalDensity.current
    LaunchedEffect(list) {
        snapshotFlow { imeBottom.getBottom(density) }.drop(1).collect {
            // 等列表按新高度排完版再贴底
            androidx.compose.runtime.withFrameNanos { }
            val total = list.layoutInfo.totalItemsCount
            if (total > 0) list.scrollToItem(total - 1)
        }
    }

    // ---- 长按多选 ----
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<MenuTarget?>(null) }
    /** 已整理进记忆的消息 id，菜单上不给编辑；跟 ChatService.isCompanionMessageConsolidated 同一套规则，一次算完 */
    var lockedIds by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(stored) {
        val conv = stored
        lockedIds = if (conv == null) emptySet() else runCatching {
            val upTo = chat.consolidatedUpTo(conv)
            if (upTo == null) emptySet()
            else conv.currentMessages.takeWhile { it.id.toString() != upTo }.map { it.id.toString() }.toSet() + upTo
        }.getOrDefault(emptySet())
    }
    val favorites: FavoriteRepository = koinInject()
    var favorited by remember(conversationId) { mutableStateOf(setOf<String>()) }
    LaunchedEffect(conversationId) {
        favorites.listByType(me.rerere.rikkahub.data.model.FavoriteType.NODE).collect { list ->
            favorited = list.mapNotNull { me.rerere.rikkahub.data.repository.NodeFavoriteAdapter.decodeRef(it) }
                .filter { it.conversationId == conversationId }.map { it.nodeId.toString() }.toSet()
        }
    }
    /** 语音转写默认收起，展开过的记在这里；点菜单「收起文字」才收回 */
    var voiceShown by remember { mutableStateOf(setOf<String>()) }
    fun toggle(id: String) {
        selected = if (id in selected) selected - id else selected + id
        if (selected.isEmpty()) selecting = false
    }
    BackHandler(enabled = selecting) { selecting = false; selected = emptySet() }

    // ---- 表情、加号面板 ----
    var showStickers by remember { mutableStateOf(false) }
    var showPlus by remember { mutableStateOf(false) }

    // ---- 语音 ----
    val tts = LocalTTSState.current
    val asr = LocalASRState.current
    val asrState by asr.state.collectAsStateWithLifecycle()
    val micPermission = rememberPermissionState(PermissionRecordAudio)
    PermissionManager(permissionState = micPermission)
    var voiceMode by rememberSaveable { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var cancelling by remember { mutableStateOf(false) }
    var heard by remember { mutableStateOf("") }
    var recordStart by remember { mutableStateOf(0L) }
    var playingKey by remember { mutableStateOf<String?>(null) }
        val speaking by tts.isSpeaking.collectAsStateWithLifecycle()
    LaunchedEffect(speaking) { if (!speaking) playingKey = null }
    DisposableEffect(Unit) {
        TTSControllerGate.suppressed.value = true
        onDispose { TTSControllerGate.suppressed.value = false }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (asr.state.value.isRecording) asr.stop()
            tts.stop()
        }
    }

    val canSend = ready && modelName.isNotBlank()
    fun sendText(text: String) {
        if (!canSend || text.isBlank()) return
        chat.sendCompanionMessage(listOf(UIMessagePart.Text(text)))
        scope.launch { runCatching { CompanionSync.sync(context) } }
    }

    // ---- 发图片/文件、编辑、重新生成：附件选择沿用普通窗口那套 ----
    val input = remember(conversationId) { ChatInputState() }
    val picker = rememberChatAttachmentPickerActions(input, settings) { showPlus = false }
    var editingId by remember(conversationId) { mutableStateOf<Uuid?>(null) }
    var editKeep by remember(conversationId) { mutableStateOf<List<UIMessagePart>>(emptyList()) }
    /** 编辑语音消息时留住 [语音:秒数] 前缀，保存时拼回去，气泡还是语音条 */
    var editPrefix by remember(conversationId) { mutableStateOf("") }
    fun cancelEdit() { editingId = null; editKeep = emptyList(); editPrefix = ""; draft = "" }
    fun sendDraft() {
        val text = draft.trim()
        val id = editingId
        if (id != null) {
            if (text.isEmpty() && editKeep.isEmpty()) return
            val saved = editPrefix + text
            val parts = editKeep + (if (saved.isEmpty()) emptyList() else listOf(UIMessagePart.Text(saved)))
            scope.launch {
                runCatching { chat.editMessage(conversationId, id, parts) }
                    .onSuccess { cancelEdit(); runCatching { CompanionSync.sync(context) } }
                    .onFailure { toaster.show(it.message ?: "没改成", ToastType.Error) }
            }
            return
        }
        val attachments = input.messageContent
        if (text.isEmpty() && attachments.isEmpty()) return
        if (!canSend) return
        chat.sendCompanionMessage(attachments + (if (text.isEmpty()) emptyList() else listOf(UIMessagePart.Text(text))))
        input.clearInput(); draft = ""
        scope.launch { runCatching { CompanionSync.sync(context) } }
    }
    /** 编辑一条消息的文字；已整理进记忆的不动。语音消息改的是转写文字，秒数和别的附件留着。 */
    fun editMessage(id: Uuid) {
        val msg = conversation?.currentMessages.orEmpty().firstOrNull { it.id == id } ?: return
        scope.launch {
            if (chat.isCompanionMessageConsolidated(id)) { toaster.show("这条已经整理进记忆，不能改了", ToastType.Warning); return@launch }
            val raw = msg.toText()
            editKeep = msg.parts.filter { it !is UIMessagePart.Text && it !is UIMessagePart.Reasoning }
            val body = if (msg.role == MessageRole.USER) WebPages.strip(raw) else raw
            val voice = Regex("""^\[\u8bed\u97f3(?:[:\uff1a]\d{1,3})?\]\s*""").find(body.trim())
            editPrefix = voice?.value ?: ""
            draft = if (voice != null) body.trim().removePrefix(voice.value) else body
            editingId = id
        }
    }
    /** 编辑最后一条她发的话 / 重新生成最后一条他的回复；已整理进记忆的不动 */
    fun editLast() {
        val last = conversation?.currentMessages.orEmpty().lastOrNull { it.role == MessageRole.USER && !it.isContextCheckpoint } ?: run { toaster.show("还没有可以编辑的话", ToastType.Warning); return }
        editMessage(last.id)
    }
    fun copyText(text: String) {
        val cm = context.getSystemService(ClipboardManager::class.java)
        runCatching { cm?.setPrimaryClip(ClipData.newPlainText("消息", text)) }
        toaster.show("复制好了")
    }
    fun toggleFavorite(id: Uuid) {
        val node = conversation?.getMessageNodeByMessageId(id) ?: return
        scope.launch {
            runCatching {
                val on = favorites.isNodeFavorited(conversationId, node.id)
                if (on) favorites.removeNodeFavorite(conversationId, node.id)
                else favorites.addNodeFavorite(NodeFavoriteTarget(conversationId, conversation.title, node.id, node))
            }.onFailure { toaster.show(it.message ?: "收藏没改成", ToastType.Error) }
        }
    }
    fun regenerateLast() {
        val msgs = conversation?.currentMessages.orEmpty().filter { (it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT) && !it.isContextCheckpoint }
        val target = msgs.lastOrNull() ?: run { toaster.show("还没有可以重新生成的", ToastType.Warning); return }
        scope.launch {
            if (chat.isCompanionMessageConsolidated(target.id)) { toaster.show("这条已经整理进记忆，不能重来了", ToastType.Warning); return@launch }
            chat.regenerateAtMessage(conversationId, target.id)
            runCatching { CompanionSync.sync(context) }
        }
    }

    SnowBackdrop(snowing = generating || nudging) {
        Column(Modifier.fillMaxSize()) {
            if (selecting) {
                SnowTopBar(
                    title = "已选 ${selected.size} 条",
                    sub = "LONG PRESS · SELECT",
                    leftIcon = HugeIcons.Cancel01, onLeft = { selecting = false; selected = emptySet() },
                )
            } else SnowTopBar(
                title = aiName,
                sub = "SNOW.LOG · %02d:%02d · %s".format(now.hour, now.minute,
                    modelName.replace(Regex("\\[[^\\]]*\\]"), "").trim().ifBlank { "未选择模型" }),
                leftIcon = HugeIcons.Menu03, onLeft = onOpenDrawer,
                rightIcon = HugeIcons.Settings03, onRight = onSettings,
                status = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (generating) {
                            SnowChip("typing", leading = { FlakeGlyph(size = 9.dp) })
                            Spacer(Modifier.width(6.dp))
                        }
                        SnowChip(if (mcpCount > 0) "MCP · $mcpCount" else "MCP OFF", dark = mcpCount > 0,
                            modifier = Modifier.clickable(enabled = chatAssistant != null) { showMcp = true })
                    }
                },
            )
            LazyColumn(
                state = list,
                modifier = Modifier.weight(1f).fillMaxWidth().alpha(if (positioned) 1f else 0f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 18.dp),
            ) {
                item(key = "profile") { ProfileCard(aiName, assistant.avatar, now, generating, depth) }
                item(key = "weather") { WeatherCard(weather, now) }
                if (rows.isEmpty()) item(key = "empty") { EmptyHint() }
                items(rows, key = { it.key }) { row ->
                    when (row) {
                        is DayRow -> DayDivider(row.label)
                        is LineRow -> {
                            val message = if (row.mine) null else byId[row.msgId]
                            val pending = message?.hasPendingTool() == true
                            val open = pending || row.msgId in openTrace
                            val kind = remember(row.text, row.key) { lineKind(context, row.text, row.key) }
                            val showCapsule = message != null && row.firstOfMsg && (message.usage != null || message.toolSteps().isNotEmpty())
                            BubbleLine(
                                row, kind, if (row.mine) myName else aiName,
                                if (row.mine) settings.displaySetting.userAvatar else assistant.avatar,
                                selecting = selecting,
                                checked = row.msgId in selected,
                                playing = playingKey == row.key,
                                voiceOpen = row.key in voiceShown,
                                capsule = if (showCapsule) {
                                    { UsageCapsule(message, open) { openTrace = if (row.msgId in openTrace) openTrace - row.msgId else openTrace + row.msgId } }
                                } else null,
                                onClick = {
                                    when {
                                        selecting -> toggle(row.msgId)
                                        kind is LineKind.Voice -> {
                                            if (playingKey == row.key) { tts.stop(); playingKey = null }
                                            else if (!tts.isAvailable.value) toaster.show("先在设置里选个语音服务", ToastType.Warning)
                                            else { tts.stop(); tts.speak(kind.text); playingKey = row.key }
                                        }
                                    }
                                },
                                onLongClick = { box ->
                                    if (!selecting) { showPlus = false; menu = MenuTarget(row.msgId, row.key, row.mine, box) }
                                },
                            )
                            if (message != null && row.lastOfMsg && open && !selecting) {
                                TracePanel(
                                    message = message,
                                    loading = generating,
                                    assistant = chatAssistant,
                                    onToolApproval = { id, ok, reason -> chat.handleToolApproval(conversationId, id, ok, reason) },
                                    onToolAnswer = { id, answer -> chat.handleToolApproval(conversationId, id, approved = true, answer = answer) },
                                )
                            }
                        }
                    }
                }
                item(key = "tail") { Spacer(Modifier.height(4.dp)) }
            }
            if (lastError != null && !selecting) {
                Row(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 6.dp)
                        .fillMaxWidth()
                        .snowGlass(RoundedCornerShape(16.dp), strong = true)
                        .clickable { chat.dismissError(lastError.id) }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(HugeIcons.Alert02, null, Modifier.size(15.dp), tint = Snow.error)
                    Spacer(Modifier.width(8.dp))
                    Text(lastError.error.message ?: lastError.title ?: "出错了", fontSize = 12.sp, color = Snow.error, maxLines = 2, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(6.dp))
                    MonoText("CLOSE", size = 8.5f)
                }
            }
            if (showMcp && chatAssistant != null) {
                McpPickerSheet(
                    assistant = chatAssistant,
                    servers = settings.mcpServers,
                    mcpManager = chat.mcpManager,
                    onUpdateAssistant = { updated ->
                        scope.launch {
                            runCatching { chat.updateChatAssistant(conversationId) { updated } }
                                .onFailure { toaster.show(it.message ?: "MCP 没改成", ToastType.Error) }
                        }
                    },
                    onDismiss = { showMcp = false },
                )
            }
            if (showStickers) {
                StickerPickerSheet(
                    onDismiss = { showStickers = false },
                    onPick = { name -> showStickers = false; sendText(stickerTag(name)) },
                )
            }
            menu?.let { target ->
                val msg = conversation?.currentMessages.orEmpty().firstOrNull { it.id.toString() == target.msgId }
                val line = rows.filterIsInstance<LineRow>().firstOrNull { it.key == target.key }
                val kind = lineKind(context, line?.text.orEmpty(), target.key)
                val voice = kind is LineKind.Voice
                val locked = target.msgId in lockedIds
                val shown = target.key in voiceShown
                MessageMenu(
                    anchor = target.anchor,
                    mine = target.mine,
                    items = buildList {
                        if (voice) add(MenuItem(if (shown) "收起文字" else "转文字", HugeIcons.TextSelection) { voiceShown = if (shown) voiceShown - target.key else voiceShown + target.key })
                        if (!locked && msg != null) add(MenuItem("编辑", HugeIcons.PencilEdit01) { editMessage(msg.id) })
                        if (msg != null) {
                            val nodeId = conversation?.getMessageNodeByMessageId(msg.id)?.id?.toString()
                            add(MenuItem(if (nodeId != null && nodeId in favorited) "取消收藏" else "收藏", HugeIcons.FavouriteCircle) { toggleFavorite(msg.id) })
                        }
                        add(MenuItem("复制", HugeIcons.Copy01) { copyText(when { voice -> (kind as LineKind.Voice).text; line != null -> plainOf(line.text).trim(); else -> plainOf(msg?.toText().orEmpty()).trim() }) })
                        add(MenuItem("选择", HugeIcons.TextSelection) { selecting = true; selected = setOf(target.msgId) })
                        add(MenuItem("删除", HugeIcons.Delete01, danger = true) { selected = setOf(target.msgId); confirmDelete = true })
                    },
                    onDismiss = { menu = null },
                )
            }
            if (confirmDelete) {
                AlertDialog(
                    onDismissRequest = { confirmDelete = false },
                    title = { Text("删掉这 ${selected.size} 条？") },
                    text = { Text("删了就找不回来了，他之后也看不到这些。") },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmDelete = false
                            val ids = selected.toList()
                            scope.launch {
                                runCatching {
                                    ids.forEach { chat.deleteMessage(conversationId, Uuid.parse(it), failIfMissing = false) }
                                }.onSuccess {
                                    selecting = false; selected = emptySet()
                                    runCatching { CompanionSync.sync(context) }
                                }.onFailure { toaster.show(it.message ?: "没删掉", ToastType.Error) }
                            }
                        }) { Text("删除", color = Snow.error) }
                    },
                    dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("算了") } },
                    containerColor = Color.White,
                )
            }

            if (selecting) {
                SelectBar(
                    count = selected.size,
                    onCopy = {
                        val text = conversation?.currentMessages.orEmpty()
                            .filter { it.id.toString() in selected }
                            .joinToString("\n") { plainOf(it.toText()).trim() }
                        val cm = context.getSystemService(ClipboardManager::class.java)
                        runCatching { cm?.setPrimaryClip(ClipData.newPlainText("消息", text)) }
                        toaster.show("复制好了")
                        selecting = false; selected = emptySet()
                    },
                    onDelete = {
                        if (generating) toaster.show("等他说完再删", ToastType.Warning)
                        else if (selected.isNotEmpty()) confirmDelete = true
                    },
                )
            } else {
            Box(Modifier.fillMaxWidth()) {
                if (recording) {
                    RecordingCard(
                        heard = heard,
                        amplitudes = asrState.amplitudes,
                        cancelling = cancelling,
                        modifier = Modifier.padding(horizontal = 30.dp).padding(bottom = 8.dp),
                    )
                }
            }
            if (showPlus && !recording) {
                PlusPanel(
                    enabled = canSend && !generating,
                    nudging = nudging,
                    onImage = { showPlus = false; picker.onPickImage() },
                    onFile = { showPlus = false; picker.onPickFile() },
                    onEdit = { showPlus = false; editLast() },
                    onRegenerate = { showPlus = false; regenerateLast() },
                    onDice = {
                        showPlus = false
                        sendText("[骰子:${kotlin.random.Random.nextInt(1, 7)}]")
                    },
                    onNudge = {
                        showPlus = false
                        if (nudging || generating) return@PlusPanel
                        scope.launch {
                            nudging = true
                            runCatching {
                                chat.getCompanionConversation()?.let { CompanionSync.activate(context, it.id, it.assistantId) }
                                CompanionSync.tryNow(context)
                            }.onFailure { toaster.show(it.message ?: "没发出来", ToastType.Error) }
                            nudging = false
                        }
                    },
                )
            }
            if (editingId != null) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 22.dp).clickable { cancelEdit() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("正在编辑这条消息，点这里取消", fontSize = 11.sp, color = Snow.ink2)
                }
            } else if (input.messageContent.isNotEmpty()) {
                MediaFileInputRow(state = input)
            }
            InputBar(
                draft = draft,
                hasAttachments = editingId == null && input.messageContent.isNotEmpty(),
                onDraft = { draft = it },
                enabled = canSend,
                placeholder = if (modelName.isBlank()) "先去设置里选个模型" else "跟${aiName}说点什么…",
                generating = generating,
                depth = depth,
                voiceMode = voiceMode,
                onToggleVoice = { voiceMode = !voiceMode; showPlus = false },
                onSticker = { showPlus = false; showStickers = true },
                plusOpen = showPlus,
                onPlus = { showPlus = !showPlus },
                onSend = { sendDraft() },
                onStop = { scope.launch { chat.stopGeneration(conversationId) } },
                hold = {
                    HoldToTalk(
                        recording = recording,
                        cancelling = cancelling,
                        onDown = {
                            when {
                                !canSend -> false
                                !micPermission.allRequiredPermissionsGranted -> { micPermission.requestPermissions(); false }
                                !asr.state.value.isAvailable -> { toaster.show("先在设置里配好语音识别", ToastType.Warning); false }
                                asr.state.value.isRecording -> false
                                else -> {
                                    heard = ""; cancelling = false; recording = true
                                    recordStart = System.currentTimeMillis()
                                    tts.stop()
                                    asr.start { heard = it }
                                    true
                                }
                            }
                        },
                        onSlide = { cancelling = it },
                        onUp = { cancelled ->
                            val secs = ((System.currentTimeMillis() - recordStart) / 1000).toInt().coerceIn(1, 60)
                            asr.stop()
                            scope.launch {
                                // 松手后才把整段录音上传识别（硅基流动不是边说边出字），要等上传和识别回来。
                                // stop() 之后状态一定会走到 Idle；录音线程被停掉时可能顺带冒一个 Error，不能把它当结束信号。
                                if (!cancelled) withTimeoutOrNull(20_000) {
                                    asr.state.first { it.status == ASRStatus.Idle }
                                }
                                // transcript 在转回 Idle 之前就写好了，比回调里的 heard 稳
                                val text = asr.state.value.transcript.ifBlank { heard }.trim()
                                recording = false; cancelling = false
                                val err = asr.state.value.errorMessage
                                when {
                                    cancelled -> {}
                                    text.isNotEmpty() -> sendText("[语音:$secs]$text")
                                    err != null -> toaster.show(err, ToastType.Error)
                                    else -> toaster.show("没听清，再说一次？")
                                }
                                heard = ""
                            }
                        },
                    )
                },
            )
            }
        }
    }
}

/* ---------------- 顶部：阿辞的小卡片 ---------------- */

@Composable
private fun ProfileCard(name: String, avatar: me.rerere.rikkahub.data.model.Avatar, now: LocalDateTime, generating: Boolean, depth: Float) {
    val night = now.hour >= 19 || now.hour < 6
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .snowGlass(RoundedCornerShape(26.dp), elevation = 2.dp)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MonoText("+  NOTE · %02d.%02d".format(now.monthValue, now.dayOfMonth), size = 8.5f)
            Spacer(Modifier.width(8.dp))
            DottedLine(Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            MonoText("No.0", size = 8.5f)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp)) {
                UIAvatar(name = name, value = avatar, modifier = Modifier.size(62.dp), shape = RoundedCornerShape(20.dp))
                SnowCap(depth, Modifier.align(Alignment.TopCenter).offset(y = (-5).dp).width(52.dp).height(10.dp), seed = 7)
                Box(
                    Modifier.align(Alignment.BottomEnd).size(22.dp).clip(CircleShape).background(Color.White)
                        .border(0.5.dp, Snow.hairline, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(if (night) HugeIcons.Moon02 else HugeIcons.Sun01, null, Modifier.size(12.dp), tint = Snow.ink2) }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Snow.ink, letterSpacing = 1.sp)
                    Spacer(Modifier.width(8.dp))
                    SnowChip(if (generating) "typing" else "online", leading = { FlakeGlyph(size = 9.dp) })
                }
                Spacer(Modifier.height(6.dp))
                Text("辞，是说出口的话。", fontSize = 12.5.sp, color = Snow.ink2)
                MonoText("made of words, kept for you.", size = 8.5f, color = Snow.faint, spacing = 0.04f)
            }
        }
    }
}

/* ---------------- 天气条：西安 ---------------- */

@Composable
private fun WeatherCard(w: JsonObject?, now: LocalDateTime) {
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Row(
            Modifier.fillMaxWidth().snowGlass(RoundedCornerShape(22.dp)).padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (w == null) {
                MonoText("XI'AN · WEATHER LOADING", size = 9f)
                FlakeGlyph(size = 14.dp)
            } else {
                val night = now.hour >= 19 || now.hour < 6
                WeatherSlot("now", skyOf(w["code"]?.jsonPrimitive?.intOrNull ?: 116), night, w["t"]?.jsonPrimitive?.intOrNull, true)
                w["hours"]?.jsonArray.orEmpty().take(4).forEach { e ->
                    val o = e.jsonObject
                    val h = o["h"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val hi = h.toIntOrNull() ?: 12
                    WeatherSlot(h, skyOf(o["code"]?.jsonPrimitive?.intOrNull ?: 116), hi >= 19 || hi < 6, o["t"]?.jsonPrimitive?.intOrNull, false)
                }
            }
        }
        if (w != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                val hum = w["humidity"]?.jsonPrimitive?.intOrNull ?: 0
                MonoText(
                    "XI'AN · ${w["dir"]?.jsonPrimitive?.contentOrNull.orEmpty()} ${w["wind"]?.jsonPrimitive?.intOrNull ?: 0} KM/H · $hum%",
                    size = 8.5f, color = Snow.ink2,
                )
                Spacer(Modifier.width(8.dp))
                DottedLine(Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                val filled = ((hum + 10) / 20).coerceIn(0, 5)
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(5) { i ->
                        Box(
                            Modifier.size(7.dp).clip(CircleShape)
                                .background(if (i < filled) Snow.dark else Color.Transparent)
                                .border(0.8.dp, if (i < filled) Snow.dark else Snow.faint, CircleShape)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherSlot(label: String, sky: SnowSky, night: Boolean, t: Int?, bold: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        MonoText(label, size = 9f, color = if (bold) Snow.ink2 else Snow.mute)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SkyIcon(sky, night, size = 15.dp, color = if (bold) Snow.ink else Snow.ink2)
            Spacer(Modifier.width(3.dp))
            Text("${t ?: "-"}°", fontSize = 13.sp, color = Snow.ink, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

/* ---------------- 消息 ---------------- */

@Composable
private fun DayDivider(label: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        DottedLine(Modifier.weight(1f))
        MonoText(label, Modifier.padding(horizontal = 10.dp), size = 8.5f)
        DottedLine(Modifier.weight(1f))
    }
}

private data class MenuTarget(val msgId: String, val key: String, val mine: Boolean, val anchor: androidx.compose.ui.layout.LayoutCoordinates)
private data class MenuItem(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val danger: Boolean = false, val action: () -> Unit)

/**
 * 长按弹出的小菜单，贴着气泡上方（微信那样）。冷灰底、白卡、小圆角。
 * 点菜单项先关掉菜单再执行。
 */
@Composable
private fun MessageMenu(anchor: androidx.compose.ui.layout.LayoutCoordinates, mine: Boolean, items: List<MenuItem>, onDismiss: () -> Unit) {
    Popup(
        popupPositionProvider = object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val box = if (anchor.isAttached) runCatching { anchor.boundsInWindow() }.getOrNull() else null
                val left = box?.left?.toInt() ?: anchorBounds.left
                val right = box?.right?.toInt() ?: anchorBounds.right
                val top = box?.top?.toInt() ?: anchorBounds.top
                val bottom = box?.bottom?.toInt() ?: anchorBounds.bottom
                val x = if (mine) right - popupContentSize.width else left
                val above = top - popupContentSize.height
                val y = if (above >= 0) above else bottom
                return IntOffset(x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)), y.coerceAtLeast(0))
            }
        },
        onDismissRequest = onDismiss,
    ) {
        Row(
            Modifier
                .padding(bottom = 6.dp)
                .shadow(6.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                Column(
                    Modifier.width(48.dp).clip(RoundedCornerShape(8.dp)).clickable { onDismiss(); item.action() }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(item.icon, null, Modifier.size(16.dp), tint = if (item.danger) Snow.error else Snow.ink)
                    Spacer(Modifier.height(3.dp))
                    Text(item.label, fontSize = 10.sp, color = if (item.danger) Snow.error else Snow.ink2, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun BubbleLine(
    row: LineRow,
    kind: LineKind,
    name: String,
    avatar: me.rerere.rikkahub.data.model.Avatar,
    selecting: Boolean,
    checked: Boolean,
    playing: Boolean,
    voiceOpen: Boolean,
    capsule: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
    onLongClick: (androidx.compose.ui.layout.LayoutCoordinates) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (selecting) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) {
            Box(Modifier.width(28.dp), contentAlignment = Alignment.CenterStart) {
                if (row.firstOfMsg) CheckDot(checked)
            }
        }
        Column(Modifier.weight(1f).padding(top = if (row.first) 6.dp else 2.dp)) {
            if (row.first) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalArrangement = if (row.mine) Arrangement.End else Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!row.mine) {
                        UIAvatar(name = name, value = avatar, modifier = Modifier.size(22.dp), shape = RoundedCornerShape(9.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(name, fontSize = 12.sp, color = Snow.ink, fontWeight = FontWeight.Medium)
                        if (capsule != null && !selecting) {
                            Spacer(Modifier.width(5.dp))
                            capsule()
                        }
                    } else {
                        SnowChip("aoi")
                        Spacer(Modifier.width(6.dp))
                        Text(name, fontSize = 12.sp, color = Snow.ink, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.width(8.dp))
                        UIAvatar(name = name, value = avatar, modifier = Modifier.size(22.dp), shape = RoundedCornerShape(9.dp))
                    }
                }
            }
            if (!row.first && capsule != null && !selecting) {
                Row(Modifier.fillMaxWidth().padding(bottom = 2.dp, top = 1.dp)) { capsule() }
            }
            var anchor by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
            val gesture = Modifier
                .onGloballyPositioned { anchor = it }
                .combinedClickable(
                    enabled = true,
                    onClick = onClick,
                    onLongClick = { anchor?.let(onLongClick) },
                )
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = if (row.mine) Alignment.End else Alignment.Start,
            ) {
                val big = 16.dp; val small = 5.dp
                val shape = if (row.mine) RoundedCornerShape(big, if (row.first) small else big, big, big)
                else RoundedCornerShape(if (row.first) small else big, big, big, big)
                val bubble = Modifier
                    .widthIn(max = 300.dp)
                    .then(if (row.mine) Modifier.clip(shape).background(MineBubble) else Modifier.snowGlass(shape, strong = true))
                when {
                    row.typing -> Box(bubble.padding(horizontal = 12.dp, vertical = 7.dp)) { TypingDots() }
                    kind is LineKind.StickerLine -> StickerImage(
                        sticker = kind.sticker,
                        modifier = Modifier.padding(vertical = 2.dp).size(108.dp).clip(RoundedCornerShape(14.dp)).then(gesture),
                    )
                    kind is LineKind.Dice -> Box(Modifier.padding(vertical = 4.dp, horizontal = 2.dp).then(gesture)) {
                        DiceFace(kind.value, roll = row.fresh)
                    }
                    kind is LineKind.Voice -> {
                        Box(bubble.then(gesture)) { VoiceBar(kind.seconds, row.mine, playing) }
                        if (voiceOpen) {
                            Text(
                                kind.text,
                                fontSize = 12.5.sp, lineHeight = 18.sp, color = Snow.ink2,
                                modifier = Modifier.padding(top = 4.dp).widthIn(max = 260.dp)
                                    .clip(RoundedCornerShape(12.dp)).background(Color(0x99FFFFFF))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                    else -> Box(bubble.then(gesture).padding(horizontal = 12.dp, vertical = 7.dp)) {
                        Text((kind as? LineKind.Plain)?.text ?: row.text, fontSize = 13.5.sp, lineHeight = 19.sp, color = Snow.ink, letterSpacing = 0.2.sp)
                    }
                }
            }
            if (row.note != null) {
                Text(
                    row.note,
                    fontSize = 10.5.sp, color = Snow.mute,
                    modifier = Modifier.fillMaxWidth().padding(top = 3.dp, end = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
            if (row.last && !row.typing) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
                    horizontalArrangement = if (row.mine) Arrangement.End else Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (row.mine) {
                        Icon(HugeIcons.Tick02, null, Modifier.size(11.dp), tint = Snow.mute)
                        Spacer(Modifier.width(4.dp))
                        MonoText("SENT · ${row.time}", size = 8.5f)
                    } else {
                        FlakeGlyph(size = 10.dp)
                        Spacer(Modifier.width(5.dp))
                        MonoText(row.time, size = 8.5f)
                    }
                }
            }
        }
    }
}

private val MineBubble = Color(0xFFDADDE2)

@Composable
private fun TypingDots() {
    val t = rememberInfiniteTransition(label = "dots")
    Row(Modifier.height(22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(3) { i ->
            val a by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(600, delayMillis = i * 180), RepeatMode.Reverse), label = "d$i")
            Box(Modifier.size(6.dp).alpha(a).clip(CircleShape).background(Snow.ink2))
        }
    }
}

@Composable
private fun EmptyHint() {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FlakeGlyph(size = 22.dp, color = Snow.faint)
        Spacer(Modifier.height(10.dp))
        MonoText("NO MESSAGES YET", size = 9f)
        Spacer(Modifier.height(4.dp))
        Text("还没下第一场雪", fontSize = 12.sp, color = Snow.mute)
    }
}

/* ---------------- 输入栏 ---------------- */

@Composable
private fun InputBar(
    draft: String,
    hasAttachments: Boolean,
    onDraft: (String) -> Unit,
    enabled: Boolean,
    placeholder: String,
    generating: Boolean,
    depth: Float,
    voiceMode: Boolean,
    onToggleVoice: () -> Unit,
    onSticker: () -> Unit,
    plusOpen: Boolean,
    onPlus: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    hold: @Composable () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .snowGlass(RoundedCornerShape(28.dp), strong = true, elevation = 8.dp)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            BarIcon(onClick = onToggleVoice, enabled = enabled) {
                if (voiceMode) KeyboardGlyph() else Icon(HugeIcons.Mic01, "语音", Modifier.size(19.dp), tint = Snow.ink2)
            }
            Box(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                if (voiceMode) hold()
                else Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp)) {
                    if (draft.isEmpty()) Text(placeholder, fontSize = 14.sp, color = Snow.faint, maxLines = 1)
                    BasicTextField(
                        value = draft,
                        onValueChange = onDraft,
                        enabled = enabled,
                        maxLines = 5,
                        textStyle = TextStyle(fontSize = 14.sp, color = Snow.ink, lineHeight = 20.sp),
                        cursorBrush = SolidColor(Snow.ink2),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            when {
                generating -> RoundIcon(HugeIcons.Cancel01, dark = true, size = 40.dp, onClick = onStop)
                !voiceMode && (draft.isNotBlank() || hasAttachments) -> RoundIcon(HugeIcons.ArrowUp02, dark = true, enabled = enabled, size = 40.dp, onClick = onSend)
                else -> {
                    BarIcon(onClick = onSticker, enabled = enabled) {
                        Icon(StickerSmileIcon, "表情", Modifier.size(20.dp), tint = Snow.ink2)
                    }
                    BarIcon(onClick = onPlus, enabled = enabled) {
                        Icon(
                            HugeIcons.Add01, "更多",
                            Modifier.size(20.dp).graphicsLayer { rotationZ = if (plusOpen) 45f else 0f },
                            tint = Snow.ink2,
                        )
                    }
                }
            }
        }
        SnowCap(depth, Modifier.align(Alignment.TopCenter).offset(y = (-4).dp).fillMaxWidth(0.62f).height(9.dp), seed = 42)
    }
}

@Composable
private fun BarIcon(onClick: () -> Unit, enabled: Boolean = true, content: @Composable () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** 加号展开的一排：摇骰子、让他先说。 */
@Composable
private fun PlusPanel(
    enabled: Boolean, nudging: Boolean,
    onImage: () -> Unit, onFile: () -> Unit, onEdit: () -> Unit, onRegenerate: () -> Unit,
    onDice: () -> Unit, onNudge: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        PlusItem("图片", enabled, onImage) { Icon(HugeIcons.Image02, null, Modifier.size(20.dp), tint = Snow.ink2) }
        PlusItem("文件", enabled, onFile) { Icon(HugeIcons.File02, null, Modifier.size(20.dp), tint = Snow.ink2) }
        PlusItem("编辑", enabled, onEdit) { Icon(HugeIcons.PencilEdit01, null, Modifier.size(20.dp), tint = Snow.ink2) }
        PlusItem("重新生成", enabled, onRegenerate) { Icon(HugeIcons.Refresh01, null, Modifier.size(20.dp), tint = Snow.ink2) }
        PlusItem("摇骰子", enabled, onDice) { DiceFace(value = 5, roll = false, size = 24.dp) }
        PlusItem("让他先说", enabled && !nudging, onNudge) {
            if (nudging) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 1.6.dp, color = Snow.ink2)
            else Icon(HugeIcons.QuillWrite01, null, Modifier.size(20.dp), tint = Snow.ink2)
        }
    }
}

@Composable
private fun PlusItem(label: String, enabled: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Column(
        Modifier.alpha(if (enabled) 1f else 0.45f).clip(RoundedCornerShape(14.dp)).clickable(enabled = enabled, onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(52.dp).snowGlass(RoundedCornerShape(16.dp), strong = true, elevation = 2.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.height(5.dp))
        Text(label, fontSize = 11.sp, color = Snow.ink2)
    }
}

/** 多选时替换输入栏：复制、删除。 */
@Composable
private fun SelectBar(count: Int, onCopy: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 10.dp)
            .snowGlass(RoundedCornerShape(28.dp), strong = true, elevation = 8.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectAction(HugeIcons.Copy01, "复制", Snow.ink2, count > 0, onCopy, Modifier.weight(1f))
        Box(Modifier.width(0.5.dp).height(22.dp).background(Snow.hairline))
        SelectAction(HugeIcons.Delete01, "删除", Snow.error, count > 0, onDelete, Modifier.weight(1f))
    }
}

@Composable
private fun SelectAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(20.dp)).clickable(enabled = enabled, onClick = onClick).padding(vertical = 10.dp)
            .alpha(if (enabled) 1f else 0.4f),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(17.dp), tint = tint)
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 13.sp, color = tint)
    }
}
