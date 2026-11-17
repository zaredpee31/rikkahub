package me.rerere.rikkahub.ui.pages.setting

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ai.provider.ModelType
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AiBrain01
import me.rerere.hugeicons.stroke.AiEditing
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.hugeicons.stroke.BubbleChat
import me.rerere.hugeicons.stroke.ScanText
import me.rerere.hugeicons.stroke.Zap
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.ui.components.ai.ModelField
import me.rerere.rikkahub.ui.components.ai.ModelListSheet
import me.rerere.rikkahub.ui.components.ai.ModelListState
import me.rerere.rikkahub.ui.components.ai.ReasoningButton
import me.rerere.rikkahub.ui.components.ai.rememberModelListState
import me.rerere.rikkahub.ui.components.ui.PagerTab
import me.rerere.rikkahub.ui.components.ui.PagerTabs
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceBackdrop
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceDivider
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceGlass
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceSection
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceSwitchRow
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceTopBar
import me.rerere.rikkahub.ui.theme.palaceColors
import org.koin.androidx.compose.koinViewModel

private enum class ModelSettingTab(
    override val icon: ImageVector,
    @param:StringRes private val labelRes: Int,
) : PagerTab {
    MODEL(HugeIcons.AiBrain01, R.string.setting_model_page_tab_model),
    PROMPT(HugeIcons.AiEditing, R.string.setting_model_page_tab_prompt);

    override val label: String
        @Composable get() = stringResource(labelRes)
}

