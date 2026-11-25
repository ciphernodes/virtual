package com.example.monitoring

import android.app.ActivityManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Binder
import android.os.Build
import android.os.Debug
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import com.example.CipherVirtualApp
import com.example.data.model.VirtualAppEntity
import com.example.data.model.VirtualSpaceEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class StabilityLevel {
    OPTIMAL,
    MODERATE,
    HIGH_LOAD
}

data class SpaceResourceUsage(
    val spaceId: Long,
    val spaceName: String,
    val tag: String,
    val appCount: Int,
    val estimatedRamMb: Long,
    val cpuSharePercent: Float
)

data class ResourceTelemetrySnapshot(
    val appHeapUsedMb: Long = 0L,
    val appHeapMaxMb: Long = 0L,
    val nativeHeapMb: Long = 0L,
    val systemAvailableRamMb: Long = 0L,
    val systemTotalRamMb: Long = 0L,
    val systemLowMemory: Boolean = false,
    val processCpuPercent: Float = 0f,
    val cpuCores: Int = 1,
    val stability: StabilityLevel = StabilityLevel.OPTIMAL,
    val spaceUsages: List<SpaceResourceUsage> = emptyList(),
    val sampledAtMillis: Long = System.currentTimeMillis(),
    val cpuHistory: List<Float> = emptyList(),
    val heapHistory: List<Float> = emptyList(),
    val storageAvailableGb: Float = 0f,
    val storageTotalGb: Float = 0f,
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val thermalLabel: String = "Nominal"
) {
    val systemUsedRamMb: Long
        get() = (systemTotalRamMb - systemAvailableRamMb).coerceAtLeast(0L)

    val systemRamUsageRatio: Float
        get() = if (systemTotalRamMb > 0L) {
            (systemUsedRamMb.toFloat() / systemTotalRamMb.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val storageUsedRatio: Float
        get() = if (storageTotalGb > 0f) {
            ((storageTotalGb - storageAvailableGb) / storageTotalGb).coerceIn(0f, 1f)
        } else 0f
}

class ResourceMonitorSampler(context: Context) {
    private val appContext = context.applicationContext
    private val activityManager =
        appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    private val powerManager =
        appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val batteryManager =
        appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

    private var lastWallClockMs: Long = SystemClock.elapsedRealtime()
    private var lastProcessCpuMs: Long = Process.getElapsedCpuTime()
    private val cpuSamples = ArrayDeque<Float>(HISTORY_CAPACITY)
    private val heapSamples = ArrayDeque<Float>(HISTORY_CAPACITY)

    @Synchronized
    fun captureSnapshot(
        spaces: List<VirtualSpaceEntity>,
        virtualApps: List<VirtualAppEntity>
    ): ResourceTelemetrySnapshot {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)

        val totalRamMb = (memInfo.totalMem / BYTES_PER_MB).coerceAtLeast(1L)
        val availRamMb = (memInfo.availMem / BYTES_PER_MB).coerceAtLeast(0L)

        val runtime = Runtime.getRuntime()
        val heapUsedMb = ((runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MB).coerceAtLeast(0L)
        val heapMaxMb = (runtime.maxMemory() / BYTES_PER_MB).coerceAtLeast(1L)
        val nativeHeapMb = (Debug.getNativeHeapAllocatedSize() / BYTES_PER_MB).coerceAtLeast(0L)

        val nowWallMs = SystemClock.elapsedRealtime()
        val nowCpuMs = Process.getElapsedCpuTime()
        val deltaWallMs = (nowWallMs - lastWallClockMs).coerceAtLeast(1L)
        val deltaCpuMs = (nowCpuMs - lastProcessCpuMs).coerceAtLeast(0L)
        val cores = runtime.availableProcessors().coerceAtLeast(1)

        val cpuPercent = ((deltaCpuMs.toFloat() / (deltaWallMs.toFloat() * cores)) * 100f)
            .coerceIn(0f, 100f)

        lastWallClockMs = nowWallMs
        lastProcessCpuMs = nowCpuMs

        appendSample(cpuSamples, cpuPercent)
        appendSample(heapSamples, heapUsedMb.toFloat())

        val (storageAvailGb, storageTotalGb) = readInternalStorageGb()
        val (batteryPercent, isCharging) = readBatteryState()
        val thermalLabel = readThermalLabel()

        val appsBySpace = virtualApps.groupBy { it.spaceId }
        val totalRegisteredApps = virtualApps.size.coerceAtLeast(1)
        val processTotalMb = (heapUsedMb + nativeHeapMb).coerceAtLeast(1L)

        val spaceUsages = spaces.map { space ->
            val apps = appsBySpace[space.id].orEmpty()
            val weight = if (virtualApps.isEmpty()) {
                1f / spaces.size.coerceAtLeast(1)
            } else {
                apps.size.toFloat() / totalRegisteredApps.toFloat()
            }
            SpaceResourceUsage(
                spaceId = space.id,
                spaceName = space.name,
                tag = space.tag,
                appCount = apps.size,
                estimatedRamMb = (processTotalMb * weight).toLong().coerceAtLeast(if (apps.isNotEmpty()) 4L else 0L),
                cpuSharePercent = (cpuPercent * weight).coerceIn(0f, 100f)
            )
        }

        val ramRatio = if (totalRamMb > 0L) (totalRamMb - availRamMb).toFloat() / totalRamMb.toFloat() else 0f
        val stability = when {
            memInfo.lowMemory || ramRatio >= 0.88f || cpuPercent >= 80f -> StabilityLevel.HIGH_LOAD
            ramRatio >= 0.72f || cpuPercent >= 50f -> StabilityLevel.MODERATE
            else -> StabilityLevel.OPTIMAL
        }

        return ResourceTelemetrySnapshot(
            appHeapUsedMb = heapUsedMb,
            appHeapMaxMb = heapMaxMb,
            nativeHeapMb = nativeHeapMb,
            systemAvailableRamMb = availRamMb,
            systemTotalRamMb = totalRamMb,
            systemLowMemory = memInfo.lowMemory,
            processCpuPercent = cpuPercent,
            cpuCores = cores,
            stability = stability,
            spaceUsages = spaceUsages,
            sampledAtMillis = System.currentTimeMillis(),
            cpuHistory = cpuSamples.toList(),
            heapHistory = heapSamples.toList(),
            storageAvailableGb = storageAvailGb,
            storageTotalGb = storageTotalGb,
            batteryPercent = batteryPercent,
            isCharging = isCharging,
            thermalLabel = thermalLabel
        )
    }

    private fun appendSample(buffer: ArrayDeque<Float>, value: Float) {
        if (buffer.size >= HISTORY_CAPACITY) {
            buffer.removeFirst()
        }
        buffer.addLast(value)
    }

    private fun readInternalStorageGb(): Pair<Float, Float> {
        return runCatching {
            val stat = StatFs(Environment.getDataDirectory().path)
            val totalGb = (stat.totalBytes / BYTES_PER_GB.toFloat()).coerceAtLeast(0f)
            val availGb = (stat.availableBytes / BYTES_PER_GB.toFloat()).coerceAtLeast(0f)
            availGb to totalGb
        }.getOrDefault(0f to 0f)
    }

    private fun readBatteryState(): Pair<Int, Boolean> {
        return runCatching {
            val intent = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val pct = if (level >= 0 && scale > 0) {
                ((level * 100f) / scale).toInt().coerceIn(0, 100)
            } else {
                batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.coerceIn(0, 100) ?: 100
            }
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL ||
                (batteryManager?.isCharging == true)
            pct to charging
        }.getOrDefault(100 to false)
    }

    private fun readThermalLabel(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return "Nominal"
        return runCatching {
            when (powerManager?.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "Nominal"
                PowerManager.THERMAL_STATUS_LIGHT -> "Warm"
                PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
                PowerManager.THERMAL_STATUS_SEVERE -> "Throttled"
                PowerManager.THERMAL_STATUS_CRITICAL,
                PowerManager.THERMAL_STATUS_EMERGENCY,
                PowerManager.THERMAL_STATUS_SHUTDOWN -> "Critical"
                else -> "Nominal"
            }
        }.getOrDefault("Nominal")
    }

    companion object {
        private const val BYTES_PER_MB = 1024L * 1024L
        private const val BYTES_PER_GB = 1024L * 1024L * 1024L
        private const val HISTORY_CAPACITY = 24
    }
}

class ResourceMonitorService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): ResourceMonitorService = this@ResourceMonitorService
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var sampler: ResourceMonitorSampler
    private var monitorJob: Job? = null

    @Volatile
    private var trackedSpaces: List<VirtualSpaceEntity> = emptyList()

    @Volatile
    private var trackedApps: List<VirtualAppEntity> = emptyList()

    private val _telemetry = MutableStateFlow(ResourceTelemetrySnapshot())
    val telemetry: StateFlow<ResourceTelemetrySnapshot> = _telemetry.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        sampler = (application as? CipherVirtualApp)?.resourceSampler
            ?: ResourceMonitorSampler(applicationContext)
        sampleNow()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_MONITORING -> startMonitoring()
            ACTION_STOP_MONITORING -> stopMonitoring()
            ACTION_SAMPLE_NOW -> sampleNow()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        stopMonitoring()
        serviceScope.cancel()
        super.onDestroy()
    }

    fun updateTrackedContainers(
        spaces: List<VirtualSpaceEntity>,
        virtualApps: List<VirtualAppEntity>
    ) {
        trackedSpaces = spaces
        trackedApps = virtualApps
        sampleNow()
    }

    fun startMonitoring(intervalMillis: Long = DEFAULT_SAMPLE_INTERVAL_MS) {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            while (isActive) {
                sampleNow()
                delay(intervalMillis.coerceAtLeast(500L))
            }
        }
    }

    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
    }

    fun sampleNow() {
        if (::sampler.isInitialized) {
            _telemetry.value = sampler.captureSnapshot(trackedSpaces, trackedApps)
        }
    }

    companion object {
        const val ACTION_START_MONITORING = "com.cipher.virtual.action.START_RESOURCE_MONITOR"
        const val ACTION_STOP_MONITORING = "com.cipher.virtual.action.STOP_RESOURCE_MONITOR"
        const val ACTION_SAMPLE_NOW = "com.cipher.virtual.action.SAMPLE_RESOURCES"
        private const val DEFAULT_SAMPLE_INTERVAL_MS = 2500L
    }
}
