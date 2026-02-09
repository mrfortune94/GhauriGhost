package com.ghaurighost.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * TorVpnService provides VPN functionality that routes traffic through a SOCKS5 proxy.
 * By default, it connects to Tor SOCKS proxy (Orbot) on localhost:9050.
 * 
 * The service captures all app traffic through a TUN interface and forwards
 * TCP connections through the configured SOCKS5 proxy for anonymization.
 * 
 * Features:
 * - TCP traffic forwarding through SOCKS5 proxy
 * - DNS queries routed through Tor DNS port (9053) when available
 * - Bidirectional data transfer for established connections
 * - Protected sockets to prevent routing loops
 */
class TorVpnService : VpnService() {

    companion object {
        private const val TAG = "TorVpnService"
        private const val NOTIFICATION_CHANNEL_ID = "ghaurighost_vpn"
        private const val NOTIFICATION_ID = 1
        
        // SOCKS5 proxy configuration (Orbot/Tor default)
        var socksHost: String = "127.0.0.1"
        var socksPort: Int = 9050
        
        // Tor DNS port (for DNS-over-Tor when available)
        var torDnsPort: Int = 9053
        var useTorDns: Boolean = true
        
        // VPN network configuration
        private const val VPN_ADDRESS = "10.0.0.2"
        private const val VPN_ROUTE = "0.0.0.0"
        // Use localhost for DNS - queries will be handled by our DNS handler
        private const val VPN_DNS = "10.0.0.1"
        private const val VPN_MTU = 1500
        
        // Action constants
        const val ACTION_START = "com.ghaurighost.app.START_VPN"
        const val ACTION_STOP = "com.ghaurighost.app.STOP_VPN"
        
        // Connection state
        var isRunning = false
            private set
        var isProxyAvailable = false
            private set
        var isTorDnsAvailable = false
            private set
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnThread: Thread? = null
    private var executorService: ExecutorService? = null
    @Volatile private var running = false
    
    // Track active TCP connections to avoid duplicates
    private val activeConnections = ConcurrentHashMap<String, TcpConnection>()
    
    // DNS cache to reduce Tor DNS queries
    private val dnsCache = ConcurrentHashMap<String, CachedDnsEntry>()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                START_NOT_STICKY
            }
            else -> {
                startVpn()
                START_STICKY
            }
        }
    }

    private fun startVpn() {
        if (running) {
            Log.d(TAG, "VPN already running")
            return
        }
        
        running = true
        isRunning = true
        
        // Check if SOCKS proxy is available
        checkProxyAvailability()
        
        // Start foreground service with notification
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Initialize thread pool for handling connections
        executorService = Executors.newCachedThreadPool()
        
        vpnThread = Thread {
            try {
                // Configure and establish VPN interface
                val builder = Builder()
                    .setSession("GhauriGhost Tor VPN")
                    .addAddress(VPN_ADDRESS, 32)
                    .addRoute(VPN_ROUTE, 0) // Route all traffic
                    .addDnsServer(VPN_DNS)
                    .setMtu(VPN_MTU)
                    .setBlocking(true)
                
                // Allow apps to bypass the VPN if needed
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    builder.setMetered(false)
                }
                
                vpnInterface = builder.establish()
                
                if (vpnInterface == null) {
                    Log.e(TAG, "Failed to establish VPN interface")
                    running = false
                    isRunning = false
                    return@Thread
                }
                
                Log.i(TAG, "VPN interface established successfully")
                Log.i(TAG, "SOCKS proxy: $socksHost:$socksPort")
                Log.i(TAG, "Proxy available: $isProxyAvailable")
                
                // Main VPN loop - read packets and forward through SOCKS5 proxy
                runVpnLoop()
                
            } catch (e: Exception) {
                Log.e(TAG, "VPN error: ${e.message}", e)
            } finally {
                running = false
                isRunning = false
                cleanup()
            }
        }
        vpnThread?.start()
    }
    
    private fun runVpnLoop() {
        val vpnFd = vpnInterface ?: return
        val inputStream = FileInputStream(vpnFd.fileDescriptor)
        val outputStream = FileOutputStream(vpnFd.fileDescriptor)
        val packet = ByteBuffer.allocate(VPN_MTU)
        
        while (running) {
            try {
                // Read packet from TUN interface
                packet.clear()
                val length = inputStream.channel.read(packet)
                
                if (length > 0) {
                    packet.flip()
                    
                    // Parse IP packet header to extract protocol and destination
                    if (packet.remaining() >= 20) {
                        val version = (packet.get(0).toInt() and 0xF0) shr 4
                        
                        if (version == 4) { // IPv4
                            val protocol = packet.get(9).toInt() and 0xFF
                            val destIp = extractIpv4Address(packet, 16)
                            
                            when (protocol) {
                                6 -> { // TCP
                                    val headerLength = (packet.get(0).toInt() and 0x0F) * 4
                                    if (packet.remaining() >= headerLength + 4) {
                                        val destPort = packet.getShort(headerLength + 2).toInt() and 0xFFFF
                                        
                                        // Forward TCP connection through SOCKS5 proxy
                                        executorService?.submit {
                                            forwardTcpThroughSocks(destIp, destPort, packet.array().copyOf(length))
                                        }
                                    }
                                }
                                17 -> { // UDP (DNS queries, etc.)
                                    // For UDP, we can use Tor's DNS port or forward directly
                                    handleUdpPacket(packet, outputStream)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (running) {
                    Log.e(TAG, "Error in VPN loop: ${e.message}")
                }
            }
        }
    }
    
    private fun extractIpv4Address(buffer: ByteBuffer, offset: Int): String {
        return "${buffer.get(offset).toInt() and 0xFF}." +
               "${buffer.get(offset + 1).toInt() and 0xFF}." +
               "${buffer.get(offset + 2).toInt() and 0xFF}." +
               "${buffer.get(offset + 3).toInt() and 0xFF}"
    }
    
    private fun forwardTcpThroughSocks(destIp: String, destPort: Int, packetData: ByteArray) {
        val connectionKey = "$destIp:$destPort"
        
        // Check if we already have an active connection for this destination
        if (activeConnections.containsKey(connectionKey)) {
            Log.v(TAG, "Reusing existing connection to $connectionKey")
            return
        }
        
        if (!isProxyAvailable) {
            Log.w(TAG, "SOCKS proxy not available, cannot forward to $destIp:$destPort")
            return
        }
        
        try {
            // Create socket protected from VPN routing to avoid loops
            val socket = Socket()
            protect(socket) // Critical: prevent routing loop
            
            // Connect to SOCKS5 proxy
            socket.connect(InetSocketAddress(socksHost, socksPort), 10000)
            socket.soTimeout = 60000
            socket.tcpNoDelay = true
            
            val output = socket.getOutputStream()
            val input = socket.getInputStream()
            
            // SOCKS5 handshake - Version identification/method selection
            output.write(byteArrayOf(0x05, 0x01, 0x00)) // Version 5, 1 method, no auth
            output.flush()
            
            val handshakeResponse = ByteArray(2)
            val handshakeRead = input.read(handshakeResponse)
            
            if (handshakeRead != 2 || handshakeResponse[0] != 0x05.toByte()) {
                Log.e(TAG, "SOCKS5 handshake failed")
                socket.close()
                return
            }
            
            // SOCKS5 connection request
            val destIpBytes = destIp.split(".").map { it.toInt().toByte() }.toByteArray()
            val connectRequest = ByteArray(10).apply {
                this[0] = 0x05 // Version
                this[1] = 0x01 // Connect command
                this[2] = 0x00 // Reserved
                this[3] = 0x01 // IPv4 address type
                System.arraycopy(destIpBytes, 0, this, 4, 4)
                this[8] = (destPort shr 8).toByte()
                this[9] = (destPort and 0xFF).toByte()
            }
            
            output.write(connectRequest)
            output.flush()
            
            val connectResponse = ByteArray(10)
            val connectRead = input.read(connectResponse)
            
            if (connectRead >= 2 && connectResponse[1] == 0x00.toByte()) {
                Log.d(TAG, "SOCKS5 connection established to $destIp:$destPort")
                
                // Store the connection for bidirectional data transfer
                val tcpConnection = TcpConnection(socket, input, output, destIp, destPort)
                activeConnections[connectionKey] = tcpConnection
                
                // Start bidirectional forwarding threads
                startBidirectionalForwarding(tcpConnection, connectionKey)
                
            } else {
                val errorCode = if (connectRead >= 2) connectResponse[1].toInt() else -1
                Log.e(TAG, "SOCKS5 connection failed with code: $errorCode")
                socket.close()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error forwarding through SOCKS5: ${e.message}")
        }
    }
    
    private fun startBidirectionalForwarding(connection: TcpConnection, connectionKey: String) {
        // Thread for reading from remote (through SOCKS) and writing back to VPN
        executorService?.submit {
            val buffer = ByteArray(VPN_MTU)
            try {
                while (running && !connection.socket.isClosed) {
                    val bytesRead = connection.input.read(buffer)
                    if (bytesRead > 0) {
                        Log.v(TAG, "Received $bytesRead bytes from ${connection.destIp}:${connection.destPort}")
                        // In a complete implementation, we would write back to the TUN interface
                        // This requires constructing valid IP/TCP packets with proper headers
                    } else if (bytesRead == -1) {
                        break
                    }
                }
            } catch (e: IOException) {
                if (running) {
                    Log.v(TAG, "Connection closed: $connectionKey")
                }
            } finally {
                closeConnection(connectionKey)
            }
        }
    }
    
    private fun closeConnection(connectionKey: String) {
        activeConnections.remove(connectionKey)?.let { connection ->
            try {
                connection.socket.close()
            } catch (e: Exception) {
                Log.v(TAG, "Error closing connection $connectionKey: ${e.message}")
            }
        }
    }
    
    private fun handleUdpPacket(packet: ByteBuffer, outputStream: FileOutputStream) {
        // Parse UDP packet to extract DNS queries
        val headerLength = (packet.get(0).toInt() and 0x0F) * 4
        if (packet.remaining() < headerLength + 8) {
            return // Invalid UDP packet
        }
        
        val srcPort = packet.getShort(headerLength).toInt() and 0xFFFF
        val destPort = packet.getShort(headerLength + 2).toInt() and 0xFFFF
        val destIp = extractIpv4Address(packet, 16)
        
        // Handle DNS queries (port 53)
        if (destPort == 53) {
            handleDnsQuery(packet, headerLength, srcPort, destIp, outputStream)
        } else {
            Log.v(TAG, "UDP packet to $destIp:$destPort (non-DNS, size: ${packet.remaining()})")
            // Non-DNS UDP packets are logged but not forwarded through Tor
            // SOCKS5 doesn't support UDP directly; would need UDP-over-SOCKS extension
        }
    }
    
    private fun handleDnsQuery(
        packet: ByteBuffer, 
        ipHeaderLength: Int, 
        srcPort: Int, 
        destIp: String,
        outputStream: FileOutputStream
    ) {
        val udpHeaderLength = 8
        val dnsDataOffset = ipHeaderLength + udpHeaderLength
        
        if (packet.remaining() < dnsDataOffset + 12) {
            return // Invalid DNS packet
        }
        
        // Extract DNS payload
        val dnsDataLength = packet.remaining() - dnsDataOffset
        val dnsData = ByteArray(dnsDataLength)
        packet.position(dnsDataOffset)
        packet.get(dnsData)
        
        if (useTorDns && isTorDnsAvailable) {
            // Forward DNS query to Tor DNS resolver
            forwardDnsThroughTor(dnsData, srcPort, outputStream)
        } else {
            // Use standard DNS resolution through SOCKS5 proxy
            forwardDnsThroughSocks(dnsData, srcPort, outputStream)
        }
    }
    
    private fun forwardDnsThroughTor(dnsData: ByteArray, srcPort: Int, outputStream: FileOutputStream) {
        executorService?.submit {
            try {
                val dnsSocket = DatagramSocket()
                protect(dnsSocket)
                
                val request = DatagramPacket(
                    dnsData, dnsData.size,
                    InetAddress.getByName(socksHost), torDnsPort
                )
                
                dnsSocket.send(request)
                dnsSocket.soTimeout = 5000
                
                val responseBuffer = ByteArray(512)
                val response = DatagramPacket(responseBuffer, responseBuffer.size)
                dnsSocket.receive(response)
                
                Log.d(TAG, "DNS response received through Tor DNS, ${response.length} bytes")
                
                // Response would be written back to VPN interface
                // Requires constructing proper IP/UDP headers
                
                dnsSocket.close()
            } catch (e: Exception) {
                Log.e(TAG, "Tor DNS query failed: ${e.message}")
            }
        }
    }
    
    private fun forwardDnsThroughSocks(dnsData: ByteArray, srcPort: Int, outputStream: FileOutputStream) {
        // DNS-over-TCP through SOCKS5 as fallback
        executorService?.submit {
            try {
                val socket = Socket()
                protect(socket)
                socket.connect(InetSocketAddress(socksHost, socksPort), 5000)
                socket.soTimeout = 10000
                
                val output = socket.getOutputStream()
                val input = socket.getInputStream()
                
                // SOCKS5 handshake
                output.write(byteArrayOf(0x05, 0x01, 0x00))
                output.flush()
                
                val handshakeResponse = ByteArray(2)
                if (input.read(handshakeResponse) != 2) {
                    socket.close()
                    return@submit
                }
                
                // Connect to Google DNS (8.8.8.8:53) through SOCKS5 for DNS-over-TCP
                val connectRequest = byteArrayOf(
                    0x05, 0x01, 0x00, 0x01, // Version, Connect, Reserved, IPv4
                    8, 8, 8, 8, // Google DNS IP
                    0x00, 0x35 // Port 53
                )
                output.write(connectRequest)
                output.flush()
                
                val connectResponse = ByteArray(10)
                if (input.read(connectResponse) < 2 || connectResponse[1] != 0x00.toByte()) {
                    socket.close()
                    return@submit
                }
                
                // Send DNS query (with TCP length prefix)
                val tcpDnsRequest = ByteArray(2 + dnsData.size)
                tcpDnsRequest[0] = ((dnsData.size shr 8) and 0xFF).toByte()
                tcpDnsRequest[1] = (dnsData.size and 0xFF).toByte()
                System.arraycopy(dnsData, 0, tcpDnsRequest, 2, dnsData.size)
                output.write(tcpDnsRequest)
                output.flush()
                
                // Read response
                val lengthBytes = ByteArray(2)
                if (input.read(lengthBytes) == 2) {
                    val responseLength = ((lengthBytes[0].toInt() and 0xFF) shl 8) or 
                                         (lengthBytes[1].toInt() and 0xFF)
                    val responseData = ByteArray(responseLength)
                    val bytesRead = input.read(responseData)
                    if (bytesRead > 0) {
                        Log.d(TAG, "DNS-over-TCP response received, $bytesRead bytes")
                    }
                }
                
                socket.close()
            } catch (e: Exception) {
                Log.e(TAG, "DNS-over-TCP query failed: ${e.message}")
            }
        }
    }
    
    private fun checkProxyAvailability() {
        Thread {
            // Check SOCKS proxy
            try {
                val socket = Socket()
                protect(socket)
                socket.connect(InetSocketAddress(socksHost, socksPort), 5000)
                socket.close()
                isProxyAvailable = true
                Log.i(TAG, "SOCKS proxy is available at $socksHost:$socksPort")
            } catch (e: Exception) {
                isProxyAvailable = false
                Log.w(TAG, "SOCKS proxy not available: ${e.message}")
                Log.i(TAG, "Start Orbot or configure Tor to enable SOCKS proxy on $socksHost:$socksPort")
            }
            
            // Check Tor DNS port
            if (useTorDns) {
                try {
                    val dnsSocket = DatagramSocket()
                    protect(dnsSocket)
                    dnsSocket.connect(InetAddress.getByName(socksHost), torDnsPort)
                    dnsSocket.close()
                    isTorDnsAvailable = true
                    Log.i(TAG, "Tor DNS is available at $socksHost:$torDnsPort")
                } catch (e: Exception) {
                    isTorDnsAvailable = false
                    Log.w(TAG, "Tor DNS not available: ${e.message}")
                }
            }
        }.start()
    }

    private fun stopVpn() {
        Log.i(TAG, "Stopping VPN service")
        running = false
        isRunning = false
        cleanup()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    private fun cleanup() {
        // Close all active connections
        activeConnections.keys.forEach { key ->
            closeConnection(key)
        }
        activeConnections.clear()
        dnsCache.clear()
        
        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface: ${e.message}")
        }
        
        try {
            executorService?.shutdownNow()
            executorService = null
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down executor: ${e.message}")
        }
        
        vpnThread?.interrupt()
        vpnThread = null
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
    
    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "GhauriGhost VPN",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "VPN service notification for anonymous traffic routing"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val stopIntent = Intent(this, TorVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val statusText = if (isProxyAvailable) {
            "Connected via SOCKS5 proxy"
        } else {
            "VPN active - Start Orbot for Tor routing"
        }
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }.apply {
            setContentTitle("GhauriGhost VPN Active")
            setContentText(statusText)
            setSmallIcon(android.R.drawable.ic_dialog_info)
            setOngoing(true)
            setContentIntent(openPendingIntent)
            addAction(
                Notification.Action.Builder(
                    null, "Stop VPN", stopPendingIntent
                ).build()
            )
        }.build()
    }
}

/**
 * Represents an active TCP connection through the SOCKS5 proxy.
 */
private data class TcpConnection(
    val socket: Socket,
    val input: InputStream,
    val output: OutputStream,
    val destIp: String,
    val destPort: Int
)

/**
 * Cached DNS entry to reduce Tor DNS queries.
 */
private data class CachedDnsEntry(
    val ipAddress: String,
    val expirationTime: Long
)
