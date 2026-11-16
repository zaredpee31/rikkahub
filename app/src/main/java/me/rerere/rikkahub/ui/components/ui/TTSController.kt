package me.rerere.rikkahub.ui.components.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.ui.toast.ToastType

/** 朗读出错时弹提示。原来的悬浮播放控制条（暂停 / X / >）已删，语音条直接点一下播放。 */
@Composable
fun TTSController() {
    val ttsState = LocalTTSState.current
    val toaster = LocalToaster.current

    LaunchedEffect(ttsState, toaster) {
        ttsState.error.collect { error ->
            if (error != null) {
                toaster.show(message = error, type = ToastType.Error)
            }
        }
    }
}
