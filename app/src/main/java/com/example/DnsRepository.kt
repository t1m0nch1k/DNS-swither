package com.example

import kotlinx.coroutines.flow.Flow

class DnsRepository(private val dnsDao: DnsDao) {
    val allServers: Flow<List<DnsServer>> = dnsDao.getAllServers()

    suspend fun insert(server: DnsServer) {
        dnsDao.insertServer(server)
    }

    suspend fun deleteById(id: Int) {
        dnsDao.deleteServerById(id)
    }
}
