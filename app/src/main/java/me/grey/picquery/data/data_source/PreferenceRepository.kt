package me.grey.picquery.data.data_source

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.grey.picquery.PicQueryApplication.Companion.context
import java.util.UUID

class PreferenceRepository {
    private companion object {
        val ACCEPT_AGREEMENT = booleanPreferencesKey("ACCEPT_AGREEMENT")
        val DEVICE_ID = stringPreferencesKey("DEVICE_ID")
        val MATCH_THRESHOLD = floatPreferencesKey("MATCH_THRESHOLD")
        val RESULT_LIMIT = intPreferencesKey("RESULT_LIMIT")
        val ENABLE_UPLOAD_LOG = booleanPreferencesKey("ENABLE_UPLOAD_LOG")
    }

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

    suspend fun getDeviceId(): String {
        var uuid = context.dataStore.data
            .map { preferences ->
                preferences[DEVICE_ID] ?: "unknown UUID"
            }.first()
        if (uuid == "unknown UUID") {
            uuid = UUID.randomUUID().toString()
            CoroutineScope(Dispatchers.IO).launch {
                context.dataStore.edit { settings ->
                    settings[DEVICE_ID] = uuid
                }
            }
        }

        return uuid
    }

    fun getDeviceIdFlow(): Flow<String> {
        return context.dataStore.data
            .map { preferences ->
                preferences[DEVICE_ID] ?: "unknown UUID"
            }
    }

    fun getAgreement(): Flow<Boolean> {
        return context.dataStore.data
            .map { preferences ->
                preferences[ACCEPT_AGREEMENT] ?: false
            }
    }

    suspend fun acceptAgreement() {
        context.dataStore.edit { settings ->
            settings[ACCEPT_AGREEMENT] = true
            settings[ENABLE_UPLOAD_LOG] = true
        }
    }

    suspend fun setEnableUploadLog(enable: Boolean) {
        context.dataStore.edit { settings ->
            settings[ENABLE_UPLOAD_LOG] = enable
        }
    }

    fun getEnableUploadLog(): Flow<Boolean> {
        return context.dataStore.data
            .map { preferences ->
                preferences[ENABLE_UPLOAD_LOG] ?: true
            }
    }
}
