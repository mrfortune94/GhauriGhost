package com.ghaurighost.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.util.regex.Pattern

suspend fun scanEndpoints(url: String): List<String> = withContext(Dispatchers.IO) {
    val endpoints = mutableListOf<String>()
    val ua = AnonymityHelper.getRandomUserAgent()

    try {
        val doc = Jsoup.connect(url)
            .userAgent(ua)
            .timeout(30000)
            .get()

        // Prioritize high-potential for injection: forms, ?params, API, login/search/promo
        doc.select("form").forEach { form ->
            val action = form.attr("abs:action")
            if (action.isNotBlank() && (action.contains("login") || action.contains("search") || action.contains("promo") || action.contains("coupon") || action.contains("checkout"))) {
                endpoints.add(action)
            }
        }

        doc.select("a[href]").forEach { a ->
            val href = a.attr("abs:href")
            if (href.contains("?") || href.contains("id=") || href.contains("search") || href.contains("product") || href.contains("promo") || href.contains("coupon")) {
                endpoints.add(href)
            }
        }

        // Extract API-like calls from scripts
        doc.select("script").forEach { script ->
            val text = script.data()
            val pattern = Pattern.compile("['\"](/api/[^'\"]+)['\"]")
            val matcher = pattern.matcher(text)
            while (matcher.find()) {
                val api = matcher.group(1)
                if (api.contains("auth") || api.contains("login") || api.contains("promo") || api.contains("coupon") || api.contains("user") || api.contains("query")) {
                    endpoints.add(api)
                }
            }
        }

    } catch (e: Exception) {
        // Silent fail
    }

    endpoints.distinct().sorted()
}
