package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.AlertCircle
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.FileImport
import me.rerere.hugeicons.stroke.McpServer
import me.rerere.hugeicons.stroke.MessageBlocked
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.ai.mcp.McpCommonOptions
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.ai.mcp.McpStatus
import me.rerere.rikkahub.ui.components.ui.ItemAction
import me.rerere.rikkahub.ui.components.ui.ItemActionMenu
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceBackdrop
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceDialog
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceField
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceDivider
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceGlass
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceRoundIcon
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceSection
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceTextButton
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceTopBar
import me.rerere.rikkahub.ui.theme.extendColors
import me.rerere.rikkahub.ui.theme.palaceColors
import me.rerere.rikkahub.utils.writeClipboardText
import me.rerere.ui.components.RikkaConfirmDialog
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun SettingMcpPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val mcpConfigs = settings.mcpServers
    val navController = LocalNavController.current
    var showImportDialog by remember { mutableStateOf(false) }
    val c = palaceColors()
    PalaceBackdrop {
        Column(Modifier.fillMaxSize()) {
            // PalaceTopBar 的右侧只有一个图标位，导入和新增两个按钮叠在顶栏右侧，行为不变
            Box(Modifier.fillMaxWidth()) {
                PalaceTopBar(
                    title = stringResource(R.string.setting_mcp_page_title),
                    sub = "MCP",
                    onBack = { navController.popBackStack() },
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(end = 14.dp, top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PalaceRoundIcon(
                        icon = HugeIcons.FileImport,
                        onClick = { showImportDialog = true },
                    )
                    PalaceRoundIcon(
                        icon = HugeIcons.Add01,
                        onClick = {
                            // 名称为空的配置不会连接，先加进列表，具体配置到详情页里填
                            val config = McpServerConfig.StreamableHTTPServer()
                            vm.updateSettings { latest ->
                                latest.copy(mcpServers = latest.mcpServers + config)
                            }
                            navController.navigate(Screen.SettingMcpDetail(serverId = config.id.toString()))
                        },
                    )
                }
            }
            val mcpManager = koinInject<McpManager>()
            val status by mcpManager.syncingStatus.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()
            val state = rememberPullToRefreshState()
            val loading = status.values.any { it == McpStatus.Connecting || it is McpStatus.Reconnecting }
            PullToRefreshBox(
                isRefreshing = loading,
                onRefresh = {
                    scope.launch {
                        mcpManager.syncAll()
                    }
                },
                state = state,
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 12.dp,
                        end = 16.dp,
                        bottom = 40.dp,
                    )
                ) {
                    item(key = "section") {
                        PalaceSection("SERVERS", "服务器")
                    }
                    if (mcpConfigs.isNotEmpty()) {
                        item(key = "servers") {
                            PalaceGlass {
                                mcpConfigs.forEachIndexed { index, mcpConfig ->
                                    key(mcpConfig.id) {
                                        if (index > 0) PalaceDivider()
                                        McpServerItem(
                                            item = mcpConfig,
                                            onEdit = {
                                                navController.navigate(Screen.SettingMcpDetail(serverId = mcpConfig.id.toString()))
                                            },
                                            onDelete = {
                                                vm.updateSettings { latest ->
                                                    latest.copy(
                                                        mcpServers = latest.mcpServers.filter { it.id != mcpConfig.id }
                                                    )
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (mcpConfigs.isEmpty()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.setting_mcp_page_no_mcp_servers_found),
                            fontSize = 14.sp,
                            color = c.ink,
                        )
                        Text(
                            text = stringResource(R.string.setting_mcp_page_add_one_to_get_started),
                            fontSize = 11.5.sp,
                            color = c.mute,
                        )
                    }
                }
            }
        }
    }
    if (showImportDialog) {
        McpImportModal(
            onDismiss = { showImportDialog = false },
            onImport = { newConfigs ->
                val existingIds = mcpConfigs.map { it.commonOptions.name }.toSet()
                val toAdd = newConfigs.filter { it.commonOptions.name.isNotBlank() && it.commonOptions.name !in existingIds }
                vm.updateSettings { it.copy(mcpServers = it.mcpServers + toAdd) }
                showImportDialog = false
            }
        )
    }
}

@Composable
private fun McpServerItem(
    item: McpServerConfig,
    modifier: Modifier = Modifier,
    onDelete: () -> Unit,
    onEdit: (McpServerConfig) -> Unit,
) {
    val mcpManager = koinInject<McpManager>()
    val status by mcpManager.getStatus(item).collectAsStateWithLifecycle(McpStatus.Idle)
    var showDeleteDialog by remember { mutableStateOf(false) }
    var errorDetail by remember { mutableStateOf<McpStatus.Error?>(null) }

    errorDetail?.let { error ->
        val context = LocalContext.current
        val fullText = error.detail ?: error.message
        PalaceDialog(
            title = item.commonOptions.name.ifBlank { "MCP" },
            onDismiss = { errorDetail = null },
            confirmText = stringResource(R.string.copy),
            onConfirm = {
                context.writeClipboardText(fullText)
                errorDetail = null
            },
            dismissText = stringResource(R.string.cancel),
        ) {
            SelectionContainer {
                Text(
                    text = fullText,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    color = palaceColors().ink,
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
        }
    }
    val c = palaceColors()
    run {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clickable { onEdit(item) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(c.chip),
                contentAlignment = Alignment.Center,
            ) {
                when (status) {
                    McpStatus.Idle -> Icon(HugeIcons.MessageBlocked, null, Modifier.size(16.dp), tint = c.ink2)
                    McpStatus.Connecting,
                    is McpStatus.Reconnecting,
                    McpStatus.Authorizing -> CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = c.ink2,
                    )

                    McpStatus.Connected -> Icon(HugeIcons.McpServer, null, Modifier.size(16.dp), tint = c.ink2)
                    is McpStatus.Error -> Icon(HugeIcons.AlertCircle, null, Modifier.size(16.dp), tint = c.ink2)
                    McpStatus.NeedsAuthorization -> Icon(HugeIcons.AlertCircle, null, Modifier.size(16.dp), tint = c.ink2)
                }
            }
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = item.commonOptions.name.ifBlank { "MCP" },
                        fontSize = 14.sp,
                        color = c.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    val dotColor =
                        if (item.commonOptions.enable) MaterialTheme.extendColors.green6 else MaterialTheme.extendColors.red6
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .drawWithContent {
                                drawCircle(
                                    color = dotColor
                                )
                            }
                    )
                }

                Spacer(Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Tag(type = TagType.SUCCESS) {
                        when (item) {
                            is McpServerConfig.SseTransportServer -> Text("SSE")
                            is McpServerConfig.StreamableHTTPServer -> Text("Streamable HTTP")
                        }
                    }
                }
                if (status is McpStatus.Error) {
                    val error = status as McpStatus.Error
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = error.message,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        color = c.error,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { errorDetail = error },
                    )
                }
                if (status == McpStatus.NeedsAuthorization) {
                    val context = LocalContext.current
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "需要 OAuth 授权",
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        color = c.error,
                    )
                    PalaceTextButton(
                        text = "OAuth 授权",
                        onClick = { mcpManager.startAuthorization(item, context) },
                    )
                }
                if (status == McpStatus.Authorizing) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "正在授权，请在浏览器中完成…",
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        color = c.mute,
                    )
                    PalaceTextButton(
                        text = "取消授权",
                        onClick = { mcpManager.cancelAuthorization(item) },
                    )
                }
            }

            Spacer(Modifier.width(8.dp))
            ItemActionMenu(
                actions = listOf(
                    ItemAction(
                        text = stringResource(R.string.delete),
                        icon = HugeIcons.Delete01,
                        destructive = true,
                        onClick = { showDeleteDialog = true },
                    ),
                )
            )
        }
    }

    RikkaConfirmDialog(
        show = showDeleteDialog,
        title = stringResource(R.string.confirm_delete),
        confirmText = stringResource(R.string.delete),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            showDeleteDialog = false
            onDelete()
        },
        onDismiss = { showDeleteDialog = false },
    ) {
        Text(stringResource(R.string.common_delete_confirm_message, item.commonOptions.name))
    }
}

