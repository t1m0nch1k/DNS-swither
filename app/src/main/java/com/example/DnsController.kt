package com.example

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

object DnsController {

    fun activateDns(context: Context, server: DnsServer, engineMode: String): String? {
        DnsPreferences.setActiveDns(context, server)
        
        if (engineMode == DnsPreferences.ENGINE_VPN) {
            setPrivateDnsModeOff(context)
            val intent = Intent(context, DnsVpnService::class.java).apply {
                action = DnsVpnService.ACTION_START
                putExtra(DnsVpnService.EXTRA_DNS_IP, server.ipAddress)
            }
            context.startService(intent)
            return null
        } else {
            val intent = Intent(context, DnsVpnService::class.java).apply {
                action = DnsVpnService.ACTION_STOP
            }
            context.startService(intent)
            return setPrivateDns(context, server.hostname)
        }
    }

    fun deactivateDns(context: Context) {
        DnsPreferences.setActiveDns(context, null)
        
        val intent = Intent(context, DnsVpnService::class.java).apply {
            action = DnsVpnService.ACTION_STOP
        }
        context.startService(intent)
        setPrivateDnsModeOff(context)
    }

    fun isDeviceOwner(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return false
        return dpm.isDeviceOwnerApp(context.packageName)
    }

    fun hasWriteSecureSettings(context: Context): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
    }

    fun canManageDns(context: Context): Boolean {
        return isDeviceOwner(context) || hasWriteSecureSettings(context)
    }

    fun getActiveDnsHostname(context: Context): String? {
        val active = DnsPreferences.getActiveDns(context)
        return active?.hostname
    }

    private fun setPrivateDnsModeOff(context: Context) {
        setPrivateDns(context, null)
    }

    private fun setPrivateDns(context: Context, hostname: String?): String? {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return "DevicePolicyManager not available"
        val adminComponent = ComponentName(context, MyDeviceAdminReceiver::class.java)

        val mode = if (hostname == null) "off" else "hostname"
        val specifier = hostname ?: ""

        if (dpm.isDeviceOwnerApp(context.packageName)) {
            try {
                dpm.setGlobalSetting(adminComponent, "private_dns_mode", mode)
                dpm.setGlobalSetting(adminComponent, "private_dns_specifier", specifier)
                return null
            } catch (e: Exception) {
                return "DeviceOwner write failed: ${e.localizedMessage}"
            }
        }

        try {
            Settings.Global.putString(context.contentResolver, "private_dns_mode", mode)
            Settings.Global.putString(context.contentResolver, "private_dns_specifier", specifier)
            return null
        } catch (e: Exception) {
            return "Secure Settings privileges required for Private DNS mode."
        }
    }
}
