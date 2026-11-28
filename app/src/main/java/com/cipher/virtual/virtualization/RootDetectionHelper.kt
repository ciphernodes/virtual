package com.cipher.virtual.virtualization

import android.os.Build
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

data class RootStatus(
    val isRooted: Boolean,
    val rootType: String,
    val suBinaryPath: String?,
    val isBusyboxPresent: Boolean
)

/**
 * Utility for detecting root access, Magisk/KernelSU installations,
 * and executing root subshell commands for rooted virtualization mode.
 */
object RootDetectionHelper {

    private val SU_BINARY_PATHS = arrayOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/system/sd/xbin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/data/local/su",
        "/su/bin/su"
    )

    fun checkRootStatus(): RootStatus {
        val suPath = findSuBinary()
        val canExecuteSu = suPath != null && testSuExecution()
        val busybox = File("/system/xbin/busybox").exists() || File("/system/bin/busybox").exists()

        val rootType = when {
            canExecuteSu && File("/data/adb/magisk").exists() -> "Magisk Root"
            canExecuteSu && File("/data/adb/ksu").exists() -> "KernelSU Root"
            canExecuteSu && File("/data/adb/apatch").exists() -> "APatch Root"
            canExecuteSu -> "Standard SU Root"
            runCatching { Build.TAGS?.contains("test-keys") == true }.getOrDefault(false) -> "Test-Keys ROM"
            else -> "Non-Rooted Standard Environment"
        }

        return RootStatus(
            isRooted = canExecuteSu,
            rootType = rootType,
            suBinaryPath = suPath,
            isBusyboxPresent = busybox
        )
    }

    fun isDeviceRooted(): Boolean = checkRootStatus().isRooted

    fun findSuBinary(): String? {
        for (path in SU_BINARY_PATHS) {
            try {
                val f = File(path)
                if (f.exists() && (f.canExecute() || f.length() > 0)) {
                    return path
                }
            } catch (ignored: SecurityException) {
            }
        }
        return null
    }

    fun testSuExecution(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readLine() ?: ""
            process.waitFor()
            output.contains("uid=0")
        } catch (e: Throwable) {
            false
        }
    }

    fun executeRootCommand(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val output = StringBuilder()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            while (errorReader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor()
            output.toString().trim()
        } catch (e: Throwable) {
            "ERROR: ${e.message}"
        }
    }
}
