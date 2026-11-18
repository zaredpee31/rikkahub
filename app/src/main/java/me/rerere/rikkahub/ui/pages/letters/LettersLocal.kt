package me.rerere.rikkahub.ui.pages.letters

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.R
import java.io.File

/** 草稿，寄出成功才清空。 */
internal data class LetterDraft(val title: String, val body: String, val timed: Boolean, val date: String)

/** 信箱页的本地记录：她读过哪些阿辞的信、没寄出的草稿、自己换的拼图图。全在 app 私有空间。 */
internal class LettersLocal(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("letters_local", Context.MODE_PRIVATE)
    private val imageFile get() = File(app.filesDir, "letters_puzzle.jpg")

    fun readIds(): Set<String> = prefs.getStringSet("read_ids", emptySet()).orEmpty().toSet()

    fun markRead(id: String) {
        prefs.edit().putStringSet("read_ids", readIds() + id).apply()
    }

    fun draft(): LetterDraft = LetterDraft(
        title = prefs.getString("draft_title", "").orEmpty(),
        body = prefs.getString("draft_body", "").orEmpty(),
        timed = prefs.getBoolean("draft_timed", false),
        date = prefs.getString("draft_date", "").orEmpty(),
    )

    fun saveDraft(d: LetterDraft) {
        prefs.edit()
            .putString("draft_title", d.title).putString("draft_body", d.body)
            .putBoolean("draft_timed", d.timed).putString("draft_date", d.date)
            .apply()
    }

    fun clearDraft() = saveDraft(LetterDraft("", "", false, ""))

    fun hasCustomImage(): Boolean = imageFile.exists()

    /** 选中的图压到宽 ≤1080 存起来，成功返回 true。 */
    suspend fun saveImage(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            require(bounds.outWidth > 0 && bounds.outHeight > 0)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 1080) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val raw = app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: error("decode")
            val bmp = if (raw.width > 1080) {
                Bitmap.createScaledBitmap(raw, 1080, (raw.height * 1080f / raw.width).toInt().coerceAtLeast(1), true)
            } else raw
            val tmp = File(app.filesDir, "letters_puzzle.tmp")
            tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            check(tmp.renameTo(imageFile) || (imageFile.delete() && tmp.renameTo(imageFile)))
        }.isSuccess
    }

    fun resetImage() {
        imageFile.delete()
    }

    /** 当前拼图图：自己换的，没有或读不出来就用默认图。 */
    suspend fun loadImage(): ImageBitmap = withContext(Dispatchers.IO) {
        val custom = if (imageFile.exists()) runCatching { BitmapFactory.decodeFile(imageFile.absolutePath) }.getOrNull() else null
        (custom ?: BitmapFactory.decodeResource(app.resources, R.drawable.letters_puzzle_default)).asImageBitmap()
    }
}
