package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.uuid.Uuid
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Package
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.skills.SkillMetadata
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceCheckRow
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceGlass
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceTextButton
import me.rerere.rikkahub.ui.theme.palaceColors

@Composable
fun ModeInjectionsContent(
    modeInjections: List<PromptInjection.ModeInjection>,
    selectedIds: Set<Uuid>,
    onToggle: (Uuid, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    colors: ListItemColors = ListItemDefaults.segmentedColors(),
    onManage: (() -> Unit)? = null,
) {
    ExtensionList(
        items = modeInjections,
        modifier = modifier,
        contentPadding = contentPadding,
        onManage = onManage,
    ) { injection ->
        ExtensionListItem(
            title = injection.name.ifBlank { stringResource(R.string.extension_content_unnamed) },
            checked = selectedIds.contains(injection.id),
            onCheckedChange = { checked -> onToggle(injection.id, checked) },
        )
    }
}

@Composable
fun LorebooksContent(
    lorebooks: List<Lorebook>,
    selectedIds: Set<Uuid>,
    onToggle: (Uuid, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    colors: ListItemColors = ListItemDefaults.segmentedColors(),
    onManage: (() -> Unit)? = null,
) {
    ExtensionList(
        items = lorebooks,
        modifier = modifier,
        contentPadding = contentPadding,
        onManage = onManage,
    ) { lorebook ->
        ExtensionListItem(
            title = lorebook.name.ifBlank { stringResource(R.string.extension_content_unnamed_lorebook) },
            description = lorebook.description,
            checked = selectedIds.contains(lorebook.id),
            onCheckedChange = { checked -> onToggle(lorebook.id, checked) },
        )
    }
}

@Composable
fun SkillsContent(
    skills: List<SkillMetadata>,
    enabledSkills: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    colors: ListItemColors = ListItemDefaults.segmentedColors(),
    onManage: (() -> Unit)? = null,
) {
    ExtensionList(
        items = skills,
        modifier = modifier,
        contentPadding = contentPadding,
        onManage = onManage,
        key = { it.skillDir.absolutePath },
    ) { skill ->
        ExtensionListItem(
            title = skill.name,
            description = skill.description,
            checked = enabledSkills.contains(skill.name),
            onCheckedChange = { checked -> onToggle(skill.name, checked) },
        )
    }
}

// 扩展项的分段列表，末尾可带一个跳转到管理页的按钮
@Composable
private fun <T> ExtensionList(
    items: List<T>,
    modifier: Modifier,
    contentPadding: PaddingValues,
    onManage: (() -> Unit)?,
    key: ((T) -> Any)? = null,
    itemContent: @Composable (item: T) -> Unit,
) {
    val itemKey: ((Int, T) -> Any)? = key?.let { keyOf -> { _, item -> keyOf(item) } }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(items = items, key = itemKey) { _, item ->
            itemContent(item)
        }
        if (onManage != null) {
            item {
                ManageButton(onClick = onManage)
            }
        }
    }
}

@Composable
private fun ExtensionListItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String = "",
) {
    PalaceGlass {
        PalaceCheckRow(
            title = title,
            desc = description,
            checked = checked,
            onChange = onCheckedChange,
        )
    }
}

@Composable
private fun ManageButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        PalaceTextButton(stringResource(R.string.extension_content_manage), onClick = onClick)
    }
}

@Composable
fun ExtensionEmptyState(
    message: String,
    description: String? = null,
    buttonText: String? = null,
    onAction: (() -> Unit)? = null,
    icon: ImageVector = HugeIcons.Package,
) {
    val c = palaceColors()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(
                    color = c.chip,
                    shape = MaterialShapes.Cookie9Sided.toShape(),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = c.ink2,
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = message,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = c.ink,
                textAlign = TextAlign.Center,
            )
            if (description != null) {
                Text(
                    text = description,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    color = c.mute,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (buttonText != null && onAction != null) {
            PalaceTextButton(buttonText, onClick = onAction)
        }
    }
}
