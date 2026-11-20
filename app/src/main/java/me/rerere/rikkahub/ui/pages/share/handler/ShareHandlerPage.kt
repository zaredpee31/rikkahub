package me.rerere.rikkahub.ui.pages.share.handler

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceBackdrop
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceGlass
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceNavRow
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceTopBar
import me.rerere.rikkahub.ui.theme.palaceColors
import me.rerere.rikkahub.utils.base64Encode
import me.rerere.rikkahub.utils.navigateToChatPage
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ShareHandlerPage(text: String, image: String?) {
    val vm: ShareHandlerVM = koinViewModel(parameters = { parametersOf(text) })
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val navController = LocalNavController.current
    val c = palaceColors()
    val context = LocalContext.current
    var stashing by remember { mutableStateOf(false) }
    var stashStatus by remember { mutableStateOf<String?>(null) }
    PalaceBackdrop {
        Column(Modifier.fillMaxSize()) {
            PalaceTopBar(
                title = stringResource(R.string.share_handler_page_title),
                sub = "SHARE",
            )
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    PalaceGlass {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = vm.shareText,
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                color = c.ink,
                            )

                            image?.let {
                                AsyncImage(
                                    model = it,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                item {
                    PalaceGlass {
                        PalaceNavRow(
                            title = "囤进待归仓",
                            desc = stashStatus ?: "是链接就先读出标题和正文再存",
                            enabled = !stashing,
                            onClick = {
                                stashing = true
                                stashStatus = "正在囤，链接要读一会儿…"
                                scope.launch {
                                    stashStatus = me.rerere.rikkahub.companion.StashApi.stashShare(context, vm.shareText)
                                    stashing = false
                                }
                            },
                        )
                    }
                }

                items(settings.assistants) { assistant ->
                    val title = assistant.name.ifEmpty {
                        stringResource(R.string.assistant_page_default_assistant)
                    }
                    PalaceGlass {
                        PalaceNavRow(
                            title = title,
                            desc = null,
                            onClick = {
                                scope.launch {
                                    vm.updateAssistant(assistant.id)
                                    navigateToChatPage(
                                        navigator = navController,
                                        initText = vm.shareText.base64Encode(),
                                        initFiles = image?.let { listOf(it.toUri()) } ?: emptyList()
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
