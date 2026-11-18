package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowTurnBackward
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceDivider
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceRow
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceTextButton
import me.rerere.rikkahub.ui.theme.palaceColors
import me.rerere.workspace.WorkspaceFileEntry
import me.rerere.workspace.WorkspaceStorageArea
import org.koin.compose.koinInject

@Composable
fun WorkspaceCwdPickerSheet(
    workspaceId: String,
    workspaceDir: String,
    currentCwd: String?,
    onSelectCwd: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val workspaceRepository: WorkspaceRepository = koinInject()

    var browsePath by remember { mutableStateOf(fromAbsolutePath(workspaceDir, currentCwd)) }
    var entries by remember { mutableStateOf<List<WorkspaceFileEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(browsePath) {
        loading = true
        try {
            val result = withContext(Dispatchers.IO) {
                workspaceRepository.listFiles(workspaceId, WorkspaceStorageArea.FILES, browsePath)
            }
            entries = result.sortedWith(compareByDescending<WorkspaceFileEntry> { it.isDirectory }.thenBy { it.name })
            loading = false
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            entries = emptyList()
            loading = false
        }
    }

    val c = palaceColors()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = c.bgTop,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.workspace_cwd_select_directory),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = c.ink,
            )
            Text(
                text = stringResource(R.string.workspace_cwd_select_desc),
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                color = c.mute,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(
                    enabled = browsePath.isNotBlank(),
                    onClick = {
                        browsePath = browsePath.substringBeforeLast('/', missingDelimiterValue = "")
                    },
                ) {
                    Icon(HugeIcons.ArrowTurnBackward, contentDescription = null, tint = c.ink2)
                }
                Text(
                    text = toAbsolutePath(workspaceDir, browsePath),
                    modifier = Modifier.weight(1f),
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    color = c.mute,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            PalaceDivider()

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp),
            ) {
                val dirs = entries.filter { it.isDirectory }
                items(dirs, key = { it.path }) { entry ->
                    PalaceRow(
                        icon = HugeIcons.Folder01,
                        title = entry.name,
                        onClick = { browsePath = entry.path },
                    )
                }

                if (!loading && dirs.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.workspace_cwd_no_subdirectories),
                            modifier = Modifier.padding(16.dp),
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            color = c.mute,
                        )
                    }
                }
            }

            PalaceDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (currentCwd != null) {
                    PalaceTextButton(
                        text = stringResource(R.string.workspace_cwd_reset),
                        onClick = {
                            onSelectCwd(null)
                            onDismiss()
                        },
                    )
                }
                PalaceTextButton(
                    text = stringResource(R.string.workspace_cwd_set),
                    onClick = {
                        onSelectCwd(toAbsolutePath(workspaceDir, browsePath))
                        onDismiss()
                    },
                )
            }
        }
    }
}

private fun toAbsolutePath(workspaceDir: String, relativePath: String): String {
    return if (relativePath.isBlank()) workspaceDir else "${workspaceDir.trimEnd('/')}/$relativePath"
}

private fun fromAbsolutePath(workspaceDir: String, absolutePath: String?): String {
    if (absolutePath.isNullOrBlank()) return ""
    return absolutePath.removePrefix("${workspaceDir.trimEnd('/')}/").removePrefix(workspaceDir)
}
