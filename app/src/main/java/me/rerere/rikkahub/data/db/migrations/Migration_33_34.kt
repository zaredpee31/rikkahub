package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

/**
 * 记忆宫殿的四张表。
 *
 * 只建新表，不动 memory_file、memory_node、memory_plate 里的旧内容。
 * 旧记忆搬进宫殿在应用层做，先整份备份；搬失败不影响这次升级，也不会删掉原文。
 */
val Migration_33_34 = object : Migration(33, 34) {
    override fun migrate(db: SupportSQLiteDatabase) {
        DatabaseMigrationTracker.onMigrationStart(33, 34)
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `palace_node` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`assistant_id` TEXT NOT NULL, " +
                "`room` TEXT NOT NULL, " +
                "`content` TEXT NOT NULL, " +
                "`importance` INTEGER NOT NULL, " +
                "`mood` TEXT NOT NULL, " +
                "`valence` REAL, " +
                "`arousal` REAL, " +
                "`tags` TEXT NOT NULL, " +
                "`created_at` INTEGER NOT NULL, " +
                "`recorded_at` INTEGER NOT NULL, " +
                "`updated_at` INTEGER NOT NULL, " +
                "`last_accessed_at` INTEGER NOT NULL, " +
                "`access_count` INTEGER NOT NULL, " +
                "`origin` TEXT NOT NULL, " +
                "`source` TEXT NOT NULL, " +
                "`digested_at` INTEGER NOT NULL, " +
                "`box_id` INTEGER NOT NULL, " +
                "`archived` INTEGER NOT NULL, " +
                "`content_version` INTEGER NOT NULL, " +
                "`embedding_key` TEXT NOT NULL, " +
                "`embedding_dims` INTEGER NOT NULL, " +
                "`embedding` BLOB NOT NULL, " +
                "`embedding_error` TEXT NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_palace_node_assistant_id_room` ON `palace_node` (`assistant_id`, `room`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_palace_node_assistant_id_created_at` ON `palace_node` (`assistant_id`, `created_at`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_palace_node_assistant_id_origin` ON `palace_node` (`assistant_id`, `origin`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `palace_link` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`assistant_id` TEXT NOT NULL, " +
                "`from_id` INTEGER NOT NULL, " +
                "`to_id` INTEGER NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`strength` REAL NOT NULL)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_palace_link_assistant_id_from_id_to_id_type` " +
                "ON `palace_link` (`assistant_id`, `from_id`, `to_id`, `type`)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_palace_link_assistant_id_to_id` ON `palace_link` (`assistant_id`, `to_id`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `palace_plate_entry` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`assistant_id` TEXT NOT NULL, " +
                "`plate` TEXT NOT NULL, " +
                "`content` TEXT NOT NULL, " +
                "`first_learned_at` INTEGER NOT NULL, " +
                "`source_count` INTEGER NOT NULL, " +
                "`updated_at` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_palace_plate_entry_assistant_id_plate` ON `palace_plate_entry` (`assistant_id`, `plate`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `palace_meta` (" +
                "`assistant_id` TEXT NOT NULL, " +
                "`key` TEXT NOT NULL, " +
                "`value` TEXT NOT NULL, " +
                "PRIMARY KEY(`assistant_id`, `key`))"
        )
        DatabaseMigrationTracker.onMigrationEnd()
    }
}
