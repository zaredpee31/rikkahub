package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.hugeicons.stroke.Code
import me.rerere.hugeicons.stroke.McpServer
import me.rerere.hugeicons.stroke.Message02
import me.rerere.hugeicons.stroke.Puzzle
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Wrench01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.hooks.heroAnimation
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import me.rerere.rikkahub.ui.theme.palaceColors

@Composable
fun AssistantDetailPage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(parameters = { parametersOf(id) })
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val name = assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) }

    PalaceBackdrop {
        Column(Modifier.fillMaxSize()) {
            PalaceTopBar(
                title = name,
                sub = "ASSISTANT",
                onBack = { navController.popBackStack() },
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AssistantHeader(assistant = assistant, name = name)

                PalaceSection("BASIC", "设定")
                PalaceGlass {
                    DetailNavRow(
                        icon = HugeIcons.Settings03,
                        title = stringResource(R.string.assistant_page_tab_basic),
                        desc = stringResource(R.string.assistant_detail_basic_desc),
                        onClick = { navController.navigate(Screen.AssistantBasic(id)) },
                    )
                    PalaceDivider()
                    DetailNavRow(
                        icon = HugeIcons.Message02,
                        title = stringResource(R.string.assistant_page_tab_prompt),
                        desc = stringResource(R.string.assistant_detail_prompt_desc),
                        onClick = { navController.navigate(Screen.AssistantPrompt(id)) },
                    )
                }

                PalaceSection("EXTEND", "能力")
                PalaceGlass {
                    DetailNavRow(
                        icon = HugeIcons.Puzzle,
                        title = stringResource(R.string.assistant_page_tab_extensions),
                        desc = stringResource(R.string.assistant_detail_extensions_desc),
                        onClick = { navController.navigate(Screen.AssistantInjections(id)) },
                    )
                    PalaceDivider()
                    DetailNavRow(
                        icon = HugeIcons.Brain02,
                        title = stringResource(R.string.assistant_page_tab_memory),
                        desc = stringResource(R.string.assistant_detail_memory_desc),
                        onClick = { navController.navigate(Screen.AssistantMemory(id)) },
                    )
                    PalaceDivider()
                    DetailNavRow(
                        icon = HugeIcons.McpServer,
                        title = stringResource(R.string.assistant_page_tab_mcp),
                        desc = stringResource(R.string.assistant_detail_mcp_desc),
                        onClick = { navController.navigate(Screen.AssistantMcp(id)) },
                    )
                    PalaceDivider()
                    DetailNavRow(
                        icon = HugeIcons.Wrench01,
                        title = stringResource(R.string.assistant_page_tab_local_tools),
                        desc = stringResource(R.string.assistant_detail_local_tools_desc),
                        onClick = { navController.navigate(Screen.AssistantLocalTool(id)) },
                    )
                }

                PalaceSection("REQUEST", "请求")
                PalaceGlass {
                    DetailNavRow(
                        icon = HugeIcons.Code,
                        title = stringResource(R.string.assistant_page_tab_request),
                        desc = stringResource(R.string.assistant_detail_request_desc),
                        onClick = { navController.navigate(Screen.AssistantRequest(id)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistantHeader(assistant: Assistant, name: String) {
    val c = palaceColors()
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        UIAvatar(
            value = assistant.avatar,
            name = name,
            shape = CircleShape,
            onUpdate = null,
            modifier = Modifier
                .size(96.dp)
                .heroAnimation("assistant_${assistant.id}")
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = name,
            fontSize = 22.sp,
            color = c.ink,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (assistant.systemPrompt.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = assistant.systemPrompt.take(100),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = c.mute,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}

/** 详情页的入口行：左边圆形图标，标题加说明，右边箭头。
 * [leading] 传了就替换左边的圆形图标（加号面板里网络搜索要显示服务图标时用）。
 * [trailing] 传了就替换右边的箭头。 */
@Composable
internal fun DetailNavRow(
    icon: ImageVector,
    title: String,
    desc: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = palaceColors()
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(c.chip),
            contentAlignment = Alignment.Center,
        ) {
            if (leading != null) leading() else Icon(icon, null, Modifier.size(16.dp), tint = c.ink2)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!desc.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(desc, fontSize = 11.5.sp, lineHeight = 16.sp, color = c.mute, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        if (trailing != null) trailing() else Icon(HugeIcons.ArrowRight01, null, Modifier.size(14.dp), tint = c.faint)
    }
}
