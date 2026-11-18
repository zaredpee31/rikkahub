package me.rerere.rikkahub.ui.pages.favorite

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.InLove
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.SwipeToDelete
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceBackdrop
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceEmptyState
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceGlass
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceTopBar
import me.rerere.rikkahub.ui.theme.palaceColors
import me.rerere.rikkahub.utils.navigateToChatPage
import me.rerere.rikkahub.utils.toLocalDateTime
import org.koin.androidx.compose.koinViewModel
import java.time.Instant

@Composable
fun FavoritePage(vm: FavoriteVM = koinViewModel()) {
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val favorites = vm.nodeFavorites.collectAsStateWithLifecycle().value
    val favoriteRemovedText = stringResource(R.string.favorite_page_removed)
    val undoText = stringResource(R.string.history_page_undo)

    PalaceBackdrop {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                PalaceTopBar(
                    title = stringResource(R.string.favorite_page_title),
                    sub = "FAVORITES",
                    onBack = { navController.popBackStack() },
                )

                if (favorites.isEmpty()) {
                    PalaceEmptyState(
                        text = stringResource(R.string.favorite_page_no_favorites),
                        icon = HugeIcons.InLove,
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 40.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .navigationBarsPadding(),
                    ) {
                        items(favorites, key = { it.id }) { item ->
                            SwipeableFavoriteCard(
                                item = item,
                                onClick = { navigateToChatPage(navController, item.conversationId, nodeId = item.nodeId) },
                                onDelete = {
                                    scope.launch {
                                        val entity = vm.getEntityByRefKey(item.refKey) ?: return@launch
                                        vm.removeFavorite(item.refKey)
                                        val result = snackbarHostState.showSnackbar(
                                            message = favoriteRemovedText,
                                            actionLabel = undoText,
                                            withDismissAction = true,
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            vm.restoreFavorite(entity)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem(),
                            )
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
            )
        }
    }
}

@Composable
private fun SwipeableFavoriteCard(
    item: NodeFavoriteListItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SwipeToDelete(
        onDelete = onDelete,
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
    ) {
        FavoriteCard(
            item = item,
            onClick = onClick,
        )
    }
}

@Composable
private fun FavoriteCard(
    item: NodeFavoriteListItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = palaceColors()
    PalaceGlass(modifier = modifier) {
        Box(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
            SelectionContainer {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = item.conversationTitle.ifBlank { stringResource(R.string.favorite_page_untitled_conversation) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = c.ink,
                    )
                    val dateText = Instant.ofEpochMilli(item.createdAt).toLocalDateTime()
                    Text(
                        text = item.preview,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 14.sp,
                        color = c.ink,
                    )
                    Text(
                        text = dateText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        color = c.mute,
                    )
                }
            }
        }
    }
}
