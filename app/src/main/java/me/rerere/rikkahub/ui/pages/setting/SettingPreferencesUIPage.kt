package me.rerere.rikkahub.ui.pages.setting

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ui.toast.ToastType
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Delete02
import me.rerere.hugeicons.stroke.FileImport
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.ChatFontFamily
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.companion.MonoText
import me.rerere.rikkahub.data.files.FileUtils
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceButtons
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceBackdrop
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceDivider
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceGlass
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceSection
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceSwitchRow
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceTopBar
import me.rerere.rikkahub.ui.theme.palaceColors
import me.rerere.rikkahub.ui.theme.rememberChatFontFamily
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingPreferencesUIPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var displaySetting by remember(settings) { mutableStateOf(settings.displaySetting) }
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val chatFontFamily = rememberChatFontFamily(displaySetting)

    fun updateDisplaySetting(setting: DisplaySetting) {
        displaySetting = setting
        vm.updateSettings { it.copy(displaySetting = setting) }
    }

    val importSuccessMsg = stringResource(R.string.setting_display_page_custom_font_import_success)
    val importFailedMsg = stringResource(R.string.setting_display_page_custom_font_import_failed)
    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    importCustomChatFontInternal(context, uri)
                }
            }.onSuccess { importedFont ->
                updateDisplaySetting(
                    displaySetting.copy(
                        chatFontFamily = ChatFontFamily.CUSTOM,
                        chatCustomFontPath = importedFont.relativePath,
                        chatCustomFontName = importedFont.displayName,
                    )
                )
                toaster.show(importSuccessMsg, type = ToastType.Success)
            }.onFailure { error ->
                toaster.show(importFailedMsg.format(error.message.orEmpty()), type = ToastType.Error)
            }
        }
    }

    val navController = LocalNavController.current
    val c = palaceColors()

    PalaceBackdrop {
        Column(Modifier.fillMaxSize()) {
            PalaceTopBar(
                title = stringResource(R.string.setting_page_preferences_ui),
                sub = "UI",
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
                PalaceSection("MESSAGE", stringResource(R.string.setting_page_message_display_settings))
                PalaceGlass {
                    PalaceSwitchRow(
                        title = stringResource(R.string.setting_display_page_show_user_avatar_title),
                        desc = stringResource(R.string.setting_display_page_show_user_avatar_desc),
                        checked = displaySetting.showUserAvatar,
                        onChange = { updateDisplaySetting(displaySetting.copy(showUserAvatar = it)) },
                    )
                    PalaceDivider()
                    PalaceSwitchRow(
                        title = stringResource(R.string.setting_display_page_show_assistant_bubble_title),
                        desc = stringResource(R.string.setting_display_page_show_assistant_bubble_desc),
                        checked = displaySetting.showAssistantBubble,
                        onChange = { updateDisplaySetting(displaySetting.copy(showAssistantBubble = it)) },
                    )
                    PalaceDivider()
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.setting_display_page_bubble_opacity_title),
                                fontSize = 14.sp,
                                color = c.ink,
                                modifier = Modifier.weight(1f),
                            )
                            MonoText("${(displaySetting.bubbleOpacity * 100).roundToInt()}%", size = 11f, color = c.mute)
                        }
                        Spacer(Modifier.height(6.dp))
                        Slider(
                            value = displaySetting.bubbleOpacity,
                            onValueChange = {
                                updateDisplaySetting(displaySetting.copy(bubbleOpacity = it))
                            },
                            valueRange = 0.1f..1.0f,
                            steps = 8,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = c.accent,
                                activeTrackColor = c.accent,
                                inactiveTrackColor = c.chip,
                            ),
                        )
                    }
                }

                PalaceSection("META", "信息")
                PalaceGlass {
                    PalaceSwitchRow(
                        title = stringResource(R.string.setting_display_page_chat_list_model_icon_title),
                        desc = stringResource(R.string.setting_display_page_chat_list_model_icon_desc),
                        checked = displaySetting.showModelIcon,
                        onChange = { updateDisplaySetting(displaySetting.copy(showModelIcon = it)) },
                    )
                    PalaceDivider()
                    PalaceSwitchRow(
                        title = stringResource(R.string.setting_display_page_show_model_name_title),
                        desc = stringResource(R.string.setting_display_page_show_model_name_desc),
                        checked = displaySetting.showModelName,
                        onChange = { updateDisplaySetting(displaySetting.copy(showModelName = it)) },
                    )
                    PalaceDivider()
                    PalaceSwitchRow(
                        title = stringResource(R.string.setting_display_page_show_datetime_in_message_title),
                        desc = stringResource(R.string.setting_display_page_show_datetime_in_message_desc),
                        checked = displaySetting.showDateTimeInMessage,
                        onChange = { updateDisplaySetting(displaySetting.copy(showDateTimeInMessage = it)) },
                    )
                    PalaceDivider()
                    PalaceSwitchRow(
                        title = stringResource(R.string.setting_display_page_show_token_usage_title),
                        desc = stringResource(R.string.setting_display_page_show_token_usage_desc),
                        checked = displaySetting.showTokenUsage,
                        onChange = { updateDisplaySetting(displaySetting.copy(showTokenUsage = it)) },
                    )
                }

                PalaceSection("CONTENT", "内容")
                PalaceGlass {
                    PalaceSwitchRow(
                        title = stringResource(R.string.setting_display_page_show_thinking_content_title),
                        desc = stringResource(R.string.setting_display_page_show_thinking_content_desc),
                        checked = displaySetting.showThinkingContent,
                        onChange = { updateDisplaySetting(displaySetting.copy(showThinkingContent = it)) },
                    )
                    PalaceDivider()
                    PalaceSwitchRow(
                        title = stringResource(R.string.setting_display_page_auto_collapse_thinking_title),
                        desc = stringResource(R.string.setting_display_page_auto_collapse_thinking_desc),
                        checked = displaySetting.autoCloseThinking,
                        onChange = { updateDisplaySetting(displaySetting.copy(autoCloseThinking = it)) },
                    )
                    PalaceDivider()
                    PalaceSwitchRow(
                        title = stringResource(R.string.setting_display_page_enable_latex_rendering_title),
                        desc = stringResource(R.string.setting_display_page_enable_latex_rendering_desc),
                        checked = displaySetting.enableLatexRendering,
                        onChange = { updateDisplaySetting(displaySetting.copy(enableLatexRendering = it)) },
                    )
                    PalaceDivider()
                    PalaceSwitchRow(
                        title = stringResource(R.string.setting_display_page_enable_quote_highlight_title),
                        desc = stringResource(R.string.setting_display_page_enable_quote_highlight_desc),
                        checked = displaySetting.enableQuoteHighlight,
                        onChange = { updateDisplaySetting(displaySetting.copy(enableQuoteHighlight = it)) },
                    )
                    PalaceDivider()
                    PalaceSwitchRow(
                        title = "斜体文字变灰",
                        desc = "*这样* 的斜体字用浅一点的灰色，跟正文分开",
                        checked = displaySetting.enableItalicGray,
                        onChange = { updateDisplaySetting(displaySetting.copy(enableItalicGray = it)) },
                    )
                }

                PalaceSection("FONT", "字体")
                PalaceGlass {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(
                            stringResource(R.string.setting_display_page_chat_font_family_title),
                            fontSize = 14.sp,
                            color = c.ink,
                        )
                        Spacer(Modifier.height(6.dp))
                        ConnectedChoiceButtons(
                            options = ChatFontFamily.entries,
                            selected = displaySetting.chatFontFamily,
                            onSelect = { family ->
                                if (family == ChatFontFamily.CUSTOM && displaySetting.chatCustomFontPath.isBlank()) {
                                    fontPickerLauncher.launch(CustomFontMimeTypesUI)
                                } else {
                                    updateDisplaySetting(displaySetting.copy(chatFontFamily = family))
                                }
                            },
                            minHeight = 64.dp,
                        ) { family, _ ->
                            // 每个按钮用对应的字体写一个样字
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Aa",
                                    fontFamily = family.toFontFamilyUI(chatFontFamily),
                                    fontSize = 16.sp,
                                    lineHeight = 22.sp,
                                )
                                Text(
                                    text = family.labelUI(),
                                    autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 11.sp),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 14.sp,
                                    color = c.mute,
                                )
                            }
                        }
                    }
                    PalaceDivider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { fontPickerLauncher.launch(CustomFontMimeTypesUI) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.setting_display_page_custom_font_title),
                                fontSize = 14.sp,
                                color = c.ink,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                if (displaySetting.chatCustomFontName.isNotBlank()) {
                                    displaySetting.chatCustomFontName
                                } else {
                                    stringResource(R.string.setting_display_page_custom_font_not_imported)
                                },
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp,
                                color = c.mute,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Row {
                            IconButton(
                                onClick = { fontPickerLauncher.launch(CustomFontMimeTypesUI) }
                            ) {
                                Icon(
                                    HugeIcons.FileImport,
                                    contentDescription = stringResource(
                                        R.string.setting_display_page_custom_font_import
                                    )
                                )
                            }
                            if (displaySetting.chatCustomFontPath.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        deleteCustomChatFontInternal(context, displaySetting.chatCustomFontPath)
                                        updateDisplaySetting(
                                            displaySetting.copy(
                                                chatFontFamily = ChatFontFamily.DEFAULT,
                                                chatCustomFontPath = "",
                                                chatCustomFontName = "",
                                            )
                                        )
                                    }
                                ) {
                                    Icon(
                                        HugeIcons.Delete02,
                                        contentDescription = stringResource(
                                            R.string.setting_display_page_custom_font_remove
                                        )
                                    )
                                }
                            }
                        }
                    }
                    PalaceDivider()
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.setting_display_page_font_size_title),
                                fontSize = 14.sp,
                                color = c.ink,
                                modifier = Modifier.weight(1f),
                            )
                            MonoText("${(displaySetting.fontSizeRatio * 100).toInt()}%", size = 11f, color = c.mute)
                        }
                        Spacer(Modifier.height(6.dp))
                        Slider(
                            value = displaySetting.fontSizeRatio,
                            onValueChange = {
                                updateDisplaySetting(displaySetting.copy(fontSizeRatio = it))
                            },
                            valueRange = 0.5f..2f,
                            steps = 11,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = c.accent,
                                activeTrackColor = c.accent,
                                inactiveTrackColor = c.chip,
                            ),
                        )
                        // 预览放在凹下去的底色里，和设置项本身区分开
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            MarkdownBlock(
                                content = stringResource(R.string.setting_display_page_font_size_preview),
                                modifier = Modifier.padding(12.dp),
                                style = LocalTextStyle.current.copy(
                                    fontSize = LocalTextStyle.current.fontSize * displaySetting.fontSizeRatio,
                                    lineHeight = LocalTextStyle.current.lineHeight * displaySetting.fontSizeRatio,
                                    fontFamily = chatFontFamily
                                )
                            )
                        }
                    }
                }

                PalaceSection("CODE", stringResource(R.string.setting_page_code_display_settings))
                PalaceGlass {
                    PalaceSwitchRow(
                        title = stringResource(R.string.setting_display_page_code_block_auto_wrap_title),
                        desc = stringResource(R.string.setting_display_page_code_block_auto_wrap_desc),
                        checked = displaySetting.codeBlockAutoWrap,
                        onChange = { updateDisplaySetting(displaySetting.copy(codeBlockAutoWrap = it)) },
                    )
                    PalaceDivider()
                    PalaceSwitchRow(
                        title = stringResource(R.string.setting_display_page_code_block_auto_collapse_title),
                        desc = stringResource(R.string.setting_display_page_code_block_auto_collapse_desc),
                        checked = displaySetting.codeBlockAutoCollapse,
                        onChange = { updateDisplaySetting(displaySetting.copy(codeBlockAutoCollapse = it)) },
                    )
                    PalaceDivider()
                    PalaceSwitchRow(
                        title = stringResource(R.string.setting_display_page_show_line_numbers_title),
                        desc = stringResource(R.string.setting_display_page_show_line_numbers_desc),
                        checked = displaySetting.showLineNumbers,
                        onChange = { updateDisplaySetting(displaySetting.copy(showLineNumbers = it)) },
                    )
                }
            }
        }
    }
}


