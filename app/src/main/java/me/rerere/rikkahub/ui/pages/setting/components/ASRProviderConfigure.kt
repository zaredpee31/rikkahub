package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.asr.ASRProviderSetting
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.utils.openUrl

val ASRProviderSetting.typeName: String
    get() = when (this) {
        is ASRProviderSetting.SiliconFlow -> "SiliconFlow"
    }

@Composable
fun ASRProviderConfigure(
    setting: ASRProviderSetting,
    modifier: Modifier = Modifier,
    onValueChange: (ASRProviderSetting) -> Unit
) {
    val context = LocalContext.current
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.verticalScroll(rememberScrollState())
    ) {
        CardGroup {
            when (setting) {
                is ASRProviderSetting.SiliconFlow -> {
                    formItem(
                        headlineContent = { Text(stringResource(R.string.setting_asr_configure_api_key)) },
                        supportingContent = { Text(stringResource(R.string.setting_asr_configure_siliconflow_desc)) }
                    ) {
                        OutlinedTextField(
                            value = setting.apiKey,
                            onValueChange = { onValueChange(setting.copy(apiKey = it.trim())) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("sk-...") },
                            singleLine = true,
                        )
                        TextButton(
                            onClick = { context.openUrl(ASRProviderSetting.SILICONFLOW_KEY_URL) },
                        ) {
                            Text(stringResource(R.string.setting_asr_configure_siliconflow_get_key))
                        }
                    }
                }
            }
        }
    }
}
