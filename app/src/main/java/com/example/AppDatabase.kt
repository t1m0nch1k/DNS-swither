package com.example

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [DnsServer::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dnsDao(): DnsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dns_database"
                )
                .fallbackToDestructiveMigration()
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        INSTANCE?.let { database ->
                            CoroutineScope(Dispatchers.IO).launch {
                                database.dnsDao().insertServer(DnsServer(label = "Cloudflare", hostname = "1dot1dot1dot1.cloudflare-dns.com", ipAddress = "1.1.1.1"))
                                database.dnsDao().insertServer(DnsServer(label = "Google", hostname = "dns.google", ipAddress = "8.8.8.8"))
                                database.dnsDao().insertServer(DnsServer(label = "AdGuard", hostname = "dns.adguard-dns.com", ipAddress = "94.140.14.14"))
                                database.dnsDao().insertServer(DnsServer(label = "Quad9", hostname = "dns.quad9.net", ipAddress = "9.9.9.9"))
                                database.dnsDao().insertServer(DnsServer(label = "CleanBrowsing", hostname = "security-filter-dns.cleanbrowsing.org", ipAddress = "185.228.168.9"))
                            }
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
