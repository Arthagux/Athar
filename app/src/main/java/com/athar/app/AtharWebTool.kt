package com.athar.app

import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

/** Herramienta de Internet separada del cerebro local de ATHAR. */
object AtharWebTool {
    fun fetchPage(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("User-Agent", "ATHAR/0.2 Android")
        return connection.inputStream.bufferedReader().use { it.readText() }
    }

    fun searchUrl(query: String): String =
        "https://www.google.com/search?q=" + URLEncoder.encode(query, "UTF-8")
}