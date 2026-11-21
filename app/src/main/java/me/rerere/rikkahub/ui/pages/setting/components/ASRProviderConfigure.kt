package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.asr.ASRProviderSetting
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.View
import me.rerere.hugeicons.stroke.ViewOff
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceGlass
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceSection
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceTextButton
import me.rerere.rikkahub.ui.theme.palaceColors
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
    val c = palaceColors()
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.verticalScroll(rememberScrollState())
    ) {
        PalaceSection("ASR", "语音识别")
        PalaceGlass {
            when (setting) {
                is ASRProviderSetting.SiliconFlow -> {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(stringResource(R.string.setting_asr_configure_api_key), fontSize = 14.sp, color = c.ink)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            stringResource(R.string.setting_asr_configure_siliconflow_desc),
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            color = c.mute,
                        )
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = setting.apiKey,
                            onValueChange = { onValueChange(setting.copy(apiKey = it.trim())) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("sk-...") },
                            singleLine = true,
                        )
                        PalaceTextButton(
                            text = stringResource(R.string.setting_asr_configure_siliconflow_get_key),
                            onClick = { context.openUrl(ASRProviderSetting.SILICONFLOW_KEY_URL) },
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("耳朵地址", fontSize = 14.sp, color = c.ink)
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = setting.earsUrl,
                            onValueChange = { onValueChange(setting.copy(earsUrl = it.trim())) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("https://...") },
                            singleLine = true,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text("耳朵钥匙", fontSize = 14.sp, color = c.ink)
                        Spacer(Modifier.height(6.dp))
                        var earsKeyVisible by remember { mutableStateOf(false) }
                        OutlinedTextField(
                            value = setting.earsKey,
                            onValueChange = { onValueChange(setting.copy(earsKey = it.trim())) },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (earsKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { earsKeyVisible = !earsKeyVisible }) {
                                    Icon(
                                        imageVector = if (earsKeyVisible) HugeIcons.ViewOff else HugeIcons.View,
                                        contentDescription = if (earsKeyVisible) "隐藏耳朵钥匙" else "显示耳朵钥匙",
                                    )
                                }
                            },
                            singleLine = true,
                        )
                    }
                }
            }
        }
    }
}
