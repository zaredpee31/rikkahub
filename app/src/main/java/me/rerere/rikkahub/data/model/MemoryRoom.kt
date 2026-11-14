package me.rerere.rikkahub.data.model

/** 记忆节点所在的房间 */
object MemoryRoom {
    /** 日常、小事 */
    const val LIVING = "living_room"

    /** 关系、共同经历 */
    const val BEDROOM = "bedroom"

    /** 项目、工作、技能 */
    const val STUDY = "study"

    /** 用户信息、习惯 */
    const val USER = "user_room"

    /** 助手自己的设定和认知，不能当成用户的事实 */
    const val SELF = "self_room"

    val all = listOf(LIVING, BEDROOM, STUDY, USER, SELF)

    fun isValid(id: String): Boolean = id in all

    fun label(id: String): String = when (id) {
        LIVING -> "客厅"
        BEDROOM -> "卧室"
        STUDY -> "书房"
        USER -> "用户房"
        SELF -> "自我房"
        else -> id
    }
}

/** 门牌种类 */
object MemoryPlateKind {
    const val USER = "user"
    const val SELF = "self"

    val all = listOf(USER, SELF)
}

/** 节点和门牌的来源 */
object MemorySource {
    /** 从旧 Markdown 记忆派生，跟着原文件走：原文删了节点也删 */
    const val LEGACY = "legacy"

    /** 用户在界面里新增或改过；改过的旧记忆节点也变成这个，不再跟着原文件走 */
    const val MANUAL = "manual"

    /** 消息页后台整理写的 */
    const val COMPANION = "companion"

    /** 聊天里用户明确要求记下的 */
    const val EXPLICIT = "explicit"
}
