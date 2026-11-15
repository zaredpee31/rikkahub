package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ai.provider.ModelType
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.ai.ModelListSheet
import me.rerere.rikkahub.ui.components.ai.ReasoningButton
import me.rerere.rikkahub.ui.components.ai.rememberModelListState
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.hooks.heroAnimation
import me.rerere.rikkahub.utils.toFixed
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToInt
import kotlin.uuid.Uuid

@Composable
fun AssistantBasicPage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(parameters = { parametersOf(id) })
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val providers by vm.providers.collectAsStateWithLifecycle()
    val workspaces by vm.workspaces.collectAsStateWithLifecycle()
    val navController = LocalNavController.current

    PalaceSubPage(
        title = stringResource(R.string.assistant_page_tab_basic),
        sub = "BASIC",
        onBack = { navController.popBackStack() },
    ) {
        AssistantBasicContent(
            assistant = assistant,
            providers = providers,
            workspaces = workspaces,
            onUpdate = { vm.update(it) },
        )
    }
}

@Composable
internal fun AssistantBasicContent(
    assistant: Assistant,
    providers: List<me.rerere.ai.provider.ProviderSetting>,
    workspaces: List<WorkspaceEntity>,
    onUpdate: (Assistant) -> Unit,
) {
    val c = palaceColors()
    val modelListState = rememberModelListState(
        modelId = assistant.chatModelId,
        providers = providers,
        type = ModelType.CHAT,
    )
    ModelListSheet(
        state = modelListState,
        onSelect = { onUpdate(assistant.copy(chatModelId = it.id)) },
    )
    var showWorkspaceDialog by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        UIAvatar(
            value = assistant.avatar,
            name = assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) },
            shape = CircleShape,
            onUpdate = { avatar -> onUpdate(assistant.copy(avatar = avatar)) },
            modifier = Modifier.size(96.dp).heroAnimation("assistant_${assistant.id}"),
        )
    }

    PalaceSection("PROFILE", "资料")
    PalaceGlass {
        PalaceInputRow(
            label = stringResource(R.string.assistant_page_name),
            value = assistant.name,
            onValueChange = { onUpdate(assistant.copy(name = it)) },
            singleLine = true,
        )
        PalaceDivider()
        PalaceNavRow(
            title = stringResource(R.string.assistant_page_workspace),
            desc = workspaces.find { it.id == assistant.workspaceId?.toString() }?.name
                ?: stringResource(R.string.workspace_no_binding),
            onClick = { showWorkspaceDialog = true },
        )
        PalaceDivider()
        PalaceSwitchRow(
            title = stringResource(R.string.assistant_page_use_assistant_avatar),
            desc = stringResource(R.string.assistant_page_use_assistant_avatar_desc),
            checked = assistant.useAssistantAvatar,
            onChange = { onUpdate(assistant.copy(useAssistantAvatar = it)) },
        )
    }

    PalaceSection("MODEL", "模型")
    PalaceGlass {
        PalaceNavRow(
            title = stringResource(R.string.assistant_page_chat_model),
            desc = modelListState.currentModel?.displayName ?: stringResource(R.string.assistant_page_chat_model_desc),
            onClick = { modelListState.open() },
        )
        PalaceDivider()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.assistant_page_thinking_budget), fontSize = 14.sp, color = c.ink, modifier = Modifier.weight(1f))
            ReasoningButton(
                reasoningLevel = assistant.reasoningLevel,
                onUpdateReasoningLevel = { level -> onUpdate(assistant.copy(reasoningLevel = level)) },
            )
        }
        PalaceDivider()
        PalaceSwitchRow(
            title = stringResource(R.string.assistant_page_stream_output),
            desc = stringResource(R.string.assistant_page_stream_output_desc),
            checked = assistant.streamOutput,
            onChange = { onUpdate(assistant.copy(streamOutput = it)) },
        )
    }

    PalaceSection("PARAMS", "参数")
    PalaceGlass {
        PalaceSwitchRow(
            title = stringResource(R.string.assistant_page_temperature),
            desc = stringResource(R.string.assistant_page_temperature_warning),
            checked = assistant.temperature != null,
            onChange = { enabled -> onUpdate(assistant.copy(temperature = if (enabled) 1.0f else null)) },
        )
        if (assistant.temperature != null) {
            var temperatureInput by remember(assistant.id) { mutableStateOf(assistant.temperature.toString()) }
            val temperatureValue = temperatureInput.toFloatOrNull()
            PalaceInputRow(
                label = "0 - 2",
                value = temperatureInput,
                onValueChange = { value ->
                    temperatureInput = value
                    value.toFloatOrNull()?.takeIf { it in 0f..2f }?.let { onUpdate(assistant.copy(temperature = it)) }
                },
                singleLine = true,
                decimal = true,
                isError = temperatureValue == null || temperatureValue !in 0f..2f,
            )
        }
        PalaceDivider()
        PalaceSwitchRow(
            title = stringResource(R.string.assistant_page_top_p),
            desc = stringResource(R.string.assistant_page_top_p_warning),
            checked = assistant.topP != null,
            onChange = { enabled -> onUpdate(assistant.copy(topP = if (enabled) 1.0f else null)) },
        )
        if (assistant.topP != null) {
            var topPInput by remember(assistant.id) { mutableStateOf(assistant.topP.toString()) }
            val topPValue = topPInput.toFloatOrNull()
            PalaceInputRow(
                label = "0 - 1",
                value = topPInput,
                onValueChange = { value ->
                    topPInput = value
                    value.toFloatOrNull()?.takeIf { it in 0f..1f }?.let { onUpdate(assistant.copy(topP = it)) }
                },
                singleLine = true,
                decimal = true,
                isError = topPValue == null || topPValue !in 0f..1f,
            )
        }
        PalaceDivider()
        var contextMessageLimitInput by remember(assistant.id, assistant.contextMessageLimit) {
            mutableStateOf(assistant.contextMessageLimit.toString())
        }
        var contextMessageLimitFocused by remember(assistant.id) { mutableStateOf(false) }
        PalaceInputRow(
            label = stringResource(R.string.assistant_page_context_message_limit),
            supporting = stringResource(R.string.assistant_page_context_message_limit_desc) + "\n" +
                stringResource(R.string.assistant_page_context_message_limit_hint),
            value = contextMessageLimitInput,
            onValueChange = { input ->
                if (input.all(Char::isDigit) && (input.isEmpty() || input.toIntOrNull() != null)) {
                    contextMessageLimitInput = input
                    input.toIntOrNull()
                        ?.takeIf { it != assistant.contextMessageLimit }
                        ?.let { onUpdate(assistant.copy(contextMessageLimit = it)) }
                }
            },
            singleLine = true,
            number = true,
            clearFocusOnDone = true,
            onFocusChanged = { focused ->
                if (contextMessageLimitFocused && !focused) {
                    val value = contextMessageLimitInput.toIntOrNull()
                    if (value == null) {
                        contextMessageLimitInput = assistant.contextMessageLimit.toString()
                    } else {
                        contextMessageLimitInput = value.toString()
                        if (value != assistant.contextMessageLimit) {
                            onUpdate(assistant.copy(contextMessageLimit = value))
                        }
                    }
                }
                contextMessageLimitFocused = focused
            },
        )
        if (assistant.contextMessageLimit > 0) {
            Text(
                text = stringResource(R.string.assistant_page_context_message_limit_warning),
                fontSize = 11.5.sp,
                color = c.error,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
            )
        }
        PalaceDivider()
        PalaceInputRow(
            label = stringResource(R.string.assistant_page_max_tokens),
            supporting = stringResource(R.string.assistant_page_max_tokens_desc) + "\n" +
                if (assistant.maxTokens != null) {
                    stringResource(R.string.assistant_page_max_tokens_limit, assistant.maxTokens)
                } else {
                    stringResource(R.string.assistant_page_max_tokens_no_token_limit)
                },
            value = assistant.maxTokens?.toString() ?: "",
            onValueChange = { text ->
                val tokens = if (text.isBlank()) null else text.toIntOrNull()?.takeIf { it > 0 }
                onUpdate(assistant.copy(maxTokens = tokens))
            },
            singleLine = true,
            number = true,
            placeholder = stringResource(R.string.assistant_page_max_tokens_no_limit),
        )
    }

    PalaceSection("CHAT", "聊天")
    PalaceGlass {
        PalaceSwitchRow(
            title = "参考历史聊天记录",
            desc = "允许助手在过去的对话中搜索相关信息，需要模型支持工具调用",
            checked = assistant.enableRecentChatsReference,
            onChange = { onUpdate(assistant.copy(enableRecentChatsReference = it)) },
        )
        PalaceDivider()
        PalaceSwitchRow(
            title = "时间提醒",
            desc = "隔一段时间再发消息时，在消息里带上当前时间",
            checked = assistant.enableTimeReminder,
            onChange = { onUpdate(assistant.copy(enableTimeReminder = it)) },
        )
        if (assistant.enableTimeReminder) {
            var intervalInput by remember(assistant.id) { mutableStateOf(assistant.timeReminderIntervalMinutes.toString()) }
            val interval = intervalInput.toIntOrNull()?.takeIf { it > 0 }
            PalaceDivider()
            PalaceInputRow(
                label = "提醒间隔（分钟）",
                supporting = "距离上次发消息超过这个时间，才在消息里带上当前时间",
                value = intervalInput,
                onValueChange = { input ->
                    intervalInput = input.filter(Char::isDigit)
                    intervalInput.toIntOrNull()?.takeIf { it > 0 }?.let {
                        onUpdate(assistant.copy(timeReminderIntervalMinutes = it))
                    }
                },
                singleLine = true,
                number = true,
                isError = interval == null,
            )
        }
    }

    PalaceSection("LOOK", "外观")
    PalaceGlass {
        PalaceSwitchRow(
            title = stringResource(R.string.assistant_page_gradient_background),
            desc = stringResource(R.string.assistant_page_gradient_background_desc),
            checked = assistant.useGradientBackground,
            onChange = { onUpdate(assistant.copy(useGradientBackground = it)) },
        )
        if (!assistant.useGradientBackground) {
            PalaceDivider()
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(stringResource(R.string.assistant_page_chat_background), fontSize = 14.sp, color = c.ink)
                Spacer(Modifier.size(2.dp))
                Text(stringResource(R.string.assistant_page_chat_background_desc), fontSize = 11.5.sp, color = c.mute)
                Spacer(Modifier.size(6.dp))
                BackgroundPicker(
                    background = assistant.background,
                    backgroundOpacity = assistant.backgroundOpacity,
                    onUpdate = { background -> onUpdate(assistant.copy(background = background)) },
                )
            }
        }
        if (!assistant.useGradientBackground && assistant.background != null) {
            val backgroundOpacity = assistant.backgroundOpacity.coerceIn(0f, 1f)
            PalaceDivider()
            PalaceSliderRow(
                title = stringResource(R.string.assistant_page_background_opacity),
                desc = stringResource(R.string.assistant_page_background_opacity_desc),
                valueText = stringResource(R.string.assistant_page_background_opacity_value, (backgroundOpacity * 100).roundToInt()),
                value = backgroundOpacity,
                onValueChange = {
                    onUpdate(assistant.copy(backgroundOpacity = it.toFixed(2).toFloatOrNull()?.coerceIn(0f, 1f) ?: 1.0f))
                },
                valueRange = 0f..1f,
            )
        }
    }

    if (showWorkspaceDialog) {
        val selected = workspaces.find { it.id == assistant.workspaceId?.toString() }
        PalaceDialog(
            title = stringResource(R.string.assistant_page_workspace),
            onDismiss = { showWorkspaceDialog = false },
            confirmText = null,
        ) {
            Text(stringResource(R.string.assistant_page_workspace_desc), fontSize = 11.5.sp, color = c.mute)
            WorkspaceOption(
                name = stringResource(R.string.workspace_no_binding),
                selected = selected == null,
                onClick = {
                    onUpdate(assistant.copy(workspaceId = null))
                    showWorkspaceDialog = false
                },
            )
            workspaces.forEach { workspace ->
                WorkspaceOption(
                    name = workspace.name,
                    selected = workspace.id == selected?.id,
                    onClick = {
                        onUpdate(assistant.copy(workspaceId = Uuid.parse(workspace.id)))
                        showWorkspaceDialog = false
                    },
                )
            }
        }
    }
}

@Composable
private fun WorkspaceOption(name: String, selected: Boolean, onClick: () -> Unit) {
    val c = palaceColors()
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, fontSize = 14.sp, color = if (selected) c.ink else c.ink2, modifier = Modifier.weight(1f))
        if (selected) Text("✓", fontSize = 14.sp, color = c.ink)
    }
}
