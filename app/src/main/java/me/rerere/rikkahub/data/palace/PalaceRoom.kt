package me.rerere.rikkahub.data.palace

/**
 * 宫殿已经拆掉，房间不再有任何逻辑。只留这 7 个名字，因为 PalaceStyle.kt（14 个普通页面在用，不能改）
 * 里的 roomTint / roomIcon 还按它们取色取图标。
 */
object PalaceRoom {
    const val LIVING = "living_room"
    const val BEDROOM = "bedroom"
    const val STUDY = "study"
    const val USER = "user_room"
    const val SELF = "self_room"
    const val ATTIC = "attic"
    const val WINDOWSILL = "windowsill"
}