@Composable
fun SettingModelPage(vm: SettingVM = koinViewModel()) {
    val navController = LocalNavController.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState { ModelSettingTab.entries.size }

    PalaceBackdrop {
        Column(Modifier.fillMaxSize()) {
            PalaceTopBar(
                title = stringResource(R.string.setting_model_page_title),
                sub = "MODELS",
                onBack = { navController.popBackStack() },
            )
            PagerTabs(
                pagerState = pagerState,
                tabs = ModelSettingTab.entries,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            )

            val contentPadding = PaddingValues(
                start = 16.dp,
                top = 12.dp,
                end = 16.dp,
                bottom = 40.dp,
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                when (ModelSettingTab.entries[page]) {
                    ModelSettingTab.MODEL -> ModelSettingsPage(
                        settings = settings,
                        vm = vm,
                        contentPadding = contentPadding,
                    )

                    ModelSettingTab.PROMPT -> PromptSettingsPage(
                        settings = settings,
                        vm = vm,
                        contentPadding = contentPadding,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelSettingsPage(settings: Settings, vm: SettingVM, contentPadding: PaddingValues) {
    // 各项的选择状态先在这里建好，弹窗统一放在列表后面
    val chatModel = rememberModelListState(
        modelId = settings.chatModelId,
        providers = settings.providers,
        type = ModelType.CHAT,
    )
    val fastModel = rememberModelListState(
        modelId = settings.fastModelId,
        providers = settings.providers,
        type = ModelType.CHAT,
    )
    val memoryModel = rememberModelListState(
        modelId = settings.memoryModelId,
        providers = settings.providers,
        type = ModelType.CHAT,
    )
    val embeddingModel = rememberModelListState(
        modelId = settings.embeddingModelId,
        providers = settings.providers,
        type = ModelType.EMBEDDING,
    )
    val ocrModel = rememberModelListState(
        modelId = settings.ocrModelId,
        providers = settings.providers,
        type = ModelType.CHAT,
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PalaceSection("CHAT", stringResource(R.string.setting_model_page_chat_model))
        }
        item {
            PalaceGlass {
                ModelRow(
                    state = chatModel,
                    icon = HugeIcons.BubbleChat,
                    title = R.string.setting_model_page_chat_model,
                    description = R.string.setting_model_page_chat_model_desc,
                )
            }
        }
        item {
            PalaceSection("FAST", stringResource(R.string.setting_model_page_fast_model))
        }
        item {
            // 标题和聊天建议都由快速模型生成，相关的选项跟它放在一组
            PalaceGlass {
                ModelRow(
                    state = fastModel,
                    icon = HugeIcons.Zap,
                    title = R.string.setting_model_page_fast_model,
                    description = R.string.setting_model_page_fast_model_desc,
                )
                PalaceDivider()
                TrailingRow(title = stringResource(R.string.assistant_page_thinking_budget)) {
                    ReasoningButton(
                        reasoningLevel = settings.fastModelReasoningLevel,
                        onUpdateReasoningLevel = {
                            vm.updateSettings { latest -> latest.copy(fastModelReasoningLevel = it) }
                        },
                    )
                }
                PalaceDivider()
                PalaceSwitchRow(
                    title = stringResource(R.string.setting_model_page_enable_auto_title),
                    desc = null,
                    checked = settings.enableAutoTitle,
                    onChange = {
                        vm.updateSettings { latest -> latest.copy(enableAutoTitle = it) }
                    },
                )
                PalaceDivider()
                PalaceSwitchRow(
                    title = stringResource(R.string.setting_model_page_enable_suggestion),
                    desc = null,
                    checked = settings.enableSuggestion,
                    onChange = {
                        vm.updateSettings { latest -> latest.copy(enableSuggestion = it) }
                    },
                )
            }
        }
        item {
            PalaceSection("MEMORY", "记忆与向量")
        }
        item {
            PalaceGlass {
                // 不设置时跟着快速模型走，所以要能清掉
                ModelRow(
                    state = memoryModel,
                    icon = HugeIcons.Brain02,
                    title = R.string.setting_model_page_memory_model,
                    description = R.string.setting_model_page_memory_model_desc,
                    placeholder = R.string.setting_model_page_memory_model_follow_fast,
                    onClear = { vm.updateSettings { latest -> latest.copy(memoryModelId = null) } },
                )
                // 没设置时推理强度也跟着快速模型
                if (memoryModel.currentModel != null) {
                    PalaceDivider()
                    TrailingRow(title = stringResource(R.string.assistant_page_thinking_budget)) {
                        ReasoningButton(
                            reasoningLevel = settings.memoryModelReasoningLevel,
                            onUpdateReasoningLevel = {
                                vm.updateSettings { latest -> latest.copy(memoryModelReasoningLevel = it) }
                            },
                        )
                    }
                }
                PalaceDivider()
                // 没设置时节点照常保存，只是不按语义召回
                ModelRow(
                    state = embeddingModel,
                    icon = HugeIcons.AiBrain01,
                    title = R.string.setting_model_page_embedding_model,
                    description = R.string.setting_model_page_embedding_model_desc,
                    placeholder = R.string.setting_model_page_embedding_model_none,
                    onClear = { vm.updateSettings { latest -> latest.copy(embeddingModelId = null) } },
                )
            }
        }
        item {
            PalaceSection("OCR", stringResource(R.string.setting_model_page_ocr_model))
        }
        item {
            PalaceGlass {
                ModelRow(
                    state = ocrModel,
                    icon = HugeIcons.ScanText,
                    title = R.string.setting_model_page_ocr_model,
                    description = R.string.setting_model_page_ocr_model_desc,
                )
            }
        }
    }

    ModelListSheet(state = chatModel) {
        vm.updateSettings { latest -> latest.copy(chatModelId = it.id) }
    }
    ModelListSheet(state = fastModel) {
        vm.updateSettings { latest -> latest.copy(fastModelId = it.id) }
    }
    ModelListSheet(state = memoryModel) {
        vm.updateSettings { latest -> latest.copy(memoryModelId = it.id) }
    }
    ModelListSheet(state = embeddingModel) {
        vm.updateSettings { latest -> latest.copy(embeddingModelId = it.id) }
    }
    ModelListSheet(state = ocrModel) {
        vm.updateSettings { latest -> latest.copy(ocrModelId = it.id) }
    }
}

// 一项默认模型：圆形图标、用途说明，右边是显示当前模型的选择按钮
@Composable
private fun ModelRow(
    state: ModelListState,
    icon: ImageVector,
    @StringRes title: Int,
    @StringRes description: Int,
    @StringRes placeholder: Int = R.string.model_list_select_model,
    onClear: (() -> Unit)? = null,
) {
    val c = palaceColors()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(c.chip),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(16.dp), tint = c.ink2)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), fontSize = 14.sp, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(description),
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                color = c.mute,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        ModelField(
            model = state.currentModel,
            onClick = { state.open() },
            placeholder = stringResource(placeholder),
            onClear = onClear,
        )
    }
}

// 只有标题和右侧控件的一行，比如推理强度
@Composable
private fun TrailingRow(title: String, trailing: @Composable () -> Unit) {
    val c = palaceColors()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 14.sp, color = c.ink, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}
