package com.ghaurighost.app

import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ghauri SQL Injection Tool Runner
 * Provides comprehensive SQL injection testing capabilities through Python integration.
 */
object GhauriRunner {
    
    /**
     * Run Ghauri on multiple endpoints with default options.
     * Returns a list of results for each endpoint.
     */
    suspend fun runOnEndpoints(endpoints: List<String>): List<Result<String>> = withContext(Dispatchers.IO) {
        val py = Python.getInstance()
        val module = py.getModule("ghauri")
        endpoints.map { url ->
            try {
                val result = module.callAttr("main", arrayOf("--url", url, "--batch", "--level=3"))
                Result.success(result?.toString() ?: "Scan completed for $url")
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Run Ghauri on a single endpoint with default options.
     */
    suspend fun runOnEndpoint(url: String): Result<String> {
        return runOnEndpoints(listOf(url)).first()
    }
    
    /**
     * Run Ghauri with a custom command string parsed into arguments.
     * This supports the full command builder functionality.
     * 
     * @param commandString Full command string (e.g., "ghauri -u http://target.com --dump --batch")
     * @return Result containing output or error
     */
    suspend fun runWithCommand(commandString: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val py = Python.getInstance()
            val module = py.getModule("ghauri")
            
            // Parse command string into arguments (skip "ghauri" prefix if present)
            val args = parseCommandArgs(commandString)
            
            val result = module.callAttr("main", args.toTypedArray())
            Result.success(result?.toString() ?: "Scan completed")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Run Ghauri with specific options for targeted scanning.
     */
    suspend fun runWithOptions(
        url: String,
        level: Int = 1,
        risk: Int = 1,
        technique: String? = null,
        dbs: Boolean = false,
        tables: Boolean = false,
        columns: Boolean = false,
        dump: Boolean = false,
        batch: Boolean = true,
        threads: Int = 1,
        delay: Int = 0,
        proxy: String? = null,
        userAgent: String? = null,
        cookie: String? = null,
        data: String? = null,
        dbms: String? = null,
        tamper: String? = null,
        outputDir: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val py = Python.getInstance()
            val module = py.getModule("ghauri")
            
            val args = mutableListOf<String>()
            
            // Required options
            args.add("--url")
            args.add(url)
            
            // Detection options
            args.add("--level=$level")
            args.add("--risk=$risk")
            
            // Technique
            if (!technique.isNullOrBlank()) {
                args.add("--technique=$technique")
            }
            
            // Enumeration options
            if (dbs) args.add("--dbs")
            if (tables) args.add("--tables")
            if (columns) args.add("--columns")
            if (dump) args.add("--dump")
            
            // General options
            if (batch) args.add("--batch")
            if (threads > 1) args.add("--threads=$threads")
            if (delay > 0) args.add("--delay=$delay")
            
            // Request options
            if (!proxy.isNullOrBlank()) args.add("--proxy=$proxy")
            if (!userAgent.isNullOrBlank()) args.add("--user-agent=$userAgent")
            if (!cookie.isNullOrBlank()) args.add("--cookie=$cookie")
            if (!data.isNullOrBlank()) args.add("--data=$data")
            
            // Database options
            if (!dbms.isNullOrBlank()) args.add("--dbms=$dbms")
            
            // Tamper scripts
            if (!tamper.isNullOrBlank()) args.add("--tamper=$tamper")
            
            // Output
            if (!outputDir.isNullOrBlank()) args.add("--output-dir=$outputDir")
            
            val result = module.callAttr("main", args.toTypedArray())
            Result.success(result?.toString() ?: "Scan completed")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Run database enumeration on a target.
     */
    suspend fun enumerateDatabases(url: String): Result<String> {
        return runWithOptions(url, dbs = true)
    }
    
    /**
     * Run table enumeration on a target.
     */
    suspend fun enumerateTables(url: String, database: String? = null): Result<String> = withContext(Dispatchers.IO) {
        try {
            val py = Python.getInstance()
            val module = py.getModule("ghauri")
            
            val args = mutableListOf("--url", url, "--tables", "--batch")
            if (!database.isNullOrBlank()) {
                args.add("-D")
                args.add(database)
            }
            
            val result = module.callAttr("main", args.toTypedArray())
            Result.success(result?.toString() ?: "Enumeration completed")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Dump data from a target.
     */
    suspend fun dumpData(
        url: String,
        database: String? = null,
        table: String? = null,
        columns: List<String>? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val py = Python.getInstance()
            val module = py.getModule("ghauri")
            
            val args = mutableListOf("--url", url, "--dump", "--batch")
            if (!database.isNullOrBlank()) {
                args.add("-D")
                args.add(database)
            }
            if (!table.isNullOrBlank()) {
                args.add("-T")
                args.add(table)
            }
            if (!columns.isNullOrEmpty()) {
                args.add("-C")
                args.add(columns.joinToString(","))
            }
            
            val result = module.callAttr("main", args.toTypedArray())
            Result.success(result?.toString() ?: "Dump completed")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Parse a command string into a list of arguments.
     * Handles quoted strings and various argument formats.
     */
    private fun parseCommandArgs(commandString: String): List<String> {
        val args = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var quoteChar = ' '
        
        // Remove "ghauri" or "proxychains4 ghauri" etc. prefix
        val cleanCommand = commandString
            .replace(Regex("^\\s*(proxychains4\\s+)?(tor\\s+)?ghauri\\s*"), "")
            .trim()
        
        for (char in cleanCommand) {
            when {
                char == '"' || char == '\'' -> {
                    if (inQuotes && char == quoteChar) {
                        inQuotes = false
                    } else if (!inQuotes) {
                        inQuotes = true
                        quoteChar = char
                    } else {
                        current.append(char)
                    }
                }
                char == ' ' && !inQuotes -> {
                    if (current.isNotEmpty()) {
                        args.add(current.toString())
                        current = StringBuilder()
                    }
                }
                else -> current.append(char)
            }
        }
        
        if (current.isNotEmpty()) {
            args.add(current.toString())
        }
        
        return args
    }
}
