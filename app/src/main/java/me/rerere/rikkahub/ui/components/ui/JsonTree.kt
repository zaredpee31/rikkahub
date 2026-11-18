package me.rerere.rikkahub.ui.components.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.rikkahub.ui.theme.JetbrainsMono
import me.rerere.rikkahub.ui.theme.palaceColors

@Composable
fun JsonTree(
    json: JsonElement,
    modifier: Modifier = Modifier,
    initialExpandLevel: Int = 1
) {
    val c = palaceColors()
    var selectedString by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.horizontalScroll(rememberScrollState())) {
        JsonNode(
            element = json,
            key = null,
            depth = 0,
            initialExpandLevel = initialExpandLevel,
            onStringClick = { selectedString = it }
        )
    }

    selectedString?.let { content ->
        ModalBottomSheet(
            onDismissRequest = { selectedString = null },
            sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
            containerColor = c.bgTop,
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        ) {
            // Sheet 在独立窗口中，需要自己的 SelectionContainer，否则会沿用外层的 SelectionRegistrar 导致崩溃
            SelectionContainer {
                Text(
                    text = content,
                    fontFamily = JetbrainsMono,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    color = c.ink,
                )
            }
        }
    }
}

@Composable
private fun JsonNode(
    element: JsonElement,
    key: String?,
    depth: Int,
    initialExpandLevel: Int,
    onStringClick: (String) -> Unit
) {
    when (element) {
        is JsonObject -> JsonObjectNode(element, key, depth, initialExpandLevel, onStringClick)
        is JsonArray -> JsonArrayNode(element, key, depth, initialExpandLevel, onStringClick)
        is JsonPrimitive -> JsonPrimitiveNode(element, key, depth, onStringClick)
        is JsonNull -> JsonNullNode(key, depth)
    }
}

@Composable
private fun JsonObjectNode(
    obj: JsonObject,
    key: String?,
    depth: Int,
    initialExpandLevel: Int,
    onStringClick: (String) -> Unit
) {
    val c = palaceColors()
    var expanded by rememberSaveable { mutableStateOf(depth < initialExpandLevel) }
    val entries = remember(obj) { obj.entries.toList() }

    Column {
        Row(
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (expanded) HugeIcons.ArrowDown01 else HugeIcons.ArrowRight01,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = (depth * 16).dp)
                    .size(14.dp),
                tint = c.ink2
            )
            if (key != null) {
                KeyText(key)
                Text(": ", fontFamily = JetbrainsMono, color = c.ink2)
            }
            Text(
                text = if (expanded) "{" else "{ ... } (${entries.size})",
                fontFamily = JetbrainsMono,
                color = c.ink2
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column {
                entries.forEach { (childKey, childElement) ->
                    JsonNode(
                        element = childElement,
                        key = childKey,
                        depth = depth + 1,
                        initialExpandLevel = initialExpandLevel,
                        onStringClick = onStringClick
                    )
                }
                Row(modifier = Modifier.padding(start = (depth * 16 + 14).dp)) {
                    Text(
                        text = "}",
                        fontFamily = JetbrainsMono,
                        color = c.ink2
                    )
                }
            }
        }
    }
}

@Composable
private fun JsonArrayNode(
    array: JsonArray,
    key: String?,
    depth: Int,
    initialExpandLevel: Int,
    onStringClick: (String) -> Unit
) {
    val c = palaceColors()
    var expanded by rememberSaveable { mutableStateOf(depth < initialExpandLevel) }

    Column {
        Row(
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (expanded) HugeIcons.ArrowDown01 else HugeIcons.ArrowRight01,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = (depth * 16).dp)
                    .size(14.dp),
                tint = c.ink2
            )
            if (key != null) {
                KeyText(key)
                Text(": ", fontFamily = JetbrainsMono, color = c.ink2)
            }
            Text(
                text = if (expanded) "[" else "[ ... ] (${array.size})",
                fontFamily = JetbrainsMono,
                color = c.ink2
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column {
                array.forEachIndexed { index, childElement ->
                    JsonNode(
                        element = childElement,
                        key = index.toString(),
                        depth = depth + 1,
                        initialExpandLevel = initialExpandLevel,
                        onStringClick = onStringClick
                    )
                }
                Row(modifier = Modifier.padding(start = (depth * 16 + 14).dp)) {
                    Text(
                        text = "]",
                        fontFamily = JetbrainsMono,
                        color = c.ink2
                    )
                }
            }
        }
    }
}

@Composable
private fun JsonPrimitiveNode(
    primitive: JsonPrimitive,
    key: String?,
    depth: Int,
    onStringClick: (String) -> Unit
) {
    val c = palaceColors()
    Row(
        modifier = Modifier.padding(start = (depth * 16 + 14).dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (key != null) {
            KeyText(key)
            Text(": ", fontFamily = JetbrainsMono, color = c.ink2)
        }
        ValueText(
            primitive = primitive,
            onClick = if (primitive.isString) {
                { onStringClick(primitive.contentOrNull ?: "") }
            } else null
        )
    }
}

@Composable
private fun JsonNullNode(
    key: String?,
    depth: Int
) {
    val c = palaceColors()
    Row(
        modifier = Modifier.padding(start = (depth * 16 + 14).dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (key != null) {
            KeyText(key)
            Text(": ", fontFamily = JetbrainsMono, color = c.ink2)
        }
        Text(
            text = "null",
            fontFamily = JetbrainsMono,
            color = c.mute
        )
    }
}

@Composable
private fun KeyText(key: String) {
    val c = palaceColors()
    Text(
        text = "\"$key\"",
        fontFamily = JetbrainsMono,
        color = c.ink
    )
}

@Composable
private fun ValueText(primitive: JsonPrimitive, onClick: (() -> Unit)? = null) {
    val c = palaceColors()
    val (text, color) = when {
        primitive.isString -> {
            val content = (primitive.contentOrNull ?: "")
                .replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
            "\"$content\"" to Color(0xFF6A8759)
        }

        primitive.booleanOrNull != null -> {
            primitive.content to Color(0xFFCC7832)
        }

        primitive.longOrNull != null || primitive.doubleOrNull != null -> {
            primitive.content to Color(0xFF6897BB)
        }

        else -> {
            primitive.content to c.ink
        }
    }

    Text(
        text = text,
        fontFamily = JetbrainsMono,
        color = color,
        textDecoration = if (onClick != null) TextDecoration.Underline else null,
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    )
}
