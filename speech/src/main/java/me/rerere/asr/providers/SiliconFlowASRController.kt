package me.rerere.asr.providers

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid
import me.rerere.asr.ASRController
import me.rerere.asr.ASRProviderSetting
import me.rerere.asr.ASRState
import me.rerere.asr.ASRStatus
import me.rerere.asr.appendAmplitude
import me.rerere.asr.calculateRmsAmplitude
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.Collections

private const val TAG = "SiliconFlowASR"

// 一段 PCM 最多 6MB（16kHz/16bit/mono 约 187 秒），超了提前上传。
private const val MAX_SEGMENT_BYTES = 6 * 1024 * 1024

// 整段录音 PCM（存档用）最多留 60 秒（按住说话上限 60 秒），超出部分丢弃。16kHz/16bit/mono 每秒 32000 字节。
private const val MAX_FULL_RECORDING_BYTES = 60 * 16000 * 2

/**
 * 硅基流动 ASR Controller（FunAudioLLM/SenseVoiceSmall）。
 * 11-21 起照 ears 原作只录音不上传：stop() 把整段录音存成 WAV（recordingPath），由 ChatInput 发给耳朵转字和听语气。
 */
class SiliconFlowASRController(
    private val context: Context,
    private val httpClient: OkHttpClient,
    private val provider: ASRProviderSetting.SiliconFlow
) : ASRController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(ASRState(isAvailable = true))
    override val state: StateFlow<ASRState> = _state.asStateFlow()

    private var recorderJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var onTranscriptChange: ((String) -> Unit)? = null

    // 同一时刻只允许一个 flush 协程在跑, 避免乱序拼结果
    private var flushJob: Job? = null

    private val bufferLock = Any()
    private var currentBuffer = ByteArrayOutputStream()
    private var segmentStartElapsedMs = 0L

    // 整段录音 PCM（存档用），与切段上传的 currentBuffer 互不影响
    private var fullRecordingBuffer = ByteArrayOutputStream()
    private val completedTranscripts = Collections.synchronizedList(mutableListOf<String>())

    override fun start(onTranscriptChange: (String) -> Unit) {
        if (state.value.isRecording) return
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            setError("Microphone permission is required")
            return
        }

        this.onTranscriptChange = onTranscriptChange
        synchronized(bufferLock) {
            currentBuffer = ByteArrayOutputStream()
            fullRecordingBuffer = ByteArrayOutputStream()
            segmentStartElapsedMs = SystemClock.elapsedRealtime()
        }
        completedTranscripts.clear()
        flushJob = null

        // HTTP 一次性接口, 没有连接阶段, 直接进入 Listening
        _state.update {
            ASRState(
                status = ASRStatus.Listening,
                isAvailable = true
            )
        }
        startRecorder()
    }

    override fun stop() {
        recorderJob?.cancel()
        releaseRecorder()
        _state.update { it.copy(status = ASRStatus.Stopping) }

        // 照 ears 原作只把整段录音发给耳朵，不再上传硅基：存档后直接切回 Idle
        scope.launch(Dispatchers.IO) {
            saveFullRecording()
            _state.update { it.copy(status = ASRStatus.Idle) }
        }
    }

    override fun dispose() {
        recorderJob?.cancel()
        flushJob?.cancel()
        releaseRecorder()
        scope.cancel()
    }

    @SuppressLint("MissingPermission")
    private fun startRecorder() {
        recorderJob?.cancel()
        recorderJob = scope.launch(Dispatchers.IO) {
            val sampleRate = SAMPLE_RATE
            val minBufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = minBufferSize
                .coerceAtLeast(sampleRate / 10 * 2)
                .coerceAtLeast(4096)

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2
            )
            audioRecord = recorder

            try {
                recorder.startRecording()
                val buffer = ByteArray(bufferSize)
                while (isActive) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        val amplitude = calculateRmsAmplitude(buffer, read)
                        _state.update { it.copy(amplitudes = it.amplitudes.appendAmplitude(amplitude)) }

                        // 另攒一份整段 PCM 用于 stop() 存档, 不参与切段上传
                        appendToFullRecording(buffer, read)
                    } else if (read < 0) {
                        throw IllegalStateException("AudioRecord read error: $read")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Audio recording failed", e)
                setError(e.message ?: "Audio recording failed")
            } finally {
                releaseRecorder()
            }
        }
    }

    private fun triggerFlush() {
        // 同一时刻只跑一个 flush, 避免后发先至导致结果乱序
        if (flushJob?.isActive == true) return
        flushJob = scope.launch(Dispatchers.IO) {
            runCatching { flushSegment() }
                .onFailure { Log.e(TAG, "Segment flush failed", it) }
        }
    }

    /**
     * 把 PCM 追加到整段录音缓冲（存档用），与切段上传互不影响。
     * 最多留 [MAX_FULL_RECORDING_BYTES]（60 秒），超出部分丢弃。
     */
    private fun appendToFullRecording(buffer: ByteArray, read: Int) {
        synchronized(bufferLock) {
            val remaining = MAX_FULL_RECORDING_BYTES - fullRecordingBuffer.size()
            if (remaining > 0) {
                fullRecordingBuffer.write(buffer, 0, read.coerceAtMost(remaining))
            }
        }
    }

    /**
     * 取出当前缓冲区里的 PCM, 转 WAV 后 POST 到硅基流动; 把识别结果加到 completedTranscripts。
     * 在 bufferLock 内拷贝出 PCM 并立刻重置缓冲区, 不持有锁等待网络, 避免阻塞录音写。
     */
    private suspend fun flushSegment() {
        val pcmBytes = synchronized(bufferLock) {
            if (currentBuffer.size() == 0) return
            val bytes = currentBuffer.toByteArray()
            currentBuffer = ByteArrayOutputStream()
            segmentStartElapsedMs = SystemClock.elapsedRealtime()
            bytes
        }

        val wavBytes = pcm16ToWav(
            pcm = pcmBytes,
            sampleRate = SAMPLE_RATE,
            channels = 1,
            bitsPerSample = 16
        )

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", ASRProviderSetting.SILICONFLOW_ASR_MODEL)
            .addFormDataPart("file", "audio.wav", wavBytes.toRequestBody("audio/wav".toMediaType()))
            .build()

        val request = Request.Builder()
            .url(ASRProviderSetting.SILICONFLOW_ASR_URL)
            .addHeader("Authorization", "Bearer ${provider.apiKey}")
            .post(body)
            .build()

        val text = withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().use { resp ->
                val respBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IOException("SiliconFlow ASR HTTP ${resp.code}: $respBody")
                }
                val json = runCatching { JSONObject(respBody) }.getOrElse {
                    throw IOException("SiliconFlow ASR response is not valid JSON: $respBody")
                }
                json.optString("text", "").trim()
            }
        }

        if (text.isNotEmpty()) {
            completedTranscripts.add(text)
            publishTranscript()
        }
    }

    /**
     * 把整段录音 PCM 用 pcm16ToWav（16k/mono/16bit）写到 filesDir/voice/<随机uuid>.wav，
     * 写好后把路径放进状态（ASRState.recordingPath）。写文件失败只记日志，不影响识别流程。
     * 只在 Dispatchers.IO 上调用。
     */
    private fun saveFullRecording() {
        val pcm = synchronized(bufferLock) {
            if (fullRecordingBuffer.size() == 0) return
            val bytes = fullRecordingBuffer.toByteArray()
            fullRecordingBuffer = ByteArrayOutputStream()
            bytes
        }

        try {
            val wavBytes = pcm16ToWav(
                pcm = pcm,
                sampleRate = SAMPLE_RATE,
                channels = 1,
                bitsPerSample = 16
            )
            val dir = File(context.filesDir, "voice")
            dir.mkdirs()
            val file = File(dir, "${Uuid.random()}.wav")
            file.writeBytes(wavBytes)
            _state.update { it.copy(recordingPath = file.absolutePath) }
        } catch (e: Exception) {
            Log.e(TAG, "Save full recording failed", e)
        }
    }

    private fun publishTranscript() {
        val transcript = completedTranscripts
            .filter { it.isNotBlank() }
            .joinToString(" ")
        _state.update { it.copy(transcript = transcript, errorMessage = null) }
        scope.launch { onTranscriptChange?.invoke(transcript) }
    }

    private fun setError(message: String) {
        _state.update {
            it.copy(
                status = ASRStatus.Error,
                errorMessage = message
            )
        }
    }

    private fun releaseRecorder() {
        recorderJob = null
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
    }

    companion object {
        private const val SAMPLE_RATE = 16000

        /**
         * 把 raw PCM16 little-endian 数据封装成最小 WAV (RIFF/WAVE/fmt/data)。
         * AudioRecord 输出的是 PCM, 必须自己包 WAV 头。
         */
        private fun pcm16ToWav(
            pcm: ByteArray,
            sampleRate: Int,
            channels: Int,
            bitsPerSample: Int
        ): ByteArray {
            val byteRate = sampleRate * channels * bitsPerSample / 8
            val blockAlign = channels * bitsPerSample / 8
            val dataSize = pcm.size
            val out = ByteArrayOutputStream(44 + dataSize)

            // RIFF header
            out.write("RIFF".toByteArray(Charsets.US_ASCII))
            writeIntLE(out, 36 + dataSize) // chunk size = file size - 8
            out.write("WAVE".toByteArray(Charsets.US_ASCII))
            // fmt chunk
            out.write("fmt ".toByteArray(Charsets.US_ASCII))
            writeIntLE(out, 16)            // PCM fmt chunk size
            writeShortLE(out, 1)           // audio format = PCM
            writeShortLE(out, channels)
            writeIntLE(out, sampleRate)
            writeIntLE(out, byteRate)
            writeShortLE(out, blockAlign)
            writeShortLE(out, bitsPerSample)
            // data chunk
            out.write("data".toByteArray(Charsets.US_ASCII))
            writeIntLE(out, dataSize)
            out.write(pcm)
            return out.toByteArray()
        }

        private fun writeIntLE(out: ByteArrayOutputStream, value: Int) {
            out.write(value and 0xFF)
            out.write((value shr 8) and 0xFF)
            out.write((value shr 16) and 0xFF)
            out.write((value shr 24) and 0xFF)
        }

        private fun writeShortLE(out: ByteArrayOutputStream, value: Int) {
            out.write(value and 0xFF)
            out.write((value shr 8) and 0xFF)
        }
    }
}
