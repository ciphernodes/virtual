package com.cipher.virtual.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cipher.virtual.data.model.VirtualAppEntity
import com.cipher.virtual.data.model.VirtualSpaceEntity

@Database(
    entities = [
        VirtualSpaceEntity::class,
        VirtualAppEntity::class,
        WorkspaceInstance::class
    ],
    version = 7,
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

        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cloned_app_instances` (
                        `instanceId` TEXT NOT NULL,
                        `packageName` TEXT NOT NULL,
                        `appName` TEXT NOT NULL,
                        `instanceLabel` TEXT NOT NULL,
                        `dataStoragePath` TEXT NOT NULL,
                        `cacheStoragePath` TEXT NOT NULL,
                        `databasesPath` TEXT NOT NULL,
                        `sharedPrefsPath` TEXT NOT NULL,
                        `externalStoragePath` TEXT,
                        `workerSlot` TEXT NOT NULL,
                        `virtualUid` INTEGER NOT NULL,
                        `virtualPid` INTEGER NOT NULL,
                        `isRootedVirtualization` INTEGER NOT NULL,
                        `virtualEnvType` TEXT NOT NULL,
                        `isActive` INTEGER NOT NULL,
                        `storageSizeBytes` INTEGER NOT NULL,
                        `fileCount` INTEGER NOT NULL,
                        `launchCount` INTEGER NOT NULL,
                        `lastLaunchedAt` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`instanceId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_cloned_app_instances_packageName` ON `cloned_app_instances` (`packageName`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_cloned_app_instances_isActive` ON `cloned_app_instances` (`isActive`)"
                )
            }
        }

        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE virtual_apps ADD COLUMN workerSlot TEXT NOT NULL DEFAULT ':w0'")
                db.execSQL("ALTER TABLE virtual_apps ADD COLUMN virtualUid INTEGER NOT NULL DEFAULT 10000")
                db.execSQL("ALTER TABLE virtual_apps ADD COLUMN isActive INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE virtual_apps ADD COLUMN storageSizeBytes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE virtual_apps ADD COLUMN fileCount INTEGER NOT NULL DEFAULT 0")

                // Backfill matching virtual_apps rows from cloned_app_instances where instanceId = space_<spaceId>_app_<id>
                db.execSQL(
                    """
                    UPDATE virtual_apps
                    SET
                        workerSlot = COALESCE(
                            (SELECT c.workerSlot FROM cloned_app_instances c
                             WHERE c.instanceId = ('space_' || virtual_apps.spaceId || '_app_' || virtual_apps.id)),
                            workerSlot
                        ),
                        virtualUid = COALESCE(
                            (SELECT c.virtualUid FROM cloned_app_instances c
                             WHERE c.instanceId = ('space_' || virtual_apps.spaceId || '_app_' || virtual_apps.id)),
                            virtualUid
                        ),
                        isActive = COALESCE(
                            (SELECT c.isActive FROM cloned_app_instances c
                             WHERE c.instanceId = ('space_' || virtual_apps.spaceId || '_app_' || virtual_apps.id)),
                            isActive
                        ),
                        storageSizeBytes = COALESCE(
                            (SELECT c.storageSizeBytes FROM cloned_app_instances c
                             WHERE c.instanceId = ('space_' || virtual_apps.spaceId || '_app_' || virtual_apps.id)),
                            storageSizeBytes
                        ),
                        fileCount = COALESCE(
                            (SELECT c.fileCount FROM cloned_app_instances c
                             WHERE c.instanceId = ('space_' || virtual_apps.spaceId || '_app_' || virtual_apps.id)),
                            fileCount
                        )
                    WHERE EXISTS (
                        SELECT 1 FROM cloned_app_instances c
                        WHERE c.instanceId = ('space_' || virtual_apps.spaceId || '_app_' || virtual_apps.id)
                    )
                    """.trimIndent()
                )

                // Ensure a parent space exists if standalone cloned_app_instances rows exist without virtual_spaces.
                db.execSQL(
                    """
                    INSERT INTO virtual_spaces (
                        name, tag, accentIndex, deviceModelPreset, isolatedStorage,
                        frameRateHud, permissionBridgeEnabled, virtualDpi, createdAt
                    )
                    SELECT 'Cipher Workspace', 'MAIN', 0, 'ROG Phone 8 Pro', 1, 1, 1, 440, 0
                    WHERE NOT EXISTS (SELECT 1 FROM virtual_spaces)
                      AND EXISTS (SELECT 1 FROM cloned_app_instances)
                    """.trimIndent()
                )

                // Preserve any standalone cloned_app_instances rows that had no matching virtual_apps row.
                db.execSQL(
                    """
                    INSERT INTO virtual_apps (
                        spaceId, packageName, displayName, instanceLabel, performanceMode,
                        launchCount, lastLaunchedAt, addedAt, notes, lastProfileId,
                        workerSlot, virtualUid, isActive, storageSizeBytes, fileCount
                    )
                    SELECT
                        (SELECT id FROM virtual_spaces ORDER BY createdAt ASC LIMIT 1),
                        c.packageName,
                        c.appName,
                        c.instanceLabel,
                        'Balanced',
                        c.launchCount,
                        c.lastLaunchedAt,
                        c.createdAt,
                        '',
                        NULL,
                        c.workerSlot,
                        c.virtualUid,
                        c.isActive,
                        c.storageSizeBytes,
                        c.fileCount
                    FROM cloned_app_instances c
                    WHERE NOT EXISTS (
                        SELECT 1 FROM virtual_apps v
                        WHERE c.instanceId = ('space_' || v.spaceId || '_app_' || v.id)
                    )
                    """.trimIndent()
                )

                db.execSQL("DROP TABLE IF EXISTS `cloned_app_instances`")
            }
        }

        fun getInstance(context: Context): OctoDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    OctoDatabase::class.java,
                    "cipher_virtual.db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7
                    )
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
