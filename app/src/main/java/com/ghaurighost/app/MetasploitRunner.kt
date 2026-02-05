package com.ghaurighost.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Metasploit Framework integration for GhauriGhost.
 * Provides comprehensive exploit framework capabilities through MSF RPC API.
 */
object MetasploitRunner {
    
    // MSF RPC Configuration
    private var rpcHost = "127.0.0.1"
    private var rpcPort = 55553
    private var rpcUser = "msf"
    private var rpcPassword = "msf"
    private var authToken: String? = null
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    
    // Common exploit modules for web vulnerabilities
    val webExploitModules = listOf(
        ExploitModule(
            name = "auxiliary/scanner/http/dir_scanner",
            description = "HTTP Directory Scanner",
            category = "Scanner",
            options = listOf("RHOSTS", "RPORT", "THREADS", "PATH")
        ),
        ExploitModule(
            name = "auxiliary/scanner/http/http_put",
            description = "HTTP PUT Method Scanner",
            category = "Scanner",
            options = listOf("RHOSTS", "RPORT", "PATH")
        ),
        ExploitModule(
            name = "auxiliary/scanner/http/sql_injection",
            description = "SQL Injection Scanner",
            category = "Scanner",
            options = listOf("RHOSTS", "RPORT", "TARGETURI")
        ),
        ExploitModule(
            name = "auxiliary/scanner/http/http_login",
            description = "HTTP Login Scanner",
            category = "Scanner",
            options = listOf("RHOSTS", "RPORT", "TARGETURI", "USERNAME", "PASSWORD")
        ),
        ExploitModule(
            name = "auxiliary/scanner/http/joomla_version",
            description = "Joomla Version Scanner",
            category = "Scanner",
            options = listOf("RHOSTS", "RPORT")
        ),
        ExploitModule(
            name = "auxiliary/scanner/http/wordpress_scanner",
            description = "WordPress Scanner",
            category = "Scanner",
            options = listOf("RHOSTS", "RPORT")
        ),
        ExploitModule(
            name = "auxiliary/scanner/http/apache_mod_cgi_bash_env",
            description = "Apache Shellshock Scanner",
            category = "Scanner",
            options = listOf("RHOSTS", "RPORT", "TARGETURI")
        ),
        ExploitModule(
            name = "auxiliary/scanner/http/http_version",
            description = "HTTP Version Detection",
            category = "Scanner",
            options = listOf("RHOSTS", "RPORT")
        ),
        ExploitModule(
            name = "auxiliary/scanner/ssl/openssl_heartbleed",
            description = "OpenSSL Heartbleed Scanner",
            category = "Scanner",
            options = listOf("RHOSTS", "RPORT")
        ),
        ExploitModule(
            name = "exploit/multi/http/apache_mod_cgi_bash_env_exec",
            description = "Apache Shellshock Exploit",
            category = "Exploit",
            options = listOf("RHOSTS", "RPORT", "TARGETURI", "PAYLOAD")
        ),
        ExploitModule(
            name = "exploit/unix/webapp/php_cgi_arg_injection",
            description = "PHP CGI Argument Injection",
            category = "Exploit",
            options = listOf("RHOSTS", "RPORT", "PAYLOAD")
        ),
        ExploitModule(
            name = "exploit/multi/http/struts2_content_type_ognl",
            description = "Apache Struts 2 OGNL Injection",
            category = "Exploit",
            options = listOf("RHOSTS", "RPORT", "TARGETURI", "PAYLOAD")
        ),
        ExploitModule(
            name = "exploit/multi/http/log4shell_header_injection",
            description = "Log4Shell Header Injection",
            category = "Exploit",
            options = listOf("RHOSTS", "RPORT", "HTTP_HEADER", "PAYLOAD")
        ),
        ExploitModule(
            name = "auxiliary/gather/http_pdf_authors",
            description = "HTTP PDF Metadata Extractor",
            category = "Gather",
            options = listOf("RHOSTS", "RPORT")
        ),
        ExploitModule(
            name = "auxiliary/gather/enum_dns",
            description = "DNS Enumeration",
            category = "Gather",
            options = listOf("DOMAIN")
        )
    )
    
    /**
     * Configure MSF RPC connection settings.
     */
    fun configure(host: String, port: Int, user: String, password: String) {
        rpcHost = host
        rpcPort = port
        rpcUser = user
        rpcPassword = password
        authToken = null // Reset token when config changes
    }
    
