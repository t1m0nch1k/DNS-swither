package com.example

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
import java.nio.ByteBuffer

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
            
            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                isRunning = false
                return
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

    private fun handlePacket(data: ByteArray, output: FileOutputStream, dnsIp: String) {
        if (data.size < 20) return
        val versionAndIhl = data[0].toInt() and 0xFF
        val version = versionAndIhl shr 4
        if (version != 4) return

        val protocol = data[9].toInt() and 0xFF
        if (protocol != 17) return

        val ipHeaderLen = (versionAndIhl and 0x0F) * 4
        if (data.size < ipHeaderLen + 8) return

        val srcPort = ((data[ipHeaderLen].toInt() and 0xFF) shl 8) or (data[ipHeaderLen + 1].toInt() and 0xFF)
        val dstPort = ((data[ipHeaderLen + 2].toInt() and 0xFF) shl 8) or (data[ipHeaderLen + 3].toInt() and 0xFF)

        if (dstPort == 53) {
            val udpLen = ((data[ipHeaderLen + 4].toInt() and 0xFF) shl 8) or (data[ipHeaderLen + 5].toInt() and 0xFF)
            val dnsPayloadLen = udpLen - 8
            if (dnsPayloadLen <= 0 || ipHeaderLen + 8 + dnsPayloadLen > data.size) return

            val dnsPayload = data.copyOfRange(ipHeaderLen + 8, ipHeaderLen + 8 + dnsPayloadLen)
            val originalSrcIp = data.copyOfRange(12, 16)

            vpnScope.launch {
                try {
                    val socket = DatagramSocket()
                    protect(socket)
                    socket.soTimeout = 3000
                    val sendPacket = DatagramPacket(dnsPayload, dnsPayload.size, InetAddress.getByName(dnsIp), 53)
                    socket.send(sendPacket)

                    val buffer = ByteArray(4096)
                    val recvPacket = DatagramPacket(buffer, buffer.size)
                    socket.receive(recvPacket)

                    val responsePayload = recvPacket.data.copyOfRange(0, recvPacket.length)
                    val responsePacket = buildUdpPacket(
                        srcIp = InetAddress.getByName(dnsIp).address,
                        dstIp = originalSrcIp,
                        srcPort = 53,
                        dstPort = srcPort,
                        payload = responsePayload
                    )

                    synchronized(output) {
                        output.write(responsePacket)
                    }
                } catch (e: Exception) {
                }
            }
        }
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

    private fun stopVpn() {
        isRunning = false
        vpnScope.cancel()
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
        }
        vpnInterface = null
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
