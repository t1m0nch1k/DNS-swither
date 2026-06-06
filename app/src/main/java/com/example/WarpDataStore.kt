package com.example

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

val Context.dataStore by preferencesDataStore(name = "settings")

object WarpDataStore {
    private val PRIVATE_KEY = stringPreferencesKey("private_key")
    private val ENDPOINT = stringPreferencesKey("endpoint")
    private val PEER_PUB = stringPreferencesKey("peer_pub")
    private val CLIENT_IP = stringPreferencesKey("client_ip")

    suspend fun saveConfig(context: Context, config: WarpConfig) {
        context.dataStore.edit {
            it[PRIVATE_KEY] = config.privateKey
            it[ENDPOINT] = config.endpoint
            it[PEER_PUB] = config.peerPublicKey
            it[CLIENT_IP] = config.clientIp
        }
    }

    suspend fun getConfig(context: Context): WarpConfig? {
        val prefs = context.dataStore.data.first()
        val privateKey = prefs[PRIVATE_KEY] ?: return null
        val endpoint = prefs[ENDPOINT] ?: return null
        val peerPub = prefs[PEER_PUB] ?: return null
        val clientIp = prefs[CLIENT_IP] ?: return null
        return WarpConfig(privateKey, endpoint, peerPub, clientIp)
    }
}
