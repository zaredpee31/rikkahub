package me.rerere.rikkahub.ui.pages.letters

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Image02

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LettersPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = remember { LettersController(context, scope, !reduceMotion(context)) }
    val short = LocalConfiguration.current.screenHeightDp <= 700
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch { c.pickImage(uri) }
    }
    LaunchedEffect(Unit) { c.loadImage() }
    LaunchedEffect(Unit) { c.refresh() }
    LaunchedEffect(c.rows != null) { c.playIntro() }
    BackHandler {
        when {
            c.writing -> c.writing = false
            c.reading != null -> c.closeReading()
            else -> onBack()
        }
    }

    Box(Modifier.fillMaxSize()) {
        LettersBackdrop(c.animate)
        Box(Modifier.align(Alignment.TopEnd).padding(end = 10.dp, top = (LocalConfiguration.current.screenHeightDp * .34f).dp).width(0.dp)) {
            LettersVerticalText()
        }
        LettersTicker(c.animate, Modifier.align(Alignment.BottomCenter).padding(bottom = 98.dp).clipToBounds())

        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(if (short) 60.dp else 78.dp))
            Text("PIECE BY PIECE · NO.%02d".format(c.boardIndex + 1), fontSize = 9.sp, letterSpacing = 5.sp, color = MuteC)
            Text(
                "信箱", Modifier.padding(top = 8.dp), fontSize = 22.sp, letterSpacing = 10.sp, fontWeight = FontWeight.Light,
                color = Ink, fontFamily = FontFamily.Serif,
            )
            Spacer(Modifier.height(26.dp))
            BoardArea(c)
            Spacer(Modifier.height(if (short) 36.dp else 46.dp))
            Caption(c)
            WritePill(c.animate) { c.sendError = null; c.writing = true }
            Spacer(Modifier.height(130.dp))
        }

        // 右上角：换拼图图案（小图标，长按恢复默认）
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 58.dp, end = 10.dp)
                .size(36.dp)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                    onClick = { picker.launch("image/*") },
                    onLongClick = { scope.launch { c.resetImage() } },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(HugeIcons.Image02, "换拼图图案", Modifier.size(18.dp), tint = MuteC)
        }

        c.reading?.let { r -> ReadPaper(r.date, r.from, r.title, r.body, r.error, c.animate) { c.closeReading() } }
        if (c.writing) {
            WritePaper(
                draft = c.draft, onDraft = { c.saveDraft(it) }, sending = c.sending, error = c.sendError, animate = c.animate,
                onBounds = { c.writeBounds = it }, onSend = { scope.launch { c.send() } }, onClose = { c.writing = false },
            )
        }
        c.shatterFrom?.let { ShatterOverlay(it, c.shatterTo, c.shatter.value) }
    }
}
