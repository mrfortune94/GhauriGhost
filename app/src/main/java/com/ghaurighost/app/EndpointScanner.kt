package com.ghaurighost.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Advanced EndpointScanner with deep domain scraping capabilities.
 * Uses multiple data sources and techniques for comprehensive endpoint discovery.
 */
object EndpointScanner {
    
    // Configuration: MAX_DEPTH limits recursion to avoid excessive crawling and detection
    // MAX_PAGES prevents memory issues and excessive scanning time on large sites
    internal const val MAX_DEPTH = 3
    internal const val MAX_PAGES = 100
    private const val TIMEOUT_SECONDS = 30L
    internal const val MAX_SITEMAP_DEPTH = 5 // Prevent infinite sitemap recursion
    
    internal val httpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    
    // API/endpoint patterns for extraction from JavaScript
    internal val apiPatterns = listOf(
        Pattern.compile("['\"]([/][a-zA-Z0-9_/-]+[?][^'\"]+)['\"]"),  // URLs with query params
        Pattern.compile("['\"]([/]api[/][^'\"]+)['\"]"),              // API endpoints
        Pattern.compile("['\"]([/]v[0-9]+[/][^'\"]+)['\"]"),          // Versioned API
        Pattern.compile("fetch\\s*\\(['\"]([^'\"]+)['\"]"),            // fetch() calls
        Pattern.compile("axios[.][a-z]+\\s*\\(['\"]([^'\"]+)['\"]"),   // axios calls
        Pattern.compile("['\"]([/][a-zA-Z0-9_/-]*(?:login|auth|user|admin|search|query|api|data|ajax|json|xml|graphql)[/a-zA-Z0-9_/-]*)['\"]", Pattern.CASE_INSENSITIVE),
        Pattern.compile("url\\s*[=:]\\s*['\"]([^'\"]+)['\"]"),         // url assignments
        Pattern.compile("endpoint\\s*[=:]\\s*['\"]([^'\"]+)['\"]"),    // endpoint assignments
        Pattern.compile("href\\s*[=:]\\s*['\"]([^'\"]+)['\"]"),        // href assignments
        Pattern.compile("action\\s*[=:]\\s*['\"]([^'\"]+)['\"]")       // action assignments
    )
    
    // Keywords indicating potentially exploitable endpoints
    internal val exploitableKeywords = listOf(
        "login", "auth", "signin", "signup", "register", "user", "admin", "account",
        "search", "query", "find", "filter", "sort", "order",
        "id=", "uid=", "pid=", "cat=", "page=", "item=", "product=", "article=",
        "promo", "coupon", "discount", "checkout", "cart", "payment", "order",
        "upload", "download", "file", "image", "document", "attachment",
        "api", "ajax", "json", "xml", "graphql", "rest", "ws", "websocket",
        "redirect", "url=", "next=", "return=", "goto=", "callback=",
        "config", "settings", "debug", "test", "dev", "admin", "panel", "dashboard",
        "exec", "cmd", "command", "run", "eval", "include", "path", "dir",
        "mail", "email", "smtp", "contact", "message", "comment", "feedback",
        "token", "session", "cookie", "key", "secret", "password", "pass"
    )
}

/**
 * Main entry point for deep endpoint scanning.
 * Performs comprehensive discovery using multiple techniques.
 */
suspend fun scanEndpoints(url: String): List<String> = withContext(Dispatchers.IO) {
    val endpoints = ConcurrentHashMap.newKeySet<String>()
    val visitedUrls = ConcurrentHashMap.newKeySet<String>()
    val baseUrl = extractBaseUrl(url)
    val domain = extractDomain(url)
    
    try {
        // Launch multiple discovery methods in parallel
        val discoveries = listOf(
            async { crawlPage(url, baseUrl, domain, endpoints, visitedUrls, 0) },
            async { discoverFromRobotsTxt(baseUrl, endpoints) },
            async { discoverFromSitemap(baseUrl, endpoints) },
            async { discoverFromWaybackMachine(domain, endpoints) },
            async { discoverFromCommonPaths(baseUrl, endpoints) }
        )
        
        discoveries.awaitAll()
        
    } catch (e: Exception) {
        // Continue with partial results
    }
    
    // Filter and prioritize exploitable endpoints
    endpoints
        .filter { isExploitable(it) }
        .distinct()
        .sortedBy { priorityScore(it) }
}