private val CustomFontMimeTypesUI = arrayOf(
    "font/*",
    "application/x-font-ttf",
    "application/x-font-otf",
    "application/vnd.ms-opentype",
    "application/octet-stream",
    "*/*",
)

private val CustomFontExtensionsUI = setOf("ttf", "otf", "ttc")

private data class ImportedChatFontUI(
    val relativePath: String,
    val displayName: String,
)

@Composable
private fun ChatFontFamily.labelUI(): String = when (this) {
    ChatFontFamily.DEFAULT -> stringResource(R.string.setting_display_page_chat_font_family_default)
    ChatFontFamily.SERIF -> stringResource(R.string.setting_display_page_chat_font_family_serif)
    ChatFontFamily.MONOSPACE -> stringResource(R.string.setting_display_page_chat_font_family_monospace)
    ChatFontFamily.CUSTOM -> stringResource(R.string.setting_display_page_chat_font_family_custom)
}

private fun ChatFontFamily.toFontFamilyUI(customFontFamily: FontFamily): FontFamily = when (this) {
    ChatFontFamily.DEFAULT -> FontFamily.Default
    ChatFontFamily.SERIF -> FontFamily.Serif
    ChatFontFamily.MONOSPACE -> FontFamily.Monospace
    ChatFontFamily.CUSTOM -> customFontFamily
}

