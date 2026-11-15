package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.transformers.DefaultPlaceholderProvider
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantAffectScope
import me.rerere.rikkahub.data.model.AssistantRegex
import me.rerere.rikkahub.ui.components.ui.TextArea
import me.rerere.rikkahub.ui.components.ui.longPressReorder
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.utils.insertAtCursor
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import sh.calvin.reorderable.ReorderableColumn
import sh.calvin.reorderable.ReorderableItem
import kotlin.uuid.Uuid
import me.rerere.ui.components.RikkaConfirmDialog

@Composable
fun AssistantPromptPage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(parameters = { parametersOf(id) })
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val navController = LocalNavController.current

    PalaceSubPage(
        title = stringResource(R.string.assistant_page_tab_prompt),
        sub = "PROMPT",
        onBack = { navController.popBackStack() },
    ) {
        AssistantPromptContent(assistant = assistant, onUpdate = { vm.update(it) })
    }
}

@Composable
private fun AssistantPromptContent(
    assistant: Assistant,
    onUpdate: (Assistant) -> Unit,
) {
    val c = palaceColors()

    PalaceSection("SYSTEM", stringResource(R.string.assistant_page_system_prompt))
    PalaceGlass {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val systemPromptValue = rememberTextFieldState(initialText = assistant.systemPrompt)
            LaunchedEffect(Unit) {
                snapshotFlow { systemPromptValue.text }.collect {
                    onUpdate(assistant.copy(systemPrompt = it.toString()))
                }
            }

            TextArea(
                state = systemPromptValue,
                label = stringResource(R.string.assistant_page_system_prompt),
                minLines = 5,
                maxLines = 10,
            )

            Text(
                text = stringResource(R.string.assistant_page_available_variables),
                fontSize = 11.5.sp,
                color = c.mute,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DefaultPlaceholderProvider.placeholders.forEach { (k, info) ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(c.chip)
                            .clickable { systemPromptValue.insertAtCursor("{{$k}}") }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CompositionLocalProvider(LocalContentColor provides c.ink2) {
                            ProvideTextStyle(TextStyle(fontSize = 12.sp)) {
                                info.displayName()
                                Text(": {{$k}}")
                            }
                        }
                    }
                }
            }
        }
        PalaceDivider()
        PalaceSwitchRow(
            title = stringResource(R.string.assistant_page_allow_conversation_system_prompt),
            desc = stringResource(R.string.assistant_page_allow_conversation_system_prompt_desc),
            checked = assistant.allowConversationSystemPrompt,
            onChange = { onUpdate(assistant.copy(allowConversationSystemPrompt = it)) },
        )
    }

    PalaceSection(
        tag = "REGEX",
        title = stringResource(R.string.assistant_page_regex_title),
        trailing = {
            PalaceRoundIcon(HugeIcons.Add01, size = 28.dp) {
                onUpdate(assistant.copy(regexes = assistant.regexes + AssistantRegex(id = Uuid.random())))
            }
        },
    )
    Text(
        text = stringResource(R.string.assistant_page_regex_desc),
        fontSize = 11.5.sp,
        lineHeight = 16.sp,
        color = c.mute,
        modifier = Modifier.padding(horizontal = 6.dp).padding(bottom = 8.dp),
    )
    var expandedIds by remember { mutableStateOf(emptySet<Uuid>()) }
    ReorderableColumn(
        list = assistant.regexes,
        onSettle = { fromIndex, toIndex ->
            val regexes = assistant.regexes.toMutableList().apply {
                add(toIndex, removeAt(fromIndex))
            }
            onUpdate(assistant.copy(regexes = regexes))
        },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { index, regex, isDragging ->
        key(regex.id) {
            ReorderableItem(modifier = Modifier.fillMaxWidth()) {
                val expanded = regex.id in expandedIds
                AssistantRegexCard(
                    regex = regex,
                    onUpdate = onUpdate,
                    assistant = assistant,
                    index = index,
                    expanded = expanded,
                    onExpandedChange = {
                        expandedIds = if (it) expandedIds + regex.id else expandedIds - regex.id
                    },
                    // 展开后内部是输入框，长按拖拽会与文本选择冲突
                    modifier = longPressReorder(
                        isDragging = isDragging,
                        enabled = !expanded && assistant.regexes.size > 1,
                    ),
                )
            }
        }
    }
}

