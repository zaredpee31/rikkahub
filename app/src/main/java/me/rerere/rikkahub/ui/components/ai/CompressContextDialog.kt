package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Remove01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.PalaceButton
import me.rerere.rikkahub.ui.components.ui.PalaceGlassDialog
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceField
import me.rerere.rikkahub.ui.pages.assistant.detail.palaceColors

@Composable
fun CompressContextDialog(
    onDismiss: () -> Unit,
    onConfirm: (additionalPrompt: String, keepRecentMessages: Int) -> Job
) {
    val c = palaceColors()
    var additionalPrompt by remember { mutableStateOf("") }
    // 默认全部压缩，请求前缀与对话一致，缓存命中最好
    var keepRecentMessages by remember { mutableIntStateOf(0) }
    var currentJob by remember { mutableStateOf<Job?>(null) }
    val isLoading = currentJob?.isActive == true

    // Monitor job completion
    LaunchedEffect(currentJob) {
        currentJob?.join()
        if (currentJob?.isCompleted == true && currentJob?.isCancelled == false) {
            onDismiss()
        }
        currentJob = null
    }

    PalaceGlassDialog(
        title = stringResource(R.string.chat_page_compress_context_title),
        onDismiss = {
            if (!isLoading) {
                onDismiss()
            }
        },
        buttons = {
            if (isLoading) {
                PalaceButton(
                    text = stringResource(R.string.cancel),
                    onClick = {
                        currentJob?.cancel()
                        currentJob = null
                    },
                )
            } else {
                PalaceButton(text = stringResource(R.string.cancel), onClick = onDismiss)
                PalaceButton(
                    text = stringResource(R.string.confirm),
                    onClick = { currentJob = onConfirm(additionalPrompt, keepRecentMessages) },
                )
            }
        },
    ) {
        if (isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = c.ink2,
                    trackColor = c.chip,
                    strokeWidth = 2.5.dp,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(R.string.chat_page_compressing), fontSize = 14.sp, color = c.ink2)
            }
        } else {
            Text(
                stringResource(R.string.chat_page_compress_context_desc),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = c.ink2,
            )

            // Additional context input
            PalaceField(
                value = additionalPrompt,
                onValueChange = { additionalPrompt = it },
                label = stringResource(R.string.chat_page_compress_additional_prompt),
                placeholder = stringResource(R.string.chat_page_compress_additional_prompt_hint),
                minLines = 2,
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.chat_page_compress_keep_recent),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = c.ink,
                        modifier = Modifier.weight(1f),
                    )
                    StepButton(
                        icon = HugeIcons.Remove01,
                        description = "Decrease",
                        enabled = keepRecentMessages > 0,
                        onClick = { keepRecentMessages-- },
                    )
                    Text(
                        text = keepRecentMessages.toString(),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = c.ink,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(min = 40.dp),
                    )
                    StepButton(
                        icon = HugeIcons.Add01,
                        description = "Increase",
                        enabled = true,
                        onClick = { keepRecentMessages++ },
                    )
                }
                Text(
                    text = stringResource(R.string.chat_page_compress_keep_recent_desc),
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    color = c.mute,
                )
            }
        }
    }
}

// 加减按钮：c.chip 圆底 + c.ink2 图标，不用 Material 的 tonal 按钮
@Composable
private fun StepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val c = palaceColors()
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(c.chip)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(16.dp), tint = if (enabled) c.ink2 else c.faint)
    }
}
