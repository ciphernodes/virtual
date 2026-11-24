package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.VirtualAppEntity
import com.example.data.model.VirtualSpaceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OctoDao {
    @Query("SELECT * FROM virtual_spaces ORDER BY createdAt ASC")
    fun observeSpaces(): Flow<List<VirtualSpaceEntity>>

    @Query("SELECT COUNT(*) FROM virtual_spaces")
    suspend fun getSpaceCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpace(space: VirtualSpaceEntity): Long

    @Update
    suspend fun updateSpace(space: VirtualSpaceEntity)

    @Query("DELETE FROM virtual_spaces WHERE id = :spaceId")
    suspend fun deleteSpace(spaceId: Long)

    @Query("SELECT * FROM virtual_apps WHERE spaceId = :spaceId ORDER BY addedAt DESC")
    fun observeAppsForSpace(spaceId: Long): Flow<List<VirtualAppEntity>>

    @Query("SELECT * FROM virtual_apps ORDER BY addedAt DESC")
    fun observeAllApps(): Flow<List<VirtualAppEntity>>

    @Query("SELECT COUNT(*) FROM virtual_apps WHERE spaceId = :spaceId AND packageName = :packageName")
    suspend fun countPackageInSpace(spaceId: Long, packageName: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApp(app: VirtualAppEntity): Long

    @Update
    suspend fun updateApp(app: VirtualAppEntity)

    @Query("UPDATE virtual_apps SET launchCount = launchCount + 1, lastLaunchedAt = :timestamp, lastProfileId = :profileId WHERE id = :appId")
    suspend fun recordAppLaunch(appId: Long, timestamp: Long, profileId: String?)

    @Query("DELETE FROM virtual_apps WHERE id = :appId")
    suspend fun deleteApp(appId: Long)
}
