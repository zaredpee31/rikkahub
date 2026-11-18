package me.rerere.rikkahub.ui.pages.setting

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceChip
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceDivider
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceSection
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.asr.ASRProviderSetting
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Mic01
import me.rerere.hugeicons.stroke.StopCircle
import me.rerere.hugeicons.stroke.VolumeHigh
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.DEFAULT_SYSTEM_TTS_ID
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.ItemAction
import me.rerere.rikkahub.ui.components.ui.ItemActionMenu
import me.rerere.rikkahub.ui.components.ui.longPressReorder
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceBackdrop
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceTopBar
import me.rerere.rikkahub.ui.pages.setting.components.ASRProviderConfigure
import me.rerere.rikkahub.ui.pages.setting.components.ProviderEditSheet
import me.rerere.rikkahub.ui.pages.setting.components.TTSProviderConfigure
import me.rerere.rikkahub.ui.pages.setting.components.typeName
import me.rerere.rikkahub.ui.theme.palaceColors
import me.rerere.tts.provider.TTSProviderSetting
import me.rerere.ui.components.RikkaConfirmDialog
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private enum class SpeechTab(
    val icon: ImageVector,
    @param:StringRes private val labelRes: Int,
) {
    TTS(HugeIcons.VolumeHigh, R.string.speech_tab_tts),
    ASR(HugeIcons.Mic01, R.string.speech_tab_asr);

    val label: String
        @Composable get() = stringResource(labelRes)
}

