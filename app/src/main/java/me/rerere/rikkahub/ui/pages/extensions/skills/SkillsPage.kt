package me.rerere.rikkahub.ui.pages.extensions.skills

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.StringRes
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.key
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import me.rerere.rikkahub.ui.pages.assistant.detail.DetailNavRow
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceBackdrop
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceDivider
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceGlass
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceRoundIcon
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceSearchField
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceSection
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceTopBar
import me.rerere.rikkahub.ui.theme.palaceColors
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Alert01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Download01
import me.rerere.hugeicons.stroke.FileImport
import me.rerere.hugeicons.stroke.Puzzle
import me.rerere.rikkahub.data.skills.InvalidSkillDir
import me.rerere.rikkahub.data.skills.InvalidSkillReason
import me.rerere.rikkahub.data.skills.SkillMetadata
import me.rerere.rikkahub.data.skills.SkillValidationError
import me.rerere.rikkahub.data.skills.SkillValidationException
import me.rerere.rikkahub.data.skills.SkillValidator
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.components.ai.ExtensionEmptyState
import me.rerere.rikkahub.ui.components.ui.ItemAction
import me.rerere.rikkahub.ui.components.ui.ItemActionMenu
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.ui.components.RikkaConfirmDialog
import org.koin.androidx.compose.koinViewModel

