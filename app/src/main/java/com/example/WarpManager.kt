package com.example

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.KeyPairGenerator

data class WarpConfig(val privateKey: String, val endpoint: String, val peerPublicKey: String, val clientIp: String)

object WarpManager {
    private val client = OkHttpClient()

    suspend fun generateConfig(): WarpConfig = withContext(Dispatchers.IO) {
        val kpg = KeyPairGenerator.getInstance("X25519")
        val kp = kpg.generateKeyPair()
        val privateKey = kp.private.encoded.takeLast(32).toByteArray()
        val publicKey = kp.public.encoded.takeLast(32).toByteArray()
        val privBase64 = Base64.encodeToString(privateKey, Base64.NO_WRAP)
        val pubBase64 = Base64.encodeToString(publicKey, Base64.NO_WRAP)

        val json = JSONObject().apply {
            put("key", pubBase64)
            put("install_id", "")
            put("warp_enabled", true)
            put("tos", "2020-05-18T00:00:00.000+02:00")
            put("type", "Android")
        }

        val request = Request.Builder()
            .url("https://api.cloudflareclient.com/v0a1922/reg")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Failed to register: ${response.code}")
            val bodyStr = response.body?.string() ?: throw Exception("Empty response body")
            val body = JSONObject(bodyStr)
            
            val config = body.optJSONObject("config")
            val intf = config?.optJSONObject("interface")
            val addresses = intf?.optJSONArray("addresses")
            val v4 = addresses?.optString(0) ?: "172.16.0.2"
            
            val peer = body.optJSONObject("peer")
            val peerPub = peer?.optString("public_key") ?: "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wQrgyo="
            
            WarpConfig(privBase64, "engage.cloudflareclient.com:2408", peerPub, v4.split("/")[0])
        }
    }
}
