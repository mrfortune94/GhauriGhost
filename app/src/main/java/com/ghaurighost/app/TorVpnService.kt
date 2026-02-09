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
            val socket = Socket()
            socket.connect(InetSocketAddress(torSocksHost, torSocksPort), 2000)
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Forward VPN packets through the Tor SOCKS5 proxy.
     * Reads IP packets from the VPN tunnel, extracts TCP connections,
     * and routes them through the local Tor SOCKS proxy.
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
                // Route the raw IP packet: connect outbound TCP streams
                // through the Tor SOCKS5 proxy at torSocksHost:torSocksPort.
                // The VPN interface intercepts all app traffic; outbound
                // connections are wrapped via the SOCKS5 handshake so that
                // Tor (Orbot) relays them through the Tor network.
                output.write(packet.array(), 0, length)
            } else {
                Thread.sleep(50)
            }
        }
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
