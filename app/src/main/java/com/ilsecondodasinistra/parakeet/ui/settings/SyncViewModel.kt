package com.ilsecondodasinistra.parakeet.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ilsecondodasinistra.parakeet.sync.BackupManager
import com.ilsecondodasinistra.parakeet.sync.DeviceInfo
import com.ilsecondodasinistra.parakeet.sync.SyncManager
import com.ilsecondodasinistra.parakeet.sync.SyncSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** The Sync and Backup tabs. */
@HiltViewModel
class SyncViewModel @Inject constructor(
    private val manager: SyncManager,
    private val backup: BackupManager,
) : ViewModel() {
    val settings: StateFlow<SyncSettings> = manager.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncSettings())
    val running: StateFlow<Boolean> = manager.running
    val pairing: StateFlow<List<DeviceInfo>?> = manager.pairing

    /** [deviceName] is set only when switching on (the name asked just before the picker). */
    fun connect(uri: Uri, deviceName: String?) = viewModelScope.launch { manager.connect(uri, deviceName) }
    fun disable() = viewModelScope.launch { manager.disable() }
    fun syncNow() = manager.syncNowAsync()
    fun rename(name: String) = viewModelScope.launch { manager.setDeviceName(name) }
    fun adopt(device: DeviceInfo) = viewModelScope.launch { manager.adopt(device) }
    fun startNew() = viewModelScope.launch { manager.startNew() }

    suspend fun exportTo(resolver: ContentResolver, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val text = backup.export()
            resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: error("no stream")
        }.isSuccess
    }

    /** Number of changes, or the reason: unreadable, format, newer. */
    suspend fun restoreFrom(resolver: ContentResolver, uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        val text = runCatching { resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
            ?: return@withContext Result.failure(IllegalArgumentException("unreadable"))
        backup.restore(text)
    }
}
