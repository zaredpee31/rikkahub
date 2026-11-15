package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.context.LocalNavController
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun AssistantRequestPage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(parameters = { parametersOf(id) })
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val navController = LocalNavController.current

    PalaceSubPage(
        title = stringResource(R.string.assistant_page_tab_request),
        sub = "REQUEST",
        onBack = { navController.popBackStack() },
    ) {
        AssistantRequestContent(assistant = assistant, onUpdate = { vm.update(it) })
    }
}

@Composable
internal fun AssistantRequestContent(
    assistant: Assistant,
    onUpdate: (Assistant) -> Unit,
) {
    CustomHeaders(
        headers = assistant.customHeaders,
        onUpdate = { onUpdate(assistant.copy(customHeaders = it)) },
    )
    CustomBodies(
        customBodies = assistant.customBodies,
        onUpdate = { onUpdate(assistant.copy(customBodies = it)) },
    )
}
