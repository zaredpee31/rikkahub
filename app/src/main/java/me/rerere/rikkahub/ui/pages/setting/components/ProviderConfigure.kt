package me.rerere.rikkahub.ui.pages.setting.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import me.rerere.ui.toast.ToastType
import me.rerere.ai.provider.ClaudePromptCacheTtl
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.ProviderQuota
import me.rerere.ai.provider.UsageWindow
import me.rerere.ai.provider.supportsQuota
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.account.AccountLoginStatus
import me.rerere.rikkahub.data.ai.account.ChatGPTAuth
import me.rerere.oauth.CustomTabsOAuthAuthorizationLauncher
import me.rerere.rikkahub.data.ai.account.ProviderAccountManager
import me.rerere.rikkahub.data.datastore.DEFAULT_PROVIDERS
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.DashboardSpeed02
import me.rerere.hugeicons.stroke.FileImport
import me.rerere.hugeicons.stroke.Login03
import me.rerere.hugeicons.stroke.Logout03
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.hugeicons.stroke.UserCircle
import me.rerere.hugeicons.stroke.View
import me.rerere.hugeicons.stroke.ViewOff
import me.rerere.rikkahub.ui.components.ai.longLabel
import me.rerere.rikkahub.ui.components.ai.rememberProviderQuota
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceButtons
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceLabel
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceDivider
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceGlass
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceSection
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceSwitchRow
import me.rerere.rikkahub.ui.theme.JetbrainsMono
import me.rerere.rikkahub.ui.theme.palaceColors
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import me.rerere.rikkahub.utils.toMessageTimeString
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlin.reflect.KClass
import kotlin.time.toJavaInstant

@Composable
fun ProviderConfigure(
    provider: ProviderSetting,
    modifier: Modifier = Modifier,
    allowTypeSwitch: Boolean = false,
    onEdit: (provider: ProviderSetting) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        provider.description()

        PalaceSection("GENERAL", "通用")
        PalaceGlass {
            // 提供商创建后类型就定死了，只有模型的提供商覆盖可以换类型
            if (allowTypeSwitch) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    ConnectedChoiceButtons(
                        options = ProviderSetting.Types,
                        selected = provider::class,
                        onSelect = { onEdit(provider.convertTo(it)) },
                    ) { type, selected ->
                        ConnectedChoiceLabel(text = type.simpleName ?: "", selected = selected)
                    }
                }
                PalaceDivider()
            }
            PalaceSwitchRow(
                title = stringResource(R.string.setting_provider_page_enable),
                desc = null,
                checked = provider.enabled,
                onChange = { onEdit(provider.copyProvider(enabled = it)) },
            )
        }

        when (provider) {
            is ProviderSetting.OpenAI -> ProviderConfigureOpenAI(provider, onEdit)
            is ProviderSetting.Google -> ProviderConfigureGoogle(provider, onEdit)
            is ProviderSetting.Claude -> ProviderConfigureClaude(provider, onEdit)
            is ProviderSetting.ChatGPT -> ProviderConfigureAccount(
                provider = provider,
                onEdit = onEdit,
                serviceName = "ChatGPT",
                plan = provider.credential?.plan?.let { "ChatGPT ${ChatGPTAuth.planDisplayName(it)}" },
                disclaimer = stringResource(R.string.setting_provider_page_chatgpt_disclaimer),
            )

            is ProviderSetting.Grok -> ProviderConfigureAccount(
                provider = provider,
                onEdit = onEdit,
                serviceName = "Grok",
                plan = null,
                disclaimer = stringResource(R.string.setting_provider_page_grok_disclaimer),
            )
        }
    }
}

