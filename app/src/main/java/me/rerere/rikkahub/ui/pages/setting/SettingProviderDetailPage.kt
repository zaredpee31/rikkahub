package me.rerere.rikkahub.ui.pages.setting

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.sp
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceDivider
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceSection
import me.rerere.rikkahub.ui.theme.palaceColors
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FloatingToolbarDefaults.ScreenOffset
import androidx.compose.material3.FloatingToolbarDefaults.floatingToolbarVerticalNestedScroll
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFilter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ui.toast.ToastType
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.supportsQuota
import me.rerere.ai.registry.ModelRegistry
import me.rerere.ai.ui.UIMessage
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Connect
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.FloppyDisk
import me.rerere.hugeicons.stroke.Package01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Share01
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.hugeicons.stroke.Tools
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.account.preserveCredential
import me.rerere.rikkahub.ui.components.ai.ExtensionEmptyState
import me.rerere.rikkahub.ui.components.ai.ModelAbilityTag
import me.rerere.rikkahub.ui.components.ai.ModelModalityTag
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.components.ai.ModelTypeTag
import me.rerere.rikkahub.ui.components.ai.ProviderQuotaText
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceButtons
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceLabel
import me.rerere.rikkahub.ui.components.ui.ConnectedMultiChoiceButtons
import me.rerere.rikkahub.ui.components.ui.ItemAction
import me.rerere.rikkahub.ui.components.ui.ItemActionMenu
import me.rerere.rikkahub.ui.components.ui.PagerTab
import me.rerere.rikkahub.ui.components.ui.PagerTabs
import me.rerere.rikkahub.ui.components.ui.ShareSheet
import me.rerere.rikkahub.ui.components.ui.SiliconFlowPowerByIcon
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.components.ui.cardGroupItemShape
import me.rerere.rikkahub.ui.components.ui.longPressReorder
import me.rerere.rikkahub.ui.components.ui.rememberShareSheetState
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.ui.pages.assistant.detail.CustomBodies
import me.rerere.rikkahub.ui.pages.assistant.detail.CustomHeaders
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceBackdrop
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceDialog
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceField
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceGlass
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceRow
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceSwitchRow
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceTextButton
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceTopBar
import me.rerere.rikkahub.ui.pages.setting.components.ProviderConfigure
import me.rerere.rikkahub.ui.pages.setting.components.ProviderConnectionTester
import me.rerere.rikkahub.ui.pages.setting.components.SettingProviderQuotaQuery
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.extendColors
import me.rerere.rikkahub.utils.UiState
import me.rerere.rikkahub.utils.formatNumber
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private enum class ProviderDetailTab(
    override val icon: ImageVector,
    @param:StringRes private val labelRes: Int,
) : PagerTab {
    CONFIG(HugeIcons.Tools, R.string.setting_provider_page_configuration),
    MODELS(HugeIcons.Package01, R.string.setting_provider_page_models),
    ADVANCED(HugeIcons.Settings03, R.string.setting_provider_page_advanced_settings);

    override val label: String
        @Composable get() = stringResource(labelRes)
}

private enum class ModelFormTab(
    override val icon: ImageVector,
    @param:StringRes private val labelRes: Int,
) : PagerTab {
    BASIC(HugeIcons.Package01, R.string.setting_provider_page_basic_settings),
    ADVANCED(HugeIcons.Settings03, R.string.setting_provider_page_advanced_settings),
    BUILT_IN_TOOLS(HugeIcons.Tools, R.string.setting_page_built_in_tools);

    override val label: String
        @Composable get() = stringResource(labelRes)
}

