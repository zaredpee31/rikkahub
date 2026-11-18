package me.rerere.rikkahub.ui.components.message

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.FileImport
import me.rerere.hugeicons.stroke.Share08
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceDivider
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceGlass
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceRow
import me.rerere.rikkahub.ui.theme.palaceColors
import org.koin.compose.koinInject
import java.io.File

private const val DEFAULT_VISIBLE_COUNT = 3
private val WORKSPACE_FILE_TOOL_NAMES = setOf("workspace_write_file", "workspace_edit_file")

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditedFilesList(
    parts: List<UIMessagePart>,
    assistant: Assistant?,
) {
    val workspaceId = assistant?.workspaceId?.toString() ?: return
    val editedFiles = remember(parts) {
        parts.filterIsInstance<UIMessagePart.Tool>()
            .filter { it.toolName in WORKSPACE_FILE_TOOL_NAMES && it.isExecuted }
            .mapNotNull { tool ->
(tool.inputAsJson() as? JsonObject)?.get("path")?.jsonPrimitive?.contentOrNull
            }
            .distinct()
    }
    if (editedFiles.isEmpty()) return

    val c = palaceColors()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val workspaceRepository: WorkspaceRepository = koinInject()

    var selectedPath by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    val visibleFiles = if (expanded) editedFiles else editedFiles.take(DEFAULT_VISIBLE_COUNT)
    val hasMore = editedFiles.size > DEFAULT_VISIBLE_COUNT

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*"),
    ) { uri ->
        val path = selectedPath.also { selectedPath = null } ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        val outputStream = context.contentResolver.openOutputStream(uri) ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                outputStream.use { output ->
                    workspaceRepository.exportAbsoluteFile(workspaceId, path, output)
                }
            }
        }
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        visibleFiles.forEach { path ->
            val fileName = remember(path) { path.substringAfterLast('/') }
            ChatMessageFileChip(
                text = fileName,
                onClick = { selectedPath = path },
                icon = {
                    Icon(
                        imageVector = HugeIcons.File02,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = c.ink2,
                    )
                },
            )
        }
        if (hasMore && !expanded) {
            ChatMessageFileChip(
                text = "+${editedFiles.size - DEFAULT_VISIBLE_COUNT}",
                onClick = { expanded = true },
                color = c.chip,
            )
        }
    }

    if (selectedPath != null) {
        val path = selectedPath!!
        val fileName = remember(path) { path.substringAfterLast('/') }
        ModalBottomSheet(
            onDismissRequest = { selectedPath = null },
            containerColor = c.bgTop,
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = fileName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                PalaceGlass {
                    PalaceRow(
                        icon = HugeIcons.FileImport,
                        title = stringResource(R.string.common_export),
                        onClick = {
                            val p = selectedPath ?: return@PalaceRow
                            exportLauncher.launch(p.substringAfterLast('/'))
                        },
                    )
                    PalaceDivider()
                    PalaceRow(
                        icon = HugeIcons.Share08,
                        title = stringResource(R.string.common_share),
                        onClick = {
                            val p = selectedPath ?: return@PalaceRow
                        selectedPath = null
                        scope.launch {
                            runCatching {
                                val dir = File(context.cacheDir, "workspace_share").apply { mkdirs() }
                                val file = File(dir, p.substringAfterLast('/'))
                                file.outputStream().use { output ->
                                    workspaceRepository.exportAbsoluteFile(workspaceId, p, output)
                                }
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file,
                                )
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/octet-stream"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, null))
                            }
                        }
                        },
                    )
                }
            }
        }
    }
}
