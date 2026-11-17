package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import me.rerere.rikkahub.R
import me.rerere.rikkahub.companion.MonoText
import me.rerere.rikkahub.data.datastore.BackgroundEffectType
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceButtons
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceLabel
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.hooks.rememberSharedPreferenceBoolean
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceBackdrop
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceDivider
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceGlass
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceSection
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceSliderRow
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceSwitchRow
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceTopBar
import me.rerere.rikkahub.ui.theme.palaceColors
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingPreferencesGeneralPage(vm: SettingVM = koinViewModel()) {
    val navController = LocalNavController.current
    val c = palaceColors()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var displaySetting by remember(settings) { mutableStateOf(settings.displaySetting) }
    var ttsPlaybackSpeed by remember(settings.defaultTTSPlaybackSpeed) {
        mutableFloatStateOf(settings.defaultTTSPlaybackSpeed)
    }

    fun updateDisplaySetting(setting: DisplaySetting) {
        displaySetting = setting
        vm.updateSettings { it.copy(displaySetting = setting) }
    }

    PalaceBackdrop {
        Column(Modifier.fillMaxSize()) {
            PalaceTopBar(
                title = stringResource(R.string.setting_page_preferences_general),
                sub = "GENERAL",
                onBack = { navController.popBackStack() },
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PalaceSection("CHAT", "会话")
                PalaceGlass {
                    var createNewConversationOnStart by rememberSharedPreferenceBoolean(
                        "create_new_conversation_on_start",
                        true
                    )
                    PalaceSwitchRow(
                        title = stringResource(R.string.setting_display_page_create_new_conversation_on_start_title),
                        desc = stringResource(R.string.setting_display_page_create_new_conversation_on_start_desc),
                        checked = createNewConversationOnStart,
                        onChange = { createNewConversationOnStart = it },
                    )
                    PalaceDivider()
                    PalaceSwitchRow(
                        title = stringResource(R.string.setting_display_page_send_on_enter_title),
                        desc = stringResource(R.string.setting_display_page_send_on_enter_desc),
                        checked = displaySetting.sendOnEnter,
                        onChange = { updateDisplaySetting(displaySetting.copy(sendOnEnter = it)) },
                    )
                }

                PalaceSection("SCROLL", "消息")
                PalaceGlass {
                    PalaceSwitchRow(
                        title = stringResource(R.string.setting_display_page_show_message_jumper_title),
                        desc = stringResource(R.string.setting_display_page_show_message_jumper_desc),
                        checked = displaySetting.showMessageJumper,
                        onChange = { updateDisplaySetting(displaySetting.copy(showMessageJumper = it)) },
                    )
                    if (displaySetting.showMessageJumper) {
                        PalaceDivider()
                        PalaceSwitchRow(
                            title = stringResource(R.string.setting_display_page_message_jumper_position_title),
                            desc = stringResource(R.string.setting_display_page_message_jumper_position_desc),
                            checked = displaySetting.messageJumperOnLeft,
                            onChange = { updateDisplaySetting(displaySetting.copy(messageJumperOnLeft = it)) },
                        )
                    }
                    PalaceDivider()
                    PalaceSwitchRow(
                        title = stringResource(R.string.setting_display_page_enable_auto_scroll_title),
                        desc = stringResource(R.string.setting_display_page_enable_auto_scroll_desc),
                        checked = displaySetting.enableAutoScroll,
                        onChange = { updateDisplaySetting(displaySetting.copy(enableAutoScroll = it)) },
                    )
                }

                PalaceSection("EFFECTS", "效果")
                PalaceGlass {
                    PalaceSwitchRow(
                        title = stringResource(R.string.setting_display_page_background_effect_title),
                        desc = stringResource(R.string.setting_display_page_background_effect_desc),
                        checked = displaySetting.enableBlurEffect,
                        onChange = { updateDisplaySetting(displaySetting.copy(enableBlurEffect = it)) },
                    )
                    if (displaySetting.enableBlurEffect) {
                        PalaceDivider()
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Text(
                                stringResource(R.string.setting_display_page_background_effect_type),
                                fontSize = 14.sp,
                                color = c.ink,
                            )
                            Spacer(Modifier.height(6.dp))
                            ConnectedChoiceButtons(
                                options = BackgroundEffectType.entries,
                                selected = displaySetting.backgroundEffectType,
                                onSelect = { updateDisplaySetting(displaySetting.copy(backgroundEffectType = it)) },
                            ) { type, selected ->
                                ConnectedChoiceLabel(
                                    text = when (type) {
                                        BackgroundEffectType.BLUR -> stringResource(R.string.setting_display_page_background_effect_blur)
                                        BackgroundEffectType.GLASS -> stringResource(R.string.setting_display_page_background_effect_glass)
                                    },
                                    selected = selected,
                                )
                            }
                        }
                    }
                    PalaceDivider()
                    PalaceSwitchRow(
                        title = stringResource(R.string.setting_display_page_enable_message_generation_haptic_effect_title),
                        desc = stringResource(R.string.setting_display_page_enable_message_generation_haptic_effect_desc),
                        checked = displaySetting.enableMessageGenerationHapticEffect,
                        onChange = {
                            updateDisplaySetting(displaySetting.copy(enableMessageGenerationHapticEffect = it))
                        },
                    )
                }

                PalaceSection("INPUT", "输入")
                PalaceGlass {
                    PalaceSwitchRow(
                        title = stringResource(R.string.setting_display_page_skip_crop_image_title),
                        desc = stringResource(R.string.setting_display_page_skip_crop_image_desc),
                        checked = displaySetting.skipCropImage,
                        onChange = { updateDisplaySetting(displaySetting.copy(skipCropImage = it)) },
                    )
                    PalaceDivider()
                    PalaceSwitchRow(
                        title = stringResource(R.string.setting_display_page_paste_long_text_as_file_title),
                        desc = stringResource(R.string.setting_display_page_paste_long_text_as_file_desc),
                        checked = displaySetting.pasteLongTextAsFile,
                        onChange = { updateDisplaySetting(displaySetting.copy(pasteLongTextAsFile = it)) },
                    )
                    if (displaySetting.pasteLongTextAsFile) {
                        PalaceDivider()
                        PalaceSliderRow(
                            title = stringResource(R.string.setting_display_page_paste_long_text_threshold_title),
                            valueText = "${displaySetting.pasteLongTextThreshold}",
                            value = displaySetting.pasteLongTextThreshold.toFloat(),
                            onValueChange = {
                                updateDisplaySetting(displaySetting.copy(pasteLongTextThreshold = it.toInt()))
                            },
                            valueRange = 100f..10000f,
                        )
                    }
                }

                PalaceSection("VOLUME", "音量键")
                PalaceGlass {
                    PalaceSwitchRow(
                        title = stringResource(R.string.setting_display_page_volume_key_scroll_title),
                        desc = stringResource(R.string.setting_display_page_volume_key_scroll_desc),
                        checked = displaySetting.enableVolumeKeyScroll,
                        onChange = { updateDisplaySetting(displaySetting.copy(enableVolumeKeyScroll = it)) },
                    )
                    if (displaySetting.enableVolumeKeyScroll) {
                        PalaceDivider()
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.setting_display_page_volume_key_scroll_ratio),
                                    fontSize = 14.sp,
                                    color = c.ink,
                                    modifier = Modifier.weight(1f),
                                )
                                MonoText(
                                    "${(displaySetting.volumeKeyScrollRatio * 100).toInt()}%",
                                    size = 11f,
                                    color = c.mute,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Slider(
                                value = displaySetting.volumeKeyScrollRatio,
                                onValueChange = {
                                    updateDisplaySetting(displaySetting.copy(volumeKeyScrollRatio = it))
                                },
                                valueRange = 0.25f..1.0f,
                                steps = 2,
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = c.accent,
                                    activeTrackColor = c.accent,
                                    inactiveTrackColor = c.chip,
                                ),
                            )
                        }
                    }
                }

                PalaceSection("SPEECH", stringResource(R.string.setting_page_tts_settings))
                PalaceGlass {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.setting_tts_page_default_playback_speed),
                                fontSize = 14.sp,
                                color = c.ink,
                                modifier = Modifier.weight(1f),
                            )
                            MonoText("x${"%.1f".format(ttsPlaybackSpeed)}", size = 11f, color = c.mute)
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            stringResource(R.string.setting_tts_page_default_playback_speed_description),
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            color = c.mute,
                        )
                        Spacer(Modifier.height(6.dp))
                        Slider(
                            value = ttsPlaybackSpeed,
                            onValueChange = {
                                ttsPlaybackSpeed = (it * 10).roundToInt() / 10f
                            },
                            onValueChangeFinished = {
                                vm.updateSettings {
                                    it.copy(defaultTTSPlaybackSpeed = ttsPlaybackSpeed)
                                }
                            },
                            valueRange = 0.5f..2.0f,
                            steps = 14,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = c.accent,
                                activeTrackColor = c.accent,
                                inactiveTrackColor = c.chip,
                            ),
                        )
                    }
                    PalaceDivider()
                    PalaceSwitchRow(
                        title = stringResource(R.string.setting_display_page_tts_only_read_quoted_title),
                        desc = stringResource(R.string.setting_display_page_tts_only_read_quoted_desc),
                        checked = displaySetting.ttsOnlyReadQuoted,
                        onChange = { updateDisplaySetting(displaySetting.copy(ttsOnlyReadQuoted = it)) },
                    )
                    PalaceDivider()
                    PalaceSwitchRow(
                        title = stringResource(R.string.setting_display_page_tts_read_outside_brackets_title),
                        desc = stringResource(R.string.setting_display_page_tts_read_outside_brackets_desc),
                        checked = displaySetting.ttsOnlyReadOutsideBrackets,
                        onChange = { updateDisplaySetting(displaySetting.copy(ttsOnlyReadOutsideBrackets = it)) },
                    )
                    PalaceDivider()
                    PalaceSwitchRow(
                        title = stringResource(R.string.setting_display_page_auto_play_tts_title),
                        desc = stringResource(R.string.setting_display_page_auto_play_tts_desc),
                        checked = displaySetting.autoPlayTTSAfterGeneration,
                        onChange = { updateDisplaySetting(displaySetting.copy(autoPlayTTSAfterGeneration = it)) },
                    )
                    PalaceDivider()
                    PalaceSwitchRow(
                        title = stringResource(R.string.setting_display_page_voice_listen_while_replying_title),
                        desc = stringResource(R.string.setting_display_page_voice_listen_while_replying_desc),
                        checked = displaySetting.voiceModeListenWhileReplying,
                        onChange = { updateDisplaySetting(displaySetting.copy(voiceModeListenWhileReplying = it)) },
                    )
                }
            }
        }
    }
}
