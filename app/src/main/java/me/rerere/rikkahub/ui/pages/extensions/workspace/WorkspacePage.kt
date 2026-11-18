package me.rerere.rikkahub.ui.pages.extensions.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.key
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import me.rerere.rikkahub.ui.pages.assistant.detail.DetailNavRow
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceBackdrop
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceDivider
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceGlass
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceRoundIcon
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceSection
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceTopBar
import me.rerere.rikkahub.ui.theme.palaceColors
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.CloudServer
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.FolderCode
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import androidx.compose.ui.res.stringResource
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ai.ExtensionEmptyState
import me.rerere.rikkahub.ui.components.ui.ItemAction
import me.rerere.rikkahub.ui.components.ui.ItemActionMenu
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.ui.components.RikkaConfirmDialog
import org.koin.androidx.compose.koinViewModel

@Composable
fun WorkspacePage(vm: WorkspaceVM = koinViewModel()) {
    val navController = LocalNavController.current
    val workspaces by vm.workspaces.collectAsStateWithLifecycle()
    var showTypeSheet by rememberSaveable { mutableStateOf(false) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<WorkspaceEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<WorkspaceEntity?>(null) }

    PalaceBackdrop {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                PalaceTopBar(
                    title = stringResource(R.string.workspace_page_title),
                    sub = "WORKSPACE",
                    onBack = { navController.popBackStack() },
                )
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    // 右下角悬浮加号要留出位置，底部留白保留 72dp
                    contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 40.dp + 72.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (workspaces.isEmpty()) {
                        item {
                            ExtensionEmptyState(
                                message = stringResource(R.string.workspace_page_empty),
                                description = stringResource(R.string.workspace_page_empty_desc),
                                icon = HugeIcons.FolderCode,
                            )
                        }
                    } else {
                        item(key = "section") {
                            PalaceSection("WORKSPACES", "工作区")
                        }
                        item(key = "workspaces") {
                            PalaceGlass {
                                workspaces.forEachIndexed { index, workspace ->
                                    key(workspace.id) {
                                        if (index > 0) PalaceDivider()
                                        WorkspaceItem(
                                            workspace = workspace,
                                            onRename = { editTarget = workspace },
                                            onDelete = { deleteTarget = workspace },
                                            onOpen = { navController.navigate(Screen.WorkspaceDetail(workspace.id)) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            PalaceRoundIcon(
                icon = HugeIcons.Add01,
                size = 52.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 16.dp),
                onClick = { showTypeSheet = true },
            )
        }
    }

    if (showTypeSheet) {
        ModalBottomSheet(onDismissRequest = { showTypeSheet = false }) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.workspace_page_create),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = palaceColors().ink,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                )
                PalaceGlass {
                    DetailNavRow(
                        icon = HugeIcons.FolderCode,
                        title = stringResource(R.string.workspace_page_type_local),
                        desc = stringResource(R.string.workspace_page_type_local_desc),
                        onClick = {
                            showTypeSheet = false
                            showAddDialog = true
                        },
                    )
                    PalaceDivider()
                    DetailNavRow(
                        icon = HugeIcons.CloudServer,
                        title = stringResource(R.string.workspace_page_type_ssh),
                        desc = stringResource(R.string.workspace_page_type_ssh_desc),
                        onClick = {
                            showTypeSheet = false
                            navController.navigate(Screen.WorkspaceSshEditor())
                        },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        EditWorkspaceDialog(
            title = stringResource(R.string.workspace_page_create),
            initialName = "",
            existingNames = workspaces.map { it.name.trim() }.toSet(),
            onDismiss = { showAddDialog = false },
            onConfirm = { name ->
                vm.create(name)
                showAddDialog = false
            },
        )
    }

    editTarget?.let { workspace ->
        EditWorkspaceDialog(
            title = stringResource(R.string.workspace_page_rename),
            initialName = workspace.name,
            existingNames = workspaces.filter { it.id != workspace.id }.map { it.name.trim() }.toSet(),
            onDismiss = { editTarget = null },
            onConfirm = { name ->
                vm.rename(workspace, name)
                editTarget = null
            },
        )
    }

    RikkaConfirmDialog(
        show = deleteTarget != null,
        title = stringResource(R.string.workspace_page_delete),
        confirmText = stringResource(R.string.common_delete),
        dismissText = stringResource(R.string.common_cancel),
        onConfirm = {
            deleteTarget?.let { vm.delete(it) }
            deleteTarget = null
        },
        onDismiss = { deleteTarget = null },
    ) {
        Text(stringResource(R.string.workspace_page_delete_confirm))
    }
}

@Composable
private fun WorkspaceItem(
    workspace: WorkspaceEntity,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
) {
    val c = palaceColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(c.chip),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (workspace.isSsh()) HugeIcons.CloudServer else HugeIcons.FolderCode,
                null,
                Modifier.size(16.dp),
                tint = c.ink2,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = workspace.name,
                fontSize = 14.sp,
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = workspace.statusLabel(),
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                color = c.mute,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        ItemActionMenu(
            actions = listOf(
                ItemAction(
                    text = stringResource(R.string.common_rename),
                    icon = HugeIcons.PencilEdit01,
                    onClick = onRename,
                ),
                ItemAction(
                    text = stringResource(R.string.common_delete),
                    icon = HugeIcons.Delete01,
                    destructive = true,
                    onClick = onDelete,
                ),
            )
        )
    }
}

@Composable
private fun EditWorkspaceDialog(
    title: String,
    initialName: String,
    existingNames: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    val trimmedName = name.trim()
    val isDuplicate = trimmedName.isNotEmpty() && trimmedName in existingNames

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.workspace_page_name)) },
                singleLine = true,
                isError = isDuplicate,
                supportingText = if (isDuplicate) {
                    { Text(stringResource(R.string.workspace_page_name_duplicate)) }
                } else null,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmedName) },
                enabled = name.isNotBlank() && !isDuplicate,
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}