fun ProviderSetting.convertTo(type: KClass<out ProviderSetting>): ProviderSetting {
    if (this::class == type) return this

    // 账号凭据不能带到可以自填地址的类型上，从账号类转过来时 key、地址和额度查询都用目标类型的默认值
    val source = this as? ProviderSetting.ApiKey
    val apiKey = source?.apiKey.orEmpty()
    val targetDefaultBaseUrl = when (type) {
        ProviderSetting.OpenAI::class -> ProviderSetting.OpenAI().baseUrl
        ProviderSetting.Google::class -> ProviderSetting.Google().baseUrl
        ProviderSetting.Claude::class -> ProviderSetting.Claude().baseUrl
        else -> error("Unsupported provider type: $type")
    }
    val convertedBaseUrl = source?.baseUrl?.convertToTargetBaseUrl(targetDefaultBaseUrl) ?: targetDefaultBaseUrl

    val converted: ProviderSetting.ApiKey = when (type) {
        ProviderSetting.OpenAI::class -> ProviderSetting.OpenAI(
            id = this.id, enabled = this.enabled, name = this.name, models = this.models,
            customHeaders = this.customHeaders, builtIn = this.builtIn,
            description = this.description, shortDescription = this.shortDescription,
            apiKey = apiKey, baseUrl = convertedBaseUrl
        )
        ProviderSetting.Google::class -> ProviderSetting.Google(
            id = this.id, enabled = this.enabled, name = this.name, models = this.models,
            customHeaders = this.customHeaders, builtIn = this.builtIn,
            description = this.description, shortDescription = this.shortDescription,
            apiKey = apiKey, baseUrl = convertedBaseUrl
        )
        ProviderSetting.Claude::class -> ProviderSetting.Claude(
            id = this.id, enabled = this.enabled, name = this.name, models = this.models,
            customHeaders = this.customHeaders, builtIn = this.builtIn,
            description = this.description, shortDescription = this.shortDescription,
            apiKey = apiKey, baseUrl = convertedBaseUrl
        )
        else -> error("Unsupported provider type: $type")
    }
    // 沿用用户配的额度查询
    return source?.let { converted.withQuotaQuery(it.quotaQuery) } ?: converted
}

internal fun ProviderSetting.ApiKey.defaultBaseUrlForReset(): String {
    val defaultProvider = DEFAULT_PROVIDERS.find { it.id == id }
    if (defaultProvider is ProviderSetting.ApiKey && defaultProvider::class == this::class) {
        return defaultProvider.baseUrl
    }
    return when (this) {
        is ProviderSetting.OpenAI -> ProviderSetting.OpenAI().baseUrl
        is ProviderSetting.Google -> ProviderSetting.Google().baseUrl
        is ProviderSetting.Claude -> ProviderSetting.Claude().baseUrl
    }
}

internal fun ProviderSetting.ApiKey.isUsingDefaultBaseUrl(): Boolean = baseUrl == defaultBaseUrlForReset()

// 地址不是默认值时，输入框右侧出现的恢复默认按钮
private fun resetBaseUrlButton(
    provider: ProviderSetting.ApiKey,
    onReset: () -> Unit,
): (@Composable () -> Unit)? {
    if (provider.isUsingDefaultBaseUrl()) return null
    return {
        IconButton(onClick = onReset) {
            Icon(
                imageVector = HugeIcons.Refresh03,
                contentDescription = stringResource(R.string.setting_model_page_reset_to_default),
            )
        }
    }
}

private fun String.convertToTargetBaseUrl(targetDefaultBaseUrl: String): String {
    val sourceUrl = this.toHttpUrlOrNull() ?: return this
    val sourceHost = sourceUrl.host.lowercase()
    if (sourceHost in OFFICIAL_PROVIDER_HOSTS) return targetDefaultBaseUrl
    val targetUrl = targetDefaultBaseUrl.toHttpUrlOrNull() ?: return this
    val convertedPath = sourceUrl.encodedPath.convertToTargetPath(targetUrl.encodedPath)
    return sourceUrl.newBuilder().encodedPath(convertedPath).build().toString()
}

private fun String.convertToTargetPath(targetPath: String): String {
    val source = this.normalizePath()
    val target = targetPath.normalizePath()
    val replaced = when {
        source.lowercase().endsWith(V1_BETA_SUFFIX) -> source.dropLast(V1_BETA_SUFFIX.length) + target
        source.lowercase().endsWith(V1_SUFFIX) -> source.dropLast(V1_SUFFIX.length) + target
        source.isBlank() -> target
        else -> source + target
    }
    return replaced.normalizePath()
}

private fun String.normalizePath(): String {
    val value = this.trim()
    if (value.isEmpty() || value == "/") return ""
    val path = if (value.startsWith("/")) value else "/$value"
    return path.trimEnd('/')
}

private fun String.isValidBaseUrl(): Boolean = this.toHttpUrlOrNull() != null