/**
 * Recursive web crawler with depth limiting.
 * Discovers links, forms, and API endpoints from HTML pages.
 */
private suspend fun crawlPage(
    url: String,
    baseUrl: String,
    domain: String,
    endpoints: MutableSet<String>,
    visitedUrls: MutableSet<String>,
    depth: Int
) {
    if (depth > EndpointScanner.MAX_DEPTH || visitedUrls.size > EndpointScanner.MAX_PAGES) return
    if (!visitedUrls.add(url)) return
    
    val ua = AnonymityHelper.getRandomUserAgent()
    
    try {
        val doc = Jsoup.connect(url)
            .userAgent(ua)
            .timeout(30000)
            .ignoreContentType(true)
            .ignoreHttpErrors(true)
            .get()
        
        // Extract all forms with their actions and inputs
        extractForms(doc, baseUrl, endpoints)
        
        // Extract all links
        val links = extractLinks(doc, baseUrl, domain, endpoints)
        
        // Extract API endpoints from scripts
        extractApiEndpoints(doc, baseUrl, endpoints)
        
        // Extract endpoints from inline JavaScript and JSON
        extractInlineEndpoints(doc, baseUrl, endpoints)
        
        // Extract hidden inputs and data attributes
        extractHiddenData(doc, baseUrl, endpoints)
        
        // Extract meta and link tags
        extractMetaLinks(doc, baseUrl, endpoints)
        
        // Add delay to avoid rate limiting
        delay(AnonymityHelper.randomDelay(500, 1500))
        
        // Recursively crawl discovered links (only same domain)
        if (depth < EndpointScanner.MAX_DEPTH) {
            links.filter { isSameDomain(it, domain) }
                .shuffled()
                .take(10)
                .forEach { link ->
                    crawlPage(link, baseUrl, domain, endpoints, visitedUrls, depth + 1)
                }
        }
        
    } catch (e: Exception) {
        // Continue with other URLs
    }
}

/**
 * Extract all forms, their actions, and input fields.
 */
private fun extractForms(doc: Document, baseUrl: String, endpoints: MutableSet<String>) {
    doc.select("form").forEach { form ->
        val action = form.attr("abs:action").ifBlank { baseUrl }
        val method = form.attr("method").uppercase().ifBlank { "GET" }
        
        // Collect all form inputs
        val inputs = mutableListOf<String>()
        form.select("input, select, textarea").forEach { input ->
            val name = input.attr("name")
            if (name.isNotBlank()) {
                inputs.add(name)
            }
        }
        
        // Build URL with parameters for GET forms
        if (method == "GET" && inputs.isNotEmpty()) {
            val paramString = inputs.joinToString("&") { "$it=test" }
            endpoints.add("$action?$paramString")
        } else {
            endpoints.add(action)
        }
        
        // Also add individual input patterns
        inputs.forEach { param ->
            endpoints.add("$action?$param=test")
        }
    }
}

/**
 * Extract all anchor links from the page.
 */
private fun extractLinks(doc: Document, baseUrl: String, domain: String, endpoints: MutableSet<String>): List<String> {
    val links = mutableListOf<String>()
    
    doc.select("a[href]").forEach { a ->
        val href = a.attr("abs:href")
        if (href.isNotBlank() && isValidUrl(href)) {
            endpoints.add(href)
            if (isSameDomain(href, domain)) {
                links.add(href)
            }
        }
    }
    
    // Also extract links from other elements
    doc.select("[src]").forEach { el ->
        val src = el.attr("abs:src")
        if (src.isNotBlank() && isValidUrl(src)) {
            endpoints.add(src)
        }
    }
    
    doc.select("[data-url], [data-href], [data-src]").forEach { el ->
        listOf("data-url", "data-href", "data-src").forEach { attr ->
            val value = el.attr(attr)
            if (value.isNotBlank()) {
                val fullUrl = resolveUrl(baseUrl, value)
                endpoints.add(fullUrl)
            }
        }
    }
    
    return links
}

