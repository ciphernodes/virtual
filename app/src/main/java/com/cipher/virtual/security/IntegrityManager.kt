package com.cipher.virtual.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.cipher.virtual.BuildConfig
import com.google.android.gms.tasks.Task
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityException
import com.google.android.play.core.integrity.StandardIntegrityManager
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import com.google.android.play.core.integrity.model.StandardIntegrityErrorCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Base64
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class IntegrityVerificationState {
    IDLE,
    VERIFYING,
    TOKEN_ISSUED,
    VERIFIED,
    VERDICT_FAILED,
    CONFIGURATION_REQUIRED,
    ERROR
}

data class IntegrityVerdictResult(
    val verified: Boolean,
    val appRecognitionVerdict: String? = null,
    val deviceRecognitionVerdict: String? = null,
    val detail: String
)

data class IntegrityStatusSnapshot(
    val state: IntegrityVerificationState = IntegrityVerificationState.IDLE,
    val isPeriodicEnabled: Boolean = true,
    val intervalMinutes: Long = 15L,
    val lastCheckedAtMillis: Long? = null,
    val lastTokenPreview: String? = null,
    val lastRequestHash: String? = null,
    val lastVerdict: IntegrityVerdictResult? = null,
    val playStorePresent: Boolean = false,
    val summary: String = "Awaiting Play Integrity check",
    val errorCode: Int? = null
)

