package com.example

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class DnsVpnService : VpnService() {
    companion object {
        const val ACTION_START = "com.example.START_VPN"
        const val ACTION_STOP = "com.example.STOP_VPN"
        const val EXTRA_DNS_IP = "com.example.EXTRA_DNS_IP"
        private var isRunning = false
        fun isServiceRunning() = isRunning
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverSocket: ServerSocket? = null
    private val natMap = ConcurrentHashMap<Int, Boolean>()

    private var socks5Enabled = false
    private var socks5Host = "127.0.0.1"
    private var socks5Port = 1080
    private var enableDoh = false

    private var wgWarpEnabled = false
    private var wgPrivateKey = ""
    private var wgClientIp = "172.16.0.2"
    private var wgPeerPubkey = "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wQrgyo="
    private var wgEndpoint = "162.159.193.1:2408"
    private var wgTunnel: WireguardTunnel? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopVpn()
            stopSelf()
        } else if (action == ACTION_START) {
            val dnsIp = intent.getStringExtra(EXTRA_DNS_IP) ?: "8.8.8.8"
            startVpn(dnsIp)
        }
        return START_STICKY
    }

    private fun startVpn(dnsIp: String) {
        stopVpn()
        isRunning = true
        vpnScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        try {
            val builder = Builder()
            builder.setSession("DNS Dynamic Split Tunnel")
            builder.addAddress("10.0.0.1", 24)
            builder.addDnsServer(dnsIp)
            builder.addRoute(dnsIp, 32)
            builder.addRoute("10.0.0.2", 32)

            // Add standard public DNS list to catch direct queries
            listOf("8.8.8.8", "8.8.4.4", "1.1.1.1", "1.0.0.1", "77.88.8.8").forEach { ip ->
                try {
                    builder.addRoute(ip, 32)
                } catch (e: Exception) {}
            }

            // Dynamically discover active system DNS servers and add routes for them
            try {
                val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                if (connectivityManager != null) {
                    val activeNetwork = connectivityManager.activeNetwork
                    val linkProperties = connectivityManager.getLinkProperties(activeNetwork)
                    linkProperties?.dnsServers?.forEach { dnsAddr ->
                        val ipStr = dnsAddr.hostAddress
                        if (ipStr != null && !ipStr.contains(":")) { // IPv4 DNS only
                            try {
                                builder.addRoute(ipStr, 32)
                            } catch (e: Exception) {}
                        }
                    }
                }
            } catch (e: Exception) {}

            // Prevent IPv6 leaks: route ::/0 into the TUN interface where it goes unhandled (dropped)
            try {
                builder.addRoute("::", 0)
            } catch (e: Exception) {}

            val sharedPrefs = getSharedPreferences("dns_controller_prefs", Context.MODE_PRIVATE)
            socks5Enabled = sharedPrefs.getBoolean("socks5_enabled", false)
            socks5Host = sharedPrefs.getString("socks5_host", "127.0.0.1") ?: "127.0.0.1"
            socks5Port = sharedPrefs.getInt("socks5_port", 1080)
            enableDoh = sharedPrefs.getBoolean("enable_doh", false)

            wgWarpEnabled = sharedPrefs.getBoolean("wg_warp_enabled", false)

            if (wgWarpEnabled) {
                vpnScope.launch {
                    try {
                        val warpConfig = WarpDataStore.getConfig(this@DnsVpnService)
                        if (warpConfig != null && warpConfig.privateKey.isNotEmpty()) {
                            try {
                                wgTunnel = WireguardTunnel(warpConfig.privateKey, warpConfig.clientIp, warpConfig.peerPublicKey, warpConfig.endpoint)
                                wgTunnel?.start(this@DnsVpnService)
                                android.util.Log.i("DnsVpnService", "Started WireGuard WARP")
                            } catch (e: Exception) {
                                android.util.Log.e("DnsVpnService", "Failed to create/start tunnel: ${e.localizedMessage}")
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("DnsVpnService", "Failed starting WireGuard: ${e.localizedMessage}")
                    }
                }
            }

            val routingMode = sharedPrefs.getString("routing_mode", "ALL") ?: "ALL"
            val routingAppsStr = sharedPrefs.getString("routing_apps", "") ?: ""
            val routingApps = if (routingAppsStr.isEmpty()) emptySet() else routingAppsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

            if (routingMode == "SELECTED" && routingApps.isNotEmpty()) {
                routingApps.forEach { pkg ->
                    try {
                        builder.addAllowedApplication(pkg)
                    } catch (e: Exception) {
                    }
                }
            } else if (routingMode == "EXCLUDE" && routingApps.isNotEmpty()) {
                routingApps.forEach { pkg ->
                    try {
                        builder.addDisallowedApplication(pkg)
                    } catch (e: Exception) {
                    }
                }
            }
            
            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                isRunning = false
                return
            }

            vpnScope.launch {
                try {
                    serverSocket = ServerSocket(18080, 100, InetAddress.getByName("10.0.0.1"))
                    while (isActive && isRunning) {
                        val clientSocket = serverSocket?.accept() ?: break
                        try {
                            clientSocket.tcpNoDelay = true
                        } catch (e: Exception) {}
                        vpnScope.launch {
                            handleClientProxy(clientSocket)
                        }
                    }
                } catch (e: Exception) {
                }
            }

            vpnScope.launch {
                val fd = vpnInterface ?: return@launch
                val input = FileInputStream(fd.fileDescriptor)
                val output = FileOutputStream(fd.fileDescriptor)
                val packetBuffer = ByteArray(32767)

                while (isActive && isRunning) {
                    try {
                        val length = input.read(packetBuffer)
                        if (length > 0) {
                            handlePacket(packetBuffer.copyOf(length), output, dnsIp)
                        } else if (length < 0) {
                            break
                        }
                    } catch (e: Exception) {
                        break
                    }
                }
            }
        } catch (e: Exception) {
            isRunning = false
        }
    }

    private suspend fun handleClientProxy(clientSocket: Socket) {
        withContext(Dispatchers.IO) {
            try {
                val clientInput = clientSocket.getInputStream()
                val clientOutput = clientSocket.getOutputStream()
                val clientPort = clientSocket.port
                
                val buffer = ByteArray(16384)
                val bytesRead = clientInput.read(buffer)
                if (bytesRead <= 0) {
                    clientSocket.close()
                    return@withContext
                }

                val clientHello = buffer.copyOf(bytesRead)
                val isTls = clientHello.size >= 6 && clientHello[0] == 0x16.toByte() && 
                        clientHello[1] == 0x03.toByte() && clientHello[5] == 0x01.toByte()

                var snHost: String? = null
                if (isTls) {
                    snHost = extractSni(clientHello)
                }

                val targetHost = snHost ?: "10.0.0.1"
                if (targetHost == "10.0.0.1") {
                    clientSocket.close()
                    return@withContext
                }

                val serverSocketToRemote = Socket()
                try {
                    serverSocketToRemote.tcpNoDelay = true
                } catch (e: Exception) {}
                protect(serverSocketToRemote)

                val serverOutput: java.io.OutputStream
                val serverInput: java.io.InputStream

                if (socks5Enabled) {
                    serverSocketToRemote.connect(InetSocketAddress(socks5Host, socks5Port), 10000)
                    val socksOut = serverSocketToRemote.getOutputStream()
                    val socksIn = serverSocketToRemote.getInputStream()

                    socksOut.write(byteArrayOf(0x05, 0x01, 0x00))
                    socksOut.flush()

                    val greeting = ByteArray(2)
                    var totalRead = 0
                    while (totalRead < 2) {
                        val count = socksIn.read(greeting, totalRead, 2 - totalRead)
                        if (count <= 0) throw java.io.IOException("SOCKS5 stream EOF during handshake")
                        totalRead += count
                    }
                    if (greeting[0] != 0x05.toByte() || greeting[1] != 0x00.toByte()) {
                        throw java.io.IOException("SOCKS5 unsupported authentication")
                    }

                    val hostBytes = targetHost.toByteArray(Charsets.UTF_8)
                    val request = ByteArray(7 + hostBytes.size)
                    request[0] = 0x05
                    request[1] = 0x01
                    request[2] = 0x00
                    request[3] = 0x03
                    request[4] = hostBytes.size.toByte()
                    System.arraycopy(hostBytes, 0, request, 5, hostBytes.size)
                    val portOffset = 5 + hostBytes.size
                    request[portOffset] = 0x01.toByte()
                    request[portOffset + 1] = 0xBB.toByte()

                    socksOut.write(request)
                    socksOut.flush()

                    val responseHeader = ByteArray(4)
                    totalRead = 0
                    while (totalRead < 4) {
                        val count = socksIn.read(responseHeader, totalRead, 4 - totalRead)
                        if (count <= 0) throw java.io.IOException("SOCKS5 EOF reading connection response")
                        totalRead += count
                    }
                    if (responseHeader[1] != 0x00.toByte()) {
                        throw java.io.IOException("SOCKS5 proxy refused connection: " + responseHeader[1])
                    }

                    val atyp = responseHeader[3].toInt() and 0xFF
                    val boundLen = when (atyp) {
                        0x01 -> 4 + 2
                        0x04 -> 16 + 2
                        0x03 -> {
                            val domainLen = socksIn.read()
                            if (domainLen < 0) throw java.io.IOException("SOCKS5 EOF reading domain length")
                            domainLen + 2
                        }
                        else -> throw java.io.IOException("SOCKS5 unknown ATYP")
                    }
                    val boundDummy = ByteArray(boundLen)
                    totalRead = 0
                    while (totalRead < boundLen) {
                        val count = socksIn.read(boundDummy, totalRead, boundLen - totalRead)
                        if (count <= 0) throw java.io.IOException("SOCKS5 EOF reading bound address")
                        totalRead += count
                    }

                    serverOutput = socksOut
                    serverInput = socksIn
                } else {
                    serverSocketToRemote.connect(InetSocketAddress(targetHost, 443), 10000)
                    serverOutput = serverSocketToRemote.getOutputStream()
                    serverInput = serverSocketToRemote.getInputStream()
                }

                val sharedPrefs = getSharedPreferences("dns_controller_prefs", Context.MODE_PRIVATE)
                val splitSize = sharedPrefs.getInt("first_packet_size", 40)
                val enableFake = sharedPrefs.getBoolean("enable_fake_packet", false)
                val enableCaseSpoof = sharedPrefs.getBoolean("enable_case_spoof", false)

                val isTarget = snHost != null && (
                    snHost.contains("youtube") || snHost.contains("youtu.be") || 
                    snHost.contains("googlevideo") || snHost.contains("ytimg") || 
                    snHost.contains("ggpht") || snHost.contains("discord") || 
                    snHost.contains("media.discordapp") || snHost.contains("discord-attachments") ||
                    snHost.contains("discordapp")
                )

                var finalHello = clientHello
                if (isTls && snHost != null && enableCaseSpoof) {
                    finalHello = applyCaseSpoof(clientHello, snHost)
                }

                if (isTls && isTarget) {
                    if (enableFake) {
                        val fakeHello = generateFakeTlsHello()
                        serverOutput.write(fakeHello)
                        serverOutput.flush()
                        delay(100)
                    }

                    val safeSplit = if (splitSize in 1 until finalHello.size) splitSize else 40
                    val part1 = finalHello.copyOfRange(0, safeSplit)
                    val part2 = finalHello.copyOfRange(safeSplit, finalHello.size)

                    serverOutput.write(part1)
                    serverOutput.flush()
                    delay(50)
                    serverOutput.write(part2)
                    serverOutput.flush()
                } else {
                    serverOutput.write(finalHello)
                    serverOutput.flush()
                }

                val job1 = launch {
                    try {
                        val rawBuffer = ByteArray(16384)
                        while (true) {
                            val length = clientInput.read(rawBuffer)
                            if (length <= 0) break
                            serverOutput.write(rawBuffer, 0, length)
                            serverOutput.flush()
                        }
                    } catch (e: Exception) {
                    } finally {
                        try { serverSocketToRemote.shutdownOutput() } catch (e: Exception) {}
                    }
                }

                val job2 = launch {
                    try {
                        val rawBuffer = ByteArray(16384)
                        while (true) {
                            val length = serverInput.read(rawBuffer)
                            if (length <= 0) break
                            clientOutput.write(rawBuffer, 0, length)
                            clientOutput.flush()
                        }
                    } catch (e: Exception) {
                    } finally {
                        try { clientSocket.shutdownOutput() } catch (e: Exception) {}
                    }
                }

                job1.join()
                job2.join()

            } catch (e: Exception) {
            } finally {
                try { clientSocket.close() } catch (e: Exception) {}
            }
        }
    }

    private fun extractSni(data: ByteArray): String? {
        try {
            var i = 43
            if (i >= data.size) return null
            val sessionIdLen = data[i].toInt() and 0xFF
            i += 1 + sessionIdLen
            if (i + 2 > data.size) return null
            val cipherSuitesLen = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2 + cipherSuitesLen
            if (i + 1 > data.size) return null
            val compMethodsLen = data[i].toInt() and 0xFF
            i += 1 + compMethodsLen
            if (i + 2 > data.size) return null
            val extensionsLen = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
            val extLimit = i + extensionsLen
            while (i + 4 <= extLimit && i + 4 <= data.size) {
                val extType = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
                val extLen = ((data[i + 2].toInt() and 0xFF) shl 8) or (data[i + 3].toInt() and 0xFF)
                i += 4
                if (extType == 0) {
                    if (i + 2 > data.size) return null
                    val sniListLen = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
                    i += 2
                    if (i + 3 > data.size) return null
                    val sniType = data[i].toInt() and 0xFF
                    val sniLen = ((data[i + 1].toInt() and 0xFF) shl 8) or (data[i + 2].toInt() and 0xFF)
                    i += 3
                    if (sniType == 0 && i + sniLen <= data.size) {
                        return String(data, i, sniLen, Charsets.UTF_8)
                    }
                }
                i += extLen
            }
        } catch (e: Exception) {
        }
        return null
    }

    private fun generateFakeTlsHello(): ByteArray {
        val fakeSni = "www.google.com"
        val sniBytes = fakeSni.toByteArray(Charsets.UTF_8)
        val sniLen = sniBytes.size
        val extLen = 9 + sniLen
        val sessionOffset = 43
        val cipherLen = 2
        val compLen = 1
        val handshakePayloadLen = 4 + 2 + 32 + 1 + sessionOffset - sessionOffset + 2 + cipherLen + 1 + compLen + 2 + 2 + extLen
        val recordLen = handshakePayloadLen + 5
        val p = ByteArray(300)
        var idx = 0
        p[idx++] = 0x16.toByte()
        p[idx++] = 0x03.toByte()
        p[idx++] = 0x01.toByte()
        val recLenPos = idx
        idx += 2
        p[idx++] = 0x01.toByte()
        val hsLenPos = idx
        idx += 3
        p[idx++] = 0x03.toByte()
        p[idx++] = 0x03.toByte()
        for (r in 0 until 32) {
            p[idx++] = 0xAA.toByte()
        }
        p[idx++] = 0x00.toByte()
        p[idx++] = 0x01.toByte()
        p[idx++] = 0x02.toByte()
        p[idx++] = 0x13.toByte()
        p[idx++] = 0x01.toByte()
        p[idx++] = 0x01.toByte()
        p[idx++] = 0x00.toByte()
        val extLenPos = idx
        idx += 2
        p[idx++] = 0x00.toByte()
        p[idx++] = 0x00.toByte()
        val sniExtLenPos = idx
        idx += 2
        val sniListLenPos = idx
        idx += 2
        p[idx++] = 0x00.toByte()
        val sniValLenPos = idx
        idx += 2
        System.arraycopy(sniBytes, 0, p, idx, sniLen)
        idx += sniLen
        val finalLen = idx
        val hsLen = finalLen - 9
        p[5] = ((hsLen shr 16) and 0xFF).toByte()
        p[6] = ((hsLen shr 8) and 0xFF).toByte()
        p[7] = (hsLen and 0xFF).toByte()
        val recLen = finalLen - 5
        p[recLenPos] = ((recLen shr 8) and 0xFF).toByte()
        p[recLenPos + 1] = (recLen and 0xFF).toByte()
        val extTotal = finalLen - extLenPos - 2
        p[extLenPos] = ((extTotal shr 8) and 0xFF).toByte()
        p[extLenPos + 1] = (extTotal and 0xFF).toByte()
        val sniExtVal = finalLen - sniExtLenPos - 2
        p[sniExtLenPos] = ((sniExtVal shr 8) and 0xFF).toByte()
        p[sniExtLenPos + 1] = (sniExtVal and 0xFF).toByte()
        val sniListVal = finalLen - sniListLenPos - 2
        p[sniListLenPos] = ((sniListVal shr 8) and 0xFF).toByte()
        p[sniListLenPos + 1] = (sniListVal and 0xFF).toByte()
        p[sniValLenPos] = ((sniLen shr 8) and 0xFF).toByte()
        p[sniValLenPos + 1] = (sniLen and 0xFF).toByte()
        return p.copyOf(finalLen)
    }

    private fun handlePacket(data: ByteArray, output: FileOutputStream, dnsIp: String) {
        if (data.size < 20) return
        val versionAndIhl = data[0].toInt() and 0xFF
        val version = versionAndIhl shr 4
        if (version != 4) return

        val protocol = data[9].toInt() and 0xFF
        val ipHeaderLen = (versionAndIhl and 0x0F) * 4

        if (protocol == 17) {
            if (data.size < ipHeaderLen + 8) return
            val srcPort = ((data[ipHeaderLen].toInt() and 0xFF) shl 8) or (data[ipHeaderLen + 1].toInt() and 0xFF)
            val dstPort = ((data[ipHeaderLen + 2].toInt() and 0xFF) shl 8) or (data[ipHeaderLen + 3].toInt() and 0xFF)

            if (dstPort == 53) {
                val udpLen = ((data[ipHeaderLen + 4].toInt() and 0xFF) shl 8) or (data[ipHeaderLen + 5].toInt() and 0xFF)
                val dnsPayloadLen = udpLen - 8
                if (dnsPayloadLen <= 0 || ipHeaderLen + 8 + dnsPayloadLen > data.size) return

                val dnsPayload = data.copyOfRange(ipHeaderLen + 8, ipHeaderLen + 8 + dnsPayloadLen)
                val originalSrcIp = data.copyOfRange(12, 16)
                val originalDstIp = data.copyOfRange(16, 20)

                val qName = parseDnsQuestionName(dnsPayload)
                val qType = if (dnsPayload.size >= 12 + qName.length + 4) {
                    var qEnd = 12
                    while (qEnd < dnsPayload.size) {
                        val len = dnsPayload[qEnd].toInt() and 0xFF
                        if (len == 0) {
                            qEnd += 1
                            break
                        }
                        qEnd += 1 + len
                    }
                    if (qEnd + 1 < dnsPayload.size) {
                        ((dnsPayload[qEnd].toInt() and 0xFF) shl 8) or (dnsPayload[qEnd + 1].toInt() and 0xFF)
                    } else 1
                } else 1

                if (shouldSpoof(qName)) {
                    val spoofedResponse = if (qType == 0x001C || qType == 64 || qType == 65) {
                        buildEmptyAaaaDnsResponse(dnsPayload)
                    } else {
                        buildSpoofedDnsResponse(dnsPayload)
                    }
                    val responsePacket = buildUdpPacket(
                        srcIp = data.copyOfRange(16, 20),
                        dstIp = originalSrcIp,
                        srcPort = 53,
                        dstPort = srcPort,
                        payload = spoofedResponse
                    )
                    synchronized(output) {
                        output.write(responsePacket)
                    }
                    return
                }

                vpnScope.launch {
                    try {
                        var responsePayload: ByteArray? = null
                        if (enableDoh) {
                            try {
                                responsePayload = resolveDnsOverHttps(dnsPayload)
                            } catch (e: Exception) {
                            }
                        }

                        if (responsePayload == null) {
                            val socket = DatagramSocket()
                            protect(socket)
                            socket.soTimeout = 3000
                            val sendPacket = DatagramPacket(dnsPayload, dnsPayload.size, InetAddress.getByName(dnsIp), 53)
                            socket.send(sendPacket)

                            val buffer = ByteArray(4096)
                            val recvPacket = DatagramPacket(buffer, buffer.size)
                            socket.receive(recvPacket)
                            responsePayload = recvPacket.data.copyOfRange(0, recvPacket.length)
                        }

                        if (responsePayload != null) {
                            val responsePacket = buildUdpPacket(
                                srcIp = originalDstIp,
                                dstIp = originalSrcIp,
                                srcPort = 53,
                                dstPort = srcPort,
                                payload = responsePayload
                            )

                            synchronized(output) {
                                output.write(responsePacket)
                            }
                        }
                    } catch (e: Exception) {
                    }
                }
            }
        } else if (protocol == 6) {
            if (data.size < ipHeaderLen + 20) return
            val srcPort = ((data[ipHeaderLen].toInt() and 0xFF) shl 8) or (data[ipHeaderLen + 1].toInt() and 0xFF)
            val dstPort = ((data[ipHeaderLen + 2].toInt() and 0xFF) shl 8) or (data[ipHeaderLen + 3].toInt() and 0xFF)

            val dstIpBytes = data.copyOfRange(16, 20)
            val isTargetIp = dstIpBytes[0] == 10.toByte() && dstIpBytes[1] == 0.toByte() && dstIpBytes[2] == 0.toByte() && dstIpBytes[3] == 2.toByte()

            if (dstPort == 43 || dstPort == 443) {
                if (isTargetIp) {
                    natMap[srcPort] = true

                    val rewritten = data.copyOf(data.size)
                    // Change Source IP (bytes 12-15) to 10.0.0.2 (looks like remote peer connecting to local server)
                    rewritten[12] = 10
                    rewritten[13] = 0
                    rewritten[14] = 0
                    rewritten[15] = 2

                    // Change Destination IP (bytes 16-19) to 10.0.0.1 (local ServerSocket destination)
                    rewritten[16] = 10
                    rewritten[17] = 0
                    rewritten[18] = 0
                    rewritten[19] = 1

                    rewritten[ipHeaderLen + 2] = 0x46.toByte()
                    rewritten[ipHeaderLen + 3] = 0xA0.toByte()

                    rewritten[10] = 0
                    rewritten[11] = 0
                    val ipChecksum = computeIpChecksum(rewritten, ipHeaderLen)
                    rewritten[10] = (ipChecksum shr 8).toByte()
                    rewritten[11] = (ipChecksum and 0xFF).toByte()

                    rewritten[ipHeaderLen + 16] = 0
                    rewritten[ipHeaderLen + 17] = 0
                    val tcpLen = data.size - ipHeaderLen
                    val tcpChecksum = computeTcpChecksum(rewritten, ipHeaderLen, tcpLen, rewritten.copyOfRange(12, 16), rewritten.copyOfRange(16, 20))
                    rewritten[ipHeaderLen + 16] = (tcpChecksum shr 8).toByte()
                    rewritten[ipHeaderLen + 17] = (tcpChecksum and 0xFF).toByte()

                    synchronized(output) {
                        output.write(rewritten)
                    }
                }
            } else if (srcPort == 18080) {
                if (natMap[dstPort] == true) {
                    val rewritten = data.copyOf(data.size)
                    // Change Source IP (bytes 12-15) to 10.0.0.2 (so client thinks response comes from the spoofed IP)
                    rewritten[12] = 10
                    rewritten[13] = 0
                    rewritten[14] = 0
                    rewritten[15] = 2

                    // Change Destination IP (bytes 16-19) to 10.0.0.1 (so it reaches local client socket)
                    rewritten[16] = 10
                    rewritten[17] = 0
                    rewritten[18] = 0
                    rewritten[19] = 1

                    rewritten[ipHeaderLen] = 0x01.toByte()
                    rewritten[ipHeaderLen + 1] = 0xBB.toByte()

                    rewritten[10] = 0
                    rewritten[11] = 0
                    val ipChecksum = computeIpChecksum(rewritten, ipHeaderLen)
                    rewritten[10] = (ipChecksum shr 8).toByte()
                    rewritten[11] = (ipChecksum and 0xFF).toByte()

                    rewritten[ipHeaderLen + 16] = 0
                    rewritten[ipHeaderLen + 17] = 0
                    val tcpLen = data.size - ipHeaderLen
                    val tcpChecksum = computeTcpChecksum(rewritten, ipHeaderLen, tcpLen, rewritten.copyOfRange(12, 16), rewritten.copyOfRange(16, 20))
                    rewritten[ipHeaderLen + 16] = (tcpChecksum shr 8).toByte()
                    rewritten[ipHeaderLen + 17] = (tcpChecksum and 0xFF).toByte()

                    synchronized(output) {
                        output.write(rewritten)
                    }
                }
            }
        }
    }

    private fun parseDnsQuestionName(dnsPayload: ByteArray): String {
        val sb = StringBuilder()
        var i = 12
        while (i < dnsPayload.size) {
            val len = dnsPayload[i].toInt() and 0xFF
            if (len == 0) break
            if (sb.isNotEmpty()) sb.append(".")
            if (i + 1 + len > dnsPayload.size) break
            sb.append(String(dnsPayload, i + 1, len, Charsets.US_ASCII))
            i += 1 + len
        }
        return sb.toString()
    }

    private fun shouldSpoof(domain: String): Boolean {
        val d = domain.lowercase()
        return d.contains("youtube") || d.contains("youtu.be") || d.contains("googlevideo") ||
               d.contains("ytimg") || d.contains("ggpht") || d.contains("discord") ||
               d.contains("media.discordapp") || d.contains("discord-attachments") ||
               d.contains("discordapp")
    }

    private fun buildSpoofedDnsResponse(query: ByteArray): ByteArray {
        val response = ByteArray(query.size + 16)
        System.arraycopy(query, 0, response, 0, query.size)
        response[2] = 0x81.toByte()
        response[3] = 0x80.toByte()
        response[6] = 0
        response[7] = 1

        var qEnd = 12
        while (qEnd < query.size) {
            val len = query[qEnd].toInt() and 0xFF
            if (len == 0) {
                qEnd += 1
                break
            }
            qEnd += 1 + len
        }
        val qSectionLen = qEnd + 4 - 12
        val answerOffset = 12 + qSectionLen

        val val1 = 0xC00C.toShort()
        response[answerOffset] = (val1.toInt() shr 8).toByte()
        response[answerOffset + 1] = (val1.toInt() and 0xFF).toByte()

        response[answerOffset + 2] = 0
        response[answerOffset + 3] = 1
        response[answerOffset + 4] = 0
        response[answerOffset + 5] = 1

        response[answerOffset + 6] = 0
        response[answerOffset + 7] = 0
        response[answerOffset + 8] = 0
        response[answerOffset + 9] = 0x3C.toByte()

        response[answerOffset + 10] = 0
        response[answerOffset + 11] = 4

        response[answerOffset + 12] = 10
        response[answerOffset + 13] = 0
        response[answerOffset + 14] = 0
        response[answerOffset + 15] = 2

        return response.copyOf(answerOffset + 16)
    }

    private fun buildEmptyAaaaDnsResponse(query: ByteArray): ByteArray {
        val response = query.copyOf()
        response[2] = 0x81.toByte()
        response[3] = 0x80.toByte()
        return response
    }

    private fun buildUdpPacket(srcIp: ByteArray, dstIp: ByteArray, srcPort: Int, dstPort: Int, payload: ByteArray): ByteArray {
        val ipLen = 20 + 8 + payload.size
        val packet = ByteArray(ipLen)

        packet[0] = 0x45.toByte()
        packet[1] = 0.toByte()
        packet[2] = (ipLen shr 8).toByte()
        packet[3] = (ipLen and 0xFF).toByte()
        packet[4] = 0.toByte()
        packet[5] = 0.toByte()
        packet[6] = 0x40.toByte()
        packet[7] = 0.toByte()
        packet[8] = 64.toByte()
        packet[9] = 17.toByte()
        packet[10] = 0.toByte()
        packet[11] = 0.toByte()
        System.arraycopy(srcIp, 0, packet, 12, 4)
        System.arraycopy(dstIp, 0, packet, 16, 4)

        val ipChecksum = computeIpChecksum(packet, 20)
        packet[10] = (ipChecksum shr 8).toByte()
        packet[11] = (ipChecksum and 0xFF).toByte()

        val udpLen = 8 + payload.size
        packet[20] = (srcPort shr 8).toByte()
        packet[21] = (srcPort and 0xFF).toByte()
        packet[22] = (dstPort shr 8).toByte()
        packet[23] = (dstPort and 0xFF).toByte()
        packet[24] = (udpLen shr 8).toByte()
        packet[25] = (udpLen and 0xFF).toByte()
        packet[26] = 0.toByte()
        packet[27] = 0.toByte()

        System.arraycopy(payload, 0, packet, 28, payload.size)
        return packet
    }

    private fun computeIpChecksum(data: ByteArray, length: Int): Int {
        var sum = 0
        var i = 0
        while (i < length - 1) {
            val word = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.inv() and 0xFFFF)
    }

    private fun computeTcpChecksum(ipPacket: ByteArray, ipHeaderLen: Int, tcpLen: Int, srcIp: ByteArray, dstIp: ByteArray): Int {
        var sum = 0

        sum += ((srcIp[0].toInt() and 0xFF) shl 8) or (srcIp[1].toInt() and 0xFF)
        sum += ((srcIp[2].toInt() and 0xFF) shl 8) or (srcIp[3].toInt() and 0xFF)
        sum += ((dstIp[0].toInt() and 0xFF) shl 8) or (dstIp[1].toInt() and 0xFF)
        sum += ((dstIp[2].toInt() and 0xFF) shl 8) or (dstIp[3].toInt() and 0xFF)
        sum += 6
        sum += tcpLen

        var i = ipHeaderLen
        var limit = ipHeaderLen + tcpLen
        val odd = tcpLen % 2 != 0
        if (odd) {
            limit -= 1
        }

        while (i < limit) {
            val word = ((ipPacket[i].toInt() and 0xFF) shl 8) or (ipPacket[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }

        if (odd) {
            val lastByteWord = (ipPacket[limit].toInt() and 0xFF) shl 8
            sum += lastByteWord
        }

        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }

        return (sum.inv() and 0xFFFF)
    }

    private fun stopVpn() {
        isRunning = false
        vpnScope.cancel()
        vpnScope.launch {
            try {
                wgTunnel?.stop()
            } catch (e: Exception) {}
            wgTunnel = null
        }
        try {
            serverSocket?.close()
        } catch (e: Exception) {
        }
        serverSocket = null
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
        }
        vpnInterface = null
    }

    private fun applyCaseSpoof(data: ByteArray, host: String): ByteArray {
        val result = data.copyOf()
        val targetBytes = host.toByteArray(Charsets.UTF_8)
        val size = targetBytes.size
        
        for (i in 0..result.size - size) {
            var found = true
            for (j in 0 until size) {
                val b1 = result[i + j].toInt() and 0xFF
                val b2 = targetBytes[j].toInt() and 0xFF
                val c1 = b1.toChar().lowercaseChar()
                val c2 = b2.toChar().lowercaseChar()
                if (c1 != c2) {
                    found = false
                    break
                }
            }
            if (found) {
                for (j in 0 until size) {
                    val b = targetBytes[j].toInt() and 0xFF
                    val c = b.toChar()
                    if (c.isLetter()) {
                        val upper = (i + j) % 2 == 0
                        val modified = if (upper) c.uppercaseChar() else c.lowercaseChar()
                        result[i + j] = modified.code.toByte()
                    }
                }
                break
            }
        }
        return result
    }

    private fun resolveDnsOverHttps(dnsPayload: ByteArray): ByteArray? {
        try {
            val client = okhttp3.OkHttpClient.Builder()
                .socketFactory(ProtectedSocketFactory(this))
                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val mediaType = "application/dns-message".toMediaTypeOrNull()
            val requestBody = dnsPayload.toRequestBody(mediaType)
            val request = okhttp3.Request.Builder()
                .url("https://1.1.1.1/dns-query")
                .header("Content-Type", "application/dns-message")
                .header("Accept", "application/dns-message")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    return response.body?.bytes()
                }
            }
        } catch (e: Exception) {
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .socketFactory(ProtectedSocketFactory(this))
                    .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val mediaType = "application/dns-message".toMediaTypeOrNull()
                val requestBody = dnsPayload.toRequestBody(mediaType)
                val request = okhttp3.Request.Builder()
                    .url("https://dns.google/dns-query")
                    .header("Content-Type", "application/dns-message")
                    .header("Accept", "application/dns-message")
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        return response.body?.bytes()
                    }
                }
            } catch (ex: Exception) {}
        }
        return null
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}

class ProtectedSocketFactory(private val vpnService: DnsVpnService) : javax.net.SocketFactory() {
    private val defaultFactory = javax.net.SocketFactory.getDefault()
    
    override fun createSocket(): java.net.Socket {
        val socket = defaultFactory.createSocket()
        vpnService.protect(socket)
        return socket
    }
    
    override fun createSocket(host: String?, port: Int): java.net.Socket {
        val socket = defaultFactory.createSocket(host, port)
        vpnService.protect(socket)
        return socket
    }
    
    override fun createSocket(host: String?, port: Int, localHost: java.net.InetAddress?, localPort: Int): java.net.Socket {
        val socket = defaultFactory.createSocket(host, port, localHost, localPort)
        vpnService.protect(socket)
        return socket
    }
    
    override fun createSocket(address: java.net.InetAddress?, port: Int): java.net.Socket {
        val socket = defaultFactory.createSocket(address, port)
        vpnService.protect(socket)
        return socket
    }
    
    override fun createSocket(address: java.net.InetAddress?, port: Int, localAddress: java.net.InetAddress?, localPort: Int): java.net.Socket {
        val socket = defaultFactory.createSocket(address, port, localAddress, localPort)
        vpnService.protect(socket)
        return socket
    }
}
