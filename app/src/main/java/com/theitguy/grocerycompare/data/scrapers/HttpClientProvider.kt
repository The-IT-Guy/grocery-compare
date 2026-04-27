package com.theitguy.grocerycompare.data.scrapers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Shared HTTP client with mobile user-agent headers.
 * All scrapers use this to make requests that mimic a mobile browser.
 */
object HttpClientProvider {

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * Execute a GET request and return the response body as a string.
     */
    suspend fun fetch(url: String, extraHeaders: Map<String, String> = emptyMap()): String? {
        return withContext(Dispatchers.IO) {
            try {
                val requestBuilder = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Connection", "keep-alive")

                extraHeaders.forEach { (key, value) ->
                    requestBuilder.header(key, value)
                }

                val request = requestBuilder.build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.string()
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Fetch JSON from an API endpoint.
     */
    suspend fun fetchJson(url: String, extraHeaders: Map<String, String> = emptyMap()): String? {
        val headers = extraHeaders.toMutableMap().apply {
            put("Accept", "application/json")
        }
        return fetch(url, headers)
    }
}
