package com.whispr.app.network

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "whispr_prefs")

object TokenStore {
    private val TOKEN_KEY = stringPreferencesKey("auth_token")
    private val BASE_URL_KEY = stringPreferencesKey("base_url")
    private val ACTIVE_ACCOUNT_ID = stringPreferencesKey("active_account_id")

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
}
