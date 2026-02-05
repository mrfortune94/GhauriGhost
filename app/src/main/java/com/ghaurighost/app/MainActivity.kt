package com.ghaurighost.app

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Prompt for VPN permission & start Tor routing
        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, 0)
        } else {
            startService(Intent(this, TorVpnService::class.java))
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF00FF88),
                    secondary = Color(0xFF00BFFF),
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E),
                    onPrimary = Color.Black,
                    onSecondary = Color.Black,
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }
    }
}

// Tab navigation
enum class AppTab(val title: String) {
    SCANNER("Endpoint Scanner"),
    GHAURI("Ghauri SQLi"),
    METASPLOIT("Metasploit")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    var selectedTab by remember { mutableStateOf(AppTab.SCANNER) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("GhauriGhost", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            when (tab) {
                                AppTab.SCANNER -> Icon(Icons.Default.Search, contentDescription = null)
                                AppTab.GHAURI -> Icon(Icons.Default.BugReport, contentDescription = null)
                                AppTab.METASPLOIT -> Icon(Icons.Default.Terminal, contentDescription = null)
                            }
                        },
                        label = { Text(tab.title) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                AppTab.SCANNER -> EndpointScannerScreen()
                AppTab.GHAURI -> GhauriScreen()
                AppTab.METASPLOIT -> MetasploitScreen()
            }
        }
    }
}

