package com.example

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class DnsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "com.example.ACTION_DNS_SWITCH") {
            val hostname = intent.getStringExtra("EXTRA_HOSTNAME")
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, DnsWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)

            CoroutineScope(Dispatchers.IO).launch {
                if (hostname == null) {
                    DnsController.deactivateDns(context)
                } else {
                    val db = AppDatabase.getDatabase(context)
                    val repository = DnsRepository(db.dnsDao())
                    val servers = repository.allServers.firstOrNull() ?: emptyList()
                    val server = servers.find { it.hostname == hostname }
                    if (server != null) {
                        val engineMode = DnsPreferences.getEngineMode(context)
                        DnsController.activateDns(context, server, engineMode)
                    }
                }
                
                for (appWidgetId in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, appWidgetId)
                }
            }
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.dns_widget)
        val activeDns = DnsPreferences.getActiveDns(context)
        val currentDns = activeDns?.hostname

        CoroutineScope(Dispatchers.IO).launch {
            val repository = DnsRepository(AppDatabase.getDatabase(context).dnsDao())
            val servers = repository.allServers.firstOrNull() ?: emptyList()

            views.setTextViewText(R.id.widget_btn_off, if (currentDns == null) "[ OFF ]" else "OFF")
            val offIntent = Intent(context, DnsWidgetProvider::class.java).apply {
                action = "com.example.ACTION_DNS_SWITCH"
            }
            val offPendingIntent = PendingIntent.getBroadcast(context, 0, offIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_btn_off, offPendingIntent)
            
            val buttons = listOf(R.id.widget_btn_1, R.id.widget_btn_2, R.id.widget_btn_3)
            
            for (i in buttons.indices) {
                if (i < servers.size) {
                    val server = servers[i]
                    views.setViewVisibility(buttons[i], android.view.View.VISIBLE)
                    val label = if (server.hostname == currentDns) "[ ${server.label} ]" else server.label
                    views.setTextViewText(buttons[i], label)
                    
                    val btnIntent = Intent(context, DnsWidgetProvider::class.java).apply {
                        action = "com.example.ACTION_DNS_SWITCH"
                        putExtra("EXTRA_HOSTNAME", server.hostname)
                    }
                    val btnPendingIntent = PendingIntent.getBroadcast(context, i + 1, btnIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    views.setOnClickPendingIntent(buttons[i], btnPendingIntent)
                } else {
                    views.setViewVisibility(buttons[i], android.view.View.GONE)
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
