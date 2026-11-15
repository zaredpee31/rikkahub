package me.rerere.rikkahub.ui.pages.assistant.detail

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.files.FilesManager
import org.koin.compose.koinInject

@Composable
fun BackgroundPicker(
    modifier: Modifier = Modifier,
    background: String?,
    backgroundOpacity: Float = 1.0f,
    onUpdate: (String?) -> Unit,
) {
    val filesManager: FilesManager = koinInject()
    var showPickOption by remember { mutableStateOf(false) }
    var showUrlInput by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        uri?.let {
            val localUris = filesManager.createChatFilesByContents(listOf(it))
            localUris.firstOrNull()?.let { localUri ->
                onUpdate(localUri.toString())
            }
        }
    }

    val previewOpacity = backgroundOpacity.coerceIn(0f, 1f)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (background != null) {
            AsyncImage(
                model = background,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .alpha(previewOpacity),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PalaceTextButton(
                text = if (background != null) {
                    stringResource(R.string.assistant_page_change_background)
                } else {
                    stringResource(R.string.assistant_page_select_background)
                },
                onClick = { showPickOption = true },
            )
            if (background != null) {
                PalaceTextButton(
                    text = stringResource(R.string.assistant_page_remove),
                    onClick = { onUpdate(null) },
                )
            }
        }
    }

    if (showPickOption) {
        PalaceDialog(
            title = stringResource(R.string.assistant_page_select_background),
            onDismiss = { showPickOption = false },
            confirmText = null,
        ) {
            PalaceTextButton(
                text = stringResource(R.string.assistant_page_select_from_gallery),
                onClick = {
                    showPickOption = false
                    imagePickerLauncher.launch("image/*")
                },
            )
            PalaceTextButton(
                text = stringResource(R.string.assistant_page_enter_image_url),
                onClick = {
                    showPickOption = false
                    urlInput = ""
                    showUrlInput = true
                },
            )
            if (background != null) {
                PalaceTextButton(
                    text = stringResource(R.string.assistant_page_remove_background),
                    onClick = {
                        showPickOption = false
                        onUpdate(null)
                    },
                )
            }
        }
    }

    if (showUrlInput) {
        PalaceDialog(
            title = stringResource(R.string.assistant_page_enter_image_url),
            onDismiss = { showUrlInput = false },
            confirmText = stringResource(R.string.assistant_page_confirm),
            confirmEnabled = urlInput.isNotBlank(),
            onConfirm = {
                if (urlInput.isNotBlank()) {
                    onUpdate(urlInput.trim())
                    showUrlInput = false
                }
            },
            dismissText = stringResource(R.string.assistant_page_cancel),
        ) {
            PalaceField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = stringResource(R.string.assistant_page_image_url),
                placeholder = "https://example.com/image.jpg",
                singleLine = true,
            )
        }
    }
}
