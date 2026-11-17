package me.rerere.rikkahub.ui.components.message

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.rikkahub.data.model.MessageNode

/** 分支计数 `1/2`，10.5sp #8E8E93；只有一条时不显示。 */
@Composable
fun ChatMessageBranchLabel(node: MessageNode, modifier: Modifier = Modifier) {
    if (node.messages.size > 1) {
        Text(
            text = "${node.selectIndex + 1}/${node.messages.size}",
            fontSize = 10.5.sp,
            color = Color(0xFF8E8E93),
            modifier = modifier,
        )
    }
}

/** 分支箭头，放进玻璃胶囊里；只有一条时不显示。 */
@Composable
fun ChatMessageBranchArrows(
    node: MessageNode,
    onUpdate: (MessageNode) -> Unit,
    tint: Color,
) {
    if (node.messages.size > 1) {
        ChatMessageActionButton(
            icon = HugeIcons.ArrowLeft01,
            contentDescription = "Prev",
            modifier = Modifier.alpha(if (node.selectIndex == 0) 0.5f else 1f),
            tint = tint,
            onClick = {
                if (node.selectIndex > 0) {
                    onUpdate(node.copy(selectIndex = node.selectIndex - 1))
                }
            },
        )
        ChatMessageActionButton(
            icon = HugeIcons.ArrowRight01,
            contentDescription = "Next",
            modifier = Modifier.alpha(if (node.selectIndex == node.messages.lastIndex) 0.5f else 1f),
            tint = tint,
            onClick = {
                if (node.selectIndex < node.messages.lastIndex) {
                    onUpdate(node.copy(selectIndex = node.selectIndex + 1))
                }
            },
        )
    }
}