/**
 * Extract API endpoints from script tags.
 */
private fun extractApiEndpoints(doc: Document, baseUrl: String, endpoints: MutableSet<String>) {
    // Inline scripts
    doc.select("script").forEach { script ->
        val content = script.data()
        extractEndpointsFromText(content, baseUrl, endpoints)
    }
    
    // External script sources
    doc.select("script[src]").forEach { script ->
        val src = script.attr("abs:src")
        try {
            val scriptContent = Jsoup.connect(src)
                .userAgent(AnonymityHelper.getRandomUserAgent())
                .timeout(10000)
                .ignoreContentType(true)
                .execute()
                .body()
            extractEndpointsFromText(scriptContent, baseUrl, endpoints)
        } catch (e: Exception) {
            // Skip inaccessible scripts
        }
    }
}

/**
 * Extract endpoints from text content using regex patterns.
 */
private fun extractEndpointsFromText(text: String, baseUrl: String, endpoints: MutableSet<String>) {
    EndpointScanner.apiPatterns.forEach { pattern ->
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            val endpoint = matcher.group(1)
            if (endpoint != null && endpoint.isNotBlank()) {
                val fullUrl = resolveUrl(baseUrl, endpoint)
                endpoints.add(fullUrl)
            }
        }
    }
}

/**
 * Extract endpoints from inline JSON and JavaScript data.
 */
private fun extractInlineEndpoints(doc: Document, baseUrl: String, endpoints: MutableSet<String>) {
    // JSON-LD and other structured data
    doc.select("script[type='application/json'], script[type='application/ld+json']").forEach { script ->
        val content = script.data()
        extractEndpointsFromText(content, baseUrl, endpoints)
    }
    
    // Data attributes with URLs
    doc.select("[data-ajax-url], [data-api-url], [data-endpoint]").forEach { el ->
        listOf("data-ajax-url", "data-api-url", "data-endpoint").forEach { attr ->
            val value = el.attr(attr)
            if (value.isNotBlank()) {
                endpoints.add(resolveUrl(baseUrl, value))
            }
        }
    }
}

/**
 * Extract hidden inputs and meta information.
 */
private fun extractHiddenData(doc: Document, baseUrl: String, endpoints: MutableSet<String>) {
    // Hidden inputs often contain endpoint references
    doc.select("input[type='hidden']").forEach { input ->
        val value = input.attr("value")
        if (value.startsWith("/") || value.startsWith("http")) {
            endpoints.add(resolveUrl(baseUrl, value))
        }
    }
    
    // Comments sometimes contain endpoint information
    doc.getAllElements().forEach { el ->
        el.childNodes().filterIsInstance<org.jsoup.nodes.Comment>().forEach { comment ->
            extractEndpointsFromText(comment.data, baseUrl, endpoints)
        }
    }
}

/**
 * Extract links from meta tags and link elements.
 */
private fun extractMetaLinks(doc: Document, baseUrl: String, endpoints: MutableSet<String>) {
    // Meta refresh redirects
    doc.select("meta[http-equiv='refresh']").forEach { meta ->
        val content = meta.attr("content")
        val urlMatch = Regex("url=([^;]+)", RegexOption.IGNORE_CASE).find(content)
        urlMatch?.groupValues?.get(1)?.let { url ->
            endpoints.add(resolveUrl(baseUrl, url.trim()))
        }
    }
    
    // Link elements
    doc.select("link[href]").forEach { link ->
        val href = link.attr("abs:href")
        if (href.isNotBlank()) {
            endpoints.add(href)
        }
    }
    
    // OpenGraph and Twitter card URLs
    doc.select("meta[property^='og:'], meta[name^='twitter:']").forEach { meta ->
        val content = meta.attr("content")
        if (content.startsWith("http")) {
            endpoints.add(content)
        }
    }
}