private fun importCustomChatFontInternal(context: Context, uri: Uri): ImportedChatFontUI {
    val displayName = FileUtils.getFileNameFromUri(context, uri)?.takeIf { it.isNotBlank() } ?: "custom_font"
    val extension = displayName.substringAfterLast('.', "")
        .lowercase()
        .takeIf { it in CustomFontExtensionsUI }
        ?: "ttf"
    val fontDir = File(context.filesDir, FileFolders.FONTS).apply { mkdirs() }
    val targetFile = File(fontDir, "chat_font.${System.currentTimeMillis()}.$extension")
    val tempFile = File(fontDir, "chat_font_import.tmp")

    try {
        tempFile.delete()
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: error("Unable to open selected font")

        runCatching {
            Typeface.createFromFile(tempFile)
        }.onFailure { error ->
            throw IllegalArgumentException(error.message ?: "Invalid font file", error)
        }

        replaceCustomChatFontInternal(fontDir, tempFile, targetFile)
    } catch (error: Throwable) {
        tempFile.delete()
        throw error
    }

    val relativePath = FileUtils.getRelativePathInFilesDir(context.filesDir, targetFile)
        ?: "${FileFolders.FONTS}/${targetFile.name}"
    return ImportedChatFontUI(relativePath = relativePath, displayName = displayName)
}

