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
        VirtualAppEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class OctoDatabase : RoomDatabase() {
    abstract fun octoDao(): OctoDao

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

        fun getInstance(context: Context): OctoDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    OctoDatabase::class.java,
                    "cipher_virtual.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