    /**
     * Authenticate with MSF RPC API and get auth token.
     */
    suspend fun authenticate(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = buildRpcRequest(
                "auth.login",
                listOf(rpcUser, rpcPassword)
            )
            
            val response = executeRpcRequest(request)
            
            if (response.optString("result") == "success") {
                authToken = response.getString("token")
                Result.success(authToken!!)
            } else {
                Result.failure(Exception("Authentication failed: ${response.optString("error")}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get MSF RPC API version.
     */
    suspend fun getVersion(): Result<String> = withContext(Dispatchers.IO) {
        try {
            ensureAuthenticated()
            
            val request = buildRpcRequest(
                "core.version",
                listOf(authToken!!)
            )
            
            val response = executeRpcRequest(request)
            val version = "${response.optString("version")} (API: ${response.optString("api")})"
            Result.success(version)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Search for exploit modules matching a query.
     */
    suspend fun searchModules(query: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            ensureAuthenticated()
            
            val request = buildRpcRequest(
                "module.search",
                listOf(authToken!!, query)
            )
            
            val response = executeRpcRequest(request)
            val modules = mutableListOf<String>()
            
            val modulesArray = response.optJSONArray("modules") ?: JSONArray()
            for (i in 0 until modulesArray.length()) {
                val module = modulesArray.optJSONObject(i)
                module?.optString("fullname")?.let { modules.add(it) }
            }
            
            Result.success(modules)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get information about a specific module.
     */
    suspend fun getModuleInfo(moduleType: String, moduleName: String): Result<ModuleInfo> = withContext(Dispatchers.IO) {
        try {
            ensureAuthenticated()
            
            val request = buildRpcRequest(
                "module.info",
                listOf(authToken!!, moduleType, moduleName)
            )
            
            val response = executeRpcRequest(request)
            
            val info = ModuleInfo(
                name = response.optString("name"),
                description = response.optString("description"),
                authors = response.optJSONArray("authors")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList(),
                references = response.optJSONArray("references")?.let { arr ->
                    (0 until arr.length()).mapNotNull { 
                        arr.optJSONArray(it)?.let { ref ->
                            "${ref.optString(0)}: ${ref.optString(1)}"
                        }
                    }
                } ?: emptyList(),
                rank = response.optString("rank"),
                privileged = response.optBoolean("privileged")
            )
            
            Result.success(info)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get options for a specific module.
     */
    suspend fun getModuleOptions(moduleType: String, moduleName: String): Result<Map<String, ModuleOption>> = withContext(Dispatchers.IO) {
        try {
            ensureAuthenticated()
            
            val request = buildRpcRequest(
                "module.options",
                listOf(authToken!!, moduleType, moduleName)
            )
            
            val response = executeRpcRequest(request)
            val options = mutableMapOf<String, ModuleOption>()
            
            response.keys().forEach { key ->
                val opt = response.optJSONObject(key)
                if (opt != null) {
                    options[key] = ModuleOption(
                        name = key,
                        type = opt.optString("type"),
                        required = opt.optBoolean("required"),
                        description = opt.optString("desc"),
                        default = opt.opt("default")?.toString()
                    )
                }
            }
            
            Result.success(options)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Execute an exploit module with given options.
     */
    suspend fun executeModule(
        moduleType: String,
        moduleName: String,
        options: Map<String, String>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            ensureAuthenticated()
            
            val optionsJson = JSONObject()
            options.forEach { (key, value) -> optionsJson.put(key, value) }
            
            val request = buildRpcRequest(
                "module.execute",
                listOf(authToken!!, moduleType, moduleName, optionsJson)
            )
            
            val response = executeRpcRequest(request)
            
            val jobId = response.optInt("job_id", -1)
            val uuid = response.optString("uuid")
            
            if (jobId >= 0) {
                Result.success("Job started: ID=$jobId, UUID=$uuid")
            } else {
                Result.failure(Exception("Failed to start module: ${response.optString("error")}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Run a scanner module against a target URL.
     */
    suspend fun runWebScan(targetUrl: String, moduleName: String = "auxiliary/scanner/http/dir_scanner"): Result<String> {
        return try {
            val uri = java.net.URI(targetUrl)
            val host = uri.host
            val port = if (uri.port > 0) uri.port else if (uri.scheme == "https") 443 else 80
            
            val options = mapOf(
                "RHOSTS" to host,
                "RPORT" to port.toString(),
                "SSL" to (uri.scheme == "https").toString()
            )
            
            executeModule("auxiliary", moduleName.removePrefix("auxiliary/"), options)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get list of active sessions.
     */
    suspend fun getSessions(): Result<List<SessionInfo>> = withContext(Dispatchers.IO) {
        try {
            ensureAuthenticated()
            
            val request = buildRpcRequest(
                "session.list",
                listOf(authToken!!)
            )
            
            val response = executeRpcRequest(request)
            val sessions = mutableListOf<SessionInfo>()
            
            response.keys().forEach { key ->
                val session = response.optJSONObject(key)
                if (session != null) {
                    sessions.add(SessionInfo(
                        id = key.toIntOrNull() ?: 0,
                        type = session.optString("type"),
                        info = session.optString("info"),
                        targetHost = session.optString("target_host"),
                        username = session.optString("username"),
                        exploitModule = session.optString("via_exploit")
                    ))
                }
            }
            
            Result.success(sessions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get list of running jobs.
     */
    suspend fun getJobs(): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        try {
            ensureAuthenticated()
            
            val request = buildRpcRequest(
                "job.list",
                listOf(authToken!!)
            )
            
            val response = executeRpcRequest(request)
            val jobs = mutableMapOf<String, String>()
            
            response.keys().forEach { key ->
                jobs[key] = response.optString(key)
            }
            
            Result.success(jobs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Stop a running job.
     */
    suspend fun stopJob(jobId: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            ensureAuthenticated()
            
            val request = buildRpcRequest(
                "job.stop",
                listOf(authToken!!, jobId)
            )
            
            val response = executeRpcRequest(request)
            Result.success(response.optString("result") == "success")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Run console command in MSF.
     */
    suspend fun runConsoleCommand(command: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            ensureAuthenticated()
            
            // Create console
            val createRequest = buildRpcRequest(
                "console.create",
                listOf(authToken!!)
            )
            val createResponse = executeRpcRequest(createRequest)
            val consoleId = createResponse.optString("id")
            
            if (consoleId.isBlank()) {
                return@withContext Result.failure(Exception("Failed to create console"))
            }
            
            // Write command
            val writeRequest = buildRpcRequest(
                "console.write",
                listOf(authToken!!, consoleId, "$command\n")
            )
            executeRpcRequest(writeRequest)
            
            // Wait for output
            kotlinx.coroutines.delay(2000)
            
            // Read output
            val readRequest = buildRpcRequest(
                "console.read",
                listOf(authToken!!, consoleId)
            )
            val readResponse = executeRpcRequest(readRequest)
            val output = readResponse.optString("data")
            
            // Destroy console
            val destroyRequest = buildRpcRequest(
                "console.destroy",
                listOf(authToken!!, consoleId)
            )
            executeRpcRequest(destroyRequest)
            
            Result.success(output)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Auto-exploit a target using recommended modules.
     */
    suspend fun autoExploit(targetUrl: String): Result<List<String>> = withContext(Dispatchers.IO) {
        val results = mutableListOf<String>()
        
        try {
            val uri = java.net.URI(targetUrl)
            val host = uri.host
            val port = if (uri.port > 0) uri.port else if (uri.scheme == "https") 443 else 80
            
            // Run multiple scanners
            val scanners = listOf(
                "auxiliary/scanner/http/http_version",
                "auxiliary/scanner/http/dir_scanner",
                "auxiliary/scanner/http/http_put"
            )
            
            scanners.forEach { scanner ->
                val options = mapOf(
                    "RHOSTS" to host,
                    "RPORT" to port.toString(),
                    "SSL" to (uri.scheme == "https").toString(),
                    "THREADS" to "10"
                )
                
                val result = executeModule("auxiliary", scanner.removePrefix("auxiliary/"), options)
                result.fold(
                    onSuccess = { results.add("$scanner: $it") },
                    onFailure = { results.add("$scanner: Failed - ${it.message}") }
                )
                
                // Delay between scans
                kotlinx.coroutines.delay(1000)
            }
            
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Check if MSF RPC is reachable.
     */
    suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("http://$rpcHost:$rpcPort/api/")
                .head()
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                response.code != 0
            }
        } catch (e: Exception) {
            false
        }
    }
    
    // Private helper methods
    
    private suspend fun ensureAuthenticated() {
        if (authToken == null) {
            authenticate().getOrThrow()
        }
    }
    
    private fun buildRpcRequest(method: String, params: List<Any>): JSONObject {
        return JSONObject().apply {
            put("jsonrpc", "2.0")
            put("method", method)
            put("id", System.currentTimeMillis())
            put("params", JSONArray(params))
        }
    }
    
    private fun executeRpcRequest(requestBody: JSONObject): JSONObject {
        val request = Request.Builder()
            .url("http://$rpcHost:$rpcPort/api/")
            .post(requestBody.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .build()
        
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: "{}"
            return JSONObject(body)
        }
    }
}

// Data classes for Metasploit integration

data class ExploitModule(
    val name: String,
    val description: String,
    val category: String,
    val options: List<String>
)

data class ModuleInfo(
    val name: String,
    val description: String,
    val authors: List<String>,
    val references: List<String>,
    val rank: String,
    val privileged: Boolean
)

data class ModuleOption(
    val name: String,
    val type: String,
    val required: Boolean,
    val description: String,
    val default: String?
)

data class SessionInfo(
    val id: Int,
    val type: String,
    val info: String,
    val targetHost: String,
    val username: String,
    val exploitModule: String
)
