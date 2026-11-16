package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.companion.DottedLine
import me.rerere.rikkahub.data.db.entity.MemoryPlateEntity
import me.rerere.rikkahub.data.repository.CoreProfileRepository
import me.rerere.rikkahub.ui.context.LocalNavController
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/** 记忆页：只有两块常驻模块「关于小葵」「关于你」，加上记忆开关。内容存在 memory_plate 表 */
@Composable
fun AssistantMemoryPage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(
        parameters = {
            parametersOf(id)
        }
    )
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val memoryCore by vm.memoryCore.collectAsStateWithLifecycle()
    val memoryError by vm.memoryError.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val c = palaceColors()

    memoryError?.let { message ->
        PalaceDialog(title = "没有保存成功", onDismiss = { vm.dismissMemoryError() }, confirmText = null, dismissText = "知道了") {
            Text(message, fontSize = 13.sp, color = c.ink)
        }
    }

    PalaceSubPage("记忆", "MEMORY", onBack = { navController.popBackStack() }) {
        PalaceCoreCard(
            kind = CoreProfileRepository.USER,
            core = memoryCore,
            onSaveCore = { kind, content, done -> vm.saveMemoryCore(kind, content, done) },
        )
        PalaceCoreCard(
            kind = CoreProfileRepository.SELF,
            core = memoryCore,
            onSaveCore = { kind, content, done -> vm.saveMemoryCore(kind, content, done) },
        )

        PalaceSection("SWITCH", "开关")
        PalaceGlass {
            PalaceSwitchRow(
                title = "带上常驻背景",
                desc = "打开后，「关于小葵」和「关于你」会在所有对话里常驻。关掉后对话不再带它们，内容不会被删",
                checked = assistant.enableMemory,
                onChange = { vm.update(assistant.copy(enableMemory = it)) },
            )
            DottedLine(Modifier.fillMaxWidth().padding(horizontal = 16.dp), color = c.dot)
            PalaceSwitchRow(
                title = "全局记忆",
                desc = "所有开了这个选项的助手共用同一份常驻背景，不开则这个助手自己一份",
                checked = assistant.useGlobalMemory,
                onChange = { vm.update(assistant.copy(useGlobalMemory = it)) },
                enabled = assistant.enableMemory,
            )
        }
    }
}

private fun coreTitle(kind: String) = if (kind == CoreProfileRepository.USER) "关于小葵" else "关于你"

/** 一块常驻背景：整段长文本，点一下编辑。自动流程不会动它 */
@Composable
private fun PalaceCoreCard(
    kind: String,
    core: List<MemoryPlateEntity>,
    onSaveCore: (kind: String, content: String, done: (Boolean) -> Unit) -> Unit,
) {
    val c = palaceColors()
    val content = core.firstOrNull { it.kind == kind }?.content.orEmpty()
    var draft by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    PalaceSection("CORE", "${coreTitle(kind)} · 所有窗口常驻")
    PalaceGlass {
        Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                content.ifBlank { "还没有内容。这里只有你手动写，或者明确让阿辞改的时候才会变" },
                fontSize = 13.sp, lineHeight = 20.sp,
                color = if (content.isBlank()) c.mute else c.ink,
                maxLines = if (expanded) Int.MAX_VALUE else 6,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp), horizontalArrangement = Arrangement.Start) {
            if (content.isNotBlank()) PalaceTextButton(if (expanded) "收起" else "展开全文", onClick = { expanded = !expanded })
            PalaceTextButton("编辑", onClick = { draft = content })
        }
    }

    draft?.let { text ->
        PalaceDialog(
            title = coreTitle(kind),
            onDismiss = { draft = null },
            onConfirm = {
                if (!saving) {
                    saving = true
                    onSaveCore(kind, text) { ok -> saving = false; if (ok) draft = null }
                }
            },
        ) {
            PalaceField(text, { draft = it }, minLines = 8)
        }
    }
}
