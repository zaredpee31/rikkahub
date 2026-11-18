package me.rerere.rikkahub.ui.pages.extensions.skills

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import me.rerere.rikkahub.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.FolderOpen
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.components.ui.ItemAction
import me.rerere.rikkahub.ui.components.ui.ItemActionMenu
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceBackdrop
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceDialog
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceField
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceGlass
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceTopBar
import me.rerere.rikkahub.ui.theme.palaceColors
import me.rerere.rikkahub.utils.fileSizeToString
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

@Composable
fun SkillDetailPage(skillName: String) {
    val vm = koinViewModel<SkillDetailVM>()
    LaunchedEffect(skillName) { vm.init(skillName) }

    val tree by vm.tree.collectAsStateWithLifecycle()
    val readOnly by vm.readOnly.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val c = palaceColors()
    val toaster = LocalToaster.current

    var editingFile by remember { mutableStateOf<SkillFile?>(null) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<SkillFile?>(null) }
    val deleteFailedMsg = stringResource(R.string.skill_detail_page_delete_failed)

    val scrollState = rememberScrollState()
    var previousScrollOffset by remember { mutableIntStateOf(0) }
    val fabVisible by remember {
        derivedStateOf {
            val delta = scrollState.value - previousScrollOffset
            previousScrollOffset = scrollState.value
            delta <= 0
        }
    }

    PalaceBackdrop {
        Scaffold(
            topBar = {
                PalaceTopBar(
                    title = skillName,
                    sub = "SKILL",
                    onBack = { navController.popBackStack() },
                )
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = fabVisible && !readOnly,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                ) {
                    FloatingActionButton(
                        onClick = { showAddDialog = true },
                        containerColor = c.accent,
                        contentColor = c.onAccent,
                    ) {
                        Icon(HugeIcons.Add01, contentDescription = null)
                    }
                }
            },
            containerColor = Color.Transparent,
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(innerPadding + PaddingValues(16.dp)),
            ) {
                // 整棵文件树放在一块磨砂面板里，行的点击水波纹跟着面板的圆角裁剪
                if (tree.isNotEmpty()) {
                    PalaceGlass {
                        FileTree(
                            nodes = tree,
                            depth = 0,
                            readOnly = readOnly,
                            onEdit = { editingFile = it },
                            onDelete = { deleteTarget = it },
                        )
                    }
                }
            }
        }
    }

    editingFile?.let { skillFile ->
        EditFileDialog(
            skillFile = skillFile,
            readOnly = readOnly,
            initialContent = remember(skillFile.relativePath) { vm.readFile(skillFile) },
            onDismiss = { editingFile = null },
            onConfirm = { content ->
                vm.saveFile(skillFile.relativePath, content) { error ->
                    if (error == null) editingFile = null
                    else toaster.show(error)
                }
            },
        )
    }

    if (showAddDialog) {
        AddFileDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { fileName, content ->
                vm.saveFile(fileName, content) { error ->
                    if (error == null) showAddDialog = false
                    else toaster.show(error)
                }
            },
        )
    }

    deleteTarget?.let { skillFile ->
        PalaceDialog(
            title = stringResource(R.string.skill_detail_page_delete_file),
            onDismiss = { deleteTarget = null },
            confirmText = stringResource(R.string.delete),
            onConfirm = {
                vm.deleteFile(skillFile) { success ->
                    if (!success) toaster.show(deleteFailedMsg)
                }
                deleteTarget = null
            },
            dismissText = stringResource(R.string.cancel),
        ) {
            Text(
                stringResource(R.string.skill_detail_page_delete_confirm, skillFile.relativePath),
                fontSize = 14.sp,
                color = c.ink,
            )
        }
    }
}

@Composable
private fun FileTree(
    nodes: List<SkillFileNode>,
    depth: Int,
    readOnly: Boolean,
    onEdit: (SkillFile) -> Unit,
    onDelete: (SkillFile) -> Unit,
) {
    nodes.fastForEach { node ->
        when (node) {
            is SkillFileNode.FileNode -> FileItem(
                skillFile = node.skillFile,
                depth = depth,
                readOnly = readOnly,
                onEdit = { onEdit(node.skillFile) },
                onDelete = { onDelete(node.skillFile) },
            )

            is SkillFileNode.DirNode -> DirItem(
                node = node,
                depth = depth,
                readOnly = readOnly,
                onEdit = onEdit,
                onDelete = onDelete,
            )
        }
    }
}

