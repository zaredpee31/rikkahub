package me.rerere.rikkahub.companion

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.ui.components.ai.ModelListSheet
import me.rerere.rikkahub.ui.components.ai.rememberModelListState
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.utils.NotificationUtil
import me.rerere.ui.toast.ToastType
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

@Composable
fun CompanionPage() {
    val context = LocalContext.current
    val chat: ChatService = koinInject()
    var showSettings by remember { mutableStateOf(false) }
    var conversationId by remember { mutableStateOf<Uuid?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(showSettings, refresh) {
        if (!showSettings) {
            val oldId = CompanionSync.target(context).takeIf { it.isNotBlank() }?.let { runCatching { Uuid.parse(it) }.getOrNull() }
            conversationId = runCatching { chat.initializeCompanionConversation(oldId)?.id }.getOrNull()
                ?: runCatching { chat.getCompanionConversation()?.id }.getOrNull()
            loaded = true
        }
    }
    when {
        !loaded -> SnowBackdrop(snowing = false) {}
        showSettings || conversationId == null -> CompanionSettingsPage(
            hasInbox = conversationId != null,
            onDone = { showSettings = false; refresh++ },
        )
        else -> CompanionInbox(conversationId = conversationId!!, onSettings = { showSettings = true })
    }
}

private fun fmtTime(epochSeconds: Double?): String {
    if (epochSeconds == null || epochSeconds <= 0) return "—"
    val t = java.time.Instant.ofEpochMilli((epochSeconds * 1000).toLong()).atZone(java.time.ZoneId.systemDefault())
    return "%02d-%02d %02d:%02d".format(t.monthValue, t.dayOfMonth, t.hour, t.minute)
}

@Composable
private fun CompanionSettingsPage(hasInbox: Boolean, onDone: () -> Unit) {
    val context = LocalContext.current
    val settings = LocalSettings.current
    val chat: ChatService = koinInject()
    val toaster = LocalToaster.current
    val nav = LocalNavController.current
    val scope = rememberCoroutineScope()
    var modelId by remember { mutableStateOf<Uuid?>(null) }
    var morning by remember { mutableStateOf(CompanionSync.morning(context)) }
    var proactive by remember { mutableStateOf(CompanionSync.proactive(context)) }
    var phone by remember { mutableStateOf(CompanionSync.status(context)) }
    var server by remember { mutableStateOf<JsonObject?>(null) }
    var serverFail by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var trying by remember { mutableStateOf(false) }
    var tryResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val modelList = rememberModelListState(modelId, settings.providers, ModelType.CHAT)

    fun back() { if (hasInbox) onDone() else nav.popBackStack() }
    BackHandler { back() }

    suspend fun refreshStatus() {
        phone = CompanionSync.status(context)
        runCatching { CompanionSync.serverStatus(context) }
            .onSuccess { server = it; serverFail = null }
            .onFailure { serverFail = it.message ?: it::class.simpleName }
    }

    suspend fun activateAndSync() {
        val conv = chat.getCompanionConversation() ?: return
        CompanionSync.activate(context, conv.id, conv.assistantId)
        runCatching { ContextCompat.startForegroundService(context, Intent(context, CompanionReceiverService::class.java)) }
        runCatching { CompanionSync.sync(context) }
        refreshStatus()
    }

    LaunchedEffect(Unit) {
        modelId = chat.initializeCompanionConversation()?.config?.chatModelId
            ?: chat.getCompanionConversation()?.config?.chatModelId
        if (modelId != null) activateAndSync() else refreshStatus()
    }

    fun choose(model: Model) {
        scope.launch {
            working = true
            try {
                chat.updateCompanionModel(model.id)
                modelId = model.id
                toaster.show("模型已保存", ToastType.Success)
                if (Build.VERSION.SDK_INT >= 33 && !NotificationUtil.hasNotificationPermission(context)) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                activateAndSync()
            } catch (e: Exception) {
                toaster.show("模型保存失败：${e.message ?: e::class.simpleName}", ToastType.Error)
            } finally {
                working = false
            }
        }
    }

    fun setOptions() {
        CompanionSync.options(context, morning, proactive)
        scope.launch { runCatching { CompanionSync.sync(context) }; refreshStatus() }
    }

    val modelName = modelId?.let { settings.findModelById(it)?.displayName }
    val phoneOk = phone.startsWith("已同步")
    val serverModel = server?.get("model")?.jsonPrimitive?.contentOrNull.orEmpty()
    val serverErr = server?.get("last_error")?.jsonPrimitive?.contentOrNull.orEmpty()

    SnowBackdrop(snowing = trying || working) {
        Column(Modifier.fillMaxSize()) {
            SnowTopBar(
                title = "消息设置",
                sub = "SETTINGS · SNOW.LOG",
                leftIcon = HugeIcons.ArrowLeft01, onLeft = { back() },
                rightIcon = HugeIcons.Refresh01,
                onRight = { scope.launch { working = true; runCatching { CompanionSync.sync(context) }; refreshStatus(); working = false } },
            )
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
            ) {
                item {
                    Section("MODEL", "模型") {
                        SettingRow("聊天模型", modelName?.replace(Regex("\\[[^\\]]*\\]"), "")?.trim() ?: "未选择", arrow = true, enabled = !working) { modelList.open() }
                    }
                }
                item {
                    Section("PROACTIVE", "主动消息") {
                        SwitchRow("工作日早安", "8 点叫你起床，带西安天气", morning) { morning = it; setOptions() }
                        RowDivider()
                        SwitchRow("主动找我聊天", "不定时，你在忙就不打扰", proactive) { proactive = it; setOptions() }
                    }
                }
                item {
                    Section("STATUS", "连接") {
                        StatusRow("手机这边", if (phoneOk) "正常" else if (phone.startsWith("同步失败")) "失败" else "等待", phoneOk)
                        Note(phone, if (phoneOk) Snow.mute else Snow.error)
                        RowDivider()
                        val serverOk = serverFail == null && server?.get("enabled")?.jsonPrimitive?.booleanOrNull == true
                        StatusRow("服务器", if (serverFail != null) "连不上" else if (serverOk) "已开启" else "未开启", serverOk)
                        if (serverFail != null) Note(serverFail!!, Snow.error)
                        else {
                            Note("上次收到资料 ${fmtTime(server?.get("synced_at")?.jsonPrimitive?.doubleOrNull)}" +
                                (if (serverModel.isNotBlank()) " · ${serverModel.replace(Regex("\\[[^\\]]*\\]"), "").trim()}" else ""), Snow.mute)
                            Note("下次可能找你 ${fmtTime(server?.get("next_heartbeat")?.jsonPrimitive?.doubleOrNull)}", Snow.mute)
                            if (serverErr.isNotBlank()) Note(serverErr, Snow.error)
                        }
                    }
                }
                item {
                    Section("TRY", "试一试") {
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !trying && modelId != null) {
                                scope.launch {
                                    trying = true; tryResult = null
                                    tryResult = runCatching {
                                        chat.getCompanionConversation()?.let { CompanionSync.activate(context, it.id, it.assistantId) }
                                        CompanionSync.tryNow(context)
                                    }.fold({ true to "发过来了，回消息页看看" }, { false to (it.message ?: it::class.simpleName ?: "没发出来") })
                                    refreshStatus()
                                    trying = false
                                }
                            }.padding(horizontal = 16.dp, vertical = 15.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("现在让阿辞发一条", fontSize = 14.5.sp, color = Snow.ink, modifier = Modifier.weight(1f))
                            if (trying) {
                                MonoText("WRITING", size = 8.5f)
                                Spacer(Modifier.width(8.dp))
                                CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 1.5.dp, color = Snow.ink2)
                            } else Icon(HugeIcons.ArrowRight01, null, Modifier.size(16.dp), tint = Snow.faint)
                        }
                        tryResult?.let { (ok, msg) -> Note(msg, if (ok) Snow.ok else Snow.error) }
                    }
                }
            }
        }
    }
    ModelListSheet(state = modelList, onSelect = ::choose)
}

