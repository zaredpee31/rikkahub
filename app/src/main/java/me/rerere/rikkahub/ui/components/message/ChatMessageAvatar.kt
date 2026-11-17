package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Favourite
import me.rerere.rikkahub.ui.theme.NameStyle
import me.rerere.rikkahub.ui.theme.palaceColors
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.isEmptyUIMessage
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.context.LocalSettings

@Composable
fun ChatMessageUserAvatar(
    message: UIMessage,
    avatar: Avatar,
    nickname: String,
    modifier: Modifier = Modifier,
) {
    val settings = LocalSettings.current
    if (message.role == MessageRole.USER && !message.parts.isEmptyUIMessage() && settings.displaySetting.showUserAvatar) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = nickname.ifEmpty { stringResource(R.string.user_default_name) },
                style = NameStyle,
                color = palaceColors().ink2,
                maxLines = 1,
            )
            // 外框保持 28dp 不变：白边 3.3 / 57 按比例缩成 1.5dp，心形角标 20.2 / 57 缩成 10dp
            Box(modifier = Modifier.size(28.dp)) {
                UIAvatar(
                    name = nickname,
                    modifier = Modifier
                        .matchParentSize()
                        .border(1.5.dp, Color.White, RoundedCornerShape(9.dp))
                        .padding(1.5.dp),
                    value = avatar,
                    loading = false,
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 1.dp, y = 2.dp)
                        .size(10.dp)
                        .background(Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = HugeIcons.Favourite,
                        contentDescription = null,
                        tint = palaceColors().ink2,
                        modifier = Modifier.size(6.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun ChatMessageAssistantAvatar(
    message: UIMessage,
    loading: Boolean,
    model: Model?,
    assistant: Assistant?,
    modifier: Modifier = Modifier,
) {
    val settings = LocalSettings.current
    val showIcon = settings.displaySetting.showModelIcon
    val useAssistantAvatar = assistant?.useAssistantAvatar == true
    if (message.role == MessageRole.ASSISTANT && (model != null || useAssistantAvatar)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
        ) {
            if (useAssistantAvatar) {
                if (showIcon) {
                    UIAvatar(
                        name = assistant.name,
                        modifier = Modifier.size(28.dp),
                        value = assistant.avatar,
                        loading = loading,
                    )
                }
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (settings.displaySetting.showModelName) {
                        Text(
                            text = assistant.name.ifEmpty { stringResource(R.string.assistant_page_default_assistant) },
                            style = NameStyle,
                            color = palaceColors().ink2,
                            maxLines = 1,
                        )
                    }
                }
            } else if (model != null) {
                if (showIcon) {
                    AutoAIIcon(
                        name = model.modelId,
                        modifier = Modifier.size(28.dp),
                        loading = loading
                    )
                }
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (settings.displaySetting.showModelName) {
                        Text(
                            text = model.displayName,
                            style = NameStyle,
                            color = palaceColors().ink2,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
