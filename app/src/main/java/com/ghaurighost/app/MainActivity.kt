package com.ghaurighost.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
fun MainScreen() {
    var url by remember { mutableStateOf("https://www.spinaud.com") }
    var endpoints by remember { mutableStateOf(listOf<String>()) }
    val scope = rememberCoroutineScope()
    var isScanning by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Ready") }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("GhauriGhost", style = MaterialTheme.typography.headlineLarge)

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Target URL") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                scope.launch {
                    isScanning = true
                    status = "Scanning..."
                    endpoints = scanEndpoints(url)
                    status = if (endpoints.isEmpty()) "No exploitable endpoints found" else "${endpoints.size} endpoints found"
                    isScanning = false
                }
            },
            enabled = !isScanning,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isScanning) CircularProgressIndicator(modifier = Modifier.size(24.dp))
            else Text("Scan for Exploitable Endpoints")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(status, style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(24.dp))

        Text("Exploitable Endpoints:", style = MaterialTheme.typography.titleMedium)

        LazyColumn {
            items(endpoints) { ep ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(ep, modifier = Modifier.weight(1f))
                        Button(onClick = {
                            // Run Ghauri on this single endpoint
                            GhauriRunner.runOnEndpoint(ep)
                        }) {
                            Text("Test")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (endpoints.isNotEmpty()) {
                    GhauriRunner.runOnEndpoints(endpoints)
                    status = "Ghauri launched on all endpoints"
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = endpoints.isNotEmpty()
        ) {
            Text("Launch Ghauri on All Endpoints")
        }
    }
}
