package com.example

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

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

    private val _firstPacketSize = MutableStateFlow(40)
    val firstPacketSize: StateFlow<Int> = _firstPacketSize.asStateFlow()

    private val _enableFakePacket = MutableStateFlow(false)
    val enableFakePacket: StateFlow<Boolean> = _enableFakePacket.asStateFlow()

    private val _enableCaseSpoof = MutableStateFlow(false)
    val enableCaseSpoof: StateFlow<Boolean> = _enableCaseSpoof.asStateFlow()

    private val _enableDoh = MutableStateFlow(false)
    val enableDoh: StateFlow<Boolean> = _enableDoh.asStateFlow()

    private val _socks5Enabled = MutableStateFlow(false)
    val socks5Enabled: StateFlow<Boolean> = _socks5Enabled.asStateFlow()

    private val _socks5Host = MutableStateFlow("127.0.0.1")
    val socks5Host: StateFlow<String> = _socks5Host.asStateFlow()

    private val _socks5Port = MutableStateFlow(1080)
    val socks5Port: StateFlow<Int> = _socks5Port.asStateFlow()

    private val _routingMode = MutableStateFlow("ALL")
    val routingMode: StateFlow<String> = _routingMode.asStateFlow()

    private val _routingApps = MutableStateFlow<Set<String>>(emptySet())
    val routingApps: StateFlow<Set<String>> = _routingApps.asStateFlow()

    private val _wgWarpEnabled = MutableStateFlow(false)
    val wgWarpEnabled: StateFlow<Boolean> = _wgWarpEnabled.asStateFlow()

    private val _wgPrivateKey = MutableStateFlow("")
    val wgPrivateKey: StateFlow<String> = _wgPrivateKey.asStateFlow()

    private val _wgClientIp = MutableStateFlow("172.16.0.2")
    val wgClientIp: StateFlow<String> = _wgClientIp.asStateFlow()

    private val _wgPeerPubkey = MutableStateFlow("bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wQrgyo=")
    val wgPeerPubkey: StateFlow<String> = _wgPeerPubkey.asStateFlow()

    private val _wgEndpoint = MutableStateFlow("162.159.193.1:2408")
    val wgEndpoint: StateFlow<String> = _wgEndpoint.asStateFlow()

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

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
        loadInstalledApps()
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
        _firstPacketSize.value = DnsPreferences.getFirstPacketSize(context)
        _enableFakePacket.value = DnsPreferences.getEnableFakePacket(context)
        _enableCaseSpoof.value = DnsPreferences.getEnableCaseSpoof(context)
        _enableDoh.value = DnsPreferences.getEnableDoh(context)
        _socks5Enabled.value = DnsPreferences.getSocks5Enabled(context)
        _socks5Host.value = DnsPreferences.getSocks5Host(context)
        _socks5Port.value = DnsPreferences.getSocks5Port(context)
        _routingMode.value = DnsPreferences.getRoutingMode(context)
        val savedApps = DnsPreferences.getRoutingApps(context)
        _routingApps.value = if (savedApps.isEmpty()) emptySet() else savedApps.split(",").toSet()

        _wgWarpEnabled.value = DnsPreferences.getWgWarpEnabled(context)
        _wgPrivateKey.value = DnsPreferences.getWgPrivateKey(context)
        _wgClientIp.value = DnsPreferences.getWgClientIp(context)
        _wgPeerPubkey.value = DnsPreferences.getWgPeerPubkey(context)
        _wgEndpoint.value = DnsPreferences.getWgEndpoint(context)
    }

    fun setWgWarpEnabled(enable: Boolean) {
        DnsPreferences.setWgWarpEnabled(context, enable)
        _wgWarpEnabled.value = enable
        restartVpnIfRunning()
    }

    fun setWgPrivateKey(key: String) {
        DnsPreferences.setWgPrivateKey(context, key)
        _wgPrivateKey.value = key
        restartVpnIfRunning()
    }

    fun setWgClientIp(ip: String) {
        DnsPreferences.setWgClientIp(context, ip)
        _wgClientIp.value = ip
        restartVpnIfRunning()
    }

    fun setWgPeerPubkey(key: String) {
        DnsPreferences.setWgPeerPubkey(context, key)
        _wgPeerPubkey.value = key
        restartVpnIfRunning()
    }

    fun setWgEndpoint(endpoint: String) {
        DnsPreferences.setWgEndpoint(context, endpoint)
        _wgEndpoint.value = endpoint
        restartVpnIfRunning()
    }

    fun setEnableDoh(enable: Boolean) {
        DnsPreferences.setEnableDoh(context, enable)
        _enableDoh.value = enable
        restartVpnIfRunning()
    }

    fun setSocks5Enabled(enable: Boolean) {
        DnsPreferences.setSocks5Enabled(context, enable)
        _socks5Enabled.value = enable
        restartVpnIfRunning()
    }

    fun setSocks5Host(host: String) {
        DnsPreferences.setSocks5Host(context, host)
        _socks5Host.value = host
        restartVpnIfRunning()
    }

    fun setSocks5Port(port: Int) {
        DnsPreferences.setSocks5Port(context, port)
        _socks5Port.value = port
        restartVpnIfRunning()
    }

    fun setFirstPacketSize(size: Int) {
        DnsPreferences.setFirstPacketSize(context, size)
        _firstPacketSize.value = size
        restartVpnIfRunning()
    }

    fun setEnableFakePacket(enable: Boolean) {
        DnsPreferences.setEnableFakePacket(context, enable)
        _enableFakePacket.value = enable
        restartVpnIfRunning()
    }

    fun setEnableCaseSpoof(enable: Boolean) {
        DnsPreferences.setEnableCaseSpoof(context, enable)
        _enableCaseSpoof.value = enable
        restartVpnIfRunning()
    }

    fun setRoutingMode(mode: String) {
        DnsPreferences.setRoutingMode(context, mode)
        _routingMode.value = mode
        restartVpnIfRunning()
    }

    fun toggleAppRouting(packageName: String) {
        val currentSet = _routingApps.value.toMutableSet()
        if (currentSet.contains(packageName)) {
            currentSet.remove(packageName)
        } else {
            currentSet.add(packageName)
        }
        _routingApps.value = currentSet
        DnsPreferences.setRoutingApps(context, currentSet.joinToString(","))
        restartVpnIfRunning()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val packages = pm.getInstalledPackages(android.content.pm.PackageManager.GET_META_DATA)
                val apps = packages.mapNotNull { pkg ->
                    val appInfo = pkg.applicationInfo ?: return@mapNotNull null
                    val label = pm.getApplicationLabel(appInfo).toString()
                    val packageName = pkg.packageName
                    val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                    val icon = pm.getApplicationIcon(appInfo)
                    
                    // Filter out our own package to prevent self-proxy loop
                    if (packageName == context.packageName) return@mapNotNull null
                    
                    AppInfo(label = label, packageName = packageName, isSystem = isSystem, icon = icon)
                }.sortedBy { it.label.lowercase() }
                _installedApps.value = apps
            } catch (e: Exception) {
            }
        }
    }

    private fun restartVpnIfRunning() {
        if (_engineMode.value == DnsPreferences.ENGINE_VPN) {
            val active = DnsPreferences.getActiveDns(context)
            if (active != null) {
                activateDns(active)
            }
        }
    }

    fun generateWarpProfile(onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val config = WarpManager.generateConfig()
                WarpDataStore.saveConfig(context, config)
                
                DnsPreferences.setWgWarpEnabled(context, true)
                _wgWarpEnabled.value = true
                
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    refreshStatus()
                    onSuccess()
                }
            } catch (e: Exception) {
                onError("Ошибка при генерации профиля: ${e.localizedMessage}")
            }
        }
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

data class AppInfo(
    val label: String,
    val packageName: String,
    val isSystem: Boolean,
    val icon: android.graphics.drawable.Drawable
)