class IntegrityManager(
    context: Context,
    private val cloudProjectNumber: Long = BuildConfig.PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER,
    private val verificationEndpointUrl: String = BuildConfig.PLAY_INTEGRITY_VERIFICATION_URL
) {
    private val appContext = context.applicationContext
    private val standardIntegrityManager: StandardIntegrityManager by lazy {
        IntegrityManagerFactory.createStandard(appContext)
    }

    private val requestMutex = Mutex()
    private var tokenProvider: StandardIntegrityTokenProvider? = null
    private var periodicJob: Job? = null

    private val _status = MutableStateFlow(
        IntegrityStatusSnapshot(
            playStorePresent = isPlayStoreInstalled()
        )
    )
    val status: StateFlow<IntegrityStatusSnapshot> = _status.asStateFlow()

    fun startPeriodicChecks(
        scope: CoroutineScope,
        intervalMillis: Long = DEFAULT_INTERVAL_MILLIS
    ) {
        periodicJob?.cancel()
        val intervalMinutes = (intervalMillis / 60_000L).coerceAtLeast(1L)
        _status.update { it.copy(isPeriodicEnabled = true, intervalMinutes = intervalMinutes) }

        periodicJob = scope.launch {
            while (isActive) {
                verifyEnvironmentIntegrity(trigger = "periodic")
                delay(intervalMillis)
            }
        }
    }

    fun stopPeriodicChecks() {
        periodicJob?.cancel()
        periodicJob = null
        _status.update { it.copy(isPeriodicEnabled = false) }
    }

    fun setPeriodicEnabled(enabled: Boolean, scope: CoroutineScope) {
        if (enabled) {
            startPeriodicChecks(scope)
        } else {
            stopPeriodicChecks()
        }
    }

    suspend fun verifyEnvironmentIntegrity(trigger: String = "manual"): IntegrityStatusSnapshot {
        return requestMutex.withLock {
            val playStoreInstalled = isPlayStoreInstalled()
            _status.update {
                it.copy(
                    state = IntegrityVerificationState.VERIFYING,
                    playStorePresent = playStoreInstalled,
                    summary = "Requesting Play Integrity token…",
                    errorCode = null
                )
            }

            if (cloudProjectNumber <= 0L) {
                val unconfigured = _status.value.copy(
                    state = IntegrityVerificationState.CONFIGURATION_REQUIRED,
                    lastCheckedAtMillis = System.currentTimeMillis(),
                    playStorePresent = playStoreInstalled,
                    summary = "Link a Google Cloud Project Number in BuildConfig.PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER to issue live Play Integrity tokens.",
                    errorCode = null
                )
                _status.value = unconfigured
                return@withLock unconfigured
            }

            if (!playStoreInstalled) {
                val missingStore = _status.value.copy(
                    state = IntegrityVerificationState.ERROR,
                    lastCheckedAtMillis = System.currentTimeMillis(),
                    playStorePresent = false,
                    summary = "Google Play Store is unavailable on this device; Play Integrity cannot issue a verdict.",
                    errorCode = StandardIntegrityErrorCode.PLAY_STORE_NOT_FOUND
                )
                _status.value = missingStore
                return@withLock missingStore
            }

            val timestamp = System.currentTimeMillis()
            val requestHash = buildRequestHash(trigger, timestamp)

            return@withLock try {
                val token = requestTokenWithRefresh(requestHash)
                val preview = if (token.length > 16) "${token.take(12)}…${token.takeLast(4)}" else token

                val verdict = if (verificationEndpointUrl.isNotBlank()) {
                    verifyTokenWithServer(token, requestHash)
                } else {
                    null
                }

                val nextState = when {
                    verdict == null -> IntegrityVerificationState.TOKEN_ISSUED
                    verdict.verified -> IntegrityVerificationState.VERIFIED
                    else -> IntegrityVerificationState.VERDICT_FAILED
                }

                val summaryText = when {
                    verdict == null ->
                        "Standard Play Integrity token issued ($trigger); configure PLAY_INTEGRITY_VERIFICATION_URL to verify server-side."
                    verdict.verified ->
                        "Play Integrity verdict verified ($trigger): ${verdict.detail}"
                    else ->
                        "Play Integrity verdict rejected ($trigger): ${verdict.detail}"
                }

                val result = _status.value.copy(
                    state = nextState,
                    lastCheckedAtMillis = timestamp,
                    lastTokenPreview = preview,
                    lastRequestHash = requestHash,
                    lastVerdict = verdict,
                    playStorePresent = true,
                    summary = summaryText,
                    errorCode = null
                )
                _status.value = result
                result
            } catch (e: StandardIntegrityException) {
                val failure = _status.value.copy(
                    state = IntegrityVerificationState.ERROR,
                    lastCheckedAtMillis = timestamp,
                    lastRequestHash = requestHash,
                    playStorePresent = playStoreInstalled,
                    summary = describeIntegrityError(e.errorCode),
                    errorCode = e.errorCode
                )
                _status.value = failure
                failure
            } catch (t: Throwable) {
                val failure = _status.value.copy(
                    state = IntegrityVerificationState.ERROR,
                    lastCheckedAtMillis = timestamp,
                    lastRequestHash = requestHash,
                    playStorePresent = playStoreInstalled,
                    summary = t.message ?: "Unexpected Play Integrity failure",
                    errorCode = null
                )
                _status.value = failure
                failure
            }
        }
    }

    private suspend fun verifyTokenWithServer(
        integrityToken: String,
        requestHash: String
    ): IntegrityVerdictResult = withContext(Dispatchers.IO) {
        val trimmedUrl = verificationEndpointUrl.trim()
        require(trimmedUrl.startsWith("https://")) {
            "Verification endpoint must use HTTPS."
        }

        val connection = (URL(trimmedUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 10_000
            useCaches = false
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Request-Hash", requestHash)
            setRequestProperty("X-App-Package", appContext.packageName)
        }

        try {
            val payload = JSONObject().apply {
                put("integrityToken", integrityToken)
                put("requestHash", requestHash)
                put("packageName", appContext.packageName)
            }
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
            }

            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use(BufferedReader::readText).orEmpty()
            if (statusCode !in 200..299) {
                val serverError = runCatching {
                    JSONObject(body).optString("error").takeIf { it.isNotBlank() }
                }.getOrNull()
                return@withContext IntegrityVerdictResult(
                    verified = false,
                    detail = serverError ?: "Server verification HTTP $statusCode"
                )
            }

            val json = JSONObject(body)
            val verified = json.optBoolean("verified", false)
            val appVerdict = json.optString("appRecognitionVerdict").takeIf { it.isNotBlank() }
            val deviceVerdict = json.optString("deviceRecognitionVerdict").takeIf { it.isNotBlank() }
            val detail = listOfNotNull(
                appVerdict?.let { "app=$it" },
                deviceVerdict?.let { "device=$it" }
            ).joinToString(", ").ifBlank {
                if (verified) "Valid environment verdict" else "Integrity verdict failed"
            }

            IntegrityVerdictResult(
                verified = verified,
                appRecognitionVerdict = appVerdict,
                deviceRecognitionVerdict = deviceVerdict,
                detail = detail
            )
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun requestTokenWithRefresh(requestHash: String): String {
        val provider = ensureTokenProvider(forceRefresh = false)
        return try {
            requestTokenFromProvider(provider, requestHash)
        } catch (e: StandardIntegrityException) {
            if (e.errorCode == StandardIntegrityErrorCode.INTEGRITY_TOKEN_PROVIDER_INVALID) {
                val refreshedProvider = ensureTokenProvider(forceRefresh = true)
                requestTokenFromProvider(refreshedProvider, requestHash)
            } else {
                throw e
            }
        }
    }

    private suspend fun ensureTokenProvider(forceRefresh: Boolean): StandardIntegrityTokenProvider {
        val existing = tokenProvider
        if (!forceRefresh && existing != null) {
            return existing
        }
        val prepareRequest = PrepareIntegrityTokenRequest.builder()
            .setCloudProjectNumber(cloudProjectNumber)
            .build()
        val prepared = standardIntegrityManager.prepareIntegrityToken(prepareRequest).awaitTask()
        tokenProvider = prepared
        return prepared
    }

    private suspend fun requestTokenFromProvider(
        provider: StandardIntegrityTokenProvider,
        requestHash: String
    ): String {
        val tokenRequest = StandardIntegrityTokenRequest.builder()
            .setRequestHash(requestHash)
            .build()
        return provider.request(tokenRequest).awaitTask().token()
    }

    private fun buildRequestHash(trigger: String, timestampMillis: Long): String {
        val payload = "${appContext.packageName}|$trigger|$timestampMillis"
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private fun isPlayStoreInstalled(): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.packageManager.getPackageInfo(
                    PLAY_STORE_PACKAGE,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                appContext.packageManager.getPackageInfo(PLAY_STORE_PACKAGE, 0)
            }
            true
        }.getOrDefault(false)
    }

    private fun describeIntegrityError(errorCode: Int): String {
        return when (errorCode) {
            StandardIntegrityErrorCode.API_NOT_AVAILABLE ->
                "Play Integrity API is not available on this device."
            StandardIntegrityErrorCode.PLAY_STORE_NOT_FOUND ->
                "Google Play Store app was not found on this device."
            StandardIntegrityErrorCode.NETWORK_ERROR ->
                "Network unavailable while contacting Google Play Integrity."
            StandardIntegrityErrorCode.PLAY_STORE_VERSION_OUTDATED ->
                "Google Play Store must be updated to perform integrity checks."
            StandardIntegrityErrorCode.PLAY_SERVICES_NOT_FOUND ->
                "Google Play Services is unavailable on this device."
            StandardIntegrityErrorCode.PLAY_SERVICES_VERSION_OUTDATED ->
                "Google Play Services needs an update for Play Integrity."
            StandardIntegrityErrorCode.CLOUD_PROJECT_NUMBER_IS_INVALID ->
                "Configured Google Cloud Project Number is invalid."
            StandardIntegrityErrorCode.CLIENT_TRANSIENT_ERROR ->
                "Transient Play Integrity client error; retry scheduled."
            StandardIntegrityErrorCode.INTEGRITY_TOKEN_PROVIDER_INVALID ->
                "Integrity token provider expired and requires refresh."
            StandardIntegrityErrorCode.APP_NOT_INSTALLED ->
                "App package not recognized by Google Play."
            else -> "Play Integrity check returned error code $errorCode."
        }
    }

    private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            if (continuation.isActive) {
                continuation.resume(result)
            }
        }
        addOnFailureListener { exception ->
            if (continuation.isActive) {
                continuation.resumeWithException(exception)
            }
        }
    }

    companion object {
        private const val PLAY_STORE_PACKAGE = "com.android.vending"
        private const val DEFAULT_INTERVAL_MILLIS = 15L * 60L * 1000L
    }
}
