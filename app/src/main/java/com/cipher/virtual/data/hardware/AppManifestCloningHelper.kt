package com.cipher.virtual.data.hardware

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.cipher.virtual.data.model.InstalledAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Helper utility for extracting APK manifest metadata from installed system and user applications
 * to register cloned instances into the virtual workspace.
 */
class AppManifestCloningHelper(private val context: Context) {

    suspend fun getCloneableSystemAndUserApps(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(PackageManager.GET_META_DATA)
        }

        packages.mapNotNull { packageInfo ->
            val appInfo = packageInfo.applicationInfo ?: return@mapNotNull null
            val pkgName = packageInfo.packageName ?: return@mapNotNull null
            if (pkgName == context.packageName) return@mapNotNull null

            val label = runCatching { appInfo.loadLabel(pm).toString() }.getOrDefault(pkgName)
            val versionName = packageInfo.versionName ?: "1.0"
            val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isGame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appInfo.category == ApplicationInfo.CATEGORY_GAME ||
                    @Suppress("DEPRECATION") ((appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0)
            } else {
                @Suppress("DEPRECATION") ((appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0)
            }

            val sourceDir = appInfo.publicSourceDir ?: appInfo.sourceDir
            val apkSizeMb = sourceDir?.let { File(it).length() / (1024f * 1024f) } ?: 0f

            InstalledAppInfo(
                packageName = pkgName,
                label = label,
                versionName = versionName,
                isGame = isGame,
                icon = null,
                isSystemApp = isSystemApp,
                targetSdk = appInfo.targetSdkVersion,
                apkSizeMb = apkSizeMb
            )
        }.sortedBy { it.label.lowercase() }
    }
}