private fun parseMcpServersFromJson(json: String): List<McpServerConfig> {
    val root = Json.parseToJsonElement(json).jsonObject
    val mcpServers = root["mcpServers"]?.jsonObject ?: return emptyList()
    return mcpServers.entries.mapNotNull { (name, element) ->
        val obj = element.jsonObject
        val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: "streamable_http"
        val url = obj["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val headers = obj["headers"]?.jsonObject?.entries?.map { (k, v) ->
            k to (v.jsonPrimitive.contentOrNull ?: "")
        } ?: emptyList()
        val commonOptions = McpCommonOptions(name = name, headers = headers)
        when (type) {
            "sse" -> McpServerConfig.SseTransportServer(commonOptions = commonOptions, url = url)
            else -> McpServerConfig.StreamableHTTPServer(commonOptions = commonOptions, url = url)
        }
    }
}

@Composable
private fun McpImportModal(
    onDismiss: () -> Unit,
    onImport: (List<McpServerConfig>) -> Unit,
) {
    val c = palaceColors()
    var jsonText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val noValidConfigMsg = stringResource(R.string.setting_mcp_page_import_no_valid_config)
    val parseErrorMsg = stringResource(R.string.setting_mcp_page_import_parse_error)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
        containerColor = c.bgTop,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .padding(16.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.setting_mcp_page_import_title),
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium,
                color = c.ink,
            )
            Text(
                stringResource(R.string.setting_mcp_page_import_desc),
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                color = c.mute,
            )
            PalaceField(
                value = jsonText,
                onValueChange = {
                    jsonText = it
                    errorMessage = null
                },
                modifier = Modifier.weight(1f),
                placeholder = "{ \"mcpServers\": { ... } }",
                minLines = 10,
                isError = errorMessage != null,
            )
            errorMessage?.let { msg ->
                Text(msg, fontSize = 11.5.sp, lineHeight = 16.sp, color = c.error)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                PalaceTextButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                )
                PalaceTextButton(
                    text = stringResource(R.string.setting_mcp_page_import_confirm),
                    onClick = {
                        try {
                            val configs = parseMcpServersFromJson(jsonText.trim())
                            if (configs.isEmpty()) {
                                errorMessage = noValidConfigMsg
                            } else {
                                onImport(configs)
                            }
                        } catch (e: Exception) {
                            errorMessage = parseErrorMsg.format(e.message ?: "")
                        }
                    },
                )
            }
        }
    }
}
