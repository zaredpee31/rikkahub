package me.rerere.rikkahub.ui.pages.setting

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FloatingToolbarDefaults.ScreenOffset
import androidx.compose.material3.FloatingToolbarDefaults.floatingToolbarVerticalNestedScroll
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ai.core.InputSchema
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Tools
import me.rerere.hugeicons.stroke.View
import me.rerere.hugeicons.stroke.ViewOff
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.ai.mcp.McpTool
import me.rerere.rikkahub.ui.components.ui.PagerTab
import me.rerere.rikkahub.ui.components.ui.PagerTabs
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceBackdrop
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceDivider
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceGlass
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceInputRow
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceSection
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceSwitchRow
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceTextButton
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceTopBar
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.palaceColors
import me.rerere.ui.components.RikkaConfirmDialog
import me.rerere.ui.components.Switch
import me.rerere.ui.components.SwitchSize
import me.rerere.ui.toast.ToastType
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

private enum class McpDetailTab(
    override val icon: ImageVector,
    @param:StringRes private val labelRes: Int,
) : PagerTab {
    BASIC(HugeIcons.Settings03, R.string.setting_mcp_page_basic_settings),
    TOOLS(HugeIcons.Tools, R.string.setting_mcp_page_tools);

    override val label: String
        @Composable get() = stringResource(labelRes)
}