@Composable
fun EndpointScannerScreen() {
    var url by remember { mutableStateOf("https://") }
    var endpoints by remember { mutableStateOf(listOf<String>()) }
    var selectedEndpoints by remember { mutableStateOf(setOf<String>()) }
    val scope = rememberCoroutineScope()
    var isScanning by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Ready - Enter target URL for deep domain scraping") }
    var scanProgress by remember { mutableStateOf(0f) }

    Column(modifier = Modifier.padding(16.dp)) {
        // Scan configuration
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Deep Domain Scanner", 
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    "Scans: Links, Forms, JavaScript, APIs, Sitemap, robots.txt, Wayback Machine",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Target URL") },
                    placeholder = { Text("https://example.com") },
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        scope.launch {
                            isScanning = true
                            status = "Deep scanning in progress..."
                            scanProgress = 0.1f
                            
                            endpoints = scanEndpoints(url)
                            
                            scanProgress = 1f
                            status = if (endpoints.isEmpty()) {
                                "No exploitable endpoints found"
                            } else {
                                "${endpoints.size} potential endpoints discovered"
                            }
                            isScanning = false
                        }
                    },
                    enabled = !isScanning && url.startsWith("http"),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scanning...")
                    } else {
                        Icon(Icons.Default.Radar, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Deep Scan")
                    }
                }
                
                if (isScanning) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { scanProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Status bar
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (endpoints.isNotEmpty()) 
                    Color(0xFF1B4332) else MaterialTheme.colorScheme.surface
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (endpoints.isNotEmpty()) Icons.Default.CheckCircle else Icons.Default.Info,
                    contentDescription = null,
                    tint = if (endpoints.isNotEmpty()) Color(0xFF00FF88) else Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(status, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Endpoints list
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Discovered Endpoints (${endpoints.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            if (endpoints.isNotEmpty()) {
                TextButton(onClick = { 
                    selectedEndpoints = if (selectedEndpoints.size == endpoints.size) 
                        emptySet() else endpoints.toSet()
                }) {
                    Text(if (selectedEndpoints.size == endpoints.size) "Deselect All" else "Select All")
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(endpoints) { ep ->
                EndpointItem(
                    endpoint = ep,
                    isSelected = ep in selectedEndpoints,
                    onSelect = {
                        selectedEndpoints = if (ep in selectedEndpoints) 
                            selectedEndpoints - ep else selectedEndpoints + ep
                    },
                    onTest = { GhauriRunner.runOnEndpoint(ep) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    val toTest = if (selectedEndpoints.isNotEmpty()) selectedEndpoints.toList() else endpoints
                    GhauriRunner.runOnEndpoints(toTest)
                },
                modifier = Modifier.weight(1f),
                enabled = endpoints.isNotEmpty()
            ) {
                Icon(Icons.Default.BugReport, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Ghauri SQLi")
            }
            
            Button(
                onClick = {
                    scope.launch {
                        MetasploitRunner.autoExploit(url)
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = endpoints.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BFFF))
            ) {
                Icon(Icons.Default.Terminal, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Metasploit")
            }
        }
    }
}

@Composable
fun EndpointItem(
    endpoint: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onTest: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                Color(0xFF1B4332) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onSelect() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary
                )
            )
            
            Text(
                endpoint,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall
            )
            
            IconButton(onClick = onTest) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Test",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun GhauriScreen() {
    var targetUrl by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Ghauri SQL Injection Testing",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    "Advanced SQL injection detection and exploitation",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = targetUrl,
                    onValueChange = { targetUrl = it },
                    label = { Text("Target URL with Parameter") },
                    placeholder = { Text("https://example.com/page.php?id=1") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        scope.launch {
                            isRunning = true
                            results = "Running Ghauri on $targetUrl..."
                            GhauriRunner.runOnEndpoint(targetUrl)
                            results = "Ghauri scan completed"
                            isRunning = false
                        }
                    },
                    enabled = !isRunning && targetUrl.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Default.BugReport, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Run Ghauri SQLi Test")
                    }
                }
            }
        }

        if (results.isNotBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Results",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        results,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun MetasploitScreen() {
    var msfHost by remember { mutableStateOf("127.0.0.1") }
    var msfPort by remember { mutableStateOf("55553") }
    var msfUser by remember { mutableStateOf("msf") }
    var msfPassword by remember { mutableStateOf("msf") }
    var isConnected by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Not connected") }
    var selectedModule by remember { mutableStateOf<ExploitModule?>(null) }
    var targetHost by remember { mutableStateOf("") }
    var consoleOutput by remember { mutableStateOf("") }
    var sessions by remember { mutableStateOf(listOf<SessionInfo>()) }
    var jobs by remember { mutableStateOf(mapOf<String, String>()) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Connection settings
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Terminal,
                        contentDescription = null,
                        tint = if (isConnected) Color(0xFF00FF88) else Color.Gray
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Metasploit RPC Connection",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    "Connect to MSF RPC API for exploit framework access",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = msfHost,
                        onValueChange = { msfHost = it },
                        label = { Text("Host") },
                        modifier = Modifier.weight(2f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = msfPort,
                        onValueChange = { msfPort = it },
                        label = { Text("Port") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = msfUser,
                        onValueChange = { msfUser = it },
                        label = { Text("Username") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = msfPassword,
                        onValueChange = { msfPassword = it },
                        label = { Text("Password") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        scope.launch {
                            isConnecting = true
                            status = "Connecting..."
                            
                            MetasploitRunner.configure(
                                msfHost,
                                msfPort.toIntOrNull() ?: 55553,
                                msfUser,
                                msfPassword
                            )
                            
                            val result = MetasploitRunner.authenticate()
                            result.fold(
                                onSuccess = { 
                                    isConnected = true
                                    status = "Connected to MSF RPC"
                                    
                                    // Load sessions and jobs
                                    MetasploitRunner.getSessions().onSuccess { sessions = it }
                                    MetasploitRunner.getJobs().onSuccess { jobs = it }
                                },
                                onFailure = { 
                                    isConnected = false
                                    status = "Connection failed: ${it.message}"
                                }
                            )
                            isConnecting = false
                        }
                    },
                    enabled = !isConnecting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Icon(
                            if (isConnected) Icons.Default.CheckCircle else Icons.Default.Link,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isConnected) "Reconnect" else "Connect")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isConnected) Color(0xFF00FF88) else Color.Gray
                )
            }
        }

        if (isConnected) {
            Spacer(modifier = Modifier.height(16.dp))

            // Module selection
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Exploit Modules",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = targetHost,
                        onValueChange = { targetHost = it },
                        label = { Text("Target Host/URL") },
                        placeholder = { Text("192.168.1.1 or https://target.com") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    MetasploitRunner.webExploitModules.forEach { module ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { selectedModule = module },
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedModule == module)
                                    Color(0xFF1B4332) else Color(0xFF2D2D2D)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        module.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        module.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }
                                
                                Chip(module.category)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    selectedModule?.let { module ->
                                        consoleOutput = "Running ${module.name}...\n"
                                        val result = MetasploitRunner.runWebScan(targetHost, module.name)
                                        result.fold(
                                            onSuccess = { consoleOutput += "Started: $it\n" },
                                            onFailure = { consoleOutput += "Error: ${it.message}\n" }
                                        )
                                    }
                                }
                            },
                            enabled = selectedModule != null && targetHost.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Run Module")
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    consoleOutput = "Running auto-exploit...\n"
                                    val result = MetasploitRunner.autoExploit(targetHost)
                                    result.fold(
                                        onSuccess = { results ->
                                            consoleOutput += results.joinToString("\n")
                                        },
                                        onFailure = { consoleOutput += "Error: ${it.message}\n" }
                                    )
                                }
                            },
                            enabled = targetHost.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B6B))
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Auto Exploit")
                        }
                    }
                }
            }

            // Sessions & Jobs
            if (sessions.isNotEmpty() || jobs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Active Sessions (${sessions.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        sessions.forEach { session ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B4332))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        "Session #${session.id} - ${session.type}",
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Target: ${session.targetHost}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "Via: ${session.exploitModule}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }

                        if (jobs.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Running Jobs (${jobs.size})",
                                style = MaterialTheme.typography.titleSmall
                            )
                            jobs.forEach { (id, name) ->
                                Text("Job #$id: $name", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            // Console output
            if (consoleOutput.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D0D))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Console Output",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00FF88)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            consoleOutput,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = Color(0xFF00FF88)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Chip(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = when (text) {
            "Scanner" -> Color(0xFF2196F3)
            "Exploit" -> Color(0xFFFF5722)
            "Gather" -> Color(0xFF4CAF50)
            else -> Color.Gray
        }
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White
        )
    }
}
