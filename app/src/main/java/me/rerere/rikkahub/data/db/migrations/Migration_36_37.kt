package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

/**
 * 记忆宫殿：palace_node 加「最近进展时间」last_event_at。
 * 旧记忆没有进展记录，先等于 created_at，行为和以前一样；只加一列，已有的行和向量原样保留。
 */
val Migration_36_37 = object : Migration(36, 37) {
    override fun migrate(db: SupportSQLiteDatabase) {
        DatabaseMigrationTracker.onMigrationStart(36, 37)
        db.execSQL("ALTER TABLE `palace_node` ADD COLUMN `last_event_at` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE `palace_node` SET `last_event_at` = `created_at`")
        DatabaseMigrationTracker.onMigrationEnd()
    }
}
