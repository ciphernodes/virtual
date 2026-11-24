package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.VirtualAppEntity
import com.example.data.model.VirtualSpaceEntity

@Database(
    entities = [
        VirtualSpaceEntity::class,
        VirtualAppEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class OctoDatabase : RoomDatabase() {
    abstract fun octoDao(): OctoDao

    companion object {
        @Volatile
        private var INSTANCE: OctoDatabase? = null

        fun getInstance(context: Context): OctoDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    OctoDatabase::class.java,
                    "cipher_virtual.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
