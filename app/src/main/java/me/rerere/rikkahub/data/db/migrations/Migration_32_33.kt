package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

/**
 * 记忆节点和门牌。
 *
 * 只建新表，不读、不改 memory_file 里的旧记忆。旧内容的备份和迁移在应用层做，
 * 迁移失败不能影响这次升级，更不能借升级删掉原文。
 */
val Migration_32_33 = object : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        DatabaseMigrationTracker.onMigrationStart(32, 33)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `memory_node` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `assistant_id` TEXT NOT NULL,
                `room` TEXT NOT NULL,
                `content` TEXT NOT NULL,
                `importance` REAL NOT NULL,
                `mood` TEXT NOT NULL,
                `keywords` TEXT NOT NULL,
                `happened_at` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL,
                `content_version` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                `origin` TEXT NOT NULL,
                `source` TEXT NOT NULL,
                `embedding_key` TEXT NOT NULL,
                `embedding_dims` INTEGER NOT NULL,
                `embedding` BLOB NOT NULL,
                `embedding_error` TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_memory_node_assistant_id_created_at` " +
                "ON `memory_node` (`assistant_id`, `created_at`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_memory_node_assistant_id_room` " +
                "ON `memory_node` (`assistant_id`, `room`)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_memory_node_assistant_id_origin` " +
                "ON `memory_node` (`assistant_id`, `origin`)"
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `memory_plate` (
                `assistant_id` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `content` TEXT NOT NULL,
                `updated_at` INTEGER NOT NULL,
                `source` TEXT NOT NULL,
                PRIMARY KEY(`assistant_id`, `kind`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `memory_import` (
                `assistant_id` TEXT NOT NULL,
                `origin` TEXT NOT NULL,
                PRIMARY KEY(`assistant_id`, `origin`)
            )
            """.trimIndent()
        )
        DatabaseMigrationTracker.onMigrationEnd()
    }
}
