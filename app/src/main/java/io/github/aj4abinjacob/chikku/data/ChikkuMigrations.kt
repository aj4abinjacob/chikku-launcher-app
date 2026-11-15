package io.github.aj4abinjacob.chikku.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit schema migrations. SQL mirrors the exported schema JSON in app/schemas exactly; Room
 * validates the result on open, so a mismatch fails loudly instead of losing data.
 */
object ChikkuMigrations {

    /**
     * v1 -> v2: folders, widgets, shortcuts and item gesture actions on `workspace_items` (new
     * nullable columns, existing rows unchanged), plus folder entries, per-app customizations and
     * drawer groups. Purely additive: no existing row or column is modified or removed.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            listOf(
                "`title` TEXT",
                "`appWidgetId` INTEGER",
                "`providerPackage` TEXT",
                "`providerClass` TEXT",
                "`shortcutId` TEXT",
                "`swipeUpAction` TEXT",
                "`swipeDownAction` TEXT",
                "`tapAction` TEXT",
                "`zIndex` INTEGER NOT NULL DEFAULT 0",
            ).forEach { column -> db.execSQL("ALTER TABLE `workspace_items` ADD COLUMN $column") }

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `folder_items` (`id` TEXT NOT NULL, `folderId` TEXT NOT NULL, " +
                    "`rank` INTEGER NOT NULL, `kind` TEXT NOT NULL, `packageName` TEXT NOT NULL, " +
                    "`className` TEXT NOT NULL, `userSerial` INTEGER NOT NULL, `shortcutId` TEXT, `title` TEXT, " +
                    "PRIMARY KEY(`id`))",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_folder_items_folderId` ON `folder_items` (`folderId`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `app_customizations` (`packageName` TEXT NOT NULL, " +
                    "`className` TEXT NOT NULL, `userSerial` INTEGER NOT NULL, `hidden` INTEGER NOT NULL DEFAULT 0, " +
                    "`customLabel` TEXT, `iconPackPackage` TEXT, `iconDrawableName` TEXT, `swipeUpAction` TEXT, " +
                    "`swipeDownAction` TEXT, PRIMARY KEY(`packageName`, `className`, `userSerial`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `drawer_groups` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                    "`rank` INTEGER NOT NULL, `sortMode` TEXT NOT NULL, PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `drawer_group_members` (`groupId` TEXT NOT NULL, " +
                    "`packageName` TEXT NOT NULL, `className` TEXT NOT NULL, `userSerial` INTEGER NOT NULL, " +
                    "`rank` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`groupId`, `packageName`, `className`, `userSerial`))",
            )
        }
    }

    /** Additive shortcut appearance fields; existing identities, widget IDs and snapshots survive. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            for (table in listOf("workspace_items", "folder_items")) {
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `shortcutIconPack` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `shortcutIconDrawable` TEXT")
            }
        }
    }

    /** Additive per-widget inner space override; null keeps following the global setting. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `workspace_items` ADD COLUMN `widgetPadding` TEXT")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
}
