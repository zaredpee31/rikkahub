package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

/**
 * 记忆宫殿整块拆掉：只删宫殿自己的 5 张表。
 *
 * 不碰 memory_plate（「关于小葵」「关于你」存在这里）、memory_file、memory_node、memory_import、
 * memory_consolidation，也不碰 conversation、message_node 等任何普通表。
 * 38 版没有向下迁移：升到 38 之后装不回旧版本。
 */
private val PALACE_TABLES = listOf(
    // 先删引用别的表的，最后删 palace_node（这些表之间没有外键约束，顺序只是求稳）
    "palace_box",
    "palace_link",
    "palace_plate_entry",
    "palace_meta",
    "palace_node",
)

private fun dropPalaceTables(db: SupportSQLiteDatabase) {
    // IF EXISTS：从 33 之前升上来的库里这些表可能还没建过
    PALACE_TABLES.forEach { db.execSQL("DROP TABLE IF EXISTS `$it`") }
}

private fun palaceMigration(from: Int) = object : Migration(from, 38) {
    override fun migrate(db: SupportSQLiteDatabase) {
        DatabaseMigrationTracker.onMigrationStart(from, 38)
        dropPalaceTables(db)
        DatabaseMigrationTracker.onMigrationEnd()
    }
}

val Migration_37_38 = palaceMigration(37)

// 宫殿表是 33→37 这几版建的、加列的；停在这几版的库也直接升到 38，不依赖已经删掉的旧迁移
val Migration_36_38 = palaceMigration(36)
val Migration_35_38 = palaceMigration(35)
val Migration_34_38 = palaceMigration(34)
val Migration_33_38 = palaceMigration(33)