/**
 * Discover endpoints from robots.txt.
 */
private suspend fun discoverFromRobotsTxt(baseUrl: String, endpoints: MutableSet<String>) {
    try {
        val robotsUrl = "$baseUrl/robots.txt"
        val doc = Jsoup.connect(robotsUrl)
            .userAgent(AnonymityHelper.getRandomUserAgent())
            .timeout(10000)
            .ignoreContentType(true)
            .execute()
            .body()
        
        val visitedSitemaps = mutableSetOf<String>()
        doc.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("Disallow:") || trimmed.startsWith("Allow:")) {
                val path = trimmed.substringAfter(":").trim()
                if (path.isNotBlank() && path != "/") {
                    endpoints.add(resolveUrl(baseUrl, path))
                }
            } else if (trimmed.startsWith("Sitemap:")) {
                val sitemapUrl = trimmed.substringAfter(":").trim()
                discoverFromSitemapUrl(sitemapUrl, endpoints, visitedSitemaps, 0)
            }
        }
    } catch (e: Exception) {
        // robots.txt not available
    }
}

/**
 * Discover endpoints from sitemap.xml.
 */
private suspend fun discoverFromSitemap(baseUrl: String, endpoints: MutableSet<String>) {
    val sitemapUrls = listOf(
        "$baseUrl/sitemap.xml",
        "$baseUrl/sitemap_index.xml",
        "$baseUrl/sitemap-index.xml",
        "$baseUrl/sitemaps.xml"
    )
    
    val visitedSitemaps = mutableSetOf<String>()
    sitemapUrls.forEach { url ->
        discoverFromSitemapUrl(url, endpoints, visitedSitemaps, 0)
    }
}

/**
 * Parse a sitemap URL and extract endpoints with depth limiting.
 */
private suspend fun discoverFromSitemapUrl(
    url: String, 
    endpoints: MutableSet<String>, 
    visitedSitemaps: MutableSet<String>,
    depth: Int
) {
    // Prevent circular references and excessive depth
    if (depth > EndpointScanner.MAX_SITEMAP_DEPTH) return
    if (!visitedSitemaps.add(url)) return
    
    try {
        val doc = Jsoup.connect(url)
            .userAgent(AnonymityHelper.getRandomUserAgent())
            .timeout(10000)
            .ignoreContentType(true)
            .get()
        
        // Extract URLs from sitemap
        doc.select("url loc, sitemap loc").forEach { loc ->
            val locUrl = loc.text().trim()
            if (locUrl.isNotBlank()) {
                endpoints.add(locUrl)
            }
        }
        
        // Handle sitemap index files with depth tracking
        doc.select("sitemap loc").forEach { loc ->
            val nestedSitemap = loc.text().trim()
            if (nestedSitemap.isNotBlank() && nestedSitemap != url) {
                discoverFromSitemapUrl(nestedSitemap, endpoints, visitedSitemaps, depth + 1)
            }
        }
    } catch (e: Exception) {
        // Sitemap not available
    }
}

/**
 * Discover historical endpoints from Wayback Machine.
 */
private suspend fun discoverFromWaybackMachine(domain: String, endpoints: MutableSet<String>) {
    try {
        val waybackUrl = "https://web.archive.org/cdx/search/cdx?url=$domain/*&output=json&fl=original&collapse=urlkey&limit=500"
        
        val request = Request.Builder()
            .url(waybackUrl)
            .header("User-Agent", AnonymityHelper.getRandomUserAgent())
            .build()
        
        EndpointScanner.httpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@use
                // Parse JSON array format: [["original"],["url1"],["url2"]...]
                val urlPattern = Pattern.compile("\"(https?://[^\"]+)\"")
                val matcher = urlPattern.matcher(body)
                while (matcher.find()) {
                    val url = matcher.group(1)
                    if (url != null && url.contains(domain)) {
                        endpoints.add(url)
                    }
                }
            }
        }
    } catch (e: Exception) {
        // Wayback Machine not accessible
    }
}

