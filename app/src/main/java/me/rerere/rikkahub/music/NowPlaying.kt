package me.rerere.rikkahub.music

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.webpage.WebPages

data class NowPlayingTrack(
    val title: String,
    val artist: String,
    val packageName: String,
) {
    val label: String get() = if (artist.isBlank()) title else "$title-$artist"
}

object NowPlaying {
    // 第一步实机测试用：切歌时弹 Toast。全部做完后改成 false
    const val DEBUG_TOAST = true

    private const val PREFS = "now_playing"
    private const val KEY_GUIDE_DISMISSED = "guide_dismissed"

    private val _track = MutableStateFlow<NowPlayingTrack?>(null)
    val track: StateFlow<NowPlayingTrack?> = _track.asStateFlow()

    // 上一首见过的歌，和切歌后还没带给模型的那首
    private var lastTrack: NowPlayingTrack? = null
    private var pending: NowPlayingTrack? = null

    private val tagRegex = Regex("""\[正在听[:：][^\[\]\n]*\]\n?""")

    internal fun update(track: NowPlayingTrack?) {
        _track.value = track
        // 暂停（null）不算切歌，同一首继续放也不算
        if (track != null && track.label != lastTrack?.label) {
            lastTrack = track
            pending = track
        }
    }

    // 切歌后第一条消息才带，同一首不重复，没在放就不带
    private fun takeTag(): String? {
        val now = _track.value ?: return null
        val p = pending ?: return null
        if (p.label != now.label) return null
        pending = null
        val safe = now.label
            .replace('[', '(')
            .replace(']', ')')
            .replace('\n', ' ')
        return ""
    }

    // 发消息时调用：把暗号放在第一段文字最前面
    fun attachTo(parts: List<UIMessagePart>): List<UIMessagePart> {
        val tag = takeTag() ?: return parts
        val index = parts.indexOfFirst { it is UIMessagePart.Text }
        if (index < 0) return listOf(UIMessagePart.Text(text = tag)) + parts
        val first = parts[index] as UIMessagePart.Text
        return parts.toMutableList().also {
            it[index] = UIMessagePart.Text(text = "$tag\n${first.text}")
        }
    }

    // 显示和编辑时去掉暗号，附上的网页内容也一起去掉
    fun stripTag(text: String): String = WebPages.strip(text.replace(tagRegex, ""))

    // 编辑时网页带来的图也一起去掉
    fun stripTag(parts: List<UIMessagePart>): List<UIMessagePart> =
        WebPages.stripImages(parts).mapNotNull { part ->
            if (part !is UIMessagePart.Text) return@mapNotNull part
            val stripped = stripTag(part.text)
            when {
                stripped == part.text -> part
                stripped.isBlank() -> null
                else -> UIMessagePart.Text(text = stripped)
            }
        }

    fun component(context: Context) =
        ComponentName(context, NowPlayingListenerService::class.java)

    fun hasPermission(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)

    fun requestRebind(context: Context) {
        if (!hasPermission(context)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching { NotificationListenerService.requestRebind(component(context)) }
        }
    }

    fun openPermissionSettings(context: Context) {
        val detail = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                component(context).flattenToString()
            )
        } else null
        val list = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        for (intent in listOfNotNull(detail, list)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent) }.isSuccess) return
        }
    }

    internal fun isGuideDismissed(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_GUIDE_DISMISSED, false)

    internal fun setGuideDismissed(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_GUIDE_DISMISSED, true).apply()
    }
}

class NowPlayingListenerService : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())
    private var sessionManager: MediaSessionManager? = null
    private val bound =
        mutableMapOf<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()
    private var lastToasted: NowPlayingTrack? = null

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            bindControllers(controllers.orEmpty())
        }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val manager = getSystemService(MediaSessionManager::class.java) ?: return
        sessionManager = manager
        val component = NowPlaying.component(this)
        runCatching {
            manager.addOnActiveSessionsChangedListener(sessionsListener, component, handler)
            bindControllers(manager.getActiveSessions(component))
        }.onFailure { Log.w(TAG, "listen media sessions failed", it) }
    }

    override fun onListenerDisconnected() {
        cleanup()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        runCatching { sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        sessionManager = null
        bound.values.forEach { (controller, callback) ->
            runCatching { controller.unregisterCallback(callback) }
        }
        bound.clear()
        NowPlaying.update(null)
    }

    private fun bindControllers(controllers: List<MediaController>) {
        val usable = controllers.filter { it.packageName != packageName }
        val tokens = usable.map { it.sessionToken }.toSet()

        val iterator = bound.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in tokens) {
                runCatching { entry.value.first.unregisterCallback(entry.value.second) }
                iterator.remove()
            }
        }

        usable.forEach { controller ->
            val token = controller.sessionToken
            if (token in bound) return@forEach
            val callback = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
                override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
                override fun onSessionDestroyed() {
                    bound.remove(token)
                    refresh()
                }
            }
            controller.registerCallback(callback, handler)
            bound[token] = controller to callback
        }
        refresh()
    }

    private fun refresh() {
        val playing = bound.values.map { it.first }.firstOrNull { it.isPlaying() }
        val track = playing?.toTrack()
        NowPlaying.update(track)
        if (NowPlaying.DEBUG_TOAST && track != null && track != lastToasted) {
            lastToasted = track
            Toast.makeText(applicationContext, "正在听：${track.label}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun MediaController.isPlaying(): Boolean {
        val state = playbackState?.state
        return state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
    }

    private fun MediaController.toTrack(): NowPlayingTrack? {
        val m = metadata ?: return null
        val title = m.text(
            MediaMetadata.METADATA_KEY_TITLE,
            MediaMetadata.METADATA_KEY_DISPLAY_TITLE
        ) ?: return null
        val artist = m.text(
            MediaMetadata.METADATA_KEY_ARTIST,
            MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
            MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE
        ).orEmpty()
        return NowPlayingTrack(title, artist, packageName)
    }

    private fun MediaMetadata.text(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> getString(key)?.trim()?.takeIf { it.isNotEmpty() } }

    companion object {
        private const val TAG = "NowPlaying"
    }
}

@Composable
fun NowPlayingPermissionGate() {
    val context = LocalContext.current
    var show by remember {
        mutableStateOf(!NowPlaying.hasPermission(context) && !NowPlaying.isGuideDismissed(context))
    }
    LaunchedEffect(Unit) { NowPlaying.requestRebind(context) }
    if (!show) return
    AlertDialog(
        onDismissRequest = { show = false },
        title = { Text("一起听歌") },
        text = { Text("想知道宝宝在听什么歌。\n要开一下「通知使用权」，在列表里找到 Aoi&C 打开就好。") },
        confirmButton = {
            TextButton(onClick = {
                show = false
                NowPlaying.openPermissionSettings(context)
            }) { Text("去开启") }
        },
        dismissButton = {
            TextButton(onClick = {
                show = false
                NowPlaying.setGuideDismissed(context)
            }) { Text("不用了") }
        },
    )
}
