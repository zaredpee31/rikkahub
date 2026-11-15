package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Alert01
import me.rerere.hugeicons.stroke.Icon1stBracket
import me.rerere.hugeicons.stroke.McpServer
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.ai.mcp.McpStatus
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.context.LocalNavController
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@Composable
fun AssistantMcpPage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(parameters = { parametersOf(id) })
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val mcpServerConfigs by vm.mcpServerConfigs.collectAsStateWithLifecycle()
    val navController = LocalNavController.current

    PalaceSubPage(
        title = stringResource(R.string.assistant_page_tab_mcp),
        sub = "MCP",
        onBack = { navController.popBackStack() },
    ) {
        val enabledServers = mcpServerConfigs.filter { it.commonOptions.enable }
        if (enabledServers.isEmpty()) {
            PalaceEmptyState(
                text = stringResource(R.string.setting_mcp_page_no_mcp_servers_found),
                icon = HugeIcons.McpServer,
                buttonText = stringResource(R.string.mcp_picker_title),
                onButton = { navController.navigate(Screen.SettingMcp) },
            )
        } else {
            PalaceGlass {
                enabledServers.forEachIndexed { index, server ->
                    if (index > 0) PalaceDivider()
                    McpServerRow(
                        assistant = assistant,
                        server = server,
                        servers = mcpServerConfigs,
                        onUpdateAssistant = { vm.update(it) },
                    )
                }
            }
        }
    }
}

/** 一行 MCP 服务器：左边状态图标，标题加连接状态，右边开关 */
@Composable
private fun McpServerRow(
    assistant: Assistant,
    server: McpServerConfig,
    servers: List<McpServerConfig>,
    onUpdateAssistant: (Assistant) -> Unit,
) {
    val c = palaceColors()
    val mcpManager = koinInject<McpManager>()
    val status by mcpManager.getStatus(server).collectAsStateWithLifecycle(McpStatus.Idle)
    val checked = server.id in assistant.mcpServers

    fun toggle(enabled: Boolean) {
        val newServers = assistant.mcpServers.toMutableSet()
        if (enabled) newServers.add(server.id) else newServers.remove(server.id)
        newServers.removeIf { id -> servers.none { it.id == id } }
        onUpdateAssistant(assistant.copy(mcpServers = newServers.toSet()))
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable { toggle(!checked) }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        McpStatusIcon(status)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(server.commonOptions.name, fontSize = 14.sp, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.size(2.dp))
            Text(mcpStatusText(status, server), fontSize = 11.5.sp, lineHeight = 16.sp, color = c.mute, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        PalaceSwitch(checked = checked, onChange = { toggle(it) })
    }
}

@Composable
private fun McpStatusIcon(status: McpStatus) {
    val c = palaceColors()
    when (status) {
        McpStatus.Idle -> StatusIcon(HugeIcons.Icon1stBracket, c.faint)
        McpStatus.Connected -> StatusIcon(HugeIcons.McpServer, c.ok)
        McpStatus.Connecting,
        is McpStatus.Reconnecting,
        McpStatus.Authorizing -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 1.5.dp, color = c.mute)
        is McpStatus.Error,
        McpStatus.NeedsAuthorization -> StatusIcon(HugeIcons.Alert01, c.error)
    }
}

@Composable
private fun StatusIcon(icon: ImageVector, tint: androidx.compose.ui.graphics.Color) {
    Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(16.dp), tint = tint)
    }
}

private fun mcpStatusText(status: McpStatus, server: McpServerConfig): String = when (status) {
    is McpStatus.Idle -> "Idle"
    is McpStatus.Connecting -> "Connecting"
    is McpStatus.Connected -> {
        val tools = server.commonOptions.tools
        val enabled = tools.count { it.enable }
        "Connected · $enabled/${tools.size} tools"
    }
    is McpStatus.Reconnecting -> "Reconnecting (${status.attempt}/${status.maxAttempts})"
    is McpStatus.Error -> "Error: ${status.message}"
    is McpStatus.NeedsAuthorization -> "Needs authorization"
    is McpStatus.Authorizing -> "Authorizing"
}