@Composable
fun SettingMcpDetailPage(id: Uuid, vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val config = settings.mcpServers.find { it.id == id } ?: return
    val pager = rememberPagerState { McpDetailTab.entries.size }
    val toaster = LocalToaster.current
    val context = LocalContext.current
    // 草稿放在页面这一层，切到工具页再切回来不会丢掉没保存的修改
    var draft by remember { mutableStateOf(config) }

    val onSave = {
        // 只合并基础设置里能改的字段，工具列表和 OAuth 状态以后台同步写回的为准
        vm.updateSettings { latest ->
            latest.copy(
                mcpServers = latest.mcpServers.map { server ->
                    if (server.id == id) {
                        draft.clone(
                            commonOptions = server.commonOptions.copy(
                                enable = draft.commonOptions.enable,
                                name = draft.commonOptions.name,
                                headers = draft.commonOptions.headers,
                            )
                        )
                    } else {
                        server
                    }
                }
            )
        }
        toaster.show(
            context.getString(R.string.setting_provider_page_save_success),
            type = ToastType.Success
        )
    }
    val onDelete: () -> Unit = {
        vm.updateSettings { latest ->
            latest.copy(mcpServers = latest.mcpServers.filter { it.id != id })
        }
        navController.popBackStack()
    }
    val onToolChange = { tool: McpTool ->
        vm.updateSettings { latest ->
            latest.copy(
                mcpServers = latest.mcpServers.map { server ->
                    if (server.id == id) {
                        server.clone(
                            commonOptions = server.commonOptions.copy(
                                tools = server.commonOptions.tools.map {
                                    if (it.name == tool.name) {
                                        it.copy(enable = tool.enable, needsApproval = tool.needsApproval)
                                    } else {
                                        it
                                    }
                                }
                            )
                        )
                    } else {
                        server
                    }
                }
            )
        }
    }

    PalaceBackdrop {
        Column(Modifier.fillMaxSize()) {
            PalaceTopBar(
                title = config.commonOptions.name.ifBlank { "MCP" },
                sub = "MCP",
                onBack = { navController.popBackStack() },
            )

            PagerTabs(
                pagerState = pager,
                tabs = McpDetailTab.entries,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            )

            HorizontalPager(
                state = pager,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                when (McpDetailTab.entries[page]) {
                    McpDetailTab.BASIC -> {
                        McpBasicSettingsTab(
                            draft = draft,
                            onEdit = { draft = it },
                            onSave = onSave,
                            onDelete = onDelete,
                        )
                    }

                    McpDetailTab.TOOLS -> {
                        McpToolsConfigure(
                            config = config,
                            onToolChange = onToolChange,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun McpBasicSettingsTab(
    draft: McpServerConfig,
    onEdit: (McpServerConfig) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(true) }
    val canSave = draft.commonOptions.name.isNotBlank() && isValidMcpName(draft.commonOptions.name)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        McpCommonOptionsConfigure(
            config = draft,
            update = onEdit,
            modifier = Modifier.floatingToolbarVerticalNestedScroll(
                expanded = expanded,
                onExpand = { expanded = true },
                onCollapse = { expanded = false },
            ),
        )

        // 键盘弹出时收起，不然会盖住正在输入的那一行
        AnimatedVisibility(
            visible = !WindowInsets.isImeVisible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -ScreenOffset),
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
        ) {
            HorizontalFloatingToolbar(
                expanded = expanded,
                colors = CustomColors.floatingToolbarColors,
                leadingContent = {
                    IconButton(
                        onClick = {
                            showDeleteDialog = true
                        },
                    ) {
                        Icon(HugeIcons.Delete01, stringResource(R.string.delete))
                    }
                },
            ) {
                PalaceTextButton(
                    text = stringResource(R.string.setting_mcp_page_save),
                    onClick = onSave,
                    enabled = canSave,
                )
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
            onDelete()
        },
        onDismiss = { showDeleteDialog = false },
    ) {
        Text(stringResource(R.string.common_delete_confirm_message, draft.commonOptions.name))
    }
}

@Composable
private fun McpCommonOptionsConfigure(
    config: McpServerConfig,
    update: (McpServerConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = palaceColors()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            // 底部留出悬浮工具栏的位置
            .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 128.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PalaceSection("GENERAL", stringResource(R.string.setting_mcp_page_basic_settings))
        PalaceGlass {
            // 启用/禁用开关
            PalaceSwitchRow(
                title = stringResource(R.string.setting_mcp_page_enable),
                desc = stringResource(R.string.setting_mcp_page_enable_desc),
                checked = config.commonOptions.enable,
                onChange = { enabled ->
                    update(
                        when (config) {
                            is McpServerConfig.SseTransportServer -> config.copy(
                                commonOptions = config.commonOptions.copy(enable = enabled)
                            )

                            is McpServerConfig.StreamableHTTPServer -> config.copy(
                                commonOptions = config.commonOptions.copy(enable = enabled)
                            )
                        }
                    )
                },
            )
            PalaceDivider()
            // 名称输入框
            val nameInvalid = !isValidMcpName(config.commonOptions.name)
            PalaceInputRow(
                label = stringResource(R.string.setting_mcp_page_name),
                value = config.commonOptions.name,
                onValueChange = { name ->
                    update(
                        when (config) {
                            is McpServerConfig.SseTransportServer -> config.copy(
                                commonOptions = config.commonOptions.copy(name = name)
                            )

                            is McpServerConfig.StreamableHTTPServer -> config.copy(
                                commonOptions = config.commonOptions.copy(name = name)
                            )
                        }
                    )
                },
                placeholder = stringResource(R.string.setting_mcp_page_name_placeholder),
                supporting = if (nameInvalid) {
                    stringResource(R.string.setting_mcp_page_name_invalid)
                } else {
                    stringResource(R.string.setting_mcp_page_name_desc)
                },
                isError = nameInvalid,
                singleLine = true,
            )
        }

        PalaceSection("TRANSPORT", stringResource(R.string.setting_mcp_page_transport_type))
        PalaceGlass {
            // 传输类型选择
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(stringResource(R.string.setting_mcp_page_transport_type), fontSize = 14.sp, color = c.ink)
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.setting_mcp_page_transport_type_desc),
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    color = c.mute,
                )
                Spacer(Modifier.height(6.dp))
                val transportTypes = listOf(
                    "Streamable HTTP",
                    "SSE"
                )
                val currentTypeIndex = when (config) {
                    is McpServerConfig.StreamableHTTPServer -> 0
                    is McpServerConfig.SseTransportServer -> 1
                }

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    transportTypes.forEachIndexed { index, type ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index, transportTypes.size),
                            onClick = {
                                if (index != currentTypeIndex) {
                                    val newConfig = when (index) {
                                        0 -> McpServerConfig.StreamableHTTPServer(
                                            id = config.id,
                                            commonOptions = config.commonOptions,
                                            url = when (config) {
                                                is McpServerConfig.SseTransportServer -> config.url
                                                is McpServerConfig.StreamableHTTPServer -> config.url
                                            }
                                        )

                                        1 -> McpServerConfig.SseTransportServer(
                                            id = config.id,
                                            commonOptions = config.commonOptions,
                                            url = when (config) {
                                                is McpServerConfig.SseTransportServer -> config.url
                                                is McpServerConfig.StreamableHTTPServer -> config.url
                                            }
                                        )

                                        else -> config
                                    }
                                    update(newConfig)
                                }
                            },
                            selected = index == currentTypeIndex
                        ) {
                            Text(type)
                        }
                    }
                }
            }
            PalaceDivider()
            // 服务器地址配置
            PalaceInputRow(
                label = stringResource(R.string.setting_mcp_page_url_label),
                value = when (config) {
                    is McpServerConfig.SseTransportServer -> config.url
                    is McpServerConfig.StreamableHTTPServer -> config.url
                },
                onValueChange = { url ->
                    update(
                        when (config) {
                            is McpServerConfig.SseTransportServer -> config.copy(url = url)
                            is McpServerConfig.StreamableHTTPServer -> config.copy(url = url)
                        }
                    )
                },
                placeholder = when (config) {
                    is McpServerConfig.SseTransportServer -> stringResource(R.string.setting_mcp_page_sse_url_placeholder)
                    is McpServerConfig.StreamableHTTPServer -> stringResource(R.string.setting_mcp_page_streamable_http_url_placeholder)
                },
                supporting = when (config) {
                    is McpServerConfig.SseTransportServer -> stringResource(R.string.setting_mcp_page_sse_url_desc)
                    is McpServerConfig.StreamableHTTPServer -> stringResource(R.string.setting_mcp_page_streamable_http_url_desc)
                },
                singleLine = true,
            )
        }

        PalaceSection("HEADERS", stringResource(R.string.setting_mcp_page_custom_headers))
        PalaceGlass {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.setting_mcp_page_custom_headers_desc),
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    color = c.mute,
                )
                config.commonOptions.headers.forEachIndexed { index, header ->
                    var headerName by remember(header.first) { mutableStateOf(header.first) }
                    var headerValue by remember(header.second) { mutableStateOf(header.second) }
                    var headerValueVisible by rememberSaveable { mutableStateOf(false) }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = headerName,
                                onValueChange = {
                                    headerName = it
                                    val updatedHeaders =
                                        config.commonOptions.headers.toMutableList()
                                    updatedHeaders[index] =
                                        it.trim() to updatedHeaders[index].second
                                    update(
                                        when (config) {
                                            is McpServerConfig.SseTransportServer -> config.copy(
                                                commonOptions = config.commonOptions.copy(headers = updatedHeaders)
                                            )

                                            is McpServerConfig.StreamableHTTPServer -> config.copy(
                                                commonOptions = config.commonOptions.copy(headers = updatedHeaders)
                                            )
                                        }
                                    )
                                },
                                label = { Text(stringResource(R.string.setting_mcp_page_header_name)) },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(stringResource(R.string.setting_mcp_page_header_name_placeholder)) }
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = headerValue,
                                onValueChange = {
                                    headerValue = it
                                    val updatedHeaders =
                                        config.commonOptions.headers.toMutableList()
                                    updatedHeaders[index] = updatedHeaders[index].first to it.trim()
                                    update(
                                        when (config) {
                                            is McpServerConfig.SseTransportServer -> config.copy(
                                                commonOptions = config.commonOptions.copy(headers = updatedHeaders)
                                            )

                                            is McpServerConfig.StreamableHTTPServer -> config.copy(
                                                commonOptions = config.commonOptions.copy(headers = updatedHeaders)
                                            )
                                        }
                                    )
                                },
                                label = { Text(stringResource(R.string.setting_mcp_page_header_value)) },
                                modifier = Modifier.fillMaxWidth(),
                                visualTransformation = if (headerValueVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { headerValueVisible = !headerValueVisible }) {
                                        Icon(
                                            if (headerValueVisible) HugeIcons.ViewOff else HugeIcons.View,
                                            contentDescription = null
                                        )
                                    }
                                },
                                placeholder = { Text(stringResource(R.string.setting_mcp_page_header_value_placeholder)) }
                            )
                        }
                        IconButton(onClick = {
                            val updatedHeaders = config.commonOptions.headers.toMutableList()
                            updatedHeaders.removeAt(index)
                            update(
                                when (config) {
                                    is McpServerConfig.SseTransportServer -> config.copy(
                                        commonOptions = config.commonOptions.copy(headers = updatedHeaders)
                                    )

                                    is McpServerConfig.StreamableHTTPServer -> config.copy(
                                        commonOptions = config.commonOptions.copy(headers = updatedHeaders)
                                    )
                                }
                            )
                        }) {
                            Icon(
                                HugeIcons.Delete01,
                                contentDescription = stringResource(R.string.setting_mcp_page_delete_header)
                            )
                        }
                    }
                }

                PalaceTextButton(
                    text = stringResource(R.string.setting_mcp_page_add_header),
                    onClick = {
                        val updatedHeaders = config.commonOptions.headers.toMutableList()
                        updatedHeaders.add("" to "")
                        update(
                            when (config) {
                                is McpServerConfig.SseTransportServer -> config.copy(
                                    commonOptions = config.commonOptions.copy(headers = updatedHeaders)
                                )

                                is McpServerConfig.StreamableHTTPServer -> config.copy(
                                    commonOptions = config.commonOptions.copy(headers = updatedHeaders)
                                )
                            }
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun McpToolsConfigure(
    config: McpServerConfig,
    onToolChange: (McpTool) -> Unit,
) {
    val mcpManager = koinInject<McpManager>()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (mcpManager.getClient(config) == null) {
            item {
                Text(stringResource(R.string.setting_mcp_page_tools_unavailable_message))
            }
        }
        items(config.commonOptions.tools) { tool ->
            McpToolCard(
                tool = tool,
                onEnableChange = { onToolChange(tool.copy(enable = it)) },
                onNeedsApprovalChange = { onToolChange(tool.copy(needsApproval = it)) },
            )
        }
    }
}

@Composable
private fun McpToolCard(
    tool: McpTool,
    onEnableChange: (Boolean) -> Unit,
    onNeedsApprovalChange: (Boolean) -> Unit,
) {
    val c = palaceColors()
    var expanded by remember { mutableStateOf(false) }
    PalaceGlass(shape = RoundedCornerShape(20.dp)) {
        Column(
            modifier = Modifier
                .animateContentSize()
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // 第一行：工具名字和3个按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = tool.name,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = c.ink,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // 需要审批开关
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.setting_mcp_page_needs_approval),
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        color = c.mute,
                    )
                    Switch(
                        checked = tool.needsApproval,
                        onCheckedChange = onNeedsApprovalChange,
                        size = SwitchSize.Small
                    )
                }
                // 启用开关
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "启用",
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        color = c.mute,
                    )
                    Switch(
                        checked = tool.enable,
                        onCheckedChange = onEnableChange,
                        size = SwitchSize.Small
                    )
                }
                // 展开/收起按钮
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        if (expanded) HugeIcons.ArrowUp01 else HugeIcons.ArrowDown01,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            // 展开后显示描述和参数
            if (expanded) {
                // 描述
                if (!tool.description.isNullOrBlank()) {
                    Text(
                        text = tool.description,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        color = c.mute,
                    )
                }
                // 参数标签
                tool.inputSchema?.let { it as? InputSchema.Obj }?.let { schema ->
                    if (schema.properties.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            schema.properties.forEach { (key, _) ->
                                Tag(
                                    type = if (schema.required?.contains(key) == true) TagType.INFO else TagType.DEFAULT
                                ) {
                                    Text(
                                        text = key,
                                        fontSize = 11.5.sp,
                                        lineHeight = 16.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun isValidMcpName(name: String): Boolean {
    return name.isEmpty() || name.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }
}
