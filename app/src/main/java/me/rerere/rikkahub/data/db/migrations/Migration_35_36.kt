package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

/**
 * 记忆宫殿：窗台期盼的生命周期（palace_node 加三列）和门牌分类标签（palace_plate_entry 加一列）。
 * 只加带默认值的列，已有的行原样保留：窗台里已有的记忆状态是空，按「刚盼上」处理，到 7 天后由消化转成锚点。
 */
val Migration_35_36 = object : Migration(35, 36) {
    override fun migrate(db: SupportSQLiteDatabase) {
        DatabaseMigrationTracker.onMigrationStart(35, 36)
        db.execSQL("ALTER TABLE `palace_node` ADD COLUMN `ant_status` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `palace_node` ADD COLUMN `anchored_at` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `palace_node` ADD COLUMN `resolved_at` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `palace_plate_entry` ADD COLUMN `tag` TEXT NOT NULL DEFAULT ''")
        DatabaseMigrationTracker.onMigrationEnd()
    }
}
