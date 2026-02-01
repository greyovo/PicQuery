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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import me.grey.picquery.PicQueryApplication.Companion.context

class PreferenceRepository {
    private companion object {
        val ACCEPT_AGREEMENT = booleanPreferencesKey("ACCEPT_AGREEMENT")
        val DEVICE_ID = stringPreferencesKey("DEVICE_ID")
        val ENABLE_UPLOAD_LOG = booleanPreferencesKey("ENABLE_UPLOAD_LOG")
        val SEARCH_MATCH_THRESHOLD = floatPreferencesKey("SEARCH_MATCH_THRESHOLD")
        val SEARCH_TOP_K = intPreferencesKey("SEARCH_TOP_K")
        val USER_GUIDE_COMPLETED = booleanPreferencesKey("USER_GUIDE_COMPLETED")
    }

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

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

    suspend fun acceptAgreement(enableUploadLog: Boolean = true) {
        context.dataStore.edit { settings ->
            settings[ACCEPT_AGREEMENT] = true
            settings[ENABLE_UPLOAD_LOG] = enableUploadLog
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

    suspend fun saveSearchConfiguration(matchThreshold: Float, topK: Int) {
        context.dataStore.edit { settings ->
            settings[SEARCH_MATCH_THRESHOLD] = matchThreshold
            settings[SEARCH_TOP_K] = topK
        }
    }

    suspend fun loadSearchConfigurationSync(): Pair<Float, Int> {
        val preferences = context.dataStore.data.first()

        val matchThreshold = preferences[SEARCH_MATCH_THRESHOLD] ?: 0.20f
        val topK = preferences[SEARCH_TOP_K] ?: 30

        return Pair(matchThreshold, topK)
    }

    suspend fun isUserGuideCompleted(): Boolean {
        val preferences = context.dataStore.data.first()
        return preferences[USER_GUIDE_COMPLETED] ?: false
    }

    suspend fun setUserGuideCompleted(completed: Boolean) {
        context.dataStore.edit { settings ->
            settings[USER_GUIDE_COMPLETED] = completed
        }
    }
}