private const val OPENAI_OFFICIAL_HOST = "api.openai.com"
private const val GOOGLE_OFFICIAL_HOST = "generativelanguage.googleapis.com"
private const val CLAUDE_OFFICIAL_HOST = "api.anthropic.com"
private const val V1_SUFFIX = "/v1"
private const val V1_BETA_SUFFIX = "/v1beta"
private val OFFICIAL_PROVIDER_HOSTS = setOf(
    OPENAI_OFFICIAL_HOST,
    GOOGLE_OFFICIAL_HOST,
    CLAUDE_OFFICIAL_HOST
)

// 标题在上、输入框在下的表单行，放在 PalaceGlass 里用：标签和说明照 PalaceInputRow，输入框保留原控件
@Composable
private fun ProviderFormRow(
    label: String,
    desc: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val c = palaceColors()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 14.sp, color = c.ink)
                if (!desc.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(desc, fontSize = 11.5.sp, lineHeight = 16.sp, color = c.mute)
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        }
        Spacer(Modifier.height(6.dp))
        content()
    }
}

// 带图标的列表行，数值照 DetailNavRow；onClick 为空时不可点
@Composable
private fun ProviderRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    desc: String? = null,
    descError: Boolean = false,
    titleColor: androidx.compose.ui.graphics.Color? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    titleContent: (@Composable () -> Unit)? = null,
) {
    val c = palaceColors()
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(c.chip),
            contentAlignment = Alignment.Center,
        ) {
            if (leading != null) leading() else if (icon != null) Icon(icon, null, Modifier.size(16.dp), tint = c.ink2)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (titleContent != null) {
                titleContent()
            } else {
                Text(title, fontSize = 14.sp, color = titleColor ?: c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!desc.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    desc,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    color = if (descError) c.error else c.mute,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

// 行内的转圈，尺寸照 SettingMcpPage 状态图标
@Composable
private fun ProviderSpinner() {
    val c = palaceColors()
    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = c.ink2)
}

// 标题在上、输入框在下的表单项；secret 为 true 时默认遮住内容，右侧是显示/隐藏按钮
@Composable
private fun TextFieldRow(
    @StringRes label: Int,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
    isError: Boolean = false,
    secret: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    monospace: Boolean = false,
    supportingText: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) = ProviderFormRow(label = stringResource(label)) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        isError = isError,
        minLines = minLines,
        maxLines = maxLines,
        textStyle = if (monospace) {
            MaterialTheme.typography.bodySmall.copy(fontFamily = JetbrainsMono)
        } else LocalTextStyle.current,
        supportingText = supportingText,
        visualTransformation = if (secret && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (secret) {
            {
                IconButton(onClick = { visible = !visible }) {
                    Icon(if (visible) HugeIcons.ViewOff else HugeIcons.View, contentDescription = null)
                }
            }
        } else trailingIcon,
    )
}

@Composable
private fun NameRow(
    provider: ProviderSetting,
    onEdit: (ProviderSetting) -> Unit,
) = TextFieldRow(
    label = R.string.setting_provider_page_name,
    value = provider.name,
    onValueChange = { onEdit(provider.copyProvider(name = it)) },
)

@Composable
private fun ApiKeyRow(
    apiKey: String,
    onValueChange: (String) -> Unit,
) = TextFieldRow(
    label = R.string.setting_provider_page_api_key,
    value = apiKey,
    onValueChange = { onValueChange(it.trim()) },
    secret = true,
    maxLines = 3,
)

@Composable
private fun ProviderConfigureOpenAI(
    provider: ProviderSetting.OpenAI,
    onEdit: (provider: ProviderSetting) -> Unit
) {
    val toaster = LocalToaster.current
    val responseAPIWarning = stringResource(R.string.setting_provider_page_response_api_warning)

    PalaceSection("CONNECTION", "连接")
    PalaceGlass {
        NameRow(provider, onEdit)
        PalaceDivider()
        ApiKeyRow(provider.apiKey) { onEdit(provider.copy(apiKey = it)) }
        PalaceDivider()
        TextFieldRow(
            label = R.string.setting_provider_page_api_base_url,
            value = provider.baseUrl,
            onValueChange = { onEdit(provider.copy(baseUrl = it.trim())) },
            isError = provider.baseUrl.isNotBlank() && !provider.baseUrl.isValidBaseUrl(),
            trailingIcon = resetBaseUrlButton(provider) {
                onEdit(provider.copy(baseUrl = provider.defaultBaseUrlForReset()))
            },
        )
        PalaceDivider()
        TextFieldRow(
            label = R.string.setting_provider_page_api_path,
            value = if (provider.useResponseApi) provider.responsesPath else provider.chatCompletionsPath,
            onValueChange = {
                onEdit(
                    if (provider.useResponseApi) {
                        provider.copy(responsesPath = it.trim())
                    } else {
                        provider.copy(chatCompletionsPath = it.trim())
                    }
                )
            },
            enabled = !provider.builtIn,
        )
    }

    PalaceSection("OPTIONS", "选项")
    PalaceGlass {
        PalaceSwitchRow(
            title = stringResource(R.string.setting_provider_page_response_api),
            desc = null,
            checked = provider.useResponseApi,
            onChange = {
                onEdit(provider.copy(useResponseApi = it))
                if (it && provider.baseUrl.toHttpUrlOrNull()?.host != "api.openai.com") {
                    toaster.show(message = responseAPIWarning, type = ToastType.Warning)
                }
            },
        )
        PalaceDivider()
        PalaceSwitchRow(
            title = stringResource(R.string.setting_provider_page_include_history_reasoning),
            desc = null,
            checked = provider.includeHistoryReasoning,
            onChange = { onEdit(provider.copy(includeHistoryReasoning = it)) },
        )
    }
}

// 账号类提供商：登录状态、用量和说明
@Composable
private fun ProviderConfigureAccount(
    provider: ProviderSetting.Account,
    onEdit: (provider: ProviderSetting) -> Unit,
    serviceName: String,
    plan: String?,
    disclaimer: String,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = palaceColors()
    val accountManager = koinInject<ProviderAccountManager>()
    val loginStatus by accountManager.loginStatus.collectAsStateWithLifecycle()
    val status = loginStatus[provider.id]
    val credential = provider.credential

    PalaceSection("PROFILE", "资料")
    PalaceGlass {
        NameRow(provider, onEdit)
    }

    PalaceSection("ACCOUNT", stringResource(R.string.setting_provider_page_account))
    PalaceGlass {
        if (credential != null) {
            ProviderRow(
                title = credential.email ?: stringResource(R.string.setting_provider_page_signed_in),
                icon = HugeIcons.UserCircle,
                desc = plan,
            )
            PalaceDivider()
            ProviderRow(
                title = stringResource(R.string.setting_provider_page_sign_out),
                icon = HugeIcons.Logout03,
                titleColor = c.error,
                onClick = { scope.launch { accountManager.logout(provider.id) } },
            )
        } else if (status is AccountLoginStatus.DeviceCode) {
            ProviderRow(
                title = status.userCode,
                leading = { ProviderSpinner() },
                desc = stringResource(R.string.setting_provider_page_device_code_reopen),
                onClick = { CustomTabsOAuthAuthorizationLauncher.launch(context, status.verificationUrl) },
                titleContent = {
                    Text(stringResource(R.string.setting_provider_page_device_code_hint), fontSize = 11.5.sp, lineHeight = 16.sp, color = c.mute)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = status.userCode,
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        fontFamily = JetbrainsMono,
                        color = c.ink,
                    )
                },
            )
            PalaceDivider()
            ProviderRow(
                title = stringResource(R.string.cancel),
                icon = HugeIcons.Cancel01,
                onClick = { accountManager.cancelLogin(provider.id) },
            )
        } else if (status == AccountLoginStatus.Authorizing) {
            ProviderRow(
                title = stringResource(R.string.setting_provider_page_waiting_authorization),
                leading = { ProviderSpinner() },
                desc = stringResource(R.string.setting_provider_page_waiting_authorization_desc),
                onClick = { accountManager.cancelLogin(provider.id) },
            )
        } else {
            val failed = status as? AccountLoginStatus.Failed
            ProviderRow(
                title = stringResource(R.string.setting_provider_page_sign_in_with, serviceName),
                icon = HugeIcons.Login03,
                desc = failed?.message,
                descError = true,
                onClick = { accountManager.startLogin(provider.id, context) },
                trailing = { Icon(HugeIcons.ArrowRight01, null, Modifier.size(14.dp), tint = c.faint) },
            )
        }
    }

    if (provider.supportsQuota) {
        AccountUsage(provider)
    }

    Text(
        text = disclaimer,
        fontSize = 11.5.sp,
        lineHeight = 16.sp,
        color = c.mute,
        modifier = Modifier.padding(horizontal = 6.dp),
    )
}

