package com.example

import android.content.Context

object DnsPreferences {
    private const val PREFS_NAME = "dns_controller_prefs"
    private const val KEY_ENGINE_MODE = "engine_mode"
    private const val KEY_ACTIVE_LABEL = "active_label"
    private const val KEY_ACTIVE_HOSTNAME = "active_hostname"
    private const val KEY_ACTIVE_IP = "active_ip"
    private const val KEY_DEFAULTS_POPULATED = "defaults_populated"

    const val ENGINE_VPN = "VPN"
    const val ENGINE_PRIVATE_DNS = "PRIVATE_DNS"

    fun areDefaultsPopulated(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DEFAULTS_POPULATED, false)
    }

    fun setDefaultsPopulated(context: Context, populated: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DEFAULTS_POPULATED, populated).apply()
    }

    fun getEngineMode(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ENGINE_MODE, ENGINE_VPN) ?: ENGINE_VPN
    }

    fun setEngineMode(context: Context, mode: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ENGINE_MODE, mode).apply()
    }

    fun getActiveDns(context: Context): DnsServer? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hostname = prefs.getString(KEY_ACTIVE_HOSTNAME, null) ?: return null
        val label = prefs.getString(KEY_ACTIVE_LABEL, "") ?: ""
        val ip = prefs.getString(KEY_ACTIVE_IP, "") ?: ""
        return DnsServer(label = label, hostname = hostname, ipAddress = ip)
    }

    fun setActiveDns(context: Context, server: DnsServer?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (server == null) {
            prefs.edit()
                .remove(KEY_ACTIVE_LABEL)
                .remove(KEY_ACTIVE_HOSTNAME)
                .remove(KEY_ACTIVE_IP)
                .apply()
        } else {
            prefs.edit()
                .putString(KEY_ACTIVE_LABEL, server.label)
                .putString(KEY_ACTIVE_HOSTNAME, server.hostname)
                .putString(KEY_ACTIVE_IP, server.ipAddress)
                .apply()
        }
    }
}
