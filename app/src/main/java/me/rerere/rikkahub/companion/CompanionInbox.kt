package me.rerere.rikkahub.companion

import android.content.Intent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Alert02
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowUp02
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Moon02
import me.rerere.hugeicons.stroke.QuillWrite01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Sun01
import me.rerere.hugeicons.stroke.Tick02
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.ui.toast.ToastType
import org.koin.compose.koinInject
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.uuid.Uuid

/* ---------------- 列表里的一行 ---------------- */

private sealed interface InboxRow { val key: String }
private data class DayRow(override val key: String, val label: String) : InboxRow
private data class LineRow(
    override val key: String,
    val mine: Boolean,
    val text: String,
    val time: String,
    val first: Boolean,
    val last: Boolean,
    val typing: Boolean,
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

private class Msg(val id: String, val mine: Boolean, val lines: List<String>, val at: LocalDateTime, val date: String, val time: String, val typing: Boolean)

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
                key = "${m.id}-$j", mine = m.mine, text = line, time = m.time,
                first = !joinPrev && j == 0, last = !joinNext && j == lines.lastIndex,
                typing = m.typing && line.isEmpty(),
            )
        }
    }
    return rows
}

@Composable
fun CompanionInbox(conversationId: Uuid, onSettings: () -> Unit) {
    val context = LocalContext.current
    val chat: ChatService = koinInject()
    val settingsStore: SettingsStore = koinInject()
    val settings = LocalSettings.current
    val toaster = LocalToaster.current
    val nav = LocalNavController.current
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
    val msgs = remember(conversation, generating) {
        val all = conversation?.currentMessages.orEmpty().filter { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT }
        all.mapIndexed { i, m ->
            val raw = m.createdAt.toString()
            val at = runCatching { LocalDateTime.parse(raw) }.getOrElse { LocalDateTime.now() }
            val body = m.toText()
            val mine = m.role == MessageRole.USER
            val lines = if (mine) listOf(body.ifBlank { "（非文字消息）" })
            else body.lines().map(String::trim).filter(String::isNotEmpty)
            Msg(m.id.toString(), mine, lines, at, raw.substringBefore('T'), raw.substringAfter('T').take(5),
                typing = generating && !mine && i == all.lastIndex && lines.isEmpty())
        }.filter { it.lines.isNotEmpty() || it.typing }
    }
    val rows = remember(msgs) { buildRows(msgs) }
    val lastError = errors.lastOrNull { it.conversationId == conversationId }

    LaunchedEffect(rows.size, rows.lastOrNull()?.let { (it as? LineRow)?.text?.length }) {
        if (rows.isNotEmpty()) list.animateScrollToItem(rows.size + 2)
    }

    SnowBackdrop(snowing = generating || nudging) {
        Column(Modifier.fillMaxSize()) {
            SnowTopBar(
                title = "阿辞的消息",
                sub = "SNOW.LOG · %02d:%02d · %s".format(now.hour, now.minute,
                    modelName.replace(Regex("\\[[^\\]]*\\]"), "").trim().ifBlank { "未选择模型" }),
                leftIcon = HugeIcons.ArrowLeft01, onLeft = { nav.popBackStack() },
                rightIcon = HugeIcons.Settings03, onRight = onSettings,
                status = { if (generating) SnowChip("typing", leading = { FlakeGlyph(size = 9.dp) }) },
            )
            LazyColumn(
                state = list,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 18.dp),
            ) {
                item(key = "profile") { ProfileCard(aiName, assistant.avatar, now, generating, depth) }
                item(key = "weather") { WeatherCard(weather, now) }
                if (rows.isEmpty()) item(key = "empty") { EmptyHint() }
                items(rows, key = { it.key }) { row ->
                    when (row) {
                        is DayRow -> DayDivider(row.label)
                        is LineRow -> BubbleLine(row, if (row.mine) myName else aiName,
                            if (row.mine) settings.displaySetting.userAvatar else assistant.avatar)
                    }
                }
                item(key = "tail") { Spacer(Modifier.height(4.dp)) }
            }
            if (lastError != null) {
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
            InputBar(
                draft = draft,
                onDraft = { draft = it },
                enabled = ready && modelName.isNotBlank(),
                placeholder = if (modelName.isBlank()) "先去设置里选个模型" else "跟阿辞说点什么…",
                generating = generating,
                nudging = nudging,
                depth = depth,
                onNudge = {
                    if (nudging || generating) return@InputBar
                    scope.launch {
                        nudging = true
                        runCatching {
                            chat.getCompanionConversation()?.let { CompanionSync.activate(context, it.id, it.assistantId) }
                            CompanionSync.tryNow(context)
                        }.onFailure { toaster.show(it.message ?: "没发出来", ToastType.Error) }
                        nudging = false
                    }
                },
                onSend = {
                    val text = draft.trim()
                    if (text.isNotEmpty()) {
                        chat.sendCompanionMessage(listOf(UIMessagePart.Text(text)))
                        draft = ""
                        scope.launch { runCatching { CompanionSync.sync(context) } }
                    }
                },
                onStop = { scope.launch { chat.stopGeneration(conversationId) } },
            )
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

@Composable
private fun BubbleLine(row: LineRow, name: String, avatar: me.rerere.rikkahub.data.model.Avatar) {
    Column(Modifier.fillMaxWidth().padding(top = if (row.first) 10.dp else 3.dp)) {
        if (row.first) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = if (row.mine) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!row.mine) {
                    UIAvatar(name = name, value = avatar, modifier = Modifier.size(26.dp), shape = RoundedCornerShape(9.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(name, fontSize = 13.sp, color = Snow.ink, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(6.dp))
                    SnowChip("C")
                } else {
                    SnowChip("aoi")
                    Spacer(Modifier.width(6.dp))
                    Text(name, fontSize = 13.sp, color = Snow.ink, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(8.dp))
                    UIAvatar(name = name, value = avatar, modifier = Modifier.size(26.dp), shape = RoundedCornerShape(9.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (row.mine) Arrangement.End else Arrangement.Start) {
            val big = 20.dp; val small = 6.dp
            val shape = if (row.mine) RoundedCornerShape(big, if (row.first) small else big, big, big)
            else RoundedCornerShape(if (row.first) small else big, big, big, big)
            Box(
                Modifier
                    .widthIn(max = 300.dp)
                    .then(if (row.mine) Modifier.clip(shape).background(Snow.dark) else Modifier.snowGlass(shape, strong = true))
                    .padding(horizontal = 15.dp, vertical = 10.dp)
            ) {
                if (row.typing) TypingDots()
                else SelectionContainer {
                    Text(row.text, fontSize = 14.5.sp, lineHeight = 22.sp, color = if (row.mine) Color.White else Snow.ink, letterSpacing = 0.3.sp)
                }
            }
        }
        if (row.last && !row.typing) {
            Row(
                Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 4.dp),
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
    onDraft: (String) -> Unit,
    enabled: Boolean,
    placeholder: String,
    generating: Boolean,
    nudging: Boolean,
    depth: Float,
    onNudge: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 10.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .snowGlass(RoundedCornerShape(30.dp), strong = true, elevation = 8.dp)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Snow.chip)
                    .clickable(enabled = enabled && !nudging && !generating, onClick = onNudge),
                contentAlignment = Alignment.Center,
            ) {
                if (nudging) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 1.6.dp, color = Snow.ink2)
                else Icon(HugeIcons.QuillWrite01, "让阿辞先说", Modifier.size(18.dp), tint = Snow.ink2)
            }
            Box(Modifier.weight(1f).padding(horizontal = 12.dp).padding(bottom = 10.dp, top = 10.dp)) {
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
            if (generating) RoundIcon(HugeIcons.Cancel01, dark = true, size = 40.dp, onClick = onStop)
            else RoundIcon(HugeIcons.ArrowUp02, dark = true, enabled = enabled && draft.isNotBlank(), size = 40.dp, onClick = onSend)
        }
        SnowCap(depth, Modifier.align(Alignment.TopCenter).offset(y = (-4).dp).fillMaxWidth(0.62f).height(9.dp), seed = 42)
    }
}
