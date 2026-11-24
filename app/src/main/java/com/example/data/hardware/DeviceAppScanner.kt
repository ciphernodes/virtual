package com.example.data.hardware

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.CrossProfileApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.data.model.InstalledAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class LaunchTargetMode {
    HOST_LAUNCHER,
    WORK_PROFILE
}

class DeviceAppScanner(private val context: Context) {

    private val iconCache = LruCache<String, ImageBitmap>(256)

    suspend fun scanInstalledLauncherApps(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }

        val seenPackages = HashSet<String>()
        resolveInfos.mapNotNull { info ->
            val activityInfo = info.activityInfo ?: return@mapNotNull null
            val pkg = activityInfo.packageName ?: return@mapNotNull null
            if (pkg == context.packageName || !seenPackages.add(pkg)) return@mapNotNull null

            val appInfo = activityInfo.applicationInfo
            val label = info.loadLabel(pm).toString().takeIf { it.isNotBlank() } ?: pkg
            val versionName = runCatching {
                val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, 0)
                }
                pkgInfo.versionName ?: "1.0"
            }.getOrDefault("1.0")

            val isGame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appInfo.category == ApplicationInfo.CATEGORY_GAME ||
                    @Suppress("DEPRECATION") ((appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0)
            } else {
                @Suppress("DEPRECATION") ((appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0)
            }

            val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0

            val apkSizeMb = runCatching {
                val sourceDir = appInfo.publicSourceDir ?: appInfo.sourceDir
                if (sourceDir != null) File(sourceDir).length() / (1024f * 1024f) else 0f
            }.getOrDefault(0f)

            InstalledAppInfo(
                packageName = pkg,
                label = label,
                versionName = versionName,
                isGame = isGame,
                icon = iconCache.get(pkg),
                isSystemApp = isSystemApp,
                targetSdk = appInfo.targetSdkVersion,
                apkSizeMb = apkSizeMb
            )
        }.sortedWith(
            compareByDescending<InstalledAppInfo> { it.isGame }
                .thenBy { it.isSystemApp }
                .thenBy { it.label.lowercase() }
        )
    }

    suspend fun loadAppIcons(packageNames: List<String>): Map<String, ImageBitmap> =
        withContext(Dispatchers.IO) {
            val result = mutableMapOf<String, ImageBitmap>()
            packageNames.distinct().forEach { pkg ->
                getAppIcon(pkg)?.let { result[pkg] = it }
            }
            result
        }

    fun getAppIcon(packageName: String): ImageBitmap? {
        iconCache.get(packageName)?.let { return it }
        return runCatching {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            drawableToImageBitmap(drawable).also { iconCache.put(packageName, it) }
        }.getOrNull()
    }

    fun hasWorkProfileTarget(packageName: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        return runCatching {
            val crossProfile = context.getSystemService(CrossProfileApps::class.java) ?: return false
            crossProfile.targetUserProfiles.isNotEmpty() &&
                context.packageManager.getLaunchIntentForPackage(packageName)?.component != null
        }.getOrDefault(false)
    }

    fun launchInstalledApp(
        packageName: String,
        preferWorkProfile: Boolean = false
    ): LaunchTargetMode? {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return null
        if (preferWorkProfile && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val component: ComponentName? = launchIntent.component
            if (component != null) {
                val crossProfile = context.getSystemService(CrossProfileApps::class.java)
                val targetUser = crossProfile?.targetUserProfiles?.firstOrNull()
                if (targetUser != null) {
                    val launched = runCatching {
                        crossProfile.startMainActivity(component, targetUser)
                        true
                    }.getOrDefault(false)
                    if (launched) return LaunchTargetMode.WORK_PROFILE
                }
            }
        }
        launchIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        )
        return if (runCatching { context.startActivity(launchIntent) }.isSuccess) {
            LaunchTargetMode.HOST_LAUNCHER
        } else {
            null
        }
    }

    private fun drawableToImageBitmap(drawable: Drawable): ImageBitmap {
        if (drawable is BitmapDrawable) {
            val bmp = drawable.bitmap
            if (bmp != null && !bmp.isRecycled && bmp.width > 0 && bmp.height > 0) {
                val safeBmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    bmp.config == Bitmap.Config.HARDWARE
                ) {
                    bmp.copy(Bitmap.Config.ARGB_8888, false)
                } else {
                    bmp
                }
                if (safeBmp != null) {
                    return if (safeBmp.width <= ICON_SIZE_PX && safeBmp.height <= ICON_SIZE_PX) {
                        safeBmp.asImageBitmap()
                    } else {
                        Bitmap.createScaledBitmap(safeBmp, ICON_SIZE_PX, ICON_SIZE_PX, true).asImageBitmap()
                    }
                }
            }
        }
        val bitmap = Bitmap.createBitmap(ICON_SIZE_PX, ICON_SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap.asImageBitmap()
    }

    companion object {
        private const val ICON_SIZE_PX = 84
    }
}
