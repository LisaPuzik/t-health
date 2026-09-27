package com.thealth.app.data

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("thealth")

// Токены + флаги онбординга. deviceId = ANDROID_ID (один на устройство).
class AuthStore(private val context: Context) {
    private val K_ACCESS = stringPreferencesKey("access")
    private val K_REFRESH = stringPreferencesKey("refresh")
    private val K_USER_ID = intPreferencesKey("user_id")
    private val K_ONBOARD = booleanPreferencesKey("onboarded")
    private val K_PRIV_ACTIVITY = booleanPreferencesKey("priv_activity")
    private val K_PRIV_MOOD = booleanPreferencesKey("priv_mood")
    private val K_PRIV_SYNC = booleanPreferencesKey("priv_sync")
    private val K_SENSOR_ASKED = booleanPreferencesKey("sensor_asked")
    private val K_STEP_BASE_DAY = stringPreferencesKey("step_base_day")
    private val K_STEP_BASE_VAL = floatPreferencesKey("step_base_val")
    private val K_STEP_UP_HOUR = stringPreferencesKey("step_up_hour")
    private val K_STEP_UP_VAL = floatPreferencesKey("step_up_val")
    private val K_LAST_SYNC = longPreferencesKey("last_health_sync_ms")
    private val K_LAST_FEED_SEEN = longPreferencesKey("last_feed_seen_ms")

    @SuppressLint("HardwareIds")
    fun deviceId(): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() } ?: "android-unknown"

    suspend fun saveSession(auth: AuthResponse) {
        context.store.edit {
            it[K_ACCESS] = auth.accessToken
            it[K_REFRESH] = auth.refreshToken
            it[K_USER_ID] = auth.userId
        }
    }

    suspend fun access(): String? = context.store.data.map { it[K_ACCESS] }.first()
    suspend fun refresh(): String? = context.store.data.map { it[K_REFRESH] }.first()
    suspend fun userId(): Int? = context.store.data.map { it[K_USER_ID] }.first()

    suspend fun setTokens(access: String, refresh: String) {
        context.store.edit { it[K_ACCESS] = access; it[K_REFRESH] = refresh }
    }

    suspend fun clear() {
        context.store.edit { it.remove(K_ACCESS); it.remove(K_REFRESH); it.remove(K_USER_ID) }
    }

    suspend fun isOnboarded(): Boolean =
        context.store.data.map { it[K_ONBOARD] == true }.first()

    suspend fun setOnboarded() = context.store.edit { it[K_ONBOARD] = true }

    suspend fun privacy(): Triple<Boolean, Boolean, Boolean> =
        context.store.data.map {
            Triple(it[K_PRIV_ACTIVITY] ?: true, it[K_PRIV_MOOD] ?: false, it[K_PRIV_SYNC] ?: true)
        }.first()

    suspend fun savePrivacy(activity: Boolean, mood: Boolean, sync: Boolean) =
        context.store.edit { it[K_PRIV_ACTIVITY] = activity; it[K_PRIV_MOOD] = mood; it[K_PRIV_SYNC] = sync }

    suspend fun sensorAsked(): Boolean =
        context.store.data.map { it[K_SENSOR_ASKED] == true }.first()

    suspend fun setSensorAsked() = context.store.edit { it[K_SENSOR_ASKED] = true }

    suspend fun stepBaseline(): Pair<String?, Float?> =
        context.store.data.map { it[K_STEP_BASE_DAY] to it[K_STEP_BASE_VAL] }.first()

    suspend fun setStepBaseline(day: String, value: Float) =
        context.store.edit { it[K_STEP_BASE_DAY] = day; it[K_STEP_BASE_VAL] = value }

    suspend fun stepUpload(): Pair<String?, Float?> =
        context.store.data.map { it[K_STEP_UP_HOUR] to it[K_STEP_UP_VAL] }.first()

    suspend fun setStepUpload(hour: String, value: Float) =
        context.store.edit { it[K_STEP_UP_HOUR] = hour; it[K_STEP_UP_VAL] = value }

    suspend fun lastSyncMs(): Long? =
        context.store.data.map { it[K_LAST_SYNC] }.first()

    suspend fun setLastSyncMs(nowMs: Long = System.currentTimeMillis()) =
        context.store.edit { it[K_LAST_SYNC] = nowMs }

    suspend fun lastFeedSeenMs(): Long? =
        context.store.data.map { it[K_LAST_FEED_SEEN] }.first()

    suspend fun setLastFeedSeenMs(nowMs: Long = System.currentTimeMillis()) =
        context.store.edit { it[K_LAST_FEED_SEEN] = nowMs }
}
