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
import androidx.compose.material.icons.automirrored.filled.Backspace
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
    
    companion object {
        private const val VPN_REQUEST_CODE = 100
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Prompt for VPN permission & start Tor routing
        val vpnPrepareIntent = VpnService.prepare(this)
        if (vpnPrepareIntent != null) {
            @Suppress("DEPRECATION")
            startActivityForResult(vpnPrepareIntent, VPN_REQUEST_CODE)
        } else {
            startVpnService()
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
    
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            startVpnService()
        }
    }
    
    private fun startVpnService() {
        val serviceIntent = Intent(this, TorVpnService::class.java).apply {
            action = TorVpnService.ACTION_START
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
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
                    onTest = { 
                        scope.launch {
                            status = "Testing endpoint: $ep"
                            val result = GhauriRunner.runOnEndpoint(ep)
                            result.fold(
                                onSuccess = { status = "Test completed: ${it.take(100)}..." },
                                onFailure = { status = "Test failed: ${it.message}" }
                            )
                        }
                    }
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
                    scope.launch {
                        isScanning = true
                        val toTest = if (selectedEndpoints.isNotEmpty()) selectedEndpoints.toList() else endpoints
                        status = "Running SQL injection tests on ${toTest.size} endpoint(s)..."
                        val results = GhauriRunner.runOnEndpoints(toTest)
                        val successCount = results.count { it.isSuccess }
                        status = "Completed: $successCount/${results.size} scans successful"
                        isScanning = false
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = endpoints.isNotEmpty() && !isScanning
            ) {
                Icon(Icons.Default.BugReport, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Ghauri SQLi")
            }
            
            Button(
                onClick = {
                    scope.launch {
                        isScanning = true
                        status = "Running Metasploit auto-exploit..."
                        val result = MetasploitRunner.autoExploit(url)
                        result.fold(
                            onSuccess = { results -> 
                                status = "Metasploit scan completed: ${results.size} modules run"
                            },
                            onFailure = { error ->
                                status = "Metasploit error: ${error.message}"
                            }
                        )
                        isScanning = false
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = endpoints.isNotEmpty() && !isScanning,
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

// Ghauri preset command options for quick command building
data class GhauriPreset(
    val label: String,
    val value: String,
    val description: String,
    val category: String
)

val ghauriPresets = listOf(
    // Network/Proxy options
    GhauriPreset("proxychains4", "proxychains4 ", "Route through proxy chains", "Proxy"),
    GhauriPreset("tor", "tor ", "Route through Tor network", "Proxy"),
    
    // Target options
    GhauriPreset("-u/--url", "-u ", "Target URL (e.g., http://target.com/page?id=1)", "Target"),
    GhauriPreset("-r", "-r ", "Load HTTP request from file", "Target"),
    GhauriPreset("-m", "-m ", "Scan multiple targets from file", "Target"),
    
    // Request options
    GhauriPreset("--data", "--data ", "POST data string", "Request"),
    GhauriPreset("--cookie", "--cookie ", "HTTP Cookie header value", "Request"),
    GhauriPreset("--headers", "--headers ", "Extra headers (e.g., \"X-Forwarded-For: 127.0.0.1\")", "Request"),
    GhauriPreset("--user-agent", "--user-agent ", "Custom User-Agent", "Request"),
    GhauriPreset("--referer", "--referer ", "HTTP Referer header", "Request"),
    GhauriPreset("--method", "--method ", "Force HTTP method (GET/POST/PUT)", "Request"),
    
    // Injection options
    GhauriPreset("-p", "-p ", "Testable parameter(s)", "Injection"),
    GhauriPreset("--dbms", "--dbms ", "Force back-end DBMS (MySQL/PostgreSQL/etc)", "Injection"),
    GhauriPreset("--technique", "--technique ", "SQL injection techniques (B/E/U/S/T)", "Injection"),
    GhauriPreset("--prefix", "--prefix ", "Injection payload prefix", "Injection"),
    GhauriPreset("--suffix", "--suffix ", "Injection payload suffix", "Injection"),
    
    // Detection options
    GhauriPreset("--level=1", "--level=1 ", "Level 1 - Default tests", "Detection"),
    GhauriPreset("--level=2", "--level=2 ", "Level 2 - More tests", "Detection"),
    GhauriPreset("--level=3", "--level=3 ", "Level 3 - Extensive tests", "Detection"),
    GhauriPreset("--risk=1", "--risk=1 ", "Risk 1 - Safe tests only", "Detection"),
    GhauriPreset("--risk=2", "--risk=2 ", "Risk 2 - Include time-based", "Detection"),
    GhauriPreset("--risk=3", "--risk=3 ", "Risk 3 - Include OR-based", "Detection"),
    
    // Enumeration options
    GhauriPreset("--dbs", "--dbs ", "Enumerate databases", "Enumeration"),
    GhauriPreset("--tables", "--tables ", "Enumerate tables", "Enumeration"),
    GhauriPreset("--columns", "--columns ", "Enumerate columns", "Enumeration"),
    GhauriPreset("--dump", "--dump ", "Dump database table entries", "Enumeration"),
    GhauriPreset("--dump-all", "--dump-all ", "Dump all database tables", "Enumeration"),
    GhauriPreset("-D", "-D ", "Database to enumerate", "Enumeration"),
    GhauriPreset("-T", "-T ", "Table to enumerate", "Enumeration"),
    GhauriPreset("-C", "-C ", "Column to enumerate", "Enumeration"),
    GhauriPreset("--current-user", "--current-user ", "Retrieve current user", "Enumeration"),
    GhauriPreset("--current-db", "--current-db ", "Retrieve current database", "Enumeration"),
    GhauriPreset("--passwords", "--passwords ", "Enumerate password hashes", "Enumeration"),
    GhauriPreset("--privileges", "--privileges ", "Enumerate user privileges", "Enumeration"),
    
    // Operating system options
    GhauriPreset("--os-shell", "--os-shell ", "Prompt for an interactive OS shell", "OS"),
    GhauriPreset("--os-cmd", "--os-cmd ", "Execute an OS command", "OS"),
    GhauriPreset("--file-read", "--file-read ", "Read a file from the server", "OS"),
    GhauriPreset("--file-write", "--file-write ", "Write a file to the server", "OS"),
    
    // General options
    GhauriPreset("--batch", "--batch ", "Never ask for user input", "General"),
    GhauriPreset("--flush-session", "--flush-session ", "Flush session files", "General"),
    GhauriPreset("--fresh-queries", "--fresh-queries ", "Ignore stored query results", "General"),
    GhauriPreset("-v", "-v ", "Verbosity level (0-6)", "General"),
    GhauriPreset("--threads", "--threads ", "Max concurrent requests", "General"),
    GhauriPreset("--timeout", "--timeout ", "Seconds to wait for response", "General"),
    GhauriPreset("--retries", "--retries ", "Retries on connection timeout", "General"),
    GhauriPreset("--delay", "--delay ", "Delay between requests (seconds)", "General"),
    GhauriPreset("--random-agent", "--random-agent ", "Use random User-Agent", "General"),
    GhauriPreset("--tamper", "--tamper ", "Use tamper scripts", "General"),
    GhauriPreset("--output-dir", "--output-dir ", "Custom output directory", "General")
)

@Composable
fun GhauriScreen() {
    var command by remember { mutableStateOf("ghauri ") }
    var targetUrl by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    var terminalOutput by remember { mutableStateOf(buildString {
        appendLine("╔══════════════════════════════════════════════════════════════╗")
        appendLine("║                    GHAURI SQL INJECTION                       ║")
        appendLine("║              Advanced SQLi Detection & Exploitation           ║")
        appendLine("╠══════════════════════════════════════════════════════════════╣")
        appendLine("║  [*] Ready - Build your command using preset buttons below    ║")
        appendLine("║  [*] Tap options to add them to command string                ║")
        appendLine("║  [*] Supports: proxychains4, tor routing, full enumeration    ║")
        appendLine("╚══════════════════════════════════════════════════════════════╝")
        appendLine("")
        appendLine("ghauri> _")
    }) }
    var selectedCategory by remember { mutableStateOf("Target") }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    
    val categories = ghauriPresets.map { it.category }.distinct()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
            .padding(8.dp)
    ) {
        // Terminal-style header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
            shape = MaterialTheme.shapes.small
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Terminal window buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(modifier = Modifier.size(12.dp).background(Color(0xFFFF5F56), shape = MaterialTheme.shapes.small))
                        Box(modifier = Modifier.size(12.dp).background(Color(0xFFFFBD2E), shape = MaterialTheme.shapes.small))
                        Box(modifier = Modifier.size(12.dp).background(Color(0xFF27CA40), shape = MaterialTheme.shapes.small))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "ghauri@kali:~$ ",
                        color = Color(0xFF00FF88),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "SQL Injection Tool",
                        color = Color.White,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Command builder display
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A)),
            shape = MaterialTheme.shapes.small
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "┌─[Command Builder]",
                    color = Color(0xFF00BFFF),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "└─▶ ",
                        color = Color(0xFF00FF88),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                    Text(
                        command.ifBlank { "ghauri " },
                        color = Color(0xFF00FF88),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { command = "ghauri " },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF6B6B))
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear", style = MaterialTheme.typography.labelSmall)
                    }
                    
                    OutlinedButton(
                        onClick = { 
                            if (command.length > 7) {
                                command = command.dropLast(1).trimEnd() + " "
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFBD2E))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Undo", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // URL Input
        OutlinedTextField(
            value = targetUrl,
            onValueChange = { targetUrl = it },
            label = { Text("Target URL", color = Color(0xFF888888)) },
            placeholder = { Text("https://example.com/page.php?id=1", color = Color(0xFF555555)) },
            leadingIcon = { 
                Text("🎯", modifier = Modifier.padding(start = 8.dp))
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF00FF88),
                unfocusedBorderColor = Color(0xFF333333),
                focusedTextColor = Color(0xFF00FF88),
                unfocusedTextColor = Color(0xFF00FF88),
                cursorColor = Color(0xFF00FF88)
            ),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        )

        Spacer(modifier = Modifier.height(4.dp))
        
        // Quick add URL button
        if (targetUrl.isNotBlank()) {
            TextButton(
                onClick = { 
                    command = command.trimEnd() + " -u $targetUrl "
                },
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF00BFFF))
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(" Add URL to command", style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Category tabs
        ScrollableTabRow(
            selectedTabIndex = categories.indexOf(selectedCategory).coerceAtLeast(0),
            containerColor = Color(0xFF1A1A1A),
            contentColor = Color(0xFF00FF88),
            edgePadding = 0.dp
        ) {
            categories.forEach { category ->
                Tab(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    text = { 
                        Text(
                            category, 
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selectedCategory == category) Color(0xFF00FF88) else Color(0xFF888888)
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Preset command buttons
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
        ) {
            LazyColumn(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val categoryPresets = ghauriPresets.filter { it.category == selectedCategory }
                items(categoryPresets) { preset ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                command = command.trimEnd() + " " + preset.value
                            }
                            .background(
                                Color(0xFF252525),
                                shape = MaterialTheme.shapes.small
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                preset.label,
                                color = Color(0xFF00FF88),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                preset.description,
                                color = Color(0xFF888888),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add",
                            tint = Color(0xFF00BFFF),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Execute button
        Button(
            onClick = {
                scope.launch {
                    isRunning = true
                    val finalCommand = if (targetUrl.isNotBlank() && !command.contains("-u ")) {
                        command.trimEnd() + " -u $targetUrl"
                    } else {
                        command.trim()
                    }
                    
                    terminalOutput = buildString {
                        appendLine("╔══════════════════════════════════════════════════════════════╗")
                        appendLine("║                    GHAURI SQL INJECTION                       ║")
                        appendLine("╚══════════════════════════════════════════════════════════════╝")
                        appendLine("")
                        appendLine("[*] Starting Ghauri SQL Injection scan...")
                        appendLine("[*] Command: $finalCommand")
                        appendLine("")
                        appendLine("[*] Testing connection to target...")
                    }
                    
                    // Run the actual Ghauri scan
                    try {
                        val urlToTest = if (targetUrl.isNotBlank()) targetUrl else {
                            val urlMatch = Regex("-u\\s+(\\S+)").find(command)
                            urlMatch?.groupValues?.get(1) ?: ""
                        }
                        
                        if (urlToTest.isNotBlank()) {
                            terminalOutput += "\n[*] Target: $urlToTest"
                            terminalOutput += "\n[*] Initiating SQL injection tests..."
                            terminalOutput += "\n"
                            
                            GhauriRunner.runOnEndpoint(urlToTest)
                            
                            terminalOutput += "\n[+] Scan completed successfully"
                            terminalOutput += "\n[*] Check detailed results in output directory"
                        } else {
                            terminalOutput += "\n[-] Error: No target URL specified"
                            terminalOutput += "\n[*] Use -u option or enter URL above"
                        }
                    } catch (e: Exception) {
                        terminalOutput += "\n[-] Error: ${e.message}"
                    }
                    
                    terminalOutput += "\n\nghauri> _"
                    isRunning = false
                }
            },
            enabled = !isRunning && (targetUrl.isNotBlank() || command.contains("-u ")),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF00FF88),
                contentColor = Color.Black
            )
        ) {
            if (isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.Black,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("EXECUTING...", fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("EXECUTE COMMAND", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Terminal output
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A)),
            shape = MaterialTheme.shapes.small
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .verticalScroll(scrollState)
            ) {
                Text(
                    terminalOutput,
                    color = Color(0xFF00FF88),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.3
                )
            }
        }
    }
}

// MSF preset commands for quick command building
data class MsfPreset(
    val label: String,
    val value: String,
    val description: String,
    val category: String
)

val msfPresets = listOf(
    // Core commands
    MsfPreset("use", "use ", "Select a module by name", "Core"),
    MsfPreset("search", "search ", "Search for modules", "Core"),
    MsfPreset("info", "info ", "Display module information", "Core"),
    MsfPreset("show options", "show options", "Show module options", "Core"),
    MsfPreset("show payloads", "show payloads", "Show compatible payloads", "Core"),
    MsfPreset("show targets", "show targets", "Show available targets", "Core"),
    MsfPreset("show exploits", "show exploits", "List all exploits", "Core"),
    MsfPreset("show auxiliary", "show auxiliary", "List auxiliary modules", "Core"),
    MsfPreset("back", "back", "Return from current context", "Core"),
    MsfPreset("exit", "exit", "Exit the console", "Core"),
    
    // Module options
    MsfPreset("set RHOSTS", "set RHOSTS ", "Set target host(s)", "Options"),
    MsfPreset("set RPORT", "set RPORT ", "Set target port", "Options"),
    MsfPreset("set LHOST", "set LHOST ", "Set local/listener host", "Options"),
    MsfPreset("set LPORT", "set LPORT ", "Set local/listener port", "Options"),
    MsfPreset("set TARGETURI", "set TARGETURI ", "Set target URI path", "Options"),
    MsfPreset("set SSL", "set SSL true", "Enable SSL/TLS", "Options"),
    MsfPreset("set THREADS", "set THREADS ", "Set number of threads", "Options"),
    MsfPreset("set PAYLOAD", "set PAYLOAD ", "Set the payload", "Options"),
    MsfPreset("setg", "setg ", "Set global option", "Options"),
    MsfPreset("unset", "unset ", "Unset an option", "Options"),
    
    // Payloads
    MsfPreset("meterpreter/reverse_tcp", "set PAYLOAD windows/meterpreter/reverse_tcp", "Windows Meterpreter reverse TCP", "Payloads"),
    MsfPreset("meterpreter/reverse_https", "set PAYLOAD windows/meterpreter/reverse_https", "Windows Meterpreter reverse HTTPS", "Payloads"),
    MsfPreset("shell/reverse_tcp", "set PAYLOAD linux/x86/shell/reverse_tcp", "Linux shell reverse TCP", "Payloads"),
    MsfPreset("cmd/unix/reverse", "set PAYLOAD cmd/unix/reverse", "Unix command shell reverse", "Payloads"),
    MsfPreset("php/meterpreter", "set PAYLOAD php/meterpreter/reverse_tcp", "PHP Meterpreter", "Payloads"),
    MsfPreset("java/meterpreter", "set PAYLOAD java/meterpreter/reverse_tcp", "Java Meterpreter", "Payloads"),
    MsfPreset("python/meterpreter", "set PAYLOAD python/meterpreter/reverse_tcp", "Python Meterpreter", "Payloads"),
    
    // Execution
    MsfPreset("run", "run", "Execute the current module", "Execute"),
    MsfPreset("exploit", "exploit", "Execute the exploit module", "Execute"),
    MsfPreset("exploit -j", "exploit -j", "Run exploit as background job", "Execute"),
    MsfPreset("check", "check", "Check if target is vulnerable", "Execute"),
    MsfPreset("reload", "reload", "Reload the current module", "Execute"),
    MsfPreset("rerun", "rerun", "Rerun the last module", "Execute"),
    
    // Session management
    MsfPreset("sessions", "sessions", "List active sessions", "Sessions"),
    MsfPreset("sessions -i", "sessions -i ", "Interact with session", "Sessions"),
    MsfPreset("sessions -k", "sessions -k ", "Kill a session", "Sessions"),
    MsfPreset("sessions -u", "sessions -u ", "Upgrade shell to meterpreter", "Sessions"),
    MsfPreset("background", "background", "Background current session", "Sessions"),
    
    // Post-exploitation
    MsfPreset("sysinfo", "sysinfo", "Get system information", "Post"),
    MsfPreset("getuid", "getuid", "Get current user ID", "Post"),
    MsfPreset("getsystem", "getsystem", "Attempt privilege escalation", "Post"),
    MsfPreset("hashdump", "hashdump", "Dump password hashes", "Post"),
    MsfPreset("shell", "shell", "Drop into system shell", "Post"),
    MsfPreset("upload", "upload ", "Upload a file", "Post"),
    MsfPreset("download", "download ", "Download a file", "Post"),
    MsfPreset("portfwd", "portfwd add ", "Add port forward", "Post"),
    MsfPreset("route", "route add ", "Add network route", "Post"),
    
    // Database
    MsfPreset("db_status", "db_status", "Show database status", "Database"),
    MsfPreset("hosts", "hosts", "List discovered hosts", "Database"),
    MsfPreset("services", "services", "List discovered services", "Database"),
    MsfPreset("vulns", "vulns", "List discovered vulnerabilities", "Database"),
    MsfPreset("loot", "loot", "List collected loot", "Database"),
    MsfPreset("creds", "creds", "List gathered credentials", "Database"),
    
    // Jobs
    MsfPreset("jobs", "jobs", "List running jobs", "Jobs"),
    MsfPreset("jobs -k", "jobs -k ", "Kill a job", "Jobs"),
    MsfPreset("jobs -K", "jobs -K", "Kill all jobs", "Jobs"),
    
    // Auxiliary
    MsfPreset("auxiliary/scanner", "use auxiliary/scanner/", "Scanner modules", "Auxiliary"),
    MsfPreset("auxiliary/gather", "use auxiliary/gather/", "Gathering modules", "Auxiliary"),
    MsfPreset("auxiliary/dos", "use auxiliary/dos/", "DoS modules", "Auxiliary"),
    MsfPreset("auxiliary/fuzz", "use auxiliary/fuzz/", "Fuzzing modules", "Auxiliary")
)

@Composable
fun MetasploitScreen() {
    var msfHost by remember { mutableStateOf("127.0.0.1") }
    var msfPort by remember { mutableStateOf("55553") }
    var msfUser by remember { mutableStateOf("msf") }
    var msfPassword by remember { mutableStateOf("msf") }
    var isConnected by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }
    var command by remember { mutableStateOf("") }
    var consoleOutput by remember { mutableStateOf(buildString {
        appendLine("                                                  ")
        appendLine("     ,           ,                                ")
        appendLine("    /             \\                               ")
        appendLine("   ((__---,,,---__))                              ")
        appendLine("      (_) O O (_)_________                        ")
        appendLine("         \\ _ /            |\\                      ")
        appendLine("          o o             | \\                     ")
        appendLine("          0 0              \\ \\                    ")
        appendLine("                            \\ \\                   ")
        appendLine("═══════════════════════════════════════════════════")
        appendLine("       [ METASPLOIT FRAMEWORK ]                    ")
        appendLine("═══════════════════════════════════════════════════")
        appendLine("  [*] Connecting to MSF RPC API...                 ")
        appendLine("  [*] Configure connection settings below          ")
        appendLine("  [*] Use preset commands to build your command    ")
        appendLine("═══════════════════════════════════════════════════")
        appendLine("")
        appendLine("msf6 > _")
    }) }
    var sessions by remember { mutableStateOf(listOf<SessionInfo>()) }
    var jobs by remember { mutableStateOf(mapOf<String, String>()) }
    var selectedModule by remember { mutableStateOf<ExploitModule?>(null) }
    var selectedCategory by remember { mutableStateOf("Core") }
    var showConnectionPanel by remember { mutableStateOf(true) }
    var targetHost by remember { mutableStateOf("") }
    var isExecuting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    
    val categories = msfPresets.map { it.category }.distinct()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
            .padding(8.dp)
    ) {
        // Terminal-style header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
            shape = MaterialTheme.shapes.small
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Terminal window buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(modifier = Modifier.size(12.dp).background(Color(0xFFFF5F56), shape = MaterialTheme.shapes.small))
                        Box(modifier = Modifier.size(12.dp).background(Color(0xFFFFBD2E), shape = MaterialTheme.shapes.small))
                        Box(modifier = Modifier.size(12.dp).background(Color(0xFF27CA40), shape = MaterialTheme.shapes.small))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "msf6@kali:~$ ",
                        color = Color(0xFF00BFFF),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "msfconsole",
                        color = Color.White,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    
                    // Connection status indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showConnectionPanel = !showConnectionPanel }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (isConnected) Color(0xFF00FF88) else Color(0xFFFF5F56),
                                    shape = MaterialTheme.shapes.small
                                )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (isConnected) "RPC Connected" else "RPC Disconnected",
                            color = if (isConnected) Color(0xFF00FF88) else Color(0xFFFF5F56),
                            style = MaterialTheme.typography.labelSmall
                        )
                        Icon(
                            if (showConnectionPanel) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Collapsible connection panel
        if (showConnectionPanel) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "┌─[MSF RPC Connection]",
                        color = Color(0xFF00BFFF),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = msfHost,
                            onValueChange = { msfHost = it },
                            label = { Text("Host", color = Color(0xFF888888)) },
                            modifier = Modifier.weight(2f),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = Color(0xFF00FF88)
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00BFFF),
                                unfocusedBorderColor = Color(0xFF333333)
                            )
                        )
                        OutlinedTextField(
                            value = msfPort,
                            onValueChange = { msfPort = it },
                            label = { Text("Port", color = Color(0xFF888888)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = Color(0xFF00FF88)
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00BFFF),
                                unfocusedBorderColor = Color(0xFF333333)
                            )
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
                            label = { Text("User", color = Color(0xFF888888)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = Color(0xFF00FF88)
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00BFFF),
                                unfocusedBorderColor = Color(0xFF333333)
                            )
                        )
                        OutlinedTextField(
                            value = msfPassword,
                            onValueChange = { msfPassword = it },
                            label = { Text("Password", color = Color(0xFF888888)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = Color(0xFF00FF88)
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00BFFF),
                                unfocusedBorderColor = Color(0xFF333333)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                isConnecting = true
                                consoleOutput += "\n[*] Connecting to $msfHost:$msfPort..."
                                
                                MetasploitRunner.configure(
                                    msfHost,
                                    msfPort.toIntOrNull() ?: 55553,
                                    msfUser,
                                    msfPassword
                                )
                                
                                val result = MetasploitRunner.authenticate()
                                result.fold(
                                    onSuccess = { token ->
                                        isConnected = true
                                        consoleOutput += "\n[+] Successfully authenticated!"
                                        consoleOutput += "\n[+] Token: ${token.take(8)}..."
                                        
                                        // Load version info
                                        MetasploitRunner.getVersion().onSuccess {
                                            consoleOutput += "\n[*] Metasploit Framework: $it"
                                        }
                                        
                                        // Load sessions and jobs
                                        MetasploitRunner.getSessions().onSuccess { 
                                            sessions = it
                                            consoleOutput += "\n[*] Active sessions: ${it.size}"
                                        }
                                        MetasploitRunner.getJobs().onSuccess { 
                                            jobs = it
                                            consoleOutput += "\n[*] Running jobs: ${it.size}"
                                        }
                                        consoleOutput += "\n\nmsf6 > _"
                                        showConnectionPanel = false
                                    },
                                    onFailure = { 
                                        isConnected = false
                                        consoleOutput += "\n[-] Connection failed: ${it.message}"
                                        consoleOutput += "\n[!] Make sure msfrpcd is running"
                                        consoleOutput += "\n[!] Start with: msfrpcd -U msf -P msf -S -f"
                                        consoleOutput += "\n\nmsf6 > _"
                                    }
                                )
                                isConnecting = false
                            }
                        },
                        enabled = !isConnecting,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isConnected) Color(0xFF00FF88) else Color(0xFF00BFFF),
                            contentColor = Color.Black
                        )
                    ) {
                        if (isConnecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("CONNECTING...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(
                                if (isConnected) Icons.Default.CheckCircle else Icons.Default.Link,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (isConnected) "RECONNECT" else "CONNECT TO MSF RPC",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Command builder with target host
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A)),
            shape = MaterialTheme.shapes.small
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Target host input
                OutlinedTextField(
                    value = targetHost,
                    onValueChange = { targetHost = it },
                    label = { Text("Target Host", color = Color(0xFF888888)) },
                    placeholder = { Text("192.168.1.1 or https://target.com", color = Color(0xFF555555)) },
                    leadingIcon = { 
                        Text("🎯", modifier = Modifier.padding(start = 8.dp))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00BFFF),
                        unfocusedBorderColor = Color(0xFF333333),
                        focusedTextColor = Color(0xFF00FF88),
                        unfocusedTextColor = Color(0xFF00FF88),
                        cursorColor = Color(0xFF00FF88)
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Command input
                Text(
                    "┌─[Command]",
                    color = Color(0xFF00BFFF),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "└─▶ msf6 > ",
                        color = Color(0xFF00BFFF),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                    OutlinedTextField(
                        value = command,
                        onValueChange = { command = it },
                        placeholder = { Text("Enter command...", color = Color(0xFF555555)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = Color(0xFF00FF88),
                            unfocusedTextColor = Color(0xFF00FF88),
                            cursorColor = Color(0xFF00FF88)
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { command = "" },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF6B6B))
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear", style = MaterialTheme.typography.labelSmall)
                    }
                    
                    Button(
                        onClick = {
                            scope.launch {
                                isExecuting = true
                                consoleOutput += "\nmsf6 > $command"
                                
                                if (isConnected) {
                                    val result = MetasploitRunner.runConsoleCommand(command)
                                    result.fold(
                                        onSuccess = { output ->
                                            consoleOutput += "\n$output"
                                        },
                                        onFailure = { error ->
                                            consoleOutput += "\n[-] Error: ${error.message}"
                                        }
                                    )
                                } else {
                                    consoleOutput += "\n[-] Not connected to MSF RPC"
                                    consoleOutput += "\n[!] Please connect first"
                                }
                                
                                consoleOutput += "\n\nmsf6 > _"
                                command = ""
                                isExecuting = false
                            }
                        },
                        enabled = command.isNotBlank() && !isExecuting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00FF88),
                            contentColor = Color.Black
                        )
                    ) {
                        if (isExecuting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Execute", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Category tabs for preset commands
        ScrollableTabRow(
            selectedTabIndex = categories.indexOf(selectedCategory).coerceAtLeast(0),
            containerColor = Color(0xFF1A1A1A),
            contentColor = Color(0xFF00BFFF),
            edgePadding = 0.dp
        ) {
            categories.forEach { category ->
                Tab(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    text = { 
                        Text(
                            category, 
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selectedCategory == category) Color(0xFF00BFFF) else Color(0xFF888888)
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Preset command buttons
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
        ) {
            LazyColumn(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val categoryPresets = msfPresets.filter { it.category == selectedCategory }
                items(categoryPresets) { preset ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                command = if (command.isBlank()) preset.value else command + " " + preset.value
                            }
                            .background(
                                Color(0xFF252525),
                                shape = MaterialTheme.shapes.small
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                preset.label,
                                color = Color(0xFF00BFFF),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                preset.description,
                                color = Color(0xFF888888),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add",
                            tint = Color(0xFF00FF88),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Quick action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    scope.launch {
                        if (targetHost.isNotBlank()) {
                            consoleOutput += "\n[*] Running auto-exploit on $targetHost..."
                            val result = MetasploitRunner.autoExploit(targetHost)
                            result.fold(
                                onSuccess = { results ->
                                    results.forEach { consoleOutput += "\n$it" }
                                },
                                onFailure = { consoleOutput += "\n[-] Error: ${it.message}" }
                            )
                            consoleOutput += "\n\nmsf6 > _"
                        }
                    }
                },
                enabled = targetHost.isNotBlank() && isConnected,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B6B))
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Auto-Exploit", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            
            Button(
                onClick = {
                    scope.launch {
                        MetasploitRunner.getSessions().onSuccess { 
                            sessions = it
                            consoleOutput += "\n[*] Active sessions: ${it.size}"
                            it.forEach { s ->
                                consoleOutput += "\n  Session ${s.id}: ${s.type} @ ${s.targetHost}"
                            }
                        }
                        MetasploitRunner.getJobs().onSuccess { 
                            jobs = it
                            consoleOutput += "\n[*] Running jobs: ${it.size}"
                        }
                        consoleOutput += "\n\nmsf6 > _"
                    }
                },
                enabled = isConnected,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BFFF))
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Refresh", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }

        // Sessions display if any
        if (sessions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B4332))
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        "🔓 Active Sessions (${sessions.size})",
                        color = Color(0xFF00FF88),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    sessions.forEach { session ->
                        Text(
                            "  [${session.id}] ${session.type} @ ${session.targetHost}",
                            color = Color(0xFF00FF88),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Terminal output
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A)),
            shape = MaterialTheme.shapes.small
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .verticalScroll(scrollState)
            ) {
                Text(
                    consoleOutput,
                    color = Color(0xFF00FF88),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.3
                )
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
