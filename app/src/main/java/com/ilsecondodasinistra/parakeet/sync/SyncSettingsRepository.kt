package com.ilsecondodasinistra.parakeet.sync

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.syncStore by preferencesDataStore(name = "sync")

/** What this device decided about the sync. Local to the phone: never synced, never in the Android backup. */
data class SyncSettings(
    val enabled: Boolean = false,
    val folderUri: String? = null,
    val deviceId: String = "",
    val deviceName: String = "",
    /** Joined a folder (or started one) already: no "is this one of those devices?" question. */
    val paired: Boolean = false,
    /** The next round also reads this device's own old file ("this is that device"). */
    val readOwnNext: Boolean = false,
    val last: SyncResult? = null,
)

@Singleton
class SyncSettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private object Keys {
        val enabled = booleanPreferencesKey("enabled")
        val folderUri = stringPreferencesKey("folder_uri")
        val deviceId = stringPreferencesKey("device_id")
        val deviceName = stringPreferencesKey("device_name")
        val paired = booleanPreferencesKey("paired")
        val readOwnNext = booleanPreferencesKey("read_own_next")
        val last = stringPreferencesKey("last_result")
        val stamps = stringPreferencesKey("setting_stamps")
        val lastKnown = stringPreferencesKey("setting_last_known")
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val stampsSerializer = MapSerializer(String.serializer(), Long.serializer())
    private val textsSerializer = MapSerializer(String.serializer(), String.serializer())

    val settings: Flow<SyncSettings> = context.syncStore.data.map { p ->
        SyncSettings(
            enabled = p[Keys.enabled] ?: false,
            folderUri = p[Keys.folderUri],
            deviceId = p[Keys.deviceId].orEmpty(),
            deviceName = p[Keys.deviceName] ?: defaultDeviceName(),
            paired = p[Keys.paired] ?: false,
            readOwnNext = p[Keys.readOwnNext] ?: false,
            last = p[Keys.last]?.let { runCatching { json.decodeFromString(SyncResult.serializer(), it) }.getOrNull() },
        )
    }

    suspend fun current(): SyncSettings = settings.first()

    /** This device's id, created the first time it is needed. */
    suspend fun deviceId(): String {
        context.syncStore.data.first()[Keys.deviceId]?.let { return it }
        var id = ""
        context.syncStore.edit { p -> id = p[Keys.deviceId] ?: newId().also { p[Keys.deviceId] = it } }
        return id
    }

    suspend fun setEnabled(value: Boolean) = context.syncStore.edit { it[Keys.enabled] = value }
    suspend fun setFolder(uri: String?) = context.syncStore.edit { if (uri == null) it.remove(Keys.folderUri) else it[Keys.folderUri] = uri }
    suspend fun setDeviceName(name: String) = context.syncStore.edit { it[Keys.deviceName] = name.trim().ifEmpty { defaultDeviceName() } }
    suspend fun setPaired(value: Boolean) = context.syncStore.edit { it[Keys.paired] = value }
    suspend fun setReadOwnNext(value: Boolean) = context.syncStore.edit { it[Keys.readOwnNext] = value }
    suspend fun setLast(result: SyncResult) = context.syncStore.edit { it[Keys.last] = json.encodeToString(SyncResult.serializer(), result) }

    /** "This is that device": takes its id and name. */
    suspend fun adoptIdentity(deviceId: String, deviceName: String) = context.syncStore.edit {
        it[Keys.deviceId] = deviceId
        it[Keys.deviceName] = deviceName
    }

    /** "New device, from scratch": a fresh id. */
    suspend fun newIdentity() = context.syncStore.edit { it[Keys.deviceId] = newId() }

    // ---- when each shared setting was last changed ---------------------------------------------------------------

    /**
     * Compares [current] with what was seen last time: a setting that changed is stamped [now]; one that was never
     * changed here but is not at its default is stamped 1 (it travels, and loses against any real edit).
     */
    suspend fun refreshStamps(current: Map<String, String>, defaults: Map<String, String>, now: Long): Map<String, Long> {
        var result: Map<String, Long> = emptyMap()
        context.syncStore.edit { p ->
            val stamps = p[Keys.stamps]?.let { runCatching { json.decodeFromString(stampsSerializer, it) }.getOrNull() }.orEmpty().toMutableMap()
            // First look ever: what the user set before the sync existed is "old" (stamp 1), not "just changed"
            val stampNow = if (p[Keys.lastKnown] == null) 1L else now
            val known = p[Keys.lastKnown]?.let { runCatching { json.decodeFromString(textsSerializer, it) }.getOrNull() }.orEmpty().toMutableMap()
            for ((key, value) in current) {
                val before = known[key] ?: defaults[key]
                if (value != before) { stamps[key] = stampNow; known[key] = value }
                else if (key !in stamps && value != defaults[key]) stamps[key] = 1
            }
            p[Keys.stamps] = json.encodeToString(stampsSerializer, stamps)
            p[Keys.lastKnown] = json.encodeToString(textsSerializer, known)
            result = stamps
        }
        return result
    }

    /** Settings that arrived from another device: value, time and "last seen" move together, so they are not sent back as a change. */
    suspend fun recordApplied(applied: Map<String, StampedValue>) {
        if (applied.isEmpty()) return
        context.syncStore.edit { p ->
            val stamps = p[Keys.stamps]?.let { runCatching { json.decodeFromString(stampsSerializer, it) }.getOrNull() }.orEmpty().toMutableMap()
            val known = p[Keys.lastKnown]?.let { runCatching { json.decodeFromString(textsSerializer, it) }.getOrNull() }.orEmpty().toMutableMap()
            for ((key, v) in applied) { stamps[key] = v.updatedAt; known[key] = v.value }
            p[Keys.stamps] = json.encodeToString(stampsSerializer, stamps)
            p[Keys.lastKnown] = json.encodeToString(textsSerializer, known)
        }
    }

    suspend fun stamps(): Map<String, Long> =
        context.syncStore.data.first()[Keys.stamps]?.let { runCatching { json.decodeFromString(stampsSerializer, it) }.getOrNull() }.orEmpty()

    private fun newId() = UUID.randomUUID().toString().replace("-", "")

    private fun defaultDeviceName(): String = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android"
}
