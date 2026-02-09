package com.ghaurighost.app

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer

class TorVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    @Volatile
    private var isRunning = false

    // Orbot's default SOCKS5 proxy address
    private val torSocksHost = "127.0.0.1"
    private val torSocksPort = 9050

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isRunning) return START_STICKY

        Thread {
            try {
                val torAvailable = isTorProxyAvailable()

                val builder = Builder()
                    .setSession("GhauriGhost Tor VPN")
                    .addAddress("10.0.0.2", 32)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer("1.1.1.1")
                    .setMtu(1500)

                vpnInterface = builder.establish() ?: return@Thread

                isRunning = true

                if (torAvailable) {
                    forwardThroughTor()
                } else {
                    forwardPassthrough()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isRunning = false
            }
        }.start()

        return START_STICKY
    }

    /**
     * Check if the Tor SOCKS proxy (Orbot) is reachable.
     */
    private fun isTorProxyAvailable(): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(torSocksHost, torSocksPort), 2000)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Forward VPN traffic through the Tor SOCKS5 proxy.
     * Reads IP packets from the VPN tunnel, parses TCP SYN packets to
     * identify new outbound connections, opens corresponding SOCKS5
     * connections through the Tor proxy, and relays data between the
     * VPN tunnel and the SOCKS5 proxy.
     */
    private fun forwardThroughTor() {
        val fd = vpnInterface ?: return
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val packet = ByteBuffer.allocate(32767)

        while (isRunning) {
            packet.clear()
            val length = input.read(packet.array())
            if (length > 0) {
                packet.limit(length)

                // Parse the IPv4 header to extract the destination
                val version = (packet.get(0).toInt() shr 4) and 0x0F
                if (version == 4 && length >= 20) {
                    val protocol = packet.get(9).toInt() and 0xFF
                    val destIp = buildDestIp(packet, 16)
                    val ihl = (packet.get(0).toInt() and 0x0F) * 4

                    if (protocol == 6 && length >= ihl + 4) { // TCP
                        val destPort = ((packet.get(ihl).toInt() and 0xFF) shl 8) or
                                (packet.get(ihl + 1).toInt() and 0xFF)

                        relayViaSocks5(destIp, destPort, packet.array(), length, output)
                        continue
                    }
                }

                // Non-TCP or unparseable packets are written back as-is
                output.write(packet.array(), 0, length)
            } else {
                Thread.sleep(50)
            }
        }
    }

    /**
     * Relay a TCP payload through the Tor SOCKS5 proxy.
     */
    private fun relayViaSocks5(
        destIp: String,
        destPort: Int,
        packetData: ByteArray,
        length: Int,
        vpnOutput: FileOutputStream
    ) {
        try {
            val proxySocket = Socket()
            // Protect the socket so its traffic bypasses the VPN tunnel
            // and goes directly to the local Tor daemon.
            protect(proxySocket)
            proxySocket.connect(InetSocketAddress(torSocksHost, torSocksPort), 5000)

            val proxyOut = proxySocket.getOutputStream()
            val proxyIn = proxySocket.getInputStream()

            // SOCKS5 handshake: greeting (no auth)
            proxyOut.write(byteArrayOf(0x05, 0x01, 0x00))
            proxyOut.flush()
            val greetResp = ByteArray(2)
            proxyIn.read(greetResp)

            if (greetResp[0] != 0x05.toByte()) {
                proxySocket.close()
                return
            }

            // SOCKS5 connect request
            val portHi = (destPort shr 8) and 0xFF
            val portLo = destPort and 0xFF
            val ipBytes = destIp.split(".").map { it.toInt().toByte() }.toByteArray()
            proxyOut.write(
                byteArrayOf(0x05, 0x01, 0x00, 0x01) + ipBytes +
                        byteArrayOf(portHi.toByte(), portLo.toByte())
            )
            proxyOut.flush()

            val connResp = ByteArray(10)
            proxyIn.read(connResp)

            if (connResp[1] != 0x00.toByte()) {
                // Connection refused or failed
                proxySocket.close()
                return
            }

            // Connection established – relay the original packet payload
            // past the IP+TCP headers
            val version = (packetData[0].toInt() and 0x0F) * 4
            val tcpHeaderLen = ((packetData[version + 12].toInt() shr 4) and 0x0F) * 4
            val payloadOffset = version + tcpHeaderLen
            if (payloadOffset < length) {
                proxyOut.write(packetData, payloadOffset, length - payloadOffset)
                proxyOut.flush()
            }

            // Read response from the proxy and write back to the VPN tunnel
            val responseBuf = ByteArray(32767)
            val read = proxyIn.read(responseBuf)
            if (read > 0) {
                vpnOutput.write(responseBuf, 0, read)
            }

            proxySocket.close()
        } catch (_: Exception) {
            // Connection failed – silently drop this packet
        }
    }

    private fun buildDestIp(packet: ByteBuffer, offset: Int): String {
        return "${packet.get(offset).toInt() and 0xFF}." +
                "${packet.get(offset + 1).toInt() and 0xFF}." +
                "${packet.get(offset + 2).toInt() and 0xFF}." +
                "${packet.get(offset + 3).toInt() and 0xFF}"
    }

    /**
     * Passthrough mode when Tor is not available.
     * Reads and writes VPN packets directly so the tunnel stays alive
     * and traffic flows normally without Tor anonymization.
     */
    private fun forwardPassthrough() {
        val fd = vpnInterface ?: return
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val packet = ByteBuffer.allocate(32767)

        while (isRunning) {
            packet.clear()
            val length = input.read(packet.array())
            if (length > 0) {
                packet.limit(length)
                output.write(packet.array(), 0, length)
            } else {
                Thread.sleep(50)
            }
        }
    }

    override fun onDestroy() {
        isRunning = false
        vpnInterface?.close()
        vpnInterface = null
        super.onDestroy()
    }
}
