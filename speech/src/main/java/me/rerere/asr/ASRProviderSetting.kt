package me.rerere.asr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
sealed class ASRProviderSetting {
    abstract val id: Uuid
    abstract val name: String

    // 硅基流动是整段上传识别，没有服务端断句，不支持语音通话模式。
    val supportsServerVadVoiceMode: Boolean
        get() = false

    abstract fun copyProvider(
        id: Uuid = this.id,
        name: String = this.name,
    ): ASRProviderSetting

    /**
     * 硅基流动语音识别。模型固定为 [SILICONFLOW_ASR_MODEL]，不开放选择。
     * 录音期间按 [SEGMENT_DURATION_SEC] 分段，每段转 WAV 后 multipart 上传到
     * https://api.siliconflow.cn/v1/audio/transcriptions。
     */
    @Serializable
    @SerialName("siliconflow")
    data class SiliconFlow(
        override val id: Uuid = Uuid.random(),
        override val name: String = "SiliconFlow",
        val apiKey: String = "",
    ) : ASRProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): ASRProviderSetting = copy(id = id, name = name)
    }

    companion object {
        const val SILICONFLOW_ASR_MODEL = "FunAudioLLM/SenseVoiceSmall"
        const val SILICONFLOW_ASR_URL = "https://api.siliconflow.cn/v1/audio/transcriptions"
        const val SILICONFLOW_KEY_URL = "https://cloud.siliconflow.cn/account/ak"
        const val SEGMENT_DURATION_SEC = 30
    }
}
