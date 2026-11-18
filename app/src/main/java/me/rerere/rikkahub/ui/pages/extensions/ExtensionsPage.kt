package me.rerere.rikkahub.ui.pages.extensions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Book03
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.Puzzle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.pages.assistant.detail.DetailNavRow
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceBackdrop
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceDivider
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceGlass
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceSection
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceTopBar

@Composable
fun ExtensionsPage() {
    val navController = LocalNavController.current

    PalaceBackdrop {
        Column(Modifier.fillMaxSize()) {
            PalaceTopBar(
                title = stringResource(R.string.extensions_page_title),
                sub = "EXTENSIONS",
                onBack = { navController.popBackStack() },
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PalaceSection("EXTEND", stringResource(R.string.extensions_page_section_extensions))
                PalaceGlass {
                    DetailNavRow(
                        icon = HugeIcons.Book03,
                        title = stringResource(R.string.extensions_page_prompts),
                        desc = stringResource(R.string.extensions_page_prompts_desc),
                        onClick = { navController.navigate(Screen.Prompts) },
                    )
                    PalaceDivider()
                    DetailNavRow(
                        icon = HugeIcons.Puzzle,
                        title = stringResource(R.string.extensions_page_agent_skills),
                        desc = stringResource(R.string.extensions_page_agent_skills_desc),
                        onClick = { navController.navigate(Screen.Skills) },
                    )
                    PalaceDivider()
                    DetailNavRow(
                        icon = HugeIcons.Folder01,
                        title = stringResource(R.string.extensions_page_workspace),
                        desc = stringResource(R.string.extensions_page_workspace_desc),
                        onClick = { navController.navigate(Screen.Workspaces) },
                    )
                }
            }
        }
    }
}