@Composable
private fun Section(tag: String, title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            MonoText(tag, size = 8.5f)
            Spacer(Modifier.width(8.dp))
            Text(title, fontSize = 12.5.sp, color = Snow.ink2, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(10.dp))
            DottedLine(Modifier.weight(1f))
        }
        Column(Modifier.fillMaxWidth().snowGlass(RoundedCornerShape(22.dp), strong = true, elevation = 2.dp).padding(vertical = 4.dp)) { content() }
    }
}

@Composable
private fun SettingRow(title: String, value: String, arrow: Boolean = false, enabled: Boolean = true, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 14.5.sp, color = Snow.ink)
        Spacer(Modifier.width(12.dp))
        Text(value, fontSize = 12.5.sp, color = Snow.mute, maxLines = 1, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        if (arrow) { Spacer(Modifier.width(6.dp)); Icon(HugeIcons.ArrowRight01, null, Modifier.size(16.dp), tint = Snow.faint) }
    }
}

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.5.sp, color = Snow.ink)
            Spacer(Modifier.height(2.dp))
            Text(sub, fontSize = 11.5.sp, color = Snow.mute)
        }
        val knob by animateDpAsState(if (checked) 20.dp else 2.dp, label = "knob")
        Box(
            Modifier.width(42.dp).height(24.dp).clip(RoundedCornerShape(50))
                .background(if (checked) Snow.dark else Color(0xFFE3E4E6))
        ) {
            Box(Modifier.offset(x = knob, y = 2.dp).size(20.dp).shadow(1.dp, CircleShape).clip(CircleShape).background(Color.White))
        }
    }
}

@Composable
private fun StatusRow(title: String, value: String, ok: Boolean) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 14.5.sp, color = Snow.ink, modifier = Modifier.weight(1f))
        Box(Modifier.size(7.dp).clip(CircleShape).background(if (ok) Snow.ok else Snow.error))
        Spacer(Modifier.width(6.dp))
        Text(value, fontSize = 12.5.sp, color = if (ok) Snow.ink2 else Snow.error)
    }
}

@Composable
private fun Note(text: String, color: Color) {
    Text(text, fontSize = 11.5.sp, lineHeight = 16.sp, color = color, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp))
}

@Composable
private fun RowDivider() {
    DottedLine(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
}
