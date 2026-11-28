package com.cipher.virtual.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.cipher.virtual.data.model.VirtualAppEntity
import com.cipher.virtual.data.model.VirtualSpaceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OctoDao {
    @Query("SELECT * FROM virtual_spaces ORDER BY createdAt ASC")
    fun observeSpaces(): Flow<List<VirtualSpaceEntity>>

    @Query("SELECT * FROM virtual_spaces ORDER BY createdAt ASC")
    suspend fun getAllSpacesSnapshot(): List<VirtualSpaceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpace(space: VirtualSpaceEntity): Long

    @Update
    suspend fun updateSpace(space: VirtualSpaceEntity)

    @Query("DELETE FROM virtual_spaces WHERE id = :spaceId")
    suspend fun deleteSpace(spaceId: Long)

    @Query("UPDATE virtual_apps SET spaceId = :toSpaceId WHERE spaceId = :fromSpaceId")
    suspend fun moveAppsToSpace(fromSpaceId: Long, toSpaceId: Long)

    @Query("SELECT * FROM virtual_apps WHERE spaceId = :spaceId ORDER BY addedAt DESC")
    fun observeAppsForSpace(spaceId: Long): Flow<List<VirtualAppEntity>>

    @Query("SELECT * FROM virtual_apps ORDER BY addedAt DESC")
    fun observeAllApps(): Flow<List<VirtualAppEntity>>

    @Query("SELECT * FROM virtual_apps ORDER BY addedAt DESC")
    suspend fun getAllAppsSnapshot(): List<VirtualAppEntity>

    @Query("SELECT COUNT(*) FROM virtual_apps WHERE spaceId = :spaceId AND packageName = :packageName")
    suspend fun countPackageInSpace(spaceId: Long, packageName: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApp(app: VirtualAppEntity): Long

    @Update
    suspend fun updateApp(app: VirtualAppEntity)

    @Query("UPDATE virtual_apps SET launchCount = launchCount + 1, lastLaunchedAt = :timestamp, lastProfileId = :profileId WHERE id = :appId")
    suspend fun recordAppLaunch(appId: Long, timestamp: Long, profileId: String?)

    @Query("UPDATE virtual_apps SET isActive = 0")
    suspend fun clearActiveAppFlags()

    @Query("UPDATE virtual_apps SET isActive = 1 WHERE id = :appId")
    suspend fun markAppActive(appId: Long)

    @Transaction
    suspend fun switchActiveAppAtomically(appId: Long) {
        clearActiveAppFlags()
        markAppActive(appId)
    }

    @Query("UPDATE virtual_apps SET workerSlot = :workerSlot, virtualUid = :virtualUid WHERE id = :appId")
    suspend fun updateAppWorkerRouting(appId: Long, workerSlot: String, virtualUid: Int)

    @Query("UPDATE virtual_apps SET storageSizeBytes = :sizeBytes, fileCount = :fileCount WHERE id = :appId")
    suspend fun updateAppStorageMetrics(appId: Long, sizeBytes: Long, fileCount: Int)

    @Query("DELETE FROM virtual_apps WHERE id = :appId")
    suspend fun deleteApp(appId: Long)
}
