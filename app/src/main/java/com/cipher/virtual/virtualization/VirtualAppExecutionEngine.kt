package com.cipher.virtual.virtualization

import android.content.Context
import android.content.Intent
import com.cipher.virtual.data.local.WorkspaceStorageManager
import com.cipher.virtual.data.model.VirtualAppEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class VirtualLaunchResult(
    val success: Boolean,
    val instanceId: String,
    val packageName: String,
    val workerSlot: String,
    val virtualUid: Int,
    val isRootedMode: Boolean,
    val nativeEngineAvailable: Boolean,
    val virtualEnvType: String,
    val isolatedStoragePath: String,
    val message: String
)

/**
 * Orchestrates launching isolated workspace sessions backed by [WorkspaceStorageManager]
 * and worker containers (`:w0`–`:w7`), reporting honestly whether `libciphervirtual.so` is loaded.
 */
class VirtualAppExecutionEngine(
    private val context: Context,
    private val storageManager: WorkspaceStorageManager
) {

    suspend fun launchInstance(
        app: VirtualAppEntity,
        instanceId: String,
        forceRootMode: Boolean = false,
        darkTheme: Boolean = true
    ): VirtualLaunchResult = withContext(Dispatchers.IO) {
        val rootStatus = RootDetectionHelper.checkRootStatus()
        val useRoot = forceRootMode && rootStatus.isRooted
        val paths = storageManager.initializeInstanceStorageSync(instanceId).getOrThrow()

        val nativeAvailable = NativeVirtualEngine.isNativeEngineAvailable()
        if (nativeAvailable) {
            NativeVirtualEngine.initVirtualEngine(
                hostPackage = context.packageName,
                hostUid = android.os.Process.myUid(),
                isRootMode = useRoot
            )
            NativeVirtualEngine.registerRedirectRule(
                instanceId = instanceId,
                targetPackage = app.packageName,
                sourcePrefix = "/data/data/${app.packageName}",
                targetPrefix = paths.dataDir.absolutePath,
                virtualUid = app.virtualUid
            )
            NativeVirtualEngine.registerRedirectRule(
                instanceId = instanceId,
                targetPackage = app.packageName,
                sourcePrefix = "/data/user/0/${app.packageName}",
                targetPrefix = paths.dataDir.absolutePath,
                virtualUid = app.virtualUid
            )
            NativeVirtualEngine.registerRedirectRule(
                instanceId = instanceId,
                targetPackage = app.packageName,
                sourcePrefix = "/data/data/${app.packageName}/shared_prefs",
                targetPrefix = paths.sharedPrefsDir.absolutePath,
                virtualUid = app.virtualUid
            )
            NativeVirtualEngine.registerRedirectRule(
                instanceId = instanceId,
                targetPackage = app.packageName,
                sourcePrefix = "/data/data/${app.packageName}/databases",
                targetPrefix = paths.databasesDir.absolutePath,
                virtualUid = app.virtualUid
            )
            NativeVirtualEngine.activateInstance(instanceId)
            NativeVirtualEngine.setVirtualUid(app.virtualUid)
        }

        val envType = when {
            !nativeAvailable -> "STORAGE_ISOLATION_ONLY"
            useRoot -> "ROOT_NAMESPACE_MOUNT"
            else -> "NATIVE_USERSPACE_ENGINE"
        }

        val containerClass = resolveWorkerContainerClass(app.workerSlot)
        val intent = Intent(context, containerClass).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(VirtualAppContainerActivity.EXTRA_INSTANCE_ID, instanceId)
            putExtra(VirtualAppContainerActivity.EXTRA_PACKAGE_NAME, app.packageName)
            putExtra(VirtualAppContainerActivity.EXTRA_APP_NAME, app.displayName)
            putExtra(VirtualAppContainerActivity.EXTRA_LABEL, app.instanceLabel)
            putExtra(VirtualAppContainerActivity.EXTRA_WORKER_SLOT, app.workerSlot)
            putExtra(VirtualAppContainerActivity.EXTRA_VIRTUAL_UID, app.virtualUid)
            putExtra(VirtualAppContainerActivity.EXTRA_IS_ROOTED, useRoot)
            putExtra(VirtualAppContainerActivity.EXTRA_ENV_TYPE, envType)
            putExtra(VirtualAppContainerActivity.EXTRA_DATA_PATH, paths.dataDir.absolutePath)
            putExtra(VirtualAppContainerActivity.EXTRA_DARK_THEME, darkTheme)
        }

        context.startActivity(intent)

        val statusSuffix = if (!nativeAvailable) {
            " (native libciphervirtual.so unavailable)"
        } else {
            ""
        }

        VirtualLaunchResult(
            success = true,
            instanceId = instanceId,
            packageName = app.packageName,
            workerSlot = app.workerSlot,
            virtualUid = app.virtualUid,
            isRootedMode = useRoot,
            nativeEngineAvailable = nativeAvailable,
            virtualEnvType = envType,
            isolatedStoragePath = paths.dataDir.absolutePath,
            message = "Opened worker ${app.workerSlot} in $envType mode$statusSuffix"
        )
    }
}
