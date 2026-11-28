package com.cipher.virtual.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "virtual_spaces")
data class VirtualSpaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val tag: String,
    val accentIndex: Int = 0,
    val deviceModelPreset: String = "ROG Phone 8 Pro",
    val isolatedStorage: Boolean = true,
    val frameRateHud: Boolean = true,
    val permissionBridgeEnabled: Boolean = true,
    val virtualDpi: Int = 440,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "virtual_apps",
    foreignKeys = [
        ForeignKey(
            entity = VirtualSpaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["spaceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("spaceId")]
)
data class VirtualAppEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val spaceId: Long,
    val packageName: String,
    val displayName: String,
    val instanceLabel: String = "Instance #1",
    val performanceMode: String = "Balanced",
    val launchCount: Int = 0,
    val lastLaunchedAt: Long? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val notes: String = "",
    val lastProfileId: String? = null,
    val workerSlot: String = ":w0",
    val virtualUid: Int = 10000,
    val isActive: Boolean = false,
    val storageSizeBytes: Long = 0L,
    val fileCount: Int = 0
)
