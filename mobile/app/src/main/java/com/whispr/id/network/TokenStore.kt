package com.whispr.id.network

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.whispr.id.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "whispr_prefs")

object TokenStore {
    private val TOKEN_KEY = stringPreferencesKey("auth_token")
    private val BASE_URL_KEY = stringPreferencesKey("base_url")
    private val ACTIVE_ACCOUNT_ID = stringPreferencesKey("active_account_id")
    private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")

    suspend fun saveToken(context: Context, token: String) {
        context.dataStore.edit { it[TOKEN_KEY] = token }
    }

    suspend fun getToken(context: Context): String? {
        return context.dataStore.data.map { it[TOKEN_KEY] }.first()
    }

    suspend fun clearToken(context: Context) {
        context.dataStore.edit { it.remove(TOKEN_KEY) }
    }

    suspend fun saveBaseUrl(context: Context, url: String) {
        context.dataStore.edit { it[BASE_URL_KEY] = url }
    }

    suspend fun getBaseUrl(context: Context): String? {
        return context.dataStore.data.map { it[BASE_URL_KEY] }.first()
    }

    suspend fun saveActiveAccount(context: Context, id: String) {
        context.dataStore.edit { it[ACTIVE_ACCOUNT_ID] = id }
    }

    suspend fun getActiveAccount(context: Context): String? {
        return context.dataStore.data.map { it[ACTIVE_ACCOUNT_ID] }.first()
    }

    suspend fun saveThemeMode(context: Context, mode: ThemeMode) {
        context.dataStore.edit { it[THEME_MODE_KEY] = mode.name }
    }

    suspend fun getThemeMode(context: Context): ThemeMode {
        return context.dataStore.data.map {
            ThemeMode.valueOf(it[THEME_MODE_KEY] ?: ThemeMode.System.name)
        }.first()
    }
}
