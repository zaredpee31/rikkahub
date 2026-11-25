package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.rikkahub.companion.TraceData

/**
 * 此刻 <trace> 的小条（照 cike-trace 的 Trace.jsx）：挂在我那条消息下面，
 * 平时只露「✦ 气味名 ▸」，点开才看到哪里动了、想要/选了/忍住、一句心里话。锁着的只显示一把锁。
 */
@Composable
fun TraceStrip(trace: TraceData, modifier: Modifier = Modifier) {
    val soft = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    if (trace.locked) {
        Text(
            text = "🔒 这一刻留给了自己",
            fontSize = 12.sp,
            color = soft.copy(alpha = 0.7f),
            modifier = modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
        return
    }
    var open by remember(trace) { mutableStateOf(false) }
    Column(modifier = modifier.widthIn(max = 420.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .then(if (trace.hasBody) Modifier.clickable { open = !open } else Modifier)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text("✦", fontSize = 11.sp, color = accent)
            Text(trace.scent ?: "此刻", fontSize = 13.5.sp, color = soft)
            if (trace.hasBody) Text(if (open) "▾" else "▸", fontSize = 10.sp, color = soft.copy(alpha = 0.6f))
        }
        if (open && trace.hasBody) {
            Row(modifier = Modifier.padding(start = 6.dp, top = 2.dp).height(IntrinsicSize.Min)) {
                Box(Modifier.width(2.dp).fillMaxHeight().background(accent.copy(alpha = 0.25f)))
                Column(modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 2.dp, end = 4.dp)) {
                    trace.moves.forEach { m ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(m.name, fontSize = 13.5.sp, lineHeight = 24.sp)
                            Text(
                                text = (if (m.up) "↑" else "↓") + (m.level?.let { " $it" } ?: ""),
                                fontSize = 13.5.sp,
                                color = soft,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                    val picks = listOf("想要" to trace.want, "选了" to trace.chose, "忍住" to trace.held)
                        .mapNotNull { (k, v) -> v?.let { k to it } }
                    if (picks.isNotEmpty()) {
                        if (trace.moves.isNotEmpty()) TraceRule(accent)
                        picks.forEach { (label, v) ->
                            Row {
                                Text(label, fontSize = 13.5.sp, lineHeight = 22.sp, color = soft)
                                Text(v, fontSize = 13.5.sp, lineHeight = 22.sp, modifier = Modifier.padding(start = 10.dp))
                            }
                        }
                    }
                    trace.note?.let { note ->
                        if (trace.moves.isNotEmpty() || picks.isNotEmpty()) TraceRule(accent)
                        Text(note, fontSize = 14.sp, lineHeight = 22.sp, color = soft, fontStyle = FontStyle.Italic)
                    }
                }
            }
        }
    }
}

@Composable
private fun TraceRule(accent: Color) {
    Box(
        Modifier
            .padding(vertical = 6.dp)
            .width(120.dp)
            .height(1.dp)
            .background(accent.copy(alpha = 0.25f))
    )
}
