package com.cipher.virtual.virtualization

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cipher.virtual.CipherVirtualApp
import com.cipher.virtual.R
import com.cipher.virtual.data.local.WorkspaceContextWrapper
import com.cipher.virtual.data.local.WorkspaceStorageManager
import com.cipher.virtual.ui.components.TelemetryPill
import com.cipher.virtual.ui.theme.LocalNeuPalette
import com.cipher.virtual.ui.theme.NeuButton
import com.cipher.virtual.ui.theme.NeuCard
import com.cipher.virtual.ui.theme.NeuIconButton
import com.cipher.virtual.ui.theme.NeuTonalButton
import com.cipher.virtual.ui.theme.NeuWell
import com.cipher.virtual.ui.theme.OctoSpaceTheme
import java.io.File

/**
 * Activity representing the active virtual container running the cloned application instance.
 *
 * Runs in dedicated `:w0`–`:w7` worker OS processes (via [VirtualWorkerContainers]) and uses
 * [WorkspaceContextWrapper] for isolated file, database, and SharedPreferences operations
 * in a unified Soft UI (Neumorphic) layout.
 */
open class VirtualAppContainerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val instanceId = intent.getStringExtra(EXTRA_INSTANCE_ID) ?: "default_instance"
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: "com.target.cloned"
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: getString(R.string.virtual_app_fallback_name)
        val label = intent.getStringExtra(EXTRA_LABEL) ?: getString(R.string.virtual_clone_fallback_label)
        val workerSlot = intent.getStringExtra(EXTRA_WORKER_SLOT) ?: ":w0"
        val virtualUid = intent.getIntExtra(EXTRA_VIRTUAL_UID, 10000)
        val isRooted = intent.getBooleanExtra(EXTRA_IS_ROOTED, false)
        val envType = intent.getStringExtra(EXTRA_ENV_TYPE) ?: "NON_ROOT_USERSPACE_HOOK"
        val dataPath = intent.getStringExtra(EXTRA_DATA_PATH)
            ?: File(filesDir, "workspaces/$instanceId/data").absolutePath
        val osPid = Process.myPid()

        val hasExplicitTheme = intent.hasExtra(EXTRA_DARK_THEME)
        val explicitDarkTheme = intent.getBooleanExtra(EXTRA_DARK_THEME, true)
        val isMainProcess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName() == applicationInfo.processName
        } else {
            true
        }
        val appContainer = if (isMainProcess) applicationContext as? CipherVirtualApp else null

        setContent {
            val darkTheme = when {
                hasExplicitTheme -> explicitDarkTheme
                appContainer != null -> appContainer.preferencesStore.darkThemeFlow
                    .collectAsStateWithLifecycle(initialValue = true).value
                else -> isSystemInDarkTheme()
            }

            OctoSpaceTheme(darkTheme = darkTheme) {
                VirtualAppContainerContent(
                    instanceId = instanceId,
                    packageName = packageName,
                    appName = appName,
                    label = label,
                    workerSlot = workerSlot,
                    virtualUid = virtualUid,
                    osPid = osPid,
                    isRooted = isRooted,
                    envType = envType,
                    dataPath = dataPath,
                    onBack = { finish() },
                    onLaunchHostIntent = {
                        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { startActivity(launchIntent) }
                                .onFailure { err ->
                                    val detail = err.localizedMessage ?: err.javaClass.simpleName
                                    Toast.makeText(
                                        this,
                                        getString(R.string.virtual_launch_error_format, detail),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                        } else {
                            Toast.makeText(
                                this,
                                getString(R.string.virtual_target_not_installed),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_INSTANCE_ID = "extra_instance_id"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_APP_NAME = "extra_app_name"
        const val EXTRA_LABEL = "extra_label"
        const val EXTRA_WORKER_SLOT = "extra_worker_slot"
        const val EXTRA_VIRTUAL_UID = "extra_virtual_uid"
        const val EXTRA_IS_ROOTED = "extra_is_rooted"
        const val EXTRA_ENV_TYPE = "extra_env_type"
        const val EXTRA_DATA_PATH = "extra_data_path"
        const val EXTRA_DARK_THEME = "extra_dark_theme"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VirtualAppContainerContent(
    instanceId: String,
    packageName: String,
    appName: String,
    label: String,
    workerSlot: String,
    virtualUid: Int,
    isRooted: Boolean,
    envType: String,
    dataPath: String,
    onBack: () -> Unit,
    onLaunchHostIntent: () -> Unit,
    osPid: Int = Process.myPid()
) {
    val context = LocalContext.current
    val palette = LocalNeuPalette.current
    val isNativeLoaded = remember { NativeVirtualEngine.isNativeEngineAvailable() }
    val nativeFailureReason = remember { NativeVirtualEngine.getLoadFailureReason() ?: "not built" }
    var redirectCount by remember {
        mutableLongStateOf(NativeVirtualEngine.getRedirectCount())
    }
    var fileCount by remember { mutableIntStateOf(File(dataPath).list()?.size ?: 0) }
    var lastSimulatedWrite by remember { mutableStateOf<String?>(null) }

    val baseDir = remember(dataPath) { File(dataPath).parentFile ?: File(dataPath) }
    val databasesPath = remember(baseDir) { File(baseDir, "databases").absolutePath }
    val sharedPrefsPath = remember(baseDir) { File(baseDir, "shared_prefs").absolutePath }
    val cachePath = remember(baseDir) { File(baseDir, "cache").absolutePath }
    val workspaceContext = remember(context, instanceId, workerSlot) {
        val storage = WorkspaceStorageManager(context.applicationContext)
        val paths = storage.initializeInstanceStorageSync(instanceId).getOrNull()
            ?: storage.getInstancePaths(instanceId)
        WorkspaceContextWrapper(context, paths, workerSlot)
    }

    Scaffold(
        containerColor = palette.base,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 920.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    NeuIconButton(
                        onClick = onBack,
                        size = 48.dp,
                        modifier = Modifier.testTag("virtual_container_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.virtual_container_title_format, appName, label),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TelemetryPill(
                        label = stringResource(R.string.virtual_running_slot_format, workerSlot),
                        icon = Icons.Rounded.CheckCircle,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }

                NeuCard(
                    cornerRadius = 24.dp,
                    elevation = 6.dp,
                    accentTint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("virtual_container_engine_card")
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.virtual_sandbox_env_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(
                                        if (isRooted) R.string.virtual_mode_rooted else R.string.virtual_mode_non_root
                                    ) + " • $envType",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TelemetryPill(
                                label = stringResource(R.string.virtual_uid_format, virtualUid),
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                            TelemetryPill(
                                label = stringResource(R.string.virtual_process_slot_format, workerSlot),
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                            TelemetryPill(
                                label = stringResource(R.string.virtual_os_pid_format, osPid),
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                            TelemetryPill(
                                label = stringResource(R.string.virtual_io_redirections_format, redirectCount),
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                            TelemetryPill(
                                label = if (isNativeLoaded) {
                                    stringResource(R.string.native_engine_loaded)
                                } else {
                                    stringResource(R.string.native_engine_unavailable_format, nativeFailureReason)
                                },
                                containerColor = if (isNativeLoaded) {
                                    MaterialTheme.colorScheme.tertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.secondaryContainer
                                }
                            )
                        }

                        NeuWell(
                            cornerRadius = 16.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (isNativeLoaded) {
                                    stringResource(R.string.virtual_syscall_summary_format, packageName)
                                } else {
                                    stringResource(R.string.virtual_native_unavailable_summary, nativeFailureReason)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                NeuCard(
                    cornerRadius = 24.dp,
                    elevation = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("virtual_container_storage_card")
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Storage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = stringResource(R.string.virtual_isolated_paths_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        StoragePathItem(
                            label = stringResource(R.string.virtual_path_private_data),
                            path = dataPath
                        )
                        StoragePathItem(
                            label = stringResource(R.string.virtual_path_databases),
                            path = databasesPath
                        )
                        StoragePathItem(
                            label = stringResource(R.string.virtual_path_shared_prefs),
                            path = sharedPrefsPath
                        )
                        StoragePathItem(
                            label = stringResource(R.string.virtual_path_cache),
                            path = cachePath
                        )

                        lastSimulatedWrite?.let { writeInfo ->
                            NeuWell(
                                cornerRadius = 14.dp,
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = writeInfo,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            NeuTonalButton(
                                onClick = {
                                    val fileName = "clone_session_${System.currentTimeMillis()}.dat"
                                    val payload = "Virtual state for instance $instanceId (UID: $virtualUid, PID: $osPid, Slot: $workerSlot)"
                                    workspaceContext.openFileOutput(fileName, android.content.Context.MODE_PRIVATE).use {
                                        it.write(payload.toByteArray())
                                    }
                                    workspaceContext.getSharedPreferences("container_session", android.content.Context.MODE_PRIVATE)
                                        .edit()
                                        .putString("last_file", fileName)
                                        .putInt("last_os_pid", osPid)
                                        .putString("worker_slot", workerSlot)
                                        .apply()
                                    val writtenFile = File(workspaceContext.filesDir, fileName)
                                    fileCount = workspaceContext.filesDir.list()?.size ?: 0
                                    redirectCount = NativeVirtualEngine.getRedirectCount()
                                    lastSimulatedWrite = context.getString(
                                        R.string.virtual_wrote_file_format,
                                        writtenFile.name,
                                        writtenFile.length()
                                    )
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.virtual_state_written_toast_format, instanceId),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                accentColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("virtual_container_simulate_io_button")
                            ) {
                                Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.virtual_simulate_io_action),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            NeuTonalButton(
                                onClick = {
                                    val files = File(dataPath).list() ?: emptyArray()
                                    val summary = if (files.isEmpty()) {
                                        context.getString(R.string.virtual_files_list_empty)
                                    } else {
                                        files.joinToString()
                                    }
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.virtual_files_list_toast_format, instanceId, summary),
                                        Toast.LENGTH_LONG
                                    ).show()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("virtual_container_list_files_button")
                            ) {
                                Icon(Icons.Rounded.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.virtual_files_count_format, fileCount),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                NeuButton(
                    onClick = onLaunchHostIntent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("launch_virtual_app_button")
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.virtual_launch_in_sandbox_format, appName),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun StoragePathItem(label: String, path: String) {
    NeuWell(
        cornerRadius = 14.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = path,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
