package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Voice
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceTextButton
import me.rerere.rikkahub.ui.pages.chat.VoicePhase
import me.rerere.rikkahub.ui.pages.chat.VoiceSessionState
import me.rerere.rikkahub.ui.theme.palaceColors

@Composable
internal fun VoiceModeRow(
    state: VoiceSessionState,
    onStop: () -> Unit,
    onRetry: () -> Unit,
) {
    val isError = state.phase == VoicePhase.Error
    val c = palaceColors()
    val statusColor = if (isError) c.error else c.ink2
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(HugeIcons.Voice, contentDescription = null, tint = statusColor, modifier = Modifier.size(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.chat_page_voice_title), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = statusColor)
                Text(
                    text = when (state.phase) {
                        VoicePhase.Off -> ""
                        VoicePhase.Connecting -> stringResource(R.string.chat_page_voice_connecting)
                        VoicePhase.Listening -> if (state.pendingReplies > 0)
                            stringResource(R.string.chat_page_voice_listening_queued) else stringResource(R.string.chat_page_voice_listening)
                        VoicePhase.Transcribing -> stringResource(R.string.chat_page_voice_transcribing)
                        VoicePhase.Waiting -> stringResource(R.string.chat_page_voice_waiting)
                        VoicePhase.Speaking -> stringResource(R.string.chat_page_voice_speaking)
                        VoicePhase.Error -> stringResource(R.string.chat_page_voice_paused)
                    },
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    color = c.mute,
                )
            }
            if (isError) PalaceTextButton(text = stringResource(R.string.chat_page_voice_retry), onClick = onRetry)
            PalaceTextButton(text = stringResource(R.string.chat_page_voice_end), onClick = onStop)
        }
        if (isError && !state.error.isNullOrBlank()) {
            Text(state.error, fontSize = 11.5.sp, lineHeight = 16.sp, color = statusColor)
        }
        if (state.transcript.isNotBlank()) {
            Text(
                text = state.transcript,
                fontSize = 14.sp,
                color = c.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
