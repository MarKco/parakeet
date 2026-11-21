package com.ilsecondodasinistra.parakeet.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ilsecondodasinistra.parakeet.data.PlanRepository
import com.ilsecondodasinistra.parakeet.data.SettingsRepository
import com.ilsecondodasinistra.parakeet.data.TaskRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sync with the shared folder: when to run a round (opening, after a change, every 15 minutes while the app is
 * open, by hand), the first connection and the folder permission. One round at a time.
 * After a merge `AlarmSync` (which watches the data) re-plans the alarm by itself.
 */
@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: RoomSyncStore,
    private val prefs: SyncSettingsRepository,
    private val settings: SettingsRepository,
    private val plan: PlanRepository,
    private val tasks: TaskRepository,
    private val clock: Clock,
) {
    private val mutex = Mutex()
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val state = prefs.settings

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    /** Devices found in the folder the first time it is connected: the user says which one this is (null = no question open). */
    private val _pairing = MutableStateFlow<List<DeviceInfo>?>(null)
    val pairing: StateFlow<List<DeviceInfo>?> = _pairing

    /** Runs a round 5 s after the plan, the catalog or a shared setting changes. */
    @OptIn(FlowPreview::class)
    fun start() {
        combine(
            plan.routines, plan.overrides, tasks.tasks,
            settings.settings.map { SharedSettings.values(it) }.distinctUntilChanged(),
        ) { a, b, c, d -> listOf(a, b, c, d) }
            .drop(1)
            .debounce(5_000)
            .onEach { syncNow() }
            .launchIn(appScope)
    }

    /** While the app is in front: once after 1.5 s, then every 15 minutes. Cancelled when it goes away. */
    suspend fun foreground() {
        delay(1_500)
        while (true) {
            syncNow()
            delay(15 * 60_000L)
        }
    }

    fun syncNowAsync() { appScope.launch { syncNow() } }

    suspend fun syncNow(): SyncResult? = mutex.withLock {
        val s = prefs.current()
        if (!s.enabled || s.folderUri == null) return@withLock null
        _running.value = true
        try {
            val result = withContext(Dispatchers.IO) {
                try {
                    val folder = SafSyncFolder(context, Uri.parse(s.folderUri))
                    SyncEngine.run(
                        folder, store, prefs.deviceId(), s.deviceName, clock.millis(),
                        LocalDate.now(clock).toEpochDay(), s.readOwnNext,
                    )
                } catch (e: Exception) {
                    SyncResult(false, error = e.message ?: e.toString(), at = clock.millis())
                }
            }
            if (result.ok && s.readOwnNext) prefs.setReadOwnNext(false)
            prefs.setLast(result)
            result
        } finally {
            _running.value = false
        }
    }

    suspend fun setDeviceName(name: String) = prefs.setDeviceName(name)

    /** The user picked [uri] with the system picker: keep the permission, and run (or ask which device this is). */
    suspend fun connect(uri: Uri, deviceName: String?) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        if (deviceName != null) prefs.setDeviceName(deviceName)
        prefs.setFolder(uri.toString())
        prefs.setEnabled(true)
        val s = prefs.current()
        if (!s.paired) {
            val id = prefs.deviceId()
            val others = withContext(Dispatchers.IO) {
                runCatching { SyncEngine.devices(SafSyncFolder(context, uri), id) }.getOrDefault(emptyList())
            }
            if (others.isNotEmpty()) { _pairing.value = others; return }
            prefs.setPaired(true)
        }
        syncNow()
    }

    suspend fun disable() {
        _pairing.value = null
        prefs.setEnabled(false)
    }

    /** "This is that device": its id and name, its phone settings, and its old file read back in. */
    suspend fun adopt(device: DeviceInfo) {
        prefs.adoptIdentity(device.deviceId, device.deviceName)
        settings.applyDeviceValues(device.device.values, onlyMissing = false)
        join(readOwn = true)
    }

    /** "New device": a fresh id. */
    suspend fun startNew() {
        prefs.newIdentity()
        join(readOwn = false)
    }

    private suspend fun join(readOwn: Boolean) {
        // what this phone has not made its own yet (the first list) gives way to the folder's
        prefs.refreshStamps(SharedSettings.values(settings.current()), SharedSettings.defaults, 1)
        store.discardUntouchedSeeds()
        prefs.setReadOwnNext(readOwn)
        prefs.setPaired(true)
        _pairing.value = null
        syncNow()
    }
}
