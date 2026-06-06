package com.example

import android.content.Context

object DnsPreferences {
    private const val PREFS_NAME = "dns_controller_prefs"
    private const val KEY_ENGINE_MODE = "engine_mode"
    private const val KEY_ACTIVE_LABEL = "active_label"
    private const val KEY_ACTIVE_HOSTNAME = "active_hostname"
    private const val KEY_ACTIVE_IP = "active_ip"
    private const val KEY_DEFAULTS_POPULATED = "defaults_populated"
    private const val KEY_FIRST_PACKET_SIZE = "first_packet_size"
    private const val KEY_ENABLE_FAKE_PACKET = "enable_fake_packet"
    private const val KEY_ENABLE_CASE_SPOOF = "enable_case_spoof"
    private const val KEY_ROUTING_MODE = "routing_mode"
    private const val KEY_ROUTING_APPS = "routing_apps"
    private const val KEY_ENABLE_DOH = "enable_doh"
    private const val KEY_SOCKS5_ENABLED = "socks5_enabled"
    private const val KEY_SOCKS5_HOST = "socks5_host"
    private const val KEY_SOCKS5_PORT = "socks5_port"
    
    private const val KEY_WG_WARP_ENABLED = "wg_warp_enabled"
    private const val KEY_WG_PRIVATE_KEY = "wg_private_key"
    private const val KEY_WG_CLIENT_IP = "wg_client_ip"
    private const val KEY_WG_PEER_PUBKEY = "wg_peer_pubkey"
    private const val KEY_WG_ENDPOINT = "wg_endpoint"

    const val ENGINE_VPN = "VPN"
    const val ENGINE_PRIVATE_DNS = "PRIVATE_DNS"

    fun getWgWarpEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_WG_WARP_ENABLED, false)
    }

    fun setWgWarpEnabled(context: Context, enable: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_WG_WARP_ENABLED, enable).apply()
    }

    fun getWgPrivateKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_WG_PRIVATE_KEY, "") ?: ""
    }

    fun setWgPrivateKey(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_WG_PRIVATE_KEY, key).apply()
    }

    fun getWgClientIp(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_WG_CLIENT_IP, "172.16.0.2") ?: "172.16.0.2"
    }

    fun setWgClientIp(context: Context, ip: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_WG_CLIENT_IP, ip).apply()
    }

    fun getWgPeerPubkey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_WG_PEER_PUBKEY, "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wQrgyo=") ?: "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wQrgyo="
    }

    fun setWgPeerPubkey(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_WG_PEER_PUBKEY, key).apply()
    }

    fun getWgEndpoint(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_WG_ENDPOINT, "162.159.193.1:2408") ?: "162.159.193.1:2408"
    }

    fun setWgEndpoint(context: Context, endpoint: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_WG_ENDPOINT, endpoint).apply()
    }

    fun getEnableDoh(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ENABLE_DOH, false)
    }

    fun setEnableDoh(context: Context, enable: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ENABLE_DOH, enable).apply()
    }

    fun getSocks5Enabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SOCKS5_ENABLED, false)
    }

    fun setSocks5Enabled(context: Context, enable: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SOCKS5_ENABLED, enable).apply()
    }

    fun getSocks5Host(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SOCKS5_HOST, "127.0.0.1") ?: "127.0.0.1"
    }

    fun setSocks5Host(context: Context, host: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SOCKS5_HOST, host).apply()
    }

    fun getSocks5Port(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_SOCKS5_PORT, 1080)
    }

    fun setSocks5Port(context: Context, port: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_SOCKS5_PORT, port).apply()
    }

    fun getEnableCaseSpoof(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ENABLE_CASE_SPOOF, false)
    }

    fun setEnableCaseSpoof(context: Context, enable: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ENABLE_CASE_SPOOF, enable).apply()
    }

    fun getRoutingMode(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ROUTING_MODE, "ALL") ?: "ALL"
    }

    fun setRoutingMode(context: Context, mode: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ROUTING_MODE, mode).apply()
    }

    fun getRoutingApps(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ROUTING_APPS, "") ?: ""
    }

    fun setRoutingApps(context: Context, apps: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ROUTING_APPS, apps).apply()
    }

    fun getFirstPacketSize(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_FIRST_PACKET_SIZE, 40)
    }

    fun setFirstPacketSize(context: Context, size: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_FIRST_PACKET_SIZE, size).apply()
    }

    fun getEnableFakePacket(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ENABLE_FAKE_PACKET, false)
    }

    fun setEnableFakePacket(context: Context, enable: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ENABLE_FAKE_PACKET, enable).apply()
    }

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