@Composable
fun SettingProviderDetailPage(id: Uuid, vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val provider = settings.providers.find { it.id == id } ?: return
    val pager = rememberPagerState { ProviderDetailTab.entries.size }
    val toaster = LocalToaster.current
    val context = LocalContext.current

    val onEdit = { newProvider: ProviderSetting ->
        vm.updateSettings { latest ->
            latest.copy(
                providers = latest.providers.map {
                    if (newProvider.id == it.id) {
                        newProvider.preserveCredential(it)
                    } else {
                        it
                    }
                }
            )
        }
    }
    val onDelete = {
        vm.updateSettings { latest ->
            latest.copy(providers = latest.providers.filter { it.id != provider.id })
        }
        navController.popBackStack()
    }

    // 账号型提供商离开账号就没法用，不提供分享
    val canShare = provider !is ProviderSetting.Account
    val shareSheetState = rememberShareSheetState()
    if (canShare) {
        ShareSheet(shareSheetState)
    }

    PalaceBackdrop {
        Column(Modifier.fillMaxSize()) {
            PalaceTopBar(
                title = provider.name,
                sub = "PROVIDER",
                onBack = { navController.popBackStack() },
                rightIcon = if (canShare) HugeIcons.Share01 else null,
                onRight = { if (canShare) shareSheetState.show(provider) },
            )

            PagerTabs(
                pagerState = pager,
                tabs = ProviderDetailTab.entries,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            )

            HorizontalPager(
                state = pager,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                when (ProviderDetailTab.entries[page]) {
                    ProviderDetailTab.CONFIG -> {
                        SettingProviderConfigPage(
                            provider = provider,
                            onEdit = {
                                onEdit(it)
                                toaster.show(
                                    context.getString(R.string.setting_provider_page_save_success),
                                    type = ToastType.Success
                                )
                            },
                            onDelete = {
                                onDelete()
                            }
                        )
                    }

                    ProviderDetailTab.MODELS -> {
                        SettingProviderModelPage(
                            provider = provider,
                            onEdit = onEdit
                        )
                    }

                    ProviderDetailTab.ADVANCED -> {
                        SettingProviderAdvancedPage(
                            provider = provider,
                            onEdit = onEdit
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingProviderConfigPage(
    provider: ProviderSetting,
    onEdit: (ProviderSetting) -> Unit,
    onDelete: () -> Unit
) {
    val c = palaceColors()
    var internalProvider by remember(provider) { mutableStateOf(provider) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .floatingToolbarVerticalNestedScroll(
                    expanded = expanded,
                    onExpand = { expanded = true },
                    onCollapse = { expanded = false },
                )
                .verticalScroll(rememberScrollState())
                // 底部留出悬浮工具栏的位置
                .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PalaceSection("CONFIG", "配置")
            PalaceGlass {
                ProviderConfigure(
                    provider = internalProvider,
                    onEdit = {
                        internalProvider = it
                    }
                )
            }

            // 账号类的额度查询写死在 Provider 里，没有这一项
            (internalProvider as? ProviderSetting.ApiKey)?.let { apiKeyProvider ->
                PalaceSection("QUOTA", "额度")
                PalaceGlass {
                    SettingProviderQuotaQuery(
                        provider = apiKeyProvider,
                        // 额度按已保存的配置查，没开就不留这一行
                        supportingContent = if (provider.supportsQuota) {
                            { ProviderQuotaText(providerSetting = provider) }
                        } else null,
                        onEdit = { internalProvider = apiKeyProvider.withQuotaQuery(it) }
                    )
                }
            }

            // 硅基流动图标
            if (provider is ProviderSetting.OpenAI && provider.baseUrl.contains("siliconflow.cn")) {
                SiliconFlowPowerByIcon(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(vertical = 16.dp)
                )
            }
        }

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
                    ProviderConnectionTester(
                        internalProvider = internalProvider,
                    )

                    if (true) {
                        IconButton(
                            onClick = {
                                showDeleteDialog = true
                            },
                        ) {
                            Icon(HugeIcons.Delete01, stringResource(R.string.delete), tint = c.ink2)
                        }
                    }
                },
            ) {
                val save = {
                    val providerToSave: ProviderSetting = internalProvider
                    onEdit(providerToSave.copyProvider(name = providerToSave.name.trim()))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = save) {
                        Icon(
                            HugeIcons.FloppyDisk,
                            contentDescription = stringResource(R.string.setting_provider_page_save),
                            tint = c.ink2,
                        )
                    }
                    AnimatedVisibility(expanded) {
                        PalaceTextButton(
                            stringResource(R.string.setting_provider_page_save),
                            onClick = save,
                        )
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        PalaceDialog(
            title = stringResource(R.string.confirm_delete),
            onDismiss = { showDeleteDialog = false },
            confirmText = stringResource(R.string.delete),
            onConfirm = {
                showDeleteDialog = false
                onDelete()
            },
            dismissText = stringResource(R.string.cancel),
        ) {
            Text(
                stringResource(R.string.setting_provider_page_delete_dialog_text),
                fontSize = 14.sp,
                color = c.ink,
            )
        }
    }
}

@Composable
private fun SettingProviderModelPage(
    provider: ProviderSetting,
    onEdit: (ProviderSetting) -> Unit
) {
    ModelList(
        providerSetting = provider,
        onUpdateProvider = onEdit
    )
}

@Composable
private fun SettingProviderAdvancedPage(
    provider: ProviderSetting,
    onEdit: (ProviderSetting) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PalaceSection("HEADERS", "请求头")
        PalaceGlass {
            CustomHeaders(
                headers = provider.customHeaders,
                onUpdate = { headers ->
                    onEdit(provider.copyProvider(customHeaders = headers))
                }
            )
        }
    }
}

@Composable
private fun ModelList(
    providerSetting: ProviderSetting,
    onUpdateProvider: (ProviderSetting) -> Unit
) {
    val providerManager = koinInject<ProviderManager>()
    val modelList by produceState(emptyList(), providerSetting) {
        runCatching {
            println("loading models...")
            value = providerManager.getProviderByType(providerSetting)
                .listModels(providerSetting)
                .sortedBy { it.modelId }
                .toList()
        }.onFailure {
            it.printStackTrace()
        }
    }
    var expanded by rememberSaveable { mutableStateOf(true) }
    val lazyListState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onUpdateProvider(providerSetting.moveMove(from.index, to.index))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (providerSetting.models.isEmpty()) {
            ExtensionEmptyState(
                message = stringResource(R.string.setting_provider_page_no_models),
                description = stringResource(R.string.setting_provider_page_add_models_hint),
                icon = HugeIcons.Package01,
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // 底部留出悬浮工具栏的位置
                    .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 128.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PalaceSection("MODELS", "模型")
                PalaceGlass(Modifier.weight(1f, fill = false)) {
                    LazyColumn(
                        modifier = Modifier.floatingToolbarVerticalNestedScroll(
                            expanded = expanded,
                            onExpand = { expanded = true },
                            onCollapse = { expanded = false },
                        ),
                        state = lazyListState,
                    ) {
                        itemsIndexed(providerSetting.models, key = { _, it -> it.id }) { index, item ->
                            ReorderableItem(
                                state = reorderableLazyListState,
                                key = item.id
                            ) { isDragging ->
                                Column(Modifier.fillMaxWidth()) {
                                    if (index > 0) PalaceDivider()
                                    ModelCard(
                                        model = item,
                                        onDelete = {
                                            onUpdateProvider(providerSetting.delModel(item))
                                        },
                                        onEdit = { editedModel ->
                                            onUpdateProvider(providerSetting.editModel(editedModel))
                                        },
                                        parentProvider = providerSetting,
                                        modifier = longPressReorder(isDragging),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        HorizontalFloatingToolbar(
            expanded = expanded,
            colors = CustomColors.floatingToolbarColors,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -ScreenOffset),
        ) {
            AddModelButton(
                models = modelList,
                selectedModels = providerSetting.models,
                onAddModel = {
                    onUpdateProvider(providerSetting.addModel(it))
                },
                onRemoveModel = {
                    onUpdateProvider(providerSetting.delModel(it))
                },
                expanded = expanded,
                parentProvider = providerSetting,
                onUpdateProvider = onUpdateProvider
            )
        }
    }
}

@Composable
private fun ModelSettingsForm(
    model: Model,
    onModelChange: (Model) -> Unit,
    isEdit: Boolean,
    parentProvider: ProviderSetting? = null
) {
    val pagerState = rememberPagerState { ModelFormTab.entries.size }

    fun setModelId(id: String) {
        val inputModality = ModelRegistry.MODEL_INPUT_MODALITIES.getData(id)
        val outputModality = ModelRegistry.MODEL_OUTPUT_MODALITIES.getData(id)
        val abilities = ModelRegistry.MODEL_ABILITIES.getData(id)
        onModelChange(
            model.copy(
                modelId = id,
                displayName = id,
                inputModalities = inputModality,
                outputModalities = outputModality,
                abilities = abilities
            )
        )
    }

    Column {
        PagerTabs(
            pagerState = pagerState,
            tabs = ModelFormTab.entries,
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            when (ModelFormTab.entries[page]) {
                ModelFormTab.BASIC -> {
                    // 基本设置页面
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // 编辑时模型 ID 不可改：onValueChange 本来就不响应，这里再压暗表示禁用
                        PalaceField(
                            value = model.modelId,
                            onValueChange = {
                                if (!isEdit) {
                                    setModelId(it.trim())
                                }
                            },
                            modifier = Modifier.alpha(if (isEdit) 0.5f else 1f),
                            label = stringResource(R.string.setting_provider_page_model_id),
                            placeholder = if (!isEdit) stringResource(R.string.setting_provider_page_model_id_placeholder) else "",
                        )

                        PalaceField(
                            value = model.displayName,
                            onValueChange = {
                                onModelChange(model.copy(displayName = it))
                            },
                            label = stringResource(if (isEdit) R.string.setting_provider_page_model_name else R.string.setting_provider_page_model_display_name),
                            placeholder = if (!isEdit) stringResource(R.string.setting_provider_page_model_display_name_placeholder) else "",
                        )

                        ModelTypeSelector(
                            selectedType = model.type,
                            onTypeSelected = {
                                onModelChange(model.copy(type = it))
                            }
                        )

                        ModelModalitySelector(
                            model = model,
                            inputModalities = model.inputModalities,
                            onUpdateInputModalities = {
                                onModelChange(model.copy(inputModalities = it))
                            },
                            outputModalities = model.outputModalities,
                            onUpdateOutputModalities = {
                                onModelChange(model.copy(outputModalities = it))
                            }
                        )

                        if (model.type == ModelType.CHAT) {
                            ModalAbilitySelector(
                                abilities = model.abilities,
                                onUpdateAbilities = {
                                    onModelChange(model.copy(abilities = it))
                                }
                            )

                            ModelContextLengthSelector(
                                contextLength = model.contextLength,
                                onUpdateContextLength = {
                                    onModelChange(model.copy(contextLength = it))
                                }
                            )

                            PalaceSwitchRow(
                                title = "开口前先想一段",
                                desc = "回复前先用我自己的口气写一段心里话，收进思考卡片。4.6 可以开；5.5 会拒绝不出字，别开",
                                checked = model.innerMonologue,
                                onChange = { onModelChange(model.copy(innerMonologue = it)) },
                            )
                            PalaceSwitchRow(
                                title = "心情",
                                desc = "每条消息报给服务器上的情绪服务，把我这会儿的心情带进回复。测功能的模型关掉，测试就不算进心情",
                                checked = model.feelDrives,
                                onChange = { onModelChange(model.copy(feelDrives = it)) },
                            )
                        }
                    }
                }

                ModelFormTab.ADVANCED -> {
                    // 高级设置页面
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 覆盖配置是提供商的独立副本，账号凭据不能复制出去，账号型提供商不支持覆盖
                        if (parentProvider !is ProviderSetting.Account) {
                            ProviderOverrideSettings(
                                providerOverride = model.providerOverwrite,
                                onUpdateProviderOverride = { providerOverride ->
                                    onModelChange(model.copy(providerOverwrite = providerOverride))
                                },
                                parentProvider = parentProvider
                            )
                        }

                        CustomHeaders(
                            headers = model.customHeaders,
                            onUpdate = { headers ->
                                onModelChange(model.copy(customHeaders = headers))
                            }
                        )

                        CustomBodies(
                            customBodies = model.customBodies,
                            onUpdate = { bodies ->
                                onModelChange(model.copy(customBodies = bodies))
                            }
                        )
                    }
                }

                ModelFormTab.BUILT_IN_TOOLS -> {
                    // 内置工具页面
                    BuiltInToolsSettings(
                        tools = model.tools,
                        onUpdateTools = { tools ->
                            onModelChange(model.copy(tools = tools))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AddModelButton(
    models: List<Model>,
    selectedModels: List<Model>,
    expanded: Boolean,
    onAddModel: (Model) -> Unit,
    onRemoveModel: (Model) -> Unit,
    parentProvider: ProviderSetting,
    onUpdateProvider: (ProviderSetting) -> Unit
) {
    val dialogState = useEditState<Model> {
        onAddModel(it.copy(displayName = it.displayName.trim()))
    }
    val scope = rememberCoroutineScope()
    val c = palaceColors()

    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModelPicker(
            models = models,
            selectedModels = selectedModels,
            onModelSelected = { model ->
                val inputModalities = ModelRegistry.MODEL_INPUT_MODALITIES.getData(model.modelId)
                val outputModalities = ModelRegistry.MODEL_OUTPUT_MODALITIES.getData(model.modelId)
                val abilities = ModelRegistry.MODEL_ABILITIES.getData(model.modelId)
                onAddModel(
                    model.copy(
                        inputModalities = inputModalities,
                        outputModalities = outputModalities,
                        abilities = abilities
                    )
                )
            },
            onModelDeselected = { model ->
                onRemoveModel(model)
            },
            onAllModelSelected = {
                onUpdateProvider(
                    parentProvider.copyProvider(
                        models = parentProvider.models + it.filter { model ->
                            parentProvider.models.none { existing -> existing.modelId == model.modelId }
                        }.map { model ->
                            model.copy(
                                inputModalities = ModelRegistry.MODEL_INPUT_MODALITIES.getData(model.modelId),
                                outputModalities = ModelRegistry.MODEL_OUTPUT_MODALITIES.getData(model.modelId),
                                abilities = ModelRegistry.MODEL_ABILITIES.getData(model.modelId)
                            )
                        }
                    )
                )
            },
            onAllModelDeselected = { filteredModels ->
                onUpdateProvider(
                    parentProvider.copyProvider(
                        models = parentProvider.models.filter { model ->
                            filteredModels.none { filtered -> filtered.modelId == model.modelId }
                        }
                    )
                )
            }
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {
                    dialogState.open(Model())
                }
            ) {
                Icon(
                    HugeIcons.Add01,
                    contentDescription = stringResource(R.string.setting_provider_page_add_model),
                    tint = c.ink2,
                )
            }
            AnimatedVisibility(expanded) {
                PalaceTextButton(
                    stringResource(R.string.setting_provider_page_add_new_model),
                    onClick = {
                        dialogState.open(Model())
                    },
                )
            }
        }
    }

    if (dialogState.isEditing) {
        dialogState.currentState?.let { modelState ->
            val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
            ModalBottomSheet(
                onDismissRequest = {
                    dialogState.dismiss()
                },
                sheetState = sheetState,
                sheetGesturesEnabled = false,
                containerColor = c.bgTop,
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                dragHandle = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                sheetState.hide()
                                dialogState.dismiss()
                            }
                        }
                    ) {
                        Icon(HugeIcons.ArrowDown01, null, tint = c.ink2)
                    }
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.95f)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.setting_provider_page_add_model),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = c.ink,
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        ModelSettingsForm(
                            model = modelState,
                            onModelChange = { dialogState.currentState = it },
                            isEdit = false,
                            parentProvider = parentProvider
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        PalaceTextButton(
                            stringResource(R.string.cancel),
                            onClick = {
                                dialogState.dismiss()
                            },
                        )
                        PalaceTextButton(
                            stringResource(R.string.setting_provider_page_add),
                            onClick = {
                                if (modelState.modelId.isNotBlank() && modelState.displayName.isNotBlank()) {
                                    dialogState.confirm()
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelPicker(
    models: List<Model>,
    selectedModels: List<Model>,
    onModelSelected: (Model) -> Unit,
    onModelDeselected: (Model) -> Unit,
    onAllModelSelected: (List<Model>) -> Unit,
    onAllModelDeselected: (List<Model>) -> Unit
) {
    val c = palaceColors()
    var showModal by remember { mutableStateOf(false) }
    if (showModal) {
        ModalBottomSheet(
            onDismissRequest = { showModal = false },
            sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
            containerColor = c.bgTop,
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        ) {
            var filterText by remember { mutableStateOf("") }
            val filterKeywords = filterText.split(" ").filter { it.isNotBlank() }
            val filteredModels = models.fastFilter {
                if (filterKeywords.isEmpty()) {
                    true
                } else {
                    filterKeywords.all { keyword ->
                        it.modelId.contains(keyword, ignoreCase = true) ||
                            it.displayName.contains(keyword, ignoreCase = true)
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f)
                    .padding(8.dp)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 标题栏和添加所有按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.setting_provider_page_avaliable_models),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = c.ink,
                    )

                    val unselectedCount = filteredModels.count { model ->
                        selectedModels.none { it.modelId == model.modelId }
                    }

                    PalaceTextButton(
                        if (unselectedCount > 0) stringResource(
                            R.string.setting_provider_page_select_all,
                            unselectedCount
                        ) else stringResource(R.string.setting_provider_page_deselect_models),
                        onClick = {
                            if (unselectedCount > 0) {
                                onAllModelSelected(filteredModels)
                            } else {
                                onAllModelDeselected(filteredModels)
                            }
                        },
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                    contentPadding = PaddingValues(8.dp),
                ) {
                    itemsIndexed(filteredModels) { index, it ->
                        Surface(
                            shape = cardGroupItemShape(index, filteredModels.size),
                            color = c.chip,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(
                                    12.dp
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
                            ) {
                                AutoAIIcon(
                                    it.modelId,
                                    Modifier.size(32.dp)
                                )
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(
                                        4.dp
                                    ),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        text = it.modelId,
                                        fontSize = 14.sp,
                                        color = c.ink,
                                    )

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        val modelMeta = remember(it) {
                                            it.copy(
                                                inputModalities = ModelRegistry.MODEL_INPUT_MODALITIES.getData(it.modelId),
                                                outputModalities = ModelRegistry.MODEL_OUTPUT_MODALITIES.getData(it.modelId),
                                                abilities = ModelRegistry.MODEL_ABILITIES.getData(it.modelId),
                                            )
                                        }
                                        ModelModalityTag(
                                            model = modelMeta,
                                        )
                                        ModelAbilityTag(
                                            model = modelMeta,
                                        )
                                    }
                                }
                                val added = selectedModels.any { model -> model.modelId == it.modelId }
                                FilledTonalIconToggleButton(
                                    colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
                                        containerColor = c.glassStrong,
                                        contentColor = c.ink2,
                                        checkedContainerColor = c.accent,
                                        checkedContentColor = c.onAccent,
                                    ),
                                    checked = added,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            onModelSelected(it)
                                        } else {
                                            // 从selectedModels中计算出要删除的model，因为删除需要id匹配，而不是ModelId
                                            onModelDeselected(selectedModels.firstOrNull { model -> model.modelId == it.modelId }
                                                ?: it)
                                        }
                                    }
                                ) {
                                    Icon(if (added) HugeIcons.Tick01 else HugeIcons.Add01, null)
                                }
                            }
                        }
                    }
                }
                PalaceField(
                    value = filterText,
                    onValueChange = {
                        filterText = it
                    },
                    label = stringResource(R.string.setting_provider_page_filter_placeholder),
                    placeholder = stringResource(R.string.setting_provider_page_filter_example),
                )
            }
        }
    }
    BadgedBox(
        badge = {
            if (models.isNotEmpty()) {
                Badge {
                    Text(models.size.toString())
                }
            }
        }
    ) {
        IconButton(
            onClick = {
                showModal = true
            }
        ) {
            Icon(HugeIcons.Package01, null, tint = c.ink2)
        }
    }
}

@Composable
private fun ModelTypeSelector(
    selectedType: ModelType,
    onTypeSelected: (ModelType) -> Unit
) {
    Text(
        stringResource(R.string.setting_provider_page_model_type),
        fontSize = 14.sp,
        color = palaceColors().ink,
    )
    ConnectedChoiceButtons(
        options = ModelType.entries,
        selected = selectedType,
        onSelect = onTypeSelected,
    ) { type, selected ->
        ConnectedChoiceLabel(
            text = stringResource(
                when (type) {
                    ModelType.CHAT -> R.string.setting_provider_page_chat_model
                    ModelType.EMBEDDING -> R.string.setting_provider_page_embedding_model
                    ModelType.IMAGE -> R.string.setting_provider_page_image_model
                }
            ),
            selected = selected,
        )
    }
}

@Composable
private fun ModelModalitySelector(
    model: Model,
    inputModalities: List<Modality>,
    onUpdateInputModalities: (List<Modality>) -> Unit,
    outputModalities: List<Modality>,
    onUpdateOutputModalities: (List<Modality>) -> Unit
) {
    if (model.type == ModelType.CHAT) {
        Text(
            stringResource(R.string.setting_provider_page_input_modality),
            fontSize = 14.sp,
            color = palaceColors().ink,
        )
        ConnectedMultiChoiceButtons(
            options = Modality.entries,
            selected = inputModalities,
            onToggle = { modality, checked ->
                onUpdateInputModalities(if (checked) inputModalities + modality else inputModalities - modality)
            },
        ) { modality, selected ->
            ConnectedChoiceLabel(
                text = stringResource(
                    when (modality) {
                        Modality.TEXT -> R.string.setting_provider_page_text
                        Modality.IMAGE -> R.string.setting_provider_page_image
                    }
                ),
                selected = selected,
                icon = HugeIcons.Tick01,
            )
        }

        Text(
            stringResource(R.string.setting_provider_page_output_modality),
            fontSize = 14.sp,
            color = palaceColors().ink,
        )
        ConnectedMultiChoiceButtons(
            options = Modality.entries,
            selected = outputModalities,
            onToggle = { modality, checked ->
                onUpdateOutputModalities(if (checked) outputModalities + modality else outputModalities - modality)
            },
        ) { modality, selected ->
            ConnectedChoiceLabel(
                text = stringResource(
                    when (modality) {
                        Modality.TEXT -> R.string.setting_provider_page_text
                        Modality.IMAGE -> R.string.setting_provider_page_image
                    }
                ),
                selected = selected,
                icon = HugeIcons.Tick01,
            )
        }
    }
}

@Composable
fun ModalAbilitySelector(
    abilities: List<ModelAbility>,
    onUpdateAbilities: (List<ModelAbility>) -> Unit
) {
    Text(
        stringResource(R.string.setting_provider_page_abilities),
        fontSize = 14.sp,
        color = palaceColors().ink,
    )
    ConnectedMultiChoiceButtons(
        options = ModelAbility.entries,
        selected = abilities,
        onToggle = { ability, checked ->
            onUpdateAbilities(if (checked) abilities + ability else abilities - ability)
        },
    ) { ability, selected ->
        ConnectedChoiceLabel(
            text = stringResource(
                when (ability) {
                    ModelAbility.TOOL -> R.string.setting_provider_page_tool
                    ModelAbility.REASONING -> R.string.setting_provider_page_reasoning
                }
            ),
            selected = selected,
            icon = HugeIcons.Tick01,
        )
    }
}

// 常见的上下文窗口大小，其余的走自定义输入
private val ContextLengthPresets = listOf(128_000, 200_000, 1_000_000)

@Composable
private fun ModelContextLengthSelector(
    contextLength: Int,
    onUpdateContextLength: (Int) -> Unit
) {
    var custom by remember { mutableStateOf(contextLength !in ContextLengthPresets) }
    // 输入过程中允许为空，只有合法的正整数才写回模型
    var customText by remember { mutableStateOf(contextLength.toString()) }
    val c = palaceColors()

    Text(
        stringResource(R.string.setting_provider_page_context_length),
        fontSize = 14.sp,
        color = c.ink,
    )
    // null 表示自定义
    ConnectedChoiceButtons(
        options = ContextLengthPresets + null,
        selected = if (custom) null else contextLength,
        onSelect = { preset ->
            custom = preset == null
            if (preset != null) {
                customText = preset.toString()
                onUpdateContextLength(preset)
            }
        },
    ) { preset, selected ->
        ConnectedChoiceLabel(
            text = preset?.formatNumber() ?: stringResource(R.string.setting_provider_page_context_length_custom),
            selected = selected,
        )
    }
    AnimatedVisibility(visible = custom) {
        PalaceField(
            value = customText,
            onValueChange = { value ->
                customText = value.filter { it.isDigit() }
                customText.toIntOrNull()?.takeIf { it > 0 }?.let(onUpdateContextLength)
            },
            label = "tokens",
            number = true,
            singleLine = true,
            isError = (customText.toIntOrNull() ?: 0) <= 0,
        )
    }
}

@Composable
private fun ModelCard(
    model: Model,
    modifier: Modifier = Modifier,
    onDelete: () -> Unit,
    onEdit: (Model) -> Unit,
    parentProvider: ProviderSetting
) {
    val dialogState = useEditState<Model> {
        onEdit(it.copy(displayName = it.displayName.trim()))
    }
    val c = palaceColors()
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }


    if (dialogState.isEditing) {
        dialogState.currentState?.let { editingModel ->
            val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
            ModalBottomSheet(
                onDismissRequest = {
                    dialogState.dismiss()
                },
                sheetState = sheetState,
                sheetGesturesEnabled = false,
                dragHandle = null,
                containerColor = c.bgTop,
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.95f)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    sheetState.hide()
                                    dialogState.dismiss()
                                }
                            },
                            modifier = Modifier.align(Alignment.CenterStart)
                        ) {
                            Icon(HugeIcons.Cancel01, null, tint = c.ink2)
                        }
                        Text(
                            text = stringResource(R.string.setting_provider_page_edit_model),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = c.ink,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        ModelSettingsForm(
                            model = editingModel,
                            onModelChange = { dialogState.currentState = it },
                            isEdit = true,
                            parentProvider = parentProvider
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        PalaceTextButton(
                            stringResource(R.string.cancel),
                            onClick = {
                                dialogState.dismiss()
                            },
                        )
                        PalaceTextButton(
                            stringResource(R.string.confirm),
                            onClick = {
                                if (editingModel.displayName.isNotBlank()) {
                                    dialogState.confirm()
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    run {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clickable { dialogState.open(model.copy()) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(c.chip),
                contentAlignment = Alignment.Center,
            ) {
                AutoAIIcon(
                    name = model.modelId,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = model.displayName,
                    fontSize = 14.sp,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (model.providerOverwrite != null) {
                        Tag(type = TagType.INFO) {
                            Text(
                                model.providerOverwrite?.javaClass?.simpleName ?: model.providerOverwrite?.name
                                ?: "ProviderOverwrite"
                            )
                        }
                    }
                    ModelTypeTag(model = model)
                    ModelModalityTag(model = model)
                    ModelAbilityTag(model = model)
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

    if (showDeleteDialog) {
        PalaceDialog(
            title = stringResource(R.string.confirm_delete),
            onDismiss = { showDeleteDialog = false },
            confirmText = stringResource(R.string.delete),
            onConfirm = {
                showDeleteDialog = false
                onDelete()
            },
            dismissText = stringResource(R.string.cancel),
        ) {
            Text(
                stringResource(R.string.common_delete_confirm_message, model.displayName),
                fontSize = 14.sp,
                color = c.ink,
            )
        }
    }
}

@Composable
private fun BuiltInToolsSettings(
    tools: Set<BuiltInTools>,
    onUpdateTools: (Set<BuiltInTools>) -> Unit
) {
    val c = palaceColors()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.setting_page_built_in_tools),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = c.ink,
        )

        Text(
            text = stringResource(R.string.setting_page_built_in_tools_desc),
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            color = c.mute,
        )

        val availableTools = listOf(
            BuiltInTools.Search to Pair(
                stringResource(R.string.setting_page_built_in_tools_search),
                stringResource(R.string.setting_page_built_in_tools_search_desc)
            ),
            BuiltInTools.UrlContext to Pair(
                stringResource(R.string.setting_page_built_in_tools_url_context),
                stringResource(R.string.setting_page_built_in_tools_url_context_desc)
            ),
            BuiltInTools.ImageGeneration to Pair(
                stringResource(R.string.setting_page_built_in_tools_image_generation),
                stringResource(R.string.setting_page_built_in_tools_image_generation_desc)
            )
        )

        PalaceGlass {
            availableTools.forEachIndexed { index, (tool, info) ->
                val (title, description) = info
                val checked = tool in tools
                if (index > 0) PalaceDivider()
                PalaceSwitchRow(
                    title = title,
                    desc = description,
                    checked = checked,
                    onChange = { enabled ->
                        onUpdateTools(if (enabled) tools + tool else tools - tool)
                    },
                )
            }
        }
    }
}

@Composable
private fun ProviderOverrideSettings(
    providerOverride: ProviderSetting?,
    onUpdateProviderOverride: (ProviderSetting?) -> Unit,
    parentProvider: ProviderSetting?
) {
    val c = palaceColors()
    var showProviderConfig by remember { mutableStateOf(false) }
    var editingProvider by remember { mutableStateOf<ProviderSetting?>(null) }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.setting_provider_page_provider_override),
            fontSize = 14.sp,
            color = c.ink,
        )

        Text(
            text = stringResource(R.string.setting_provider_page_provider_override_desc),
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            color = c.mute,
        )

        if (providerOverride != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = c.chip,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AutoAIIcon(
                            providerOverride.name,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "${providerOverride.name} (Override)",
                            fontSize = 14.sp,
                            color = c.ink,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                editingProvider = providerOverride
                                showProviderConfig = true
                            }
                        ) {
                            Icon(HugeIcons.Tools, contentDescription = "Edit override", tint = c.ink2)
                        }
                        IconButton(
                            onClick = {
                                onUpdateProviderOverride(null)
                            }
                        ) {
                            Icon(HugeIcons.Cancel01, contentDescription = "Remove override", tint = c.ink2)
                        }
                    }
                }
            }
        } else {
            PalaceGlass {
                PalaceRow(
                    icon = HugeIcons.Add01,
                    title = stringResource(R.string.setting_provider_page_add_provider_override),
                    onClick = {
                        editingProvider = parentProvider?.copyProvider(
                            id = Uuid.random(),
                            builtIn = false,
                            models = emptyList(), // 这里必须设置为空，不然会导致循环依赖JSON
                            description = {},
                        )
                        showProviderConfig = true
                    },
                )
            }
        }

        // Provider configuration modal
        if (showProviderConfig && editingProvider != null) {
            ModalBottomSheet(
                onDismissRequest = {
                    showProviderConfig = false
                    editingProvider = null
                },
                sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
                // 和服务商详情页同一个底色，里面的分段表单才分得开
                containerColor = c.bgTop,
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            ) {
                var internalProvider by remember(editingProvider) { mutableStateOf(editingProvider!!) }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.9f)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.setting_provider_page_configure_provider_override),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = c.ink,
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ProviderConfigure(
                            provider = internalProvider,
                            allowTypeSwitch = true,
                            onEdit = { internalProvider = it }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        PalaceTextButton(
                            stringResource(R.string.cancel),
                            onClick = {
                                showProviderConfig = false
                                editingProvider = null
                            },
                        )
                        PalaceTextButton(
                            stringResource(R.string.setting_provider_page_save),
                            onClick = {
                                onUpdateProviderOverride(internalProvider.copyProvider(name = internalProvider.name.trim()))
                                showProviderConfig = false
                                editingProvider = null
                            },
                        )
                    }
                }
            }
        }
    }
}
