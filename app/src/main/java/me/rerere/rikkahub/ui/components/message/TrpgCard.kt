package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.ai.ui.UIMessagePart
import org.json.JSONObject

/** 跑团卡片：trpg 工具带回的叙事，原样给她看 */
data class TrpgCardData(val title: String, val narration: String)

fun trpgCards(parts: List<UIMessagePart>): List<TrpgCardData> =
    parts.filterIsInstance<UIMessagePart.Tool>()
        .filter { it.toolName == "trpg" && it.isExecuted }
        .mapNotNull { t ->
            val raw = t.output.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text }
            val o = runCatching { JSONObject(raw) }.getOrNull() ?: return@mapNotNull null
            if (!o.optBoolean("ok")) return@mapNotNull null
            val n = o.optString("narration").trim()
            if (n.isEmpty()) return@mapNotNull null
            val title = when (o.optString("action")) {
                "look" -> "灰橡宅 · 刚才"
                "settle" -> "灰橡宅 · 清算"
                else -> "灰橡宅"
            }
            TrpgCardData(title, n)
        }

@Composable
fun TrpgCard(card: TrpgCardData, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(card.title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                card.narration,
                fontSize = 13.5.sp,
                lineHeight = 21.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
