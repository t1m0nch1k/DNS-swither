package com.example

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DnsDao {
    @Query("SELECT * FROM dns_servers ORDER BY id ASC")
    fun getAllServers(): Flow<List<DnsServer>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: DnsServer)

    @Query("DELETE FROM dns_servers WHERE id = :id")
    suspend fun deleteServerById(id: Int)
}
