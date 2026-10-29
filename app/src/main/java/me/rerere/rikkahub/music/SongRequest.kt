package me.rerere.rikkahub.music

import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.MusicNote03

data class SongRequest(
    val title: String,
    val artist: String,
) {
    val query: String get() = if (artist.isBlank()) title else "$title $artist"
}

object SongRequests {
    private const val QQ_MUSIC = "com.tencent.qqmusic"

    // 冒号全角半角都认
    private val tagRegex = Regex("""\[点歌[:：]([^\[\]\n]+)\]""")
    private val stripRegex = Regex("""\[点歌[:：][^\[\]\n]+\]\n?""")

    fun find(text: String): List<SongRequest> =
        tagRegex.findAll(text).mapNotNull { parse(it.groupValues[1]) }.toList()

    // 显示时把暗号从文字里拿掉，改成卡片
    fun strip(text: String): String = text.replace(stripRegex, "")

    // 按第一个短横线分开：歌手名里带横线也没事（比如 塞壬唱片-MSR）
    private fun parse(raw: String): SongRequest? {
        val body = raw.trim()
        if (body.isEmpty()) return null
        val i = body.indexOfFirst { it == '-' || it == '－' }
        if (i <= 0) return SongRequest(body, "")
        val title = body.substring(0, i).trim()
        val artist = body.substring(i + 1).trim()
        return SongRequest(title.ifEmpty { body }, artist)
    }

    // 先把「歌名 歌手」复制好，打开 QQ 音乐后在搜索框粘贴就行
    private fun copyQuery(context: Context, song: SongRequest) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        runCatching { clipboard.setPrimaryClip(ClipData.newPlainText("点歌", song.query)) }
        // Android 13 以上系统自己会提示已复制，这里就不重复弹了
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, "歌名复制好了，粘贴就能搜", Toast.LENGTH_SHORT).show()
        }
    }

    fun play(context: Context, song: SongRequest) {
        copyQuery(context, song)

        val pm = context.packageManager
        val search = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            setPackage(QQ_MUSIC)
            putExtra(SearchManager.QUERY, song.query)
            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, MediaStore.Audio.Media.ENTRY_CONTENT_TYPE)
            putExtra(MediaStore.EXTRA_MEDIA_TITLE, song.title)
            if (song.artist.isNotBlank()) putExtra(MediaStore.EXTRA_MEDIA_ARTIST, song.artist)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (search.resolveActivity(pm) != null &&
            runCatching { context.startActivity(search) }.isSuccess
        ) return

        // QQ 音乐不认上面的调用：退一步，只打开 QQ 音乐
        val launch = pm.getLaunchIntentForPackage(QQ_MUSIC)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (launch != null && runCatching { context.startActivity(launch) }.isSuccess) return

        Toast.makeText(context, "没找到 QQ 音乐", Toast.LENGTH_SHORT).show()
    }
}

private val SongOrange = Color(0xFFD97757)

@Composable
fun SongCard(
    song: SongRequest,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .width(240.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { SongRequests.play(context, song) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SongOrange.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = HugeIcons.MusicNote03,
                contentDescription = null,
                tint = SongOrange,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (song.artist.isNotBlank()) {
                Text(
                    text = song.artist,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(SongOrange),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = SongPlayIcon,
                contentDescription = "播放",
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

// 自己画的播放三角，不靠图标库
private val SongPlayIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "SongPlay",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(7f, 4.5f)
            lineTo(20f, 12f)
            lineTo(7f, 19.5f)
            close()
        }
    }.build()
}
