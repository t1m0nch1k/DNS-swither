package com.example

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DnsViewModel(private val repository: DnsRepository, private val context: Context) : ViewModel() {

    private val _servers = MutableStateFlow<List<DnsServer>>(emptyList())
    val servers: StateFlow<List<DnsServer>> = _servers.asStateFlow()

    private val _currentDns = MutableStateFlow<String?>(null)
    val currentDns: StateFlow<String?> = _currentDns.asStateFlow()

    private val _engineMode = MutableStateFlow(DnsPreferences.ENGINE_VPN)
    val engineMode: StateFlow<String> = _engineMode.asStateFlow()

    private val _isDeviceOwner = MutableStateFlow(false)
    val isDeviceOwner: StateFlow<Boolean> = _isDeviceOwner.asStateFlow()

    private val _hasWriteSecure = MutableStateFlow(false)
    val hasWriteSecure: StateFlow<Boolean> = _hasWriteSecure.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _pendingVpnServer = MutableStateFlow<DnsServer?>(null)
    val pendingVpnServer: StateFlow<DnsServer?> = _pendingVpnServer.asStateFlow()

    init {
        viewModelScope.launch {
            repository.allServers.collect { list ->
                _servers.value = list
                if (list.isEmpty() && !DnsPreferences.areDefaultsPopulated(context)) {
                    prepopulateDefaultServers()
                }
            }
        }
        refreshStatus()
    }

    private fun prepopulateDefaultServers() {
        DnsPreferences.setDefaultsPopulated(context, true)
        viewModelScope.launch {
            repository.insert(DnsServer(label = "Cloudflare", hostname = "1dot1dot1dot1.cloudflare-dns.com", ipAddress = "1.1.1.1"))
            repository.insert(DnsServer(label = "Google", hostname = "dns.google", ipAddress = "8.8.8.8"))
            repository.insert(DnsServer(label = "AdGuard", hostname = "dns.adguard-dns.com", ipAddress = "94.140.14.14"))
            repository.insert(DnsServer(label = "Quad9", hostname = "dns.quad9.net", ipAddress = "9.9.9.9"))
            repository.insert(DnsServer(label = "CleanBrowsing", hostname = "security-filter-dns.cleanbrowsing.org", ipAddress = "185.228.168.9"))
        }
    }

    fun refreshStatus() {
        _isDeviceOwner.value = DnsController.isDeviceOwner(context)
        _hasWriteSecure.value = DnsController.hasWriteSecureSettings(context)
        _engineMode.value = DnsPreferences.getEngineMode(context)
        _currentDns.value = DnsController.getActiveDnsHostname(context)
    }

    fun setEngineMode(mode: String) {
        DnsPreferences.setEngineMode(context, mode)
        _engineMode.value = mode
        
        val active = DnsPreferences.getActiveDns(context)
        if (active != null) {
            activateDns(active)
        } else {
            refreshStatus()
        }
    }

    fun addServer(label: String, hostname: String, ipAddress: String) {
        viewModelScope.launch {
            repository.insert(DnsServer(label = label, hostname = hostname, ipAddress = ipAddress))
        }
    }

    fun deleteServer(server: DnsServer) {
        viewModelScope.launch {
            repository.deleteById(server.id)
            val active = DnsPreferences.getActiveDns(context)
            if (active?.hostname == server.hostname) {
                deactivateDns()
            }
        }
    }

    fun clearError() {
        _lastError.value = null
    }

    fun activateDns(server: DnsServer) {
        val mode = DnsPreferences.getEngineMode(context)
        if (mode == DnsPreferences.ENGINE_VPN) {
            val vpnIntent = android.net.VpnService.prepare(context)
            if (vpnIntent != null) {
                _pendingVpnServer.value = server
                return
            }
        }
        val error = DnsController.activateDns(context, server, mode)
        _lastError.value = error
        refreshStatus()
        updateWidgets()
    }

    fun onVpnPermissionGranted() {
        val pending = _pendingVpnServer.value
        _pendingVpnServer.value = null
        if (pending != null) {
            activateDns(pending)
        }
    }

    fun onVpnPermissionDenied() {
        _pendingVpnServer.value = null
        _lastError.value = "Разрешение на создание VPN туннеля не получено."
    }

    fun deactivateDns() {
        DnsController.deactivateDns(context)
        refreshStatus()
        updateWidgets()
    }

    private fun updateWidgets() {
        val intent = android.content.Intent(context, DnsWidgetProvider::class.java).apply {
            action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
        }
        val ids = android.appwidget.AppWidgetManager.getInstance(context).getAppWidgetIds(
            android.content.ComponentName(context, DnsWidgetProvider::class.java)
        )
        intent.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        context.sendBroadcast(intent)
    }
}

class DnsViewModelFactory(private val repository: DnsRepository, private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DnsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DnsViewModel(repository, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
