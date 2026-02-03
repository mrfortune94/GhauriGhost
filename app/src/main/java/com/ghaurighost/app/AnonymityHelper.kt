package com.ghaurighost.app

import java.util.UUID
import kotlin.random.Random

object AnonymityHelper {
    private val userAgents = listOf(
        "Mozilla/5.0 (Linux; Android 14; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 12; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 11; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
    )

    fun getRandomUserAgent(): String = userAgents.random()

    fun spoofDeviceId(): String = UUID.randomUUID().toString().replace("-", "").take(16)

    fun randomizeResolution(): Pair<Int, Int> {
        val widths = listOf(1080, 1440, 1280, 1600, 720)
        val heights = listOf(2400, 3200, 2560, 3600, 1600)
        return widths.random() to heights.random()
    }

    fun randomDelay(min: Long = 800, max: Long = 4500): Long = Random.nextLong(min, max)
}
