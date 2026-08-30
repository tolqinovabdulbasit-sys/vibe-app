package com.vibeapp.core.crypto

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

val Context.devicePrefs: DataStore<Preferences> by preferencesDataStore(name = "device_identity")

/**
 * Manages this device's unique identity.
 * Device ID is generated once on first launch and persisted in DataStore.
 * It does NOT contain phone numbers, email, or any PII.
 */
@Singleton
class DeviceIdentityManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val DEVICE_ID_KEY = stringPreferencesKey("device_id")
    private val DEVICE_DISPLAY_NAME_KEY = stringPreferencesKey("device_display_name")

    suspend fun getOrCreateDeviceId(): String {
        val existing = context.devicePrefs.data
            .map { it[DEVICE_ID_KEY] }
            .first()

        if (existing != null) return existing

        val newId = UUID.randomUUID().toString()
        context.devicePrefs.edit { prefs ->
            prefs[DEVICE_ID_KEY] = newId
        }
        return newId
    }

    suspend fun getDeviceId(): String? {
        return context.devicePrefs.data
            .map { it[DEVICE_ID_KEY] }
            .first()
    }

    suspend fun getOrCreateDisplayName(): String {
        val existing = context.devicePrefs.data
            .map { it[DEVICE_DISPLAY_NAME_KEY] }
            .first()
        if (existing != null) return existing

        val name = "Phone ${(1..999).random()}"
        context.devicePrefs.edit { prefs ->
            prefs[DEVICE_DISPLAY_NAME_KEY] = name
        }
        return name
    }

    suspend fun setDisplayName(name: String) {
        context.devicePrefs.edit { prefs ->
            prefs[DEVICE_DISPLAY_NAME_KEY] = name
        }
    }
}