@Composable
fun SettingSpeechPage(vm: SettingVM = koinViewModel()) {
    val navController = LocalNavController.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    var editingTTSProvider by remember { mutableStateOf<TTSProviderSetting?>(null) }
    var editingASRProvider by remember { mutableStateOf<ASRProviderSetting?>(null) }
    var newTTSProvider by remember { mutableStateOf<TTSProviderSetting?>(null) }
    var newASRProvider by remember { mutableStateOf<ASRProviderSetting?>(null) }
    val pagerState = rememberPagerState { SpeechTab.entries.size }
    val scope = rememberCoroutineScope()

    PalaceBackdrop {
        Column(Modifier.fillMaxSize()) {
            PalaceTopBar(
                title = stringResource(R.string.speech_page_title),
                sub = "SPEECH",
                onBack = { navController.popBackStack() },
                rightIcon = HugeIcons.Add01,
                onRight = {
                    when (SpeechTab.entries[pagerState.targetPage]) {
                        SpeechTab.TTS -> newTTSProvider = TTSProviderSetting.SystemTTS()
                        SpeechTab.ASR -> newASRProvider = ASRProviderSetting.SiliconFlow()
                    }
                },
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SpeechTab.entries.forEachIndexed { index, tab ->
                    PalaceChip(
                        text = tab.label,
                        selected = pagerState.targetPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                when (SpeechTab.entries[page]) {
                    SpeechTab.TTS -> TTSProviderList(
                        settings = settings,
                        onUpdateSettings = vm::updateSettings,
                        onEdit = { editingTTSProvider = it },
                    )

                    SpeechTab.ASR -> ASRProviderList(
                        settings = settings,
                        onUpdateSettings = vm::updateSettings,
                        onEdit = { editingASRProvider = it },
                    )
                }
            }
        }
    }

    newTTSProvider?.let { provider ->
        ProviderEditSheet(
            title = stringResource(R.string.setting_tts_page_add_provider),
            initial = provider,
            confirmText = stringResource(R.string.setting_tts_page_add),
            onConfirm = { added ->
                vm.updateSettings { latest ->
                    latest.copy(ttsProviders = listOf(added) + latest.ttsProviders)
                }
            },
            onDismiss = { newTTSProvider = null },
        ) { value, onValueChange, modifier ->
            TTSProviderConfigure(setting = value, onValueChange = onValueChange, modifier = modifier)
        }
    }

    newASRProvider?.let { provider ->
        ProviderEditSheet(
            title = stringResource(R.string.setting_asr_page_add_provider),
            initial = provider,
            confirmText = stringResource(R.string.setting_tts_page_add),
            onConfirm = { added ->
                vm.updateSettings { latest ->
                    latest.copy(
                        asrProviders = listOf(added) + latest.asrProviders,
                        selectedASRProviderId = latest.selectedASRProviderId ?: added.id
                    )
                }
            },
            onDismiss = { newASRProvider = null },
        ) { value, onValueChange, modifier ->
            ASRProviderConfigure(setting = value, onValueChange = onValueChange, modifier = modifier)
        }
    }

    editingTTSProvider?.let { provider ->
        ProviderEditSheet(
            title = stringResource(R.string.setting_tts_page_edit_provider),
            initial = provider,
            confirmText = stringResource(R.string.chat_page_save),
            onConfirm = { edited ->
                vm.updateSettings { latest ->
                    latest.copy(ttsProviders = latest.ttsProviders.map { if (it.id == provider.id) edited else it })
                }
            },
            onDismiss = { editingTTSProvider = null },
        ) { value, onValueChange, modifier ->
            TTSProviderConfigure(setting = value, onValueChange = onValueChange, modifier = modifier)
        }
    }

    editingASRProvider?.let { provider ->
        ProviderEditSheet(
            title = stringResource(R.string.setting_asr_page_edit_provider),
            initial = provider,
            confirmText = stringResource(R.string.chat_page_save),
            onConfirm = { edited ->
                vm.updateSettings { latest ->
                    latest.copy(asrProviders = latest.asrProviders.map { if (it.id == provider.id) edited else it })
                }
            },
            onDismiss = { editingASRProvider = null },
        ) { value, onValueChange, modifier ->
            ASRProviderConfigure(setting = value, onValueChange = onValueChange, modifier = modifier)
        }
    }
}

@Composable
private fun TTSProviderList(
    settings: Settings,
    onUpdateSettings: ((Settings) -> Settings) -> Unit,
    onEdit: (TTSProviderSetting) -> Unit,
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val newProviders = settings.ttsProviders.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        onUpdateSettings { it.copy(ttsProviders = newProviders) }
    }
    var deleteTarget by remember { mutableStateOf<TTSProviderSetting?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        state = lazyListState
    ) {
        item(key = "section") {
            Column(Modifier.padding(bottom = 4.dp)) { PalaceSection("TEXT TO SPEECH", "文字转语音") }
        }
        itemsIndexed(settings.ttsProviders, key = { _, it -> it.id }) { index, provider ->
            ReorderableItem(
                state = reorderableState,
                key = provider.id
            ) { isDragging ->
                val isSelected = settings.selectedTTSProviderId == provider.id
                SpeechProviderItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(longPressReorder(isDragging)),
                    isFirst = index == 0,
                    isLast = index == settings.ttsProviders.lastIndex,
                    name = provider.name.ifEmpty { stringResource(R.string.setting_tts_page_default_name) },
                    typeName = provider.typeName,
                    isSelected = isSelected,
                    deletable = provider.id != DEFAULT_SYSTEM_TTS_ID,
                    onSelect = {
                        onUpdateSettings { it.copy(selectedTTSProviderId = provider.id) }
                    },
                    onEdit = {
                        onEdit(provider)
                    },
                    onDelete = {
                        deleteTarget = provider
                    },
                    action = if (isSelected) {
                        { TTSTestButton() }
                    } else null,
                )
            }
        }
    }

    RikkaConfirmDialog(
        show = deleteTarget != null,
        title = stringResource(R.string.confirm_delete),
        confirmText = stringResource(R.string.delete),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            deleteTarget?.let { target ->
                val newProviders = settings.ttsProviders.filter { it.id != target.id }
                val newSelectedId =
                    if (settings.selectedTTSProviderId == target.id) DEFAULT_SYSTEM_TTS_ID else settings.selectedTTSProviderId
                onUpdateSettings {
                    it.copy(
                        ttsProviders = newProviders,
                        selectedTTSProviderId = newSelectedId
                    )
                }
            }
            deleteTarget = null
        },
        onDismiss = { deleteTarget = null },
    ) {
        Text(stringResource(R.string.common_delete_confirm_message, deleteTarget?.name.orEmpty()))
    }
}

@Composable
private fun ASRProviderList(
    settings: Settings,
    onUpdateSettings: ((Settings) -> Settings) -> Unit,
    onEdit: (ASRProviderSetting) -> Unit,
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val newProviders = settings.asrProviders.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        onUpdateSettings { it.copy(asrProviders = newProviders) }
    }
    var deleteTarget by remember { mutableStateOf<ASRProviderSetting?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        state = lazyListState
    ) {
        item(key = "section") {
            Column(Modifier.padding(bottom = 4.dp)) { PalaceSection("SPEECH TO TEXT", "语音识别") }
        }
        itemsIndexed(settings.asrProviders, key = { _, it -> it.id }) { index, provider ->
            ReorderableItem(
                state = reorderableState,
                key = provider.id
            ) { isDragging ->
                SpeechProviderItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(longPressReorder(isDragging)),
                    isFirst = index == 0,
                    isLast = index == settings.asrProviders.lastIndex,
                    name = provider.name.ifEmpty { stringResource(R.string.setting_asr_page_default_name) },
                    typeName = provider.typeName,
                    isSelected = settings.selectedASRProviderId == provider.id,
                    onSelect = {
                        onUpdateSettings { it.copy(selectedASRProviderId = provider.id) }
                    },
                    onEdit = {
                        onEdit(provider)
                    },
                    onDelete = {
                        deleteTarget = provider
                    }
                )
            }
        }
    }

    RikkaConfirmDialog(
        show = deleteTarget != null,
        title = stringResource(R.string.confirm_delete),
        confirmText = stringResource(R.string.delete),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            deleteTarget?.let { target ->
                val newProviders = settings.asrProviders.filter { it.id != target.id }
                val newSelectedId =
                    if (settings.selectedASRProviderId == target.id) {
                        newProviders.firstOrNull()?.id
                    } else {
                        settings.selectedASRProviderId
                    }
                onUpdateSettings {
                    it.copy(
                        asrProviders = newProviders,
                        selectedASRProviderId = newSelectedId
                    )
                }
            }
            deleteTarget = null
        },
        onDismiss = { deleteTarget = null },
    ) {
        Text(stringResource(R.string.common_delete_confirm_message, deleteTarget?.name.orEmpty()))
    }
}

