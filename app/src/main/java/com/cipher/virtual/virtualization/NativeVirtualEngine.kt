package com.cipher.virtual.virtualization

import android.util.Log

/**
 * JNI interface bridge for `libciphervirtual.so`.
 *
 * Never fakes path interception or virtual UID enforcement when the native library fails to load:
 * callers must check [isNativeEngineAvailable] and surface [getLoadFailureReason] in diagnostics.
 */
object NativeVirtualEngine {

    private const val TAG = "NativeVirtualEngine"

    @Volatile
    private var isNativeLoaded: Boolean = false

    @Volatile
    private var loadFailureReason: String? = null

    init {
        try {
            System.loadLibrary("ciphervirtual")
            isNativeLoaded = true
            loadFailureReason = null
            logInfo("Native virtualization engine library (libciphervirtual.so) loaded successfully.")
        } catch (e: Throwable) {
            isNativeLoaded = false
            loadFailureReason = "${e::class.java.simpleName}: ${e.message ?: "libciphervirtual.so failed to load"}"
            logWarning(
                "Failed to load native library libciphervirtual.so ($loadFailureReason). " +
                    "Native path/UID/property interception is unavailable."
            )
        }
    }

    fun isNativeEngineAvailable(): Boolean = isNativeLoaded

    fun getLoadFailureReason(): String? = loadFailureReason

    fun initVirtualEngine(hostPackage: String, hostUid: Int, isRootMode: Boolean): Boolean {
        if (!isNativeLoaded) {
            logUnavailable("initVirtualEngine")
            return false
        }
        return try {
            nativeInitVirtualEngine(hostPackage, hostUid, isRootMode)
        } catch (e: UnsatisfiedLinkError) {
            markNativeFailure("initVirtualEngine", e)
            false
        }
    }

    fun registerRedirectRule(
        instanceId: String,
        targetPackage: String,
        sourcePrefix: String,
        targetPrefix: String,
        virtualUid: Int
    ): Boolean {
        if (!isNativeLoaded) {
            logUnavailable("registerRedirectRule")
            return false
        }
        return try {
            nativeRegisterRedirectRule(instanceId, targetPackage, sourcePrefix, targetPrefix, virtualUid)
        } catch (e: UnsatisfiedLinkError) {
            markNativeFailure("registerRedirectRule", e)
            false
        }
    }

    fun activateInstance(instanceId: String): Boolean {
        if (!isNativeLoaded) {
            logUnavailable("activateInstance")
            return false
        }
        return try {
            nativeActivateInstance(instanceId)
        } catch (e: UnsatisfiedLinkError) {
            markNativeFailure("activateInstance", e)
            false
        }
    }

    fun redirectPath(originalPath: String): String {
        if (!isNativeLoaded) {
            return originalPath
        }
        return try {
            nativeRedirectPath(originalPath)
        } catch (e: UnsatisfiedLinkError) {
            markNativeFailure("redirectPath", e)
            originalPath
        }
    }

    fun getRedirectCount(): Long {
        if (!isNativeLoaded) {
            return 0L
        }
        return try {
            nativeGetRedirectCount()
        } catch (e: UnsatisfiedLinkError) {
            markNativeFailure("getRedirectCount", e)
            0L
        }
    }

    fun setVirtualUid(uid: Int): Boolean {
        if (!isNativeLoaded) {
            logUnavailable("setVirtualUid")
            return false
        }
        return try {
            nativeSetVirtualUid(uid)
        } catch (e: UnsatisfiedLinkError) {
            markNativeFailure("setVirtualUid", e)
            false
        }
    }

    fun getVirtualUid(): Int {
        if (!isNativeLoaded) {
            return -1
        }
        return try {
            nativeGetVirtualUid()
        } catch (e: UnsatisfiedLinkError) {
            markNativeFailure("getVirtualUid", e)
            -1
        }
    }

    fun checkRootCapability(): Boolean {
        if (!isNativeLoaded) {
            return false
        }
        return try {
            nativeCheckRootCapability()
        } catch (e: UnsatisfiedLinkError) {
            markNativeFailure("checkRootCapability", e)
            false
        }
    }

    fun executeRootCommand(command: String): String {
        if (!isNativeLoaded) {
            return "ERROR: Native library libciphervirtual.so unavailable"
        }
        return try {
            nativeExecuteRootCommand(command)
        } catch (e: UnsatisfiedLinkError) {
            markNativeFailure("executeRootCommand", e)
            "ERROR: ${e.message ?: "UnsatisfiedLinkError"}"
        }
    }

    fun getEngineStatus(): String {
        if (!isNativeLoaded) {
            return "NativeVirtualEngine[unavailable: ${loadFailureReason ?: "libciphervirtual.so not loaded"}]"
        }
        return try {
            nativeGetEngineStatus()
        } catch (e: UnsatisfiedLinkError) {
            markNativeFailure("getEngineStatus", e)
            "NativeVirtualEngine[unavailable: ${loadFailureReason ?: "UnsatisfiedLinkError"}]"
        }
    }

    private fun markNativeFailure(operation: String, error: Throwable) {
        isNativeLoaded = false
        loadFailureReason = "${error::class.java.simpleName}: ${error.message ?: operation}"
        logWarning("Native call $operation failed: $loadFailureReason")
    }

    private fun logUnavailable(operation: String) {
        logWarning("Skipped $operation: libciphervirtual.so unavailable (${loadFailureReason ?: "not loaded"})")
    }

    private fun logInfo(message: String) {
        runCatching { Log.i(TAG, message) }
    }

    private fun logWarning(message: String) {
        val logged = runCatching { Log.w(TAG, message) }.isSuccess
        if (!logged) {
            System.err.println("W/$TAG: $message")
        }
    }

    // JNI Native Declarations
    private external fun nativeInitVirtualEngine(hostPackage: String, hostUid: Int, isRootMode: Boolean): Boolean
    private external fun nativeRegisterRedirectRule(
        instanceId: String,
        targetPackage: String,
        sourcePrefix: String,
        targetPrefix: String,
        virtualUid: Int
    ): Boolean
    private external fun nativeActivateInstance(instanceId: String): Boolean
    private external fun nativeRedirectPath(originalPath: String): String
    private external fun nativeGetRedirectCount(): Long
    private external fun nativeSetVirtualUid(uid: Int): Boolean
    private external fun nativeGetVirtualUid(): Int
    private external fun nativeCheckRootCapability(): Boolean
    private external fun nativeExecuteRootCommand(command: String): String
    private external fun nativeGetEngineStatus(): String
}
