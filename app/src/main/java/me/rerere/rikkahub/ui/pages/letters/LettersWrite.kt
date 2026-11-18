package me.rerere.rikkahub.ui.pages.letters

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

@Composable
private fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    Text(
        text,
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .border(BorderStroke(.8.dp, if (on) Ink2 else FaintC), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
        fontSize = 11.sp, color = if (on) Ink else Ink2,
    )
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, hint: String, style: TextStyle, modifier: Modifier) {
    BasicTextField(
        value = value, onValueChange = onChange, textStyle = style, cursorBrush = SolidColor(Ink2), modifier = modifier,
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(hint, style = style.copy(color = FaintC))
                inner()
            }
        },
    )
}

/** 写信：标题、正文、落款小葵；直接寄出或定时打开（日期必须是以后）。 */
@Composable
internal fun WritePaper(
    draft: LetterDraft,
    onDraft: (LetterDraft) -> Unit,
    sending: Boolean,
    error: String?,
    animate: Boolean,
    onBounds: (Rect) -> Unit,
    onSend: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val minBody = (LocalConfiguration.current.screenHeightDp * .3f).dp
    PaperLayer(onClose = onClose, closeLabel = "先不写 ×", animate = animate, onBounds = onBounds) {
        Column(Modifier.fillMaxWidth()) {
            Text("TO 阿辞", fontSize = 9.sp, letterSpacing = 3.sp, color = MuteC)
            Field(
                draft.title, { onDraft(draft.copy(title = it.take(40))) }, "给这封信起个名字",
                TextStyle(fontSize = 19.sp, color = Ink, fontFamily = FontFamily.Serif),
                Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 12.dp),
            )
            Field(
                draft.body, { onDraft(draft.copy(body = it)) }, "阿辞，",
                TextStyle(fontSize = 15.sp, lineHeight = 30.sp, color = Ink, fontFamily = FontFamily.Serif),
                Modifier.fillMaxWidth().heightIn(min = minBody).padding(top = 6.dp),
            )
            Text("小葵", Modifier.fillMaxWidth().padding(top = 26.dp), fontSize = 15.sp, color = Ink, fontFamily = FontFamily.Serif, textAlign = TextAlign.End)
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Chip("直接寄出", !draft.timed) { onDraft(draft.copy(timed = false)) }
                Chip("定时打开", draft.timed) { onDraft(draft.copy(timed = true)) }
                if (draft.timed) {
                    Text(
                        draft.date.ifBlank { "选日期" },
                        Modifier.clickable {
                            val t = LocalDate.now().plusDays(1)
                            DatePickerDialog(context, { _, y, m, d -> onDraft(draft.copy(date = "%04d-%02d-%02d".format(y, m + 1, d))) }, t.year, t.monthValue - 1, t.dayOfMonth)
                                .apply { datePicker.minDate = t.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }
                                .show()
                        }.padding(horizontal = 2.dp, vertical = 6.dp),
                        fontSize = 12.sp, color = Ink2,
                    )
                }
            }
            if (error != null) Text(error, Modifier.padding(top = 12.dp), fontSize = 11.sp, color = Color(0xFFB4615A))
            Text(
                if (sending) "投递中……" else "投 递",
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 24.dp)
                    .alpha(if (sending) .5f else 1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF3C3E42))
                    .clickable(enabled = !sending, onClick = onSend)
                    .padding(horizontal = 34.dp, vertical = 10.dp),
                fontSize = 12.sp, letterSpacing = 5.sp, color = Color.White,
            )
        }
    }
}