// 每深一层缩进这么多
private val TreeIndent = 24.dp

@Composable
private fun FileItem(
    skillFile: SkillFile,
    depth: Int,
    readOnly: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    // SKILL.md 是技能的入口文件，不能删
    val deletable = !readOnly && skillFile.relativePath != "SKILL.md"
    val c = palaceColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .heightIn(min = 64.dp)
            .padding(start = 16.dp + TreeIndent * depth, end = if (deletable) 4.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CardGroupIcon(HugeIcons.File02)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = skillFile.name,
                fontSize = 14.sp,
                color = c.ink,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = skillFile.size.fileSizeToString(),
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                color = c.mute,
            )
        }
        if (deletable) {
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
private fun DirItem(
    node: SkillFileNode.DirNode,
    depth: Int,
    readOnly: Boolean,
    onEdit: (SkillFile) -> Unit,
    onDelete: (SkillFile) -> Unit,
) {
    val c = palaceColors()
    var expanded by rememberSaveable(node.relativePath) { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    )

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .heightIn(min = 64.dp)
                .padding(start = 16.dp + TreeIndent * depth, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CardGroupIcon(
                icon = if (expanded) HugeIcons.FolderOpen else HugeIcons.Folder01,
                containerColor = c.chip,
                contentColor = c.ink2,
            )
            Text(
                text = node.name,
                fontSize = 14.sp,
                color = c.ink,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
            )
            Icon(
                imageVector = HugeIcons.ArrowDown01,
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(arrowRotation),
                tint = c.ink2,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                FileTree(
                    nodes = node.children,
                    depth = depth + 1,
                    readOnly = readOnly,
                    onEdit = onEdit,
                    onDelete = onDelete,
                )
            }
        }
    }
}

@Composable
private fun EditFileDialog(
    skillFile: SkillFile,
    readOnly: Boolean,
    initialContent: String,
    onDismiss: () -> Unit,
    onConfirm: (content: String) -> Unit,
) {
    var content by remember(skillFile.relativePath) { mutableStateOf(initialContent) }

    PalaceDialog(
        title = skillFile.relativePath,
        onDismiss = onDismiss,
        confirmText = if (readOnly) null else stringResource(R.string.skill_detail_page_save),
        onConfirm = { onConfirm(content) },
        dismissText = if (readOnly) stringResource(R.string.skill_detail_page_close) else stringResource(R.string.cancel),
    ) {
        PalaceField(
            value = content,
            onValueChange = { if (!readOnly) content = it },
            label = stringResource(R.string.skill_detail_page_content),
            minLines = 10,
            textStyle = TextStyle(fontFamily = FontFamily.Monospace),
        )
    }
}

@Composable
private fun AddFileDialog(
    onDismiss: () -> Unit,
    onConfirm: (fileName: String, content: String) -> Unit,
) {
    val c = palaceColors()
    var fileName by rememberSaveable { mutableStateOf("") }
    var content by rememberSaveable { mutableStateOf("") }
    val fileNameError = fileName.isNotBlank() && (fileName.contains('\\'))

    PalaceDialog(
        title = stringResource(R.string.skill_detail_page_new_file),
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.skill_detail_page_create),
        confirmEnabled = fileName.isNotBlank() && !fileNameError,
        onConfirm = { onConfirm(fileName.trim(), content) },
        dismissText = stringResource(R.string.cancel),
    ) {
        PalaceField(
            value = fileName,
            onValueChange = { fileName = it },
            label = stringResource(R.string.skill_detail_page_file_name),
            placeholder = "examples/basic.md",
            isError = fileNameError,
            singleLine = true,
            textStyle = TextStyle(fontFamily = FontFamily.Monospace),
        )
        if (fileNameError) {
            Text(
                stringResource(R.string.skill_detail_page_file_name_invalid),
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                color = c.error,
            )
        }
        PalaceField(
            value = content,
            onValueChange = { content = it },
            label = stringResource(R.string.skill_detail_page_content),
            minLines = 6,
            textStyle = TextStyle(fontFamily = FontFamily.Monospace),
        )
    }
}
