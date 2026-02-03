package com.ghaurighost.app

import com.chaquo.python.Python

object GhauriRunner {
    fun runOnEndpoints(endpoints: List<String>) {
        val py = Python.getInstance()
        val module = py.getModule("ghauri") // assumes ghauri in assets or pip
        endpoints.forEach { url ->
            val result = module.callAttr("main", arrayOf("--url", url, "--batch", "--level=3"))
            // TODO: show result in UI
        }
    }

    fun runOnEndpoint(url: String) {
        runOnEndpoints(listOf(url))
    }
}
