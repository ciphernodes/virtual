package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.VirtualAppEntity
import com.example.data.model.VirtualSpaceEntity

@Database(
    entities = [
        VirtualSpaceEntity::class,
        VirtualAppEntity::class,
        WorkspaceInstance::class
    ],
    version = 5,
    exportSchema = true
)
abstract class OctoDatabase : RoomDatabase() {
    abstract fun octoDao(): OctoDao
    abstract fun workspaceInstanceDao(): WorkspaceInstanceDao

    companion object {
        @Volatile
        private var INSTANCE: OctoDatabase? = null

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE virtual_spaces ADD COLUMN accentIndex INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE virtual_spaces ADD COLUMN deviceModelPreset TEXT NOT NULL DEFAULT 'ROG Phone 8 Pro'")
                db.execSQL("ALTER TABLE virtual_spaces ADD COLUMN isolatedStorage INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE virtual_spaces ADD COLUMN frameRateHud INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE virtual_spaces ADD COLUMN permissionBridgeEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE virtual_spaces ADD COLUMN virtualDpi INTEGER NOT NULL DEFAULT 440")
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE virtual_apps ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE virtual_apps ADD COLUMN lastProfileId TEXT")
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `workspace_instances` (
                        `instanceId` TEXT NOT NULL,
                        `label` TEXT NOT NULL,
                        `targetPackageName` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `isActive` INTEGER NOT NULL,
                        PRIMARY KEY(`instanceId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_workspace_instances_targetPackageName` ON `workspace_instances` (`targetPackageName`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_workspace_instances_isActive` ON `workspace_instances` (`isActive`)"
                )
            }
        }

        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workspace_instances ADD COLUMN workerSlot TEXT")
            }
        }

        fun getInstance(context: Context): OctoDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    OctoDatabase::class.java,
                    "cipher_virtual.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
