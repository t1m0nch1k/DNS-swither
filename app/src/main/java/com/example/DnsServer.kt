package com.example

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dns_servers")
data class DnsServer(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val label: String,
    val hostname: String,
    val ipAddress: String = ""
)