@Composable
fun SkillsPage() {
    val navController = LocalNavController.current
    val vm = koinViewModel<SkillsVM>()
    val skills by vm.skills.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    val context = LocalContext.current
    var showImportSheet by rememberSaveable { mutableStateOf(false) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var showImportDialog by rememberSaveable { mutableStateOf(false) }
    val invalidSkillDirs by vm.invalidSkillDirs.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<SkillMetadata?>(null) }
    var invalidDeleteTarget by remember { mutableStateOf<InvalidSkillDir?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val filteredInvalidSkillDirs = remember(invalidSkillDirs, searchQuery) {
        invalidSkillDirs.filter { it.dirName.contains(searchQuery, ignoreCase = true) }
    }
    val filteredSkills = remember(skills, searchQuery) {
        if (searchQuery.isBlank()) {
            skills
        } else {
            skills.filter { skill ->
                skill.name.contains(searchQuery, ignoreCase = true) ||
                    skill.description.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    val fileImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        vm.importSkillFromFile(uri) { success, message ->
            if (success) {
                toaster.show(context.getString(R.string.skills_page_import_success, message))
            } else {
                toaster.show(context.getString(R.string.skills_page_import_failed, message))
            }
        }
    }

    val c = palaceColors()
    PalaceBackdrop {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                PalaceTopBar(
                    title = stringResource(R.string.skills_page_title),
                    sub = "SKILLS",
                    onBack = { navController.popBackStack() },
                )
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    // 右下角悬浮加号要留出位置，底部留白保留原来的 72dp
                    contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 40.dp + 72.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (skills.isEmpty()) {
                        item {
                            ExtensionEmptyState(
                                message = stringResource(R.string.skills_page_empty_title),
                                description = stringResource(R.string.skills_page_empty_hint),
                                icon = HugeIcons.Puzzle,
                            )
                        }
                    }

                    if (skills.isNotEmpty()) {
                        item(key = "search") {
                            PalaceSearchField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = stringResource(R.string.skills_page_search_placeholder),
                            )
                        }
                    }

                    if (skills.isNotEmpty() && filteredSkills.isEmpty()) {
                        item(key = "no_result") {
                            Text(
                                text = stringResource(R.string.skills_page_search_no_result),
                                fontSize = 13.sp,
                                color = c.mute,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    if (filteredSkills.isNotEmpty()) {
                        item(key = "skills_section") {
                            PalaceSection("SKILLS", "技能")
                        }
                        item(key = "skills") {
                            PalaceGlass {
                                filteredSkills.forEachIndexed { index, skill ->
                                    key(skill.skillDir.absolutePath) {
                                        if (index > 0) PalaceDivider()
                                        SkillCard(
                                            skill = skill,
                                            onClick = { navController.navigate(Screen.SkillDetail(skill.name)) },
                                            onDelete = { deleteTarget = skill },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 空目录、缺少 SKILL.md 等无法识别的目录不会出现在技能列表里，单独列出供清理
                    if (filteredInvalidSkillDirs.isNotEmpty()) {
                        item(key = "invalid_skill_dirs") {
                            InvalidSkillDirsGroup(
                                dirs = filteredInvalidSkillDirs,
                                onDelete = { invalidDeleteTarget = it },
                            )
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
                onClick = { showImportSheet = true },
            )
        }
    }

    if (showImportSheet) {
        SkillImportSheet(
            onDismiss = { showImportSheet = false },
            onAddManually = {
                showImportSheet = false
                showAddDialog = true
            },
            onImportFromFile = {
                showImportSheet = false
                fileImportLauncher.launch(
                    arrayOf(
                        "text/*",
                        "application/zip",
                        "application/x-zip-compressed",
                        "application/octet-stream",
                    )
                )
            },
            onImportFromGitHub = {
                showImportSheet = false
                showImportDialog = true
            },
        )
    }

    if (showAddDialog) {
        AddSkillDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, content ->
                vm.saveSkill(name, content) { success ->
                    showAddDialog = false
                    if (!success) {
                        toaster.show(context.getString(R.string.skills_page_save_failed))
                    }
                }
            },
        )
    }

    if (showImportDialog) {
        ImportSkillDialog(
            onDismiss = { showImportDialog = false },
            onConfirm = { repoUrl ->
                vm.importSkillFromGitHub(repoUrl) { success, message ->
                    showImportDialog = false
                    if (success) {
                        toaster.show(context.getString(R.string.skills_page_import_success, message))
                    } else {
                        toaster.show(context.getString(R.string.skills_page_import_failed, message))
                    }
                }
            },
        )
    }

    RikkaConfirmDialog(
        show = deleteTarget != null,
        title = stringResource(R.string.skills_page_delete_title),
        confirmText = stringResource(R.string.delete),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            deleteTarget?.let { vm.deleteSkill(it) }
            deleteTarget = null
        },
        onDismiss = { deleteTarget = null },
    ) {
        Text(stringResource(R.string.skills_page_delete_message, deleteTarget?.name ?: ""))
    }

    RikkaConfirmDialog(
        show = invalidDeleteTarget != null,
        title = stringResource(R.string.skills_page_delete_dir_title),
        confirmText = stringResource(R.string.delete),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            invalidDeleteTarget?.let { vm.deleteInvalidSkillDir(it.dirName) }
            invalidDeleteTarget = null
        },
        onDismiss = { invalidDeleteTarget = null },
    ) {
        Text(stringResource(R.string.skills_page_delete_dir_message, invalidDeleteTarget?.dirName.orEmpty()))
    }
}

@Composable
private fun invalidSkillReasonText(reason: InvalidSkillReason): String = stringResource(
    when (reason) {
        InvalidSkillReason.MissingSkillMd -> R.string.skills_page_invalid_reason_missing_skill_md
        InvalidSkillReason.MissingName -> R.string.skills_page_invalid_reason_missing_name
        InvalidSkillReason.MissingDescription -> R.string.skills_page_invalid_reason_missing_description
        InvalidSkillReason.ReadFailed -> R.string.skills_page_invalid_reason_read_failed
    }
)

// 页面和 SkillsVM 的导入提示共用
@get:StringRes
internal val SkillValidationError.messageRes: Int
    get() = when (this) {
        SkillValidationError.MissingName -> R.string.skills_page_name_error
        SkillValidationError.NameHasSurroundingWhitespace -> R.string.skills_page_name_whitespace_error
        SkillValidationError.NameTooLong -> R.string.skills_page_name_too_long_error
        SkillValidationError.NameStartsWithDot -> R.string.skills_page_name_dot_error
        SkillValidationError.NameHasInvalidChars -> R.string.skills_page_name_invalid_chars_error
        SkillValidationError.MissingDescription -> R.string.skills_page_description_error
    }

internal val SkillValidationError.formatArgs: Array<Any>
    get() = if (this == SkillValidationError.NameTooLong) arrayOf(SkillValidator.MAX_NAME_LENGTH) else emptyArray()

@Composable
private fun InvalidSkillDirsGroup(
    dirs: List<InvalidSkillDir>,
    onDelete: (InvalidSkillDir) -> Unit,
) {
    val c = palaceColors()
    Column {
        PalaceSection("INVALID", stringResource(R.string.skills_page_invalid_dirs_title))
        PalaceGlass {
            dirs.forEachIndexed { index, dir ->
                if (index > 0) PalaceDivider()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(c.chip),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(HugeIcons.Alert01, null, Modifier.size(16.dp), tint = c.ink2)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(dir.dirName, fontSize = 14.sp, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            invalidSkillReasonText(dir.reason),
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            color = c.mute,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    ItemActionMenu(
                        actions = listOf(
                            ItemAction(
                                text = stringResource(R.string.delete),
                                icon = HugeIcons.Delete01,
                                destructive = true,
                                onClick = { onDelete(dir) },
                            ),
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun SkillCard(
    skill: SkillMetadata,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = palaceColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = if (skill.builtin) 16.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(c.chip),
            contentAlignment = Alignment.Center,
        ) {
            Icon(HugeIcons.Puzzle, null, Modifier.size(16.dp), tint = c.ink2)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = skill.name,
                    modifier = Modifier.weight(1f, fill = false),
                    fontSize = 14.sp,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (skill.builtin) {
                    Text(
                        stringResource(R.string.skills_page_builtin_tag),
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        color = c.ink2,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(c.chip)
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = skill.description,
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                color = c.mute,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!skill.compatibility.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = skill.compatibility,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    color = c.faint,
                )
            }
        }
        // 内置技能只读，不提供删除
        if (!skill.builtin) {
            Spacer(Modifier.width(8.dp))
            ItemActionMenu(
                actions = listOf(
                    ItemAction(
                        text = stringResource(R.string.delete),
                        icon = HugeIcons.Delete01,
                        destructive = true,
                        onClick = onDelete,
                    ),
                )
            )
        }
    }
}

@Composable
private fun SkillImportSheet(
    onDismiss: () -> Unit,
    onAddManually: () -> Unit,
    onImportFromFile: () -> Unit,
    onImportFromGitHub: () -> Unit,
) {
    val c = palaceColors()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.skills_page_add_title),
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = c.ink,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            )
            PalaceGlass {
                DetailNavRow(
                    icon = HugeIcons.Add01,
                    title = stringResource(R.string.skills_page_add_manually),
                    desc = null,
                    onClick = onAddManually,
                )
                PalaceDivider()
                DetailNavRow(
                    icon = HugeIcons.FileImport,
                    title = stringResource(R.string.skills_page_import_from_file),
                    desc = null,
                    onClick = onImportFromFile,
                )
                PalaceDivider()
                DetailNavRow(
                    icon = HugeIcons.Download01,
                    title = stringResource(R.string.skills_page_import_from_github),
                    desc = null,
                    onClick = onImportFromGitHub,
                )
            }
        }
    }
}

@Composable
private fun AddSkillDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, content: String) -> Unit,
) {
    var content by rememberSaveable { mutableStateOf("") }

    val validation = remember(content) { SkillValidator.validate(content) }
    val name = validation.getOrNull()
    val nameError = content.isNotBlank() && name == null
    val validationError = (validation.exceptionOrNull() as? SkillValidationException)?.error

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.skills_page_add_title)) },
        text = {
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text(stringResource(R.string.skills_page_skill_content_label)) },
                placeholder = {
                    Text(
                        "---\nname: my-skill\ndescription: \"...\"\n---\n\n" +
                            stringResource(R.string.skills_page_skill_body_placeholder),
                        fontFamily = FontFamily.Monospace,
                    )
                },
                supportingText = {
                    if (nameError) Text(
                        validationError?.let { stringResource(it.messageRes, *it.formatArgs) }.orEmpty(),
                        color = MaterialTheme.colorScheme.error
                    )
                    else if (name != null) Text(stringResource(R.string.skills_page_skill_name, name))
                    else Text(stringResource(R.string.skills_page_paste_hint))
                },
                isError = nameError,
                minLines = 8,
                maxLines = 14,
                textStyle = TextStyle(fontSize = 11.5.sp, lineHeight = 16.sp, fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { name?.let { onConfirm(it, content) } },
                enabled = name != null,
            ) {
                Text(stringResource(R.string.skills_page_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun ImportSkillDialog(
    onDismiss: () -> Unit,
    onConfirm: (repoUrl: String) -> Unit,
) {
    var url by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text(stringResource(R.string.skills_page_import_from_github)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.skills_page_import_description),
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    color = palaceColors().mute,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.skills_page_repo_url_label)) },
                    placeholder = { Text("https://github.com/owner/repo", fontFamily = FontFamily.Monospace) },
                    supportingText = { Text(stringResource(R.string.skills_page_repo_url_hint)) },
                    singleLine = true,
                    enabled = !loading,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (loading) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularWavyProgressIndicator(modifier = Modifier.size(16.dp))
                        Text(
                            stringResource(R.string.skills_page_downloading),
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            color = palaceColors().mute,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    loading = true
                    onConfirm(url)
                },
                enabled = url.isNotBlank() && !loading,
            ) {
                Text(stringResource(R.string.skills_page_import_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) { Text(stringResource(R.string.cancel)) }
        },
    )
}
