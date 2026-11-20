package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 现在只提供 TimeInfo、ScreenTime、Calendar、Stash。
 * 标「已弃用」的子类不再提供，但必须保留：旧设置里存着它们的名字，
 * 删掉子类会让设置解码失败，app 启动即退出。
 */
@Serializable
sealed class LocalToolOption {
    /** 已弃用，不再提供。 */
    @Serializable
    @SerialName("javascript_engine")
    data object JavascriptEngine : LocalToolOption()

    @Serializable
    @SerialName("time_info")
    data object TimeInfo : LocalToolOption()

    /** 已弃用，不再提供。 */
    @Serializable
    @SerialName("clipboard")
    data object Clipboard : LocalToolOption()

    /** 已弃用，不再提供。 */
    @Serializable
    @SerialName("tts")
    data object Tts : LocalToolOption()

    /** 已弃用，不再提供。旧会话里的 ask_user 调用仍会渲染。 */
    @Serializable
    @SerialName("ask_user")
    data object AskUser : LocalToolOption()

    @Serializable
    @SerialName("screen_time")
    data object ScreenTime : LocalToolOption()

    @Serializable
    @SerialName("calendar")
    data object Calendar : LocalToolOption()

    /** 囤粮处：读写服务器 /companion/stash*，跟页面、QQ 同一份。 */
    @Serializable
    @SerialName("stash")
    data object Stash : LocalToolOption()

    /** 已弃用，不再提供。旧会话里的图表仍会渲染。 */
    @Serializable
    @SerialName("chart_display")
    data object ChartDisplay : LocalToolOption()
}