@Composable
private fun AssistantRegexCard(
    regex: AssistantRegex,
    onUpdate: (Assistant) -> Unit,
    assistant: Assistant,
    index: Int,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = palaceColors()
    var showDeleteDialog by remember { mutableStateOf(false) }

    fun updateRegex(transform: (AssistantRegex) -> AssistantRegex) {
        onUpdate(
            assistant.copy(
                regexes = assistant.regexes.mapIndexed { i, reg ->
                    if (i == index) transform(reg) else reg
                },
            ),
        )
    }

    PalaceGlass(modifier = modifier) {
        Column(
            Modifier.animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .clickable { onExpandedChange(!expanded) }
                    .padding(start = 14.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val arrowRotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f)
                Icon(
                    imageVector = HugeIcons.ArrowDown01,
                    contentDescription = null,
                    tint = c.mute,
                    modifier = Modifier.padding(end = 10.dp).size(16.dp).rotate(arrowRotation),
                )
                Text(
                    text = regex.name.ifBlank { stringResource(R.string.extension_content_unnamed) },
                    fontSize = 14.sp,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                PalaceSwitch(
                    checked = regex.enabled,
                    onChange = { enabled -> updateRegex { it.copy(enabled = enabled) } },
                )
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { showDeleteDialog = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(HugeIcons.Delete01, stringResource(R.string.delete), Modifier.size(15.dp), tint = c.mute)
                }
            }

            if (expanded) {
                PalaceDivider()
                Column(
                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PalaceField(
                        value = regex.name,
                        onValueChange = { name -> updateRegex { it.copy(name = name) } },
                        label = stringResource(R.string.assistant_page_regex_name),
                        singleLine = true,
                    )
                    PalaceField(
                        value = regex.findRegex,
                        onValueChange = { findRegex -> updateRegex { it.copy(findRegex = findRegex.trim()) } },
                        label = stringResource(R.string.assistant_page_regex_find_regex),
                        placeholder = "e.g., \\b\\w+@\\w+\\.\\w+\\b",
                        singleLine = true,
                    )
                    PalaceField(
                        value = regex.replaceString,
                        onValueChange = { replaceString -> updateRegex { it.copy(replaceString = replaceString) } },
                        label = stringResource(R.string.assistant_page_regex_replace_string),
                        placeholder = "e.g., [EMAIL]",
                        singleLine = true,
                    )

                    Text(
                        text = stringResource(R.string.assistant_page_regex_affecting_scopes),
                        fontSize = 12.sp,
                        color = c.mute,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AssistantAffectScope.entries.forEach { scope ->
                            val selected = scope in regex.affectingScope
                            PalaceChip(
                                text = scope.name.lowercase().replaceFirstChar { it.uppercase() },
                                selected = selected,
                                onClick = {
                                    val newScopes = if (selected) regex.affectingScope - scope else regex.affectingScope + scope
                                    updateRegex { it.copy(affectingScope = newScopes) }
                                },
                            )
                        }
                    }

                    PalaceSwitchRow(
                        title = stringResource(R.string.assistant_page_regex_visual_only),
                        desc = null,
                        checked = regex.visualOnly,
                        onChange = { visualOnly -> updateRegex { it.copy(visualOnly = visualOnly) } },
                    )
                }
            }
        }
    }

    RikkaConfirmDialog(
        show = showDeleteDialog,
        title = stringResource(R.string.confirm_delete),
        confirmText = stringResource(R.string.delete),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            showDeleteDialog = false
            onUpdate(assistant.copy(regexes = assistant.regexes.filter { it.id != regex.id }))
        },
        onDismiss = { showDeleteDialog = false },
    ) {
        Text(stringResource(R.string.common_delete_confirm_message, regex.name))
    }
}
