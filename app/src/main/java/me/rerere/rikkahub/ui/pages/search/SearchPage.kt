package me.rerere.rikkahub.ui.pages.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Sorting01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.db.fts.MessageSearchResult
import me.rerere.rikkahub.data.db.fts.MessageSearchSort
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceBackdrop
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceChip
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceDivider
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceEmptyState
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceGlass
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceRoundIcon
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceSection
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceTopBar
import me.rerere.rikkahub.ui.theme.palaceColors
import me.rerere.rikkahub.utils.navigateToChatPage
import me.rerere.rikkahub.utils.toLocalDateTime
import org.koin.androidx.compose.koinViewModel
import kotlin.uuid.Uuid

@Composable
fun SearchPage(vm: SearchVM = koinViewModel()) {
    val navController = LocalNavController.current
    val focusRequester = remember { FocusRequester() }
    var showRebuildDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    if (showRebuildDialog) {
        AlertDialog(
            onDismissRequest = { showRebuildDialog = false },
            title = { Text(stringResource(R.string.search_page_rebuild_index)) },
            text = { Text(stringResource(R.string.search_page_rebuild_index_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRebuildDialog = false
                        vm.rebuildIndex()
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRebuildDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    PalaceBackdrop {
        Column(Modifier.fillMaxSize()) {
            // PalaceTopBar 的右侧只有一个图标位，排序和重建两个按钮叠在顶栏右侧，行为不变
            Box(Modifier.fillMaxWidth()) {
                PalaceTopBar(
                    title = stringResource(R.string.search_page_title),
                    sub = "SEARCH",
                    onBack = { navController.popBackStack() },
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(end = 14.dp, top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SortMenuButton(
                        current = vm.sortOrder,
                        onSortChange = { vm.onSortChange(it) },
                    )
                    PalaceRoundIcon(
                        icon = HugeIcons.Refresh01,
                        onClick = { if (!vm.isRebuilding) showRebuildDialog = true },
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SearchBox(
                    value = vm.searchQuery,
                    onValueChange = { vm.onQueryChange(it) },
                    placeholder = stringResource(R.string.search_page_placeholder),
                    onSearch = { vm.search() },
                    modifier = Modifier.focusRequester(focusRequester),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MessageSearchScope.entries.forEach { scope ->
                        PalaceChip(
                            text = stringResource(
                                when (scope) {
                                    MessageSearchScope.CURRENT_ASSISTANT -> R.string.search_page_scope_current_assistant
                                    MessageSearchScope.ALL_ASSISTANTS -> R.string.search_page_scope_all_assistants
                                }
                            ),
                            selected = vm.searchScope == scope,
                            onClick = { vm.onScopeChange(scope) },
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    if (vm.isLoading || vm.isRebuilding) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    when {
                        vm.isRebuilding -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                val (current, total) = vm.rebuildProgress
                                PalaceEmptyState(
                                    text = if (total > 0) stringResource(
                                        R.string.search_page_rebuilding,
                                        current,
                                        total
                                    ) else stringResource(R.string.search_page_rebuilding_simple),
                                    icon = HugeIcons.Refresh01,
                                )
                            }
                        }

                        vm.searchQuery.isBlank() -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                PalaceEmptyState(
                                    text = stringResource(R.string.search_page_hint),
                                    icon = HugeIcons.Search01,
                                )
                            }
                        }

                        vm.results.isEmpty() && !vm.isLoading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                PalaceEmptyState(
                                    text = stringResource(R.string.search_page_no_results),
                                    icon = HugeIcons.Search01,
                                )
                            }
                        }

                        else -> {
                            LazyColumn(
                                contentPadding = PaddingValues(bottom = 40.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                item(key = "section") {
                                    PalaceSection("RESULTS", "结果")
                                }
                                item(key = "results") {
                                    PalaceGlass {
                                        vm.results.forEachIndexed { index, result ->
                                            if (index > 0) PalaceDivider()
                                            SearchResultItem(
                                                result = result,
                                                onClick = {
                                                    navigateToChatPage(
                                                        navController,
                                                        chatId = Uuid.parse(result.conversationId),
                                                        nodeId = Uuid.parse(result.nodeId),
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 搜索框：外观照 PalaceSearchField，另带键盘的搜索键，按下执行搜索 */
@Composable
private fun SearchBox(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = palaceColors()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = TextStyle(fontSize = 13.sp, color = c.ink),
        cursorBrush = SolidColor(c.ink),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        decorationBox = { inner ->
            Row(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.glassStrong)
                    .border(0.5.dp, c.border, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(HugeIcons.Search01, null, Modifier.size(15.dp), tint = c.mute)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, fontSize = 13.sp, color = c.faint)
                    inner()
                }
            }
        },
    )
}

@Composable
private fun SortMenuButton(
    current: MessageSearchSort,
    onSortChange: (MessageSearchSort) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        PalaceRoundIcon(
            icon = HugeIcons.Sorting01,
            onClick = { expanded = true },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            MessageSearchSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                when (sort) {
                                    MessageSearchSort.RELEVANCE -> R.string.search_page_sort_relevance
                                    MessageSearchSort.NEWEST_FIRST -> R.string.search_page_sort_newest
                                    MessageSearchSort.OLDEST_FIRST -> R.string.search_page_sort_oldest
                                }
                            )
                        )
                    },
                    leadingIcon = {
                        RadioButton(
                            selected = sort == current,
                            onClick = null,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSortChange(sort)
                    },
                )
            }
        }
    }
}

@Composable
private fun SearchResultItem(
    result: MessageSearchResult,
    onClick: () -> Unit,
) {
    val c = palaceColors()
    val untitled = stringResource(R.string.search_page_untitled)
    val snippetText = buildAnnotatedString {
        val snippet = result.snippet
        var index = 0
        while (index < snippet.length) {
            val start = snippet.indexOf('[', index)
            if (start == -1) {
                append(snippet.substring(index))
                break
            }
            if (start > index) {
                append(snippet.substring(index, start))
            }
            val end = snippet.indexOf(']', start + 1)
            if (end == -1) {
                append(snippet.substring(start))
                break
            }
            val matched = snippet.substring(start + 1, end)
            withStyle(SpanStyle(background = c.chip, color = c.ink)) {
                append(matched)
            }
            index = end + 1
        }
    }
    val formattedTime = remember(result.updateAt) {
        result.updateAt.toLocalDateTime()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = result.title.ifBlank { untitled },
            fontSize = 14.sp,
            color = c.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = snippetText,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            color = c.mute,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = formattedTime,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            color = c.faint,
        )
    }
}
