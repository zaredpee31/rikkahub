package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.CustomHeader
import me.rerere.highlight.LocalCodeHighlighter
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.richtext.HighlightCodeVisualTransformation
import me.rerere.rikkahub.ui.theme.JetbrainsMono
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.ui.components.SelectTextField

private val jsonLenient = Json {
    ignoreUnknownKeys = true
    isLenient = true
    prettyPrint = true
}

private val COMMON_HEADER_NAMES = listOf(
    "User-Agent",
    "HTTP-Referer",
    "X-Title",
    "Referer",
    "Origin",
    "Accept-Language",
    "Cookie",
    "anthropic-beta",
    "OpenAI-Organization",
    "OpenAI-Project",
)

// 按已输入内容过滤常用请求头，没有匹配(或已完整输入)时展示全部
private fun commonHeaderNames(input: String): List<String> {
    val keyword = input.trim()
    val matched = COMMON_HEADER_NAMES.filter {
        it.contains(keyword, ignoreCase = true) && !it.equals(keyword, ignoreCase = true)
    }
    return matched.ifEmpty { COMMON_HEADER_NAMES }
}

@Composable
fun CustomHeaders(headers: List<CustomHeader>, onUpdate: (List<CustomHeader>) -> Unit) {
    PalaceSection(
        tag = "HEADERS",
        title = stringResource(R.string.assistant_page_custom_headers),
        trailing = {
            PalaceRoundIcon(HugeIcons.Add01, size = 28.dp) {
                onUpdate(headers + CustomHeader("", ""))
            }
        },
    )

    if (headers.isEmpty()) return

    PalaceGlass {
        headers.forEachIndexed { index, header ->
            if (index > 0) PalaceDivider()
            var headerName by remember(header.name) { mutableStateOf(header.name) }
            var headerValue by remember(header.value) { mutableStateOf(header.value) }

            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val updateHeaderName = { name: String ->
                        headerName = name
                        val updatedHeaders = headers.toMutableList()
                        updatedHeaders[index] = updatedHeaders[index].copy(name = name.trim())
                        onUpdate(updatedHeaders)
                    }
                    SelectTextField(
                        value = headerName,
                        options = commonHeaderNames(headerName),
                        onValueChange = updateHeaderName,
                        onOptionSelected = updateHeaderName,
                        label = { Text(stringResource(R.string.assistant_page_header_name)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PalaceField(
                        value = headerValue,
                        onValueChange = {
                            headerValue = it
                            val updatedHeaders = headers.toMutableList()
                            updatedHeaders[index] = updatedHeaders[index].copy(value = it.trim())
                            onUpdate(updatedHeaders)
                        },
                        label = stringResource(R.string.assistant_page_header_value),
                        singleLine = true,
                    )
                }
                DeleteIconButton(
                    contentDescription = stringResource(R.string.assistant_page_delete_header),
                    onClick = { onUpdate(headers.toMutableList().apply { removeAt(index) }) },
                )
            }
        }
    }
}

@Composable
fun CustomBodies(customBodies: List<CustomBody>, onUpdate: (List<CustomBody>) -> Unit) {
    val context = LocalContext.current
    PalaceSection(
        tag = "BODY",
        title = stringResource(R.string.assistant_page_custom_bodies),
        trailing = {
            PalaceRoundIcon(HugeIcons.Add01, size = 28.dp) {
                onUpdate(customBodies + CustomBody("", JsonPrimitive("")))
            }
        },
    )

    if (customBodies.isEmpty()) return

    PalaceGlass {
        customBodies.forEachIndexed { index, body ->
            if (index > 0) PalaceDivider()
            var bodyKey by remember(body.key) { mutableStateOf(body.key) }
            var bodyValueString by remember(body.value) {
                mutableStateOf(jsonLenient.encodeToString(JsonElement.serializer(), body.value))
            }
            var jsonParseError by remember { mutableStateOf<String?>(null) }

            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PalaceField(
                        value = bodyKey,
                        onValueChange = {
                            bodyKey = it
                            val updatedBodies = customBodies.toMutableList()
                            updatedBodies[index] = updatedBodies[index].copy(key = it.trim())
                            onUpdate(updatedBodies)
                        },
                        label = stringResource(R.string.assistant_page_body_key),
                        singleLine = true,
                    )
                    // JSON 语法高亮编辑器保留，只换容器和标题
                    PalaceField(
                        value = bodyValueString,
                        onValueChange = { newString ->
                            bodyValueString = newString
                            try {
                                val newJsonValue = jsonLenient.parseToJsonElement(newString)
                                val updatedBodies = customBodies.toMutableList()
                                updatedBodies[index] = updatedBodies[index].copy(value = newJsonValue)
                                onUpdate(updatedBodies)
                                jsonParseError = null
                            } catch (e: Exception) {
                                jsonParseError = context.getString(
                                    R.string.assistant_page_invalid_json,
                                    e.message?.take(100) ?: "",
                                )
                            }
                        },
                        label = stringResource(R.string.assistant_page_body_value),
                        minLines = 3,
                        isError = jsonParseError != null,
                        visualTransformation = HighlightCodeVisualTransformation(
                            language = "json",
                            highlighter = LocalCodeHighlighter.current,
                            darkMode = LocalDarkMode.current,
                        ),
                        textStyle = LocalTextStyle.current.merge(fontFamily = JetbrainsMono),
                    )
                    if (jsonParseError != null) {
                        Text(jsonParseError!!, fontSize = 11.5.sp, color = palaceColors().error)
                    }
                }
                DeleteIconButton(
                    contentDescription = stringResource(R.string.assistant_page_delete_body),
                    onClick = { onUpdate(customBodies.toMutableList().apply { removeAt(index) }) },
                )
            }
        }
    }
}

@Composable
private fun DeleteIconButton(contentDescription: String, onClick: () -> Unit) {
    val c = palaceColors()
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(HugeIcons.Delete01, contentDescription, Modifier.size(15.dp), tint = c.mute)
    }
}
