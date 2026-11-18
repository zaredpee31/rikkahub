package me.rerere.rikkahub.ui.components.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastFilter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Alert01
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.Icon1stBracket
import me.rerere.hugeicons.stroke.McpServer
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.ai.mcp.McpStatus
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.pages.assistant.detail.DetailNavRow
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceGlass
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceSwitch
import me.rerere.rikkahub.ui.theme.palaceColors
import me.rerere.ui.components.ToggleSurface
import org.koin.compose.koinInject

@Composable
fun McpPickerButton(
    assistant: Assistant,
    servers: List<McpServerConfig>,
    mcpManager: McpManager,
    modifier: Modifier = Modifier,
    onUpdateAssistant: (Assistant) -> Unit
) {
    var showMcpPicker by remember { mutableStateOf(false) }
    val status by mcpManager.syncingStatus.collectAsStateWithLifecycle()
    val loading = status.values.any { it == McpStatus.Connecting }
    val enabledServers = servers.fastFilter {
        it.commonOptions.enable && assistant.mcpServers.contains(it.id)
    }
    ToggleSurface(
        modifier = modifier,
        checked = assistant.mcpServers.isNotEmpty(),
        onClick = {
            showMcpPicker = true
        }
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 8.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    BadgedBox(
                        badge = {
                            if (enabledServers.isNotEmpty()) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                ) {
                                    Text(text = enabledServers.size.toString())
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = HugeIcons.McpServer,
                            contentDescription = stringResource(R.string.mcp_picker_title),
                        )
                    }

                }
            }
        }
    }
    if (showMcpPicker) {
        val c = palaceColors()
        ModalBottomSheet(
            onDismissRequest = { showMcpPicker = false },
            sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
            containerColor = c.bgTop,
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        ) {
            Column(
                modifier = Modifier.Companion
                    .fillMaxWidth()
                    .fillMaxHeight(0.7f)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.mcp_picker_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = c.ink,
                )
                AnimatedVisibility(loading) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        LinearWavyProgressIndicator()
                        Text(
                            text = stringResource(id = R.string.mcp_picker_syncing),
                            fontSize = 14.sp,
                            color = c.ink,
                        )
                    }
                }
                McpPicker(
                    assistant = assistant,
                    servers = servers,
                    onUpdateAssistant = {
                        onUpdateAssistant(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }
}

@Composable
fun McpPickerListItem(
    assistant: Assistant,
    servers: List<McpServerConfig>,
    mcpManager: McpManager,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val status by mcpManager.syncingStatus.collectAsStateWithLifecycle()
    val loading = status.values.any { it == McpStatus.Connecting }
    val enabledServers = servers.fastFilter {
        it.commonOptions.enable && assistant.mcpServers.contains(it.id)
    }

    DetailNavRow(
        icon = HugeIcons.McpServer,
        title = stringResource(R.string.mcp_picker_title),
        desc = null,
        onClick = onClick,
        modifier = modifier,
        leading = if (loading) {
            { CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp) }
        } else null,
        trailing = { if (enabledServers.isNotEmpty()) CountBadge(count = enabledServers.size) },
    )
}

// MCP 选择页，作为子页面嵌在加号 sheet 里
@Composable
internal fun McpPickerPage(
    assistant: Assistant,
    servers: List<McpServerConfig>,
    mcpManager: McpManager,
    onUpdateAssistant: (Assistant) -> Unit,
    onBack: () -> Unit,
) {
    val status by mcpManager.syncingStatus.collectAsStateWithLifecycle()
    val loading = status.values.any { it == McpStatus.Connecting }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.7f)
            .padding(horizontal = 16.dp),
    ) {
        SheetHeader(
            title = stringResource(R.string.mcp_picker_title),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(HugeIcons.ArrowLeft01, contentDescription = stringResource(R.string.back))
                }
            },
        )
        AnimatedVisibility(
            visible = loading,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                LinearWavyProgressIndicator()
                Text(
                    text = stringResource(id = R.string.mcp_picker_syncing),
                    fontSize = 14.sp,
                    color = palaceColors().ink,
                )
            }
        }
        McpPicker(
            assistant = assistant,
            servers = servers,
            onUpdateAssistant = onUpdateAssistant,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp),
        )
    }
}

@Composable
fun McpPicker(
    assistant: Assistant,
    servers: List<McpServerConfig>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    colors: ListItemColors = ListItemDefaults.segmentedColors(),
    onUpdateAssistant: (Assistant) -> Unit
) {
    val mcpManager = koinInject<McpManager>()
    val c = palaceColors()
    val enabledServers = servers.fastFilter { it.commonOptions.enable }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(enabledServers, key = { server -> server.id }) { server ->
            val status by mcpManager.getStatus(server).collectAsStateWithLifecycle(McpStatus.Idle)
            val checked = server.id in assistant.mcpServers
            val statusText = when (val s = status) {
                is McpStatus.Idle -> "Idle"
                is McpStatus.Connecting -> "Connecting"
                is McpStatus.Connected -> {
                    val tools = server.commonOptions.tools
                    "Connected · ${tools.fastFilter { it.enable }.size}/${tools.size} tools"
                }
                is McpStatus.Reconnecting -> "Reconnecting (${s.attempt}/${s.maxAttempts})"
                is McpStatus.Error -> "Error: ${s.message}"
                is McpStatus.NeedsAuthorization -> "Needs authorization"
                is McpStatus.Authorizing -> "Authorizing"
            }
            val isBusy = status == McpStatus.Connecting ||
                status is McpStatus.Reconnecting ||
                status == McpStatus.Authorizing
            val isError = status is McpStatus.Error || status == McpStatus.NeedsAuthorization
            val onChange = { on: Boolean ->
                val newServers = assistant.mcpServers.toMutableSet()
                if (on) newServers.add(server.id) else newServers.remove(server.id)
                newServers.removeIf { servers.none { s -> s.id == server.id } } // remove invalid servers
                onUpdateAssistant(
                    assistant.copy(
                        mcpServers = newServers.toSet()
                    )
                )
            }
            PalaceGlass {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onChange(!checked) }
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (checked && !isBusy && !isError) c.accent else c.chip),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            // 连接中、重连中、授权中：转圈
                            isBusy -> CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = c.ink2,
                            )
                            // 出错、需要授权：红色警告图标
                            isError -> Icon(
                                imageVector = HugeIcons.Alert01,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = c.error,
                            )
                            else -> Icon(
                                imageVector = if (status == McpStatus.Connected) HugeIcons.McpServer else HugeIcons.Icon1stBracket,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = if (checked) c.onAccent else c.ink2,
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = server.commonOptions.name,
                            fontSize = 14.sp,
                            color = c.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = statusText,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            color = c.mute,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    PalaceSwitch(checked = checked, onChange = onChange)
                }
            }
        }
    }
}

/** 不带按钮的 MCP 选择弹层，给消息页这种自己画按钮的地方用；内容和普通窗口完全一样。 */
@Composable
fun McpPickerSheet(
    assistant: Assistant,
    servers: List<McpServerConfig>,
    mcpManager: McpManager,
    onUpdateAssistant: (Assistant) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
        containerColor = palaceColors().bgTop,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
    ) {
        McpPickerPage(
            assistant = assistant,
            servers = servers,
            mcpManager = mcpManager,
            onUpdateAssistant = onUpdateAssistant,
            onBack = onDismiss,
        )
    }
}