// 订阅用量：每个窗口一条进度和重置时间
@Composable
private fun AccountUsage(provider: ProviderSetting.Account) {
    val c = palaceColors()
    val result = rememberProviderQuota(provider)
    val limits = result?.getOrNull() as? ProviderQuota.UsageLimits
    PalaceSection("USAGE", stringResource(R.string.setting_provider_page_usage))
    PalaceGlass {
        when {
            result == null -> ProviderRow(
                title = stringResource(R.string.setting_provider_page_usage_loading),
                leading = { ProviderSpinner() },
            )

            limits == null -> ProviderRow(
                title = stringResource(R.string.setting_provider_page_usage_failed),
                icon = HugeIcons.DashboardSpeed02,
                desc = result.exceptionOrNull()?.message.orEmpty(),
                descError = true,
            )

            limits.windows.isEmpty() -> ProviderRow(
                title = stringResource(R.string.setting_provider_page_usage_none),
                icon = HugeIcons.DashboardSpeed02,
            )

            else -> limits.windows.forEachIndexed { index, window ->
                if (index > 0) PalaceDivider()
                val reached = limits.limitReached || window.usedPercent >= 100
                val resetsAt = window.resetsAt
                ProviderFormRow(
                    label = window.longLabel(),
                    desc = resetsAt?.let {
                        val time = LocalDateTime.ofInstant(it.toJavaInstant(), ZoneId.systemDefault())
                        stringResource(R.string.setting_provider_page_usage_resets, time.toMessageTimeString())
                    },
                    trailing = {
                        Text(
                            text = "${window.usedPercent.roundToInt()}%",
                            fontSize = 14.sp,
                            color = if (reached) c.error else c.ink,
                        )
                    },
                ) {
                    LinearWavyProgressIndicator(
                        progress = { (window.usedPercent / 100).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (reached) c.error else c.accent,
                        trackColor = c.chip,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderConfigureClaude(
    provider: ProviderSetting.Claude,
    onEdit: (provider: ProviderSetting) -> Unit
) {
    PalaceSection("CONNECTION", "连接")
    PalaceGlass {
        NameRow(provider, onEdit)
        PalaceDivider()
        ApiKeyRow(provider.apiKey) { onEdit(provider.copy(apiKey = it)) }
        PalaceDivider()
        TextFieldRow(
            label = R.string.setting_provider_page_api_base_url,
            value = provider.baseUrl,
            onValueChange = { onEdit(provider.copy(baseUrl = it.trim())) },
            isError = provider.baseUrl.isNotBlank() && !provider.baseUrl.isValidBaseUrl(),
            trailingIcon = resetBaseUrlButton(provider) {
                onEdit(provider.copy(baseUrl = provider.defaultBaseUrlForReset()))
            },
        )
    }

    PalaceSection("CACHE", "缓存")
    PalaceGlass {
        PalaceSwitchRow(
            title = stringResource(R.string.setting_provider_page_claude_prompt_caching),
            desc = null,
            checked = provider.promptCaching,
            onChange = { onEdit(provider.copy(promptCaching = it)) },
        )
        if (provider.promptCaching) {
            PalaceDivider()
            ProviderFormRow(
                label = stringResource(R.string.setting_provider_page_claude_prompt_cache_ttl),
            ) {
                ConnectedChoiceButtons(
                    options = ClaudePromptCacheTtl.entries,
                    selected = provider.promptCacheTtl,
                    onSelect = { onEdit(provider.copy(promptCacheTtl = it)) },
                ) { ttl, selected ->
                    ConnectedChoiceLabel(
                        text = when (ttl) {
                            ClaudePromptCacheTtl.FIVE_MINUTES -> stringResource(R.string.setting_provider_page_claude_prompt_cache_ttl_5m)
                            ClaudePromptCacheTtl.ONE_HOUR -> stringResource(R.string.setting_provider_page_claude_prompt_cache_ttl_1h)
                        },
                        selected = selected,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderConfigureGoogle(
    provider: ProviderSetting.Google,
    onEdit: (provider: ProviderSetting) -> Unit
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val serviceAccountJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val content = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.readText()
                ?: return@rememberLauncherForActivityResult
            val json = Json.parseToJsonElement(content).jsonObject
            onEdit(
                provider.copy(
                    projectId = json["project_id"]?.jsonPrimitive?.contentOrNull?.ifEmpty { null } ?: provider.projectId,
                    serviceAccountEmail = json["client_email"]?.jsonPrimitive?.contentOrNull?.ifEmpty { null } ?: provider.serviceAccountEmail,
                    privateKey = json["private_key"]?.jsonPrimitive?.contentOrNull?.ifEmpty { null } ?: provider.privateKey,
                )
            )
            toaster.show("Service account imported", type = ToastType.Success)
        } catch (e: Exception) {
            toaster.show("Failed to import: ${e.message}", type = ToastType.Error)
        }
    }
    val useServiceAccount = provider.vertexAI && provider.useServiceAccount
    val c = palaceColors()

    PalaceSection("CONNECTION", "连接")
    PalaceGlass {
        NameRow(provider, onEdit)
        if (!useServiceAccount) {
            PalaceDivider()
            ApiKeyRow(provider.apiKey) { onEdit(provider.copy(apiKey = it)) }
        }
        if (!provider.vertexAI) {
            PalaceDivider()
            TextFieldRow(
                label = R.string.setting_provider_page_api_base_url,
                value = provider.baseUrl,
                onValueChange = { onEdit(provider.copy(baseUrl = it.trim())) },
                isError = provider.baseUrl.isNotBlank() && (
                    !provider.baseUrl.isValidBaseUrl() || !provider.baseUrl.endsWith("/v1beta")
                    ),
                supportingText = if (!provider.baseUrl.endsWith("/v1beta")) {
                    { Text("The base URL usually ends with `/v1beta`") }
                } else null,
                trailingIcon = resetBaseUrlButton(provider) {
                    onEdit(provider.copy(baseUrl = provider.defaultBaseUrlForReset()))
                },
            )
        }
    }

    PalaceSection("OPTIONS", "选项")
    PalaceGlass {
        PalaceSwitchRow(
            title = stringResource(R.string.setting_provider_page_vertex_ai),
            desc = null,
            checked = provider.vertexAI,
            onChange = { onEdit(provider.copy(vertexAI = it)) },
        )
        PalaceDivider()
        if (provider.vertexAI) {
            PalaceSwitchRow(
                title = stringResource(R.string.setting_provider_page_use_service_account),
                desc = null,
                checked = provider.useServiceAccount,
                onChange = { onEdit(provider.copy(useServiceAccount = it)) },
            )
        } else {
            PalaceSwitchRow(
                title = "Interactions API (Beta)",
                desc = null,
                checked = provider.useInteractionsApi,
                onChange = { onEdit(provider.copy(useInteractionsApi = it)) },
            )
        }
    }

    if (useServiceAccount) {
        PalaceSection("SERVICE", "服务账号")
        PalaceGlass {
            ProviderRow(
                title = stringResource(R.string.setting_provider_page_import_service_account_json),
                icon = HugeIcons.FileImport,
                onClick = { serviceAccountJsonLauncher.launch(arrayOf("application/json", "*/*")) },
                trailing = { Icon(HugeIcons.ArrowRight01, null, Modifier.size(14.dp), tint = c.faint) },
            )
            PalaceDivider()
            TextFieldRow(
                label = R.string.setting_provider_page_service_account_email,
                value = provider.serviceAccountEmail,
                onValueChange = { onEdit(provider.copy(serviceAccountEmail = it.trim())) },
            )
            PalaceDivider()
            TextFieldRow(
                label = R.string.setting_provider_page_private_key,
                value = provider.privateKey,
                onValueChange = { onEdit(provider.copy(privateKey = it.trim())) },
                secret = true,
                minLines = 3,
                maxLines = 6,
                monospace = true,
            )
            PalaceDivider()
            TextFieldRow(
                label = R.string.setting_provider_page_location,
                value = provider.location,
                onValueChange = { onEdit(provider.copy(location = it.trim())) },
            )
            PalaceDivider()
            TextFieldRow(
                label = R.string.setting_provider_page_project_id,
                value = provider.projectId,
                onValueChange = { onEdit(provider.copy(projectId = it.trim())) },
            )
        }
    }
}