private fun replaceCustomChatFontInternal(fontDir: File, tempFile: File, targetFile: File) {
    val existingFiles = fontDir.listFiles { file ->
        file.isFile && file.name.startsWith("chat_font.") && file != tempFile
    }?.toList().orEmpty()
    val backups = existingFiles.map { file ->
        file to File(fontDir, "previous_${file.name}").also { it.delete() }
    }

    try {
        backups.forEach { (file, backup) ->
            check(file.renameTo(backup)) { "Unable to prepare existing font for replacement" }
        }
        check(tempFile.renameTo(targetFile)) { "Unable to save selected font" }
        backups.forEach { (_, backup) -> backup.delete() }
    } catch (error: Throwable) {
        tempFile.delete()
        backups.forEach { (file, backup) ->
            if (!file.exists() && backup.exists()) {
                backup.renameTo(file)
            }
        }
        throw error
    }
}

private fun deleteCustomChatFontInternal(context: Context, relativePath: String) {
    val filesDir = runCatching { context.filesDir.canonicalFile }.getOrNull() ?: return
    val fontFile = runCatching { File(filesDir, relativePath).canonicalFile }.getOrNull() ?: return
    if (fontFile.path.startsWith("${filesDir.path}${File.separator}")) {
        fontFile.delete()
    }
}
