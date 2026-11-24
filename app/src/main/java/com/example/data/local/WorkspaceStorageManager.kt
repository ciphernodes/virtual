package com.example.data.local

import com.example.data.hardware.DeviceProfile
import com.example.data.model.VirtualAppEntity
import com.example.data.model.VirtualSpaceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class InstanceWorkspaceStats(
    val directoryPath: String,
    val sizeBytes: Long,
    val fileCount: Int,
    val lastSessionRecordedAt: Long?
)

class WorkspaceStorageManager(
    private val rootDir: File
) {
    private val workspacesRoot: File
        get() = File(rootDir, WORKSPACES_FOLDER)

    fun spaceDirectory(spaceId: Long): File = File(workspacesRoot, "space_$spaceId")

    fun instanceDirectory(spaceId: Long, appId: Long): File =
        File(spaceDirectory(spaceId), "instance_$appId")

    suspend fun recordSessionMetadata(
        space: VirtualSpaceEntity,
        app: VirtualAppEntity,
        profile: DeviceProfile?
    ): File = withContext(Dispatchers.IO) {
        val dir = instanceDirectory(space.id, app.id)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val configFile = File(dir, SESSION_CONFIG_FILE)
        val json = buildString {
            append('{')
            append("\"spaceId\":").append(space.id).append(',')
            append("\"spaceName\":\"").append(escape(space.name)).append("\",")
            append("\"appId\":").append(app.id).append(',')
            append("\"packageName\":\"").append(escape(app.packageName)).append("\",")
            append("\"instanceLabel\":\"").append(escape(app.instanceLabel)).append("\",")
            append("\"performanceMode\":\"").append(escape(app.performanceMode)).append("\",")
            append("\"virtualDpi\":").append(space.virtualDpi).append(',')
            append("\"isolatedStorage\":").append(space.isolatedStorage).append(',')
            append("\"recordedAt\":").append(System.currentTimeMillis())
            if (profile != null) {
                append(",\"profileId\":\"").append(escape(profile.profileId)).append("\",")
                append("\"profileModel\":\"").append(escape(profile.model)).append("\",")
                append("\"profileManufacturer\":\"").append(escape(profile.manufacturer)).append("\",")
                append("\"sdkInt\":").append(profile.sdkInt)
            }
            append('}')
        }
        configFile.writeText(json)
        configFile
    }

    suspend fun inspectInstanceWorkspace(spaceId: Long, appId: Long): InstanceWorkspaceStats =
        withContext(Dispatchers.IO) {
            val dir = instanceDirectory(spaceId, appId)
            if (!dir.exists()) {
                return@withContext InstanceWorkspaceStats(
                    directoryPath = dir.absolutePath,
                    sizeBytes = 0L,
                    fileCount = 0,
                    lastSessionRecordedAt = null
                )
            }
            var totalBytes = 0L
            var count = 0
            dir.walkTopDown().filter { it.isFile }.forEach { file ->
                totalBytes += file.length()
                count++
            }
            val configFile = File(dir, SESSION_CONFIG_FILE)
            val recordedAt = if (configFile.exists()) configFile.lastModified() else null
            InstanceWorkspaceStats(
                directoryPath = dir.absolutePath,
                sizeBytes = totalBytes,
                fileCount = count,
                lastSessionRecordedAt = recordedAt
            )
        }

    suspend fun resetInstanceWorkspace(spaceId: Long, appId: Long): Boolean =
        withContext(Dispatchers.IO) {
            val dir = instanceDirectory(spaceId, appId)
            if (!dir.exists()) true else dir.deleteRecursively()
        }

    suspend fun deleteSpaceWorkspaces(spaceId: Long): Boolean =
        withContext(Dispatchers.IO) {
            val dir = spaceDirectory(spaceId)
            if (!dir.exists()) true else dir.deleteRecursively()
        }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    companion object {
        private const val WORKSPACES_FOLDER = "workspaces"
        private const val SESSION_CONFIG_FILE = "session_config.json"
    }
}
