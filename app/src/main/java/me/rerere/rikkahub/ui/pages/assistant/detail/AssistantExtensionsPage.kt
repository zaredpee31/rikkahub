package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Book01
import me.rerere.hugeicons.stroke.MagicWand01
import me.rerere.hugeicons.stroke.Puzzle
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.components.ui.ExtensionTab
import me.rerere.rikkahub.ui.context.LocalNavController
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun AssistantExtensionsPage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(parameters = { parametersOf(id) })
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val skills by vm.skills.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    var page by rememberSaveable { mutableIntStateOf(0) }

    // 标题栏右侧的「管理」图标：和美化前每个分页底部的「管理」按钮去向一致。
    // 模式注入、世界书 -> 提示词页（导入、新建都在那里）；Skills -> Skills 管理页
    val onManage = {
        navController.navigate(if (page == 2) Screen.Skills else Screen.Prompts)
    }

    PalaceBackdrop {
        Column(Modifier.fillMaxSize()) {
            PalaceTopBar(
                title = stringResource(R.string.assistant_extensions_page_title),
                sub = "EXTEND",
                onBack = { navController.popBackStack() },
                rightIcon = HugeIcons.Settings03,
                onRight = onManage,
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 12.dp),
                ) {
                    ExtensionTab.entries.forEachIndexed { index, tab ->
                        PalaceChip(
                            text = tab.label,
                            selected = page == index,
                            onClick = { page = index },
                        )
                    }
                }

                when (page) {
                    0 -> ExtensionToggleList(
                        items = settings.modeInjections,
                        emptyIcon = HugeIcons.MagicWand01,
                        emptyMessage = stringResource(R.string.assistant_extensions_page_empty_mode_injections),
                        buttonText = stringResource(R.string.assistant_extensions_page_goto_prompts),
                        onAction = { navController.navigate(Screen.Prompts) },
                        title = { it.name.ifBlank { stringResource(R.string.extension_content_unnamed) } },
                        desc = { null },
                        checked = { assistant.modeInjectionIds.contains(it.id) },
                        onOpen = { navController.navigate(Screen.Prompts) },
                        onToggle = { injection, checked ->
                            val newIds = if (checked) assistant.modeInjectionIds + injection.id else assistant.modeInjectionIds - injection.id
                            vm.update(assistant.copy(modeInjectionIds = newIds))
                        },
                    )

                    1 -> ExtensionToggleList(
                        items = settings.lorebooks,
                        emptyIcon = HugeIcons.Book01,
                        emptyMessage = stringResource(R.string.assistant_extensions_page_empty_lorebooks),
                        buttonText = stringResource(R.string.assistant_extensions_page_goto_prompts),
                        onAction = { navController.navigate(Screen.Prompts) },
                        title = { it.name.ifBlank { stringResource(R.string.extension_content_unnamed_lorebook) } },
                        desc = { it.description.ifBlank { null } },
                        checked = { assistant.lorebookIds.contains(it.id) },
                        onOpen = { navController.navigate(Screen.Prompts) },
                        onToggle = { lorebook, checked ->
                            val newIds = if (checked) assistant.lorebookIds + lorebook.id else assistant.lorebookIds - lorebook.id
                            vm.update(assistant.copy(lorebookIds = newIds))
                        },
                    )

                    2 -> ExtensionToggleList(
                        items = skills,
                        emptyIcon = HugeIcons.Puzzle,
                        emptyMessage = stringResource(R.string.assistant_extensions_page_empty_skills),
                        buttonText = stringResource(R.string.assistant_extensions_page_goto_extensions),
                        onAction = { navController.navigate(Screen.Skills) },
                        title = { it.name },
                        desc = { it.description.ifBlank { null } },
                        checked = { assistant.enabledSkills.contains(it.name) },
                        onOpen = { navController.navigate(Screen.SkillDetail(it.name)) },
                        onToggle = { skill, checked ->
                            val newSkills = if (checked) assistant.enabledSkills + skill.name else assistant.enabledSkills - skill.name
                            vm.update(assistant.copy(enabledSkills = newSkills))
                        },
                    )
                }
            }
        }
    }
}

/** 扩展页的开关列表：空时给一句说明和跳转按钮，有内容时放进一块磨砂面板 */
@Composable
private fun <T> ExtensionToggleList(
    items: List<T>,
    emptyIcon: androidx.compose.ui.graphics.vector.ImageVector,
    emptyMessage: String,
    buttonText: String,
    onAction: () -> Unit,
    title: @Composable (T) -> String,
    desc: @Composable (T) -> String?,
    checked: (T) -> Boolean,
    onToggle: (T, Boolean) -> Unit,
    onOpen: (T) -> Unit,
) {
    if (items.isEmpty()) {
        PalaceEmptyState(text = emptyMessage, icon = emptyIcon, buttonText = buttonText, onButton = onAction)
        return
    }
    PalaceGlass {
        items.forEachIndexed { index, item ->
            if (index > 0) PalaceDivider()
            // 整行点击切换开关（和美化前一致），右侧小箭头进入条目所在的管理页
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    PalaceCheckRow(
                        title = title(item),
                        desc = desc(item),
                        checked = checked(item),
                        onChange = { onToggle(item, it) },
                    )
                }
                PalaceRoundIcon(HugeIcons.ArrowRight01, size = 30.dp, onClick = { onOpen(item) })
                Spacer(Modifier.width(12.dp))
            }
        }
    }
}