/**
 * Discover endpoints from common paths and directories.
 */
private suspend fun discoverFromCommonPaths(baseUrl: String, endpoints: MutableSet<String>) {
    val commonPaths = listOf(
        // Admin paths
        "/admin", "/admin/login", "/administrator", "/wp-admin", "/cpanel", "/phpmyadmin",
        "/manager", "/dashboard", "/control", "/backend", "/cms", "/portal",
        
        // API paths  
        "/api", "/api/v1", "/api/v2", "/api/v3", "/rest", "/graphql", "/json",
        "/api/users", "/api/login", "/api/auth", "/api/data", "/api/search",
        
        // Auth paths
        "/login", "/signin", "/signup", "/register", "/auth", "/oauth",
        "/logout", "/forgot-password", "/reset-password", "/2fa", "/mfa",
        
        // Common files
        "/config.php", "/config.json", "/settings.json", "/.env", "/wp-config.php",
        "/config.xml", "/web.config", "/application.properties", "/config.yml",
        
        // Debug/dev paths
        "/debug", "/test", "/dev", "/staging", "/phpinfo.php", "/info.php",
        "/status", "/health", "/metrics", "/trace", "/actuator",
        
        // User content
        "/upload", "/uploads", "/files", "/media", "/images", "/documents",
        "/attachments", "/downloads", "/assets", "/static", "/public",
        
        // Search and data
        "/search", "/query", "/find", "/filter", "/browse", "/list",
        "/data", "/export", "/import", "/backup", "/dump",
        
        // E-commerce
        "/cart", "/checkout", "/payment", "/order", "/orders", "/products",
        "/catalog", "/shop", "/store", "/promo", "/coupon", "/discount"
    )
    
    commonPaths.forEach { path ->
        endpoints.add("$baseUrl$path")
    }
}

// Utility functions

private fun extractBaseUrl(url: String): String {
    return try {
        val uri = URI(url)
        "${uri.scheme}://${uri.host}${if (uri.port > 0 && uri.port != 80 && uri.port != 443) ":${uri.port}" else ""}"
    } catch (e: Exception) {
        url.substringBefore("/", url)
    }
}

private fun extractDomain(url: String): String {
    return try {
        URI(url).host ?: url
    } catch (e: Exception) {
        url
    }
}

private fun resolveUrl(baseUrl: String, path: String): String {
    return when {
        path.startsWith("http://") || path.startsWith("https://") -> path
        path.startsWith("//") -> "https:$path"
        path.startsWith("/") -> "$baseUrl$path"
        else -> "$baseUrl/$path"
    }
}

private fun isValidUrl(url: String): Boolean {
    return url.startsWith("http://") || url.startsWith("https://")
}

private fun isSameDomain(url: String, domain: String): Boolean {
    return try {
        URI(url).host?.contains(domain.replace("www.", "")) == true
    } catch (e: Exception) {
        false
    }
}

private fun isExploitable(url: String): Boolean {
    val lowerUrl = url.lowercase()
    return EndpointScanner.exploitableKeywords.any { keyword -> lowerUrl.contains(keyword) } ||
           url.contains("?") ||
           url.contains("=")
}

private fun priorityScore(url: String): Int {
    val lowerUrl = url.lowercase()
    var score = 0
    
    // Higher priority (lower score) for more likely vulnerable endpoints
    if (lowerUrl.contains("login") || lowerUrl.contains("auth")) score -= 10
    if (lowerUrl.contains("search") || lowerUrl.contains("query")) score -= 8
    if (lowerUrl.contains("id=") || lowerUrl.contains("?")) score -= 6
    if (lowerUrl.contains("admin") || lowerUrl.contains("config")) score -= 5
    if (lowerUrl.contains("api")) score -= 4
    if (lowerUrl.contains("upload") || lowerUrl.contains("file")) score -= 3
    
    return score
}