private val TTSProviderSetting.typeName: String
    @Composable get() = when (this) {
        is TTSProviderSetting.OpenAI -> stringResource(R.string.setting_tts_page_provider_openai)
        is TTSProviderSetting.Gemini -> stringResource(R.string.setting_tts_page_provider_gemini)
        is TTSProviderSetting.MiniMax -> "MiniMax"
        is TTSProviderSetting.SystemTTS -> stringResource(R.string.setting_tts_page_provider_system)
        is TTSProviderSetting.Qwen -> "Qwen"
        is TTSProviderSetting.Groq -> "Groq"
        is TTSProviderSetting.XAI -> "xAI"
        is TTSProviderSetting.MiMo -> "MiMo"
        is TTSProviderSetting.Step -> "Step"
        is TTSProviderSetting.ElevenLabs -> "ElevenLabs"
        is TTSProviderSetting.FishAudio -> "Fish Audio"
        is TTSProviderSetting.Volcengine -> "火山引擎"
    }

// TTS 和 ASR 列表共用的服务商行，点行编辑，点单选圈设为当前使用。
// 重排要求每行是独立的列表项，所以用首尾圆角加虚线拼成一张卡
@Composable
private fun SpeechProviderItem(
    name: String,
    typeName: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    isFirst: Boolean = true,
    isLast: Boolean = true,
    deletable: Boolean = true,
    // 跟在标签后面的操作，比如试听
    action: (@Composable () -> Unit)? = null,
) {
    val c = palaceColors()
    val shape = RoundedCornerShape(
        topStart = if (isFirst) 22.dp else 0.dp,
        topEnd = if (isFirst) 22.dp else 0.dp,
        bottomStart = if (isLast) 22.dp else 0.dp,
        bottomEnd = if (isLast) 22.dp else 0.dp,
    )

    Column(
        modifier
            .clip(shape)
            .background(if (isSelected) c.chip else c.glass)
    ) {
        if (!isFirst) PalaceDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit)
                .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(c.chip),
                contentAlignment = Alignment.Center,
            ) {
                AutoAIIcon(name = name, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    fontSize = 14.sp,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(text = typeName, fontSize = 11.5.sp, lineHeight = 16.sp, color = c.mute)
                if (isSelected) {
                    Spacer(Modifier.height(2.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.setting_tts_page_selected),
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            color = c.ink2,
                        )
                        action?.invoke()
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) c.accent else Color.Transparent)
                    .border(1.dp, if (isSelected) c.accent else c.faint, CircleShape)
                    .clickable(onClick = onSelect)
                    .padding(6.dp),
            ) {
                if (isSelected) Box(Modifier.fillMaxSize().clip(CircleShape).background(c.onAccent))
            }
            Spacer(Modifier.width(4.dp))
            ItemActionMenu(
                actions = listOf(
                    ItemAction(
                        text = stringResource(R.string.delete),
                        icon = HugeIcons.Delete01,
                        destructive = true,
                        enabled = deletable,
                        onClick = onDelete,
                    ),
                )
            )
        }
    }
}

// 用当前选中的 TTS 念一句测试文本，播放中再点一次停止
@Composable
private fun TTSTestButton() {
    val c = palaceColors()
    val tts = LocalTTSState.current
    val isSpeaking by tts.isSpeaking.collectAsState()
    val isAvailable by tts.isAvailable.collectAsState()
    if (!isAvailable) return

    val testText = stringResource(R.string.setting_tts_page_test_text)
    val tint = if (isSpeaking) c.error else c.ink2
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(c.chip)
            .clickable {
                if (!isSpeaking) {
                    tts.speak(testText)
                } else {
                    tts.stop()
                }
            }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isSpeaking) HugeIcons.StopCircle else HugeIcons.VolumeHigh,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = tint,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (isSpeaking) stringResource(R.string.stop) else stringResource(R.string.test_tts),
            fontSize = 12.sp,
            color = tint,
        )
    }
}
