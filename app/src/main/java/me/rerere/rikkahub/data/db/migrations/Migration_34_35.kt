package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

/** 记忆宫殿的事件盒表。只建新表，别的表不动 */
val Migration_34_35 = object : Migration(34, 35) {
    override fun migrate(db: SupportSQLiteDatabase) {
        DatabaseMigrationTracker.onMigrationStart(34, 35)
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `palace_box` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`assistant_id` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`tags` TEXT NOT NULL, " +
                "`summary_node_id` INTEGER NOT NULL, " +
                "`sealed` INTEGER NOT NULL, " +
                "`compression_count` INTEGER NOT NULL, " +
                "`predecessor_id` INTEGER NOT NULL, " +
                "`created_at` INTEGER NOT NULL, " +
                "`updated_at` INTEGER NOT NULL, " +
                "`last_compressed_at` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_palace_box_assistant_id` ON `palace_box` (`assistant_id`)")
        DatabaseMigrationTracker.onMigrationEnd()
    }
}
