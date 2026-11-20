package com.example.jarvis.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class WebSearchResult(
    val title: String,
    val snippet: String,
    val url: String,
    val source: String
)

class WebSearchClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun search(query: String, maxResults: Int = 5): Result<List<WebSearchResult>> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Search query cannot be empty."))
            }

            val results = mutableListOf<WebSearchResult>()
            val encoded = URLEncoder.encode(trimmed, "UTF-8")

            // 1. DuckDuckGo Instant Answer JSON API (free, no key, returns official URLs & abstracts)
            runCatching {
                val ddgRequest = Request.Builder()
                    .url("https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1")
                    .header("User-Agent", "JARVIS-Android-Assistant/1.0")
                    .build()
                client.newCall(ddgRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        if (body.isNotBlank()) {
                            val json = JSONObject(body)
                            val abstractText = json.optString("AbstractText")
                            val abstractUrl = json.optString("AbstractURL")
                            val heading = json.optString("Heading")
                            if (abstractText.isNotBlank() && abstractUrl.isNotBlank()) {
                                results.add(
                                    WebSearchResult(
                                        title = heading.ifBlank { trimmed },
                                        snippet = abstractText,
                                        url = abstractUrl,
                                        source = json.optString("AbstractSource").ifBlank { "Web" }
                                    )
                                )
                            }
                            val related = json.optJSONArray("RelatedTopics")
                            if (related != null) {
                                for (i in 0 until related.length()) {
                                    if (results.size >= maxResults) break
                                    val obj = related.optJSONObject(i) ?: continue
                                    val text = obj.optString("Text")
                                    val firstUrl = obj.optString("FirstURL")
                                    if (text.isNotBlank() && firstUrl.isNotBlank()) {
                                        results.add(
                                            WebSearchResult(
                                                title = text.substringBefore(" - ").take(80),
                                                snippet = text,
                                                url = firstUrl,
                                                source = "DuckDuckGo"
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Wikipedia OpenSearch API for authoritative knowledge & source links
            if (results.size < maxResults) {
                runCatching {
                    val wikiRequest = Request.Builder()
                        .url("https://en.wikipedia.org/w/api.php?action=opensearch&search=$encoded&limit=$maxResults&namespace=0&format=json")
                        .header("User-Agent", "JARVIS-Android-Assistant/1.0")
                        .build()
                    client.newCall(wikiRequest).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string().orEmpty()
                            if (body.startsWith("[")) {
                                val root = JSONArray(body)
                                val titles = root.optJSONArray(1) ?: JSONArray()
                                val descs = root.optJSONArray(2) ?: JSONArray()
                                val urls = root.optJSONArray(3) ?: JSONArray()
                                for (i in 0 until titles.length()) {
                                    if (results.size >= maxResults) break
                                    val title = titles.optString(i)
                                    val desc = descs.optString(i)
                                    val url = urls.optString(i)
                                    if (title.isNotBlank() && url.isNotBlank() &&
                                        results.none { it.url == url }
                                    ) {
                                        results.add(
                                            WebSearchResult(
                                                title = title,
                                                snippet = desc.ifBlank { "Wikipedia article for $title" },
                                                url = url,
                                                source = "Wikipedia"
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (results.isEmpty()) {
                // Provide an honest direct search link when instant answers return nothing
                val fallbackUrl = "https://duckduckgo.com/?q=$encoded"
                Result.success(
                    listOf(
                        WebSearchResult(
                            title = "Web Search: $trimmed",
                            snippet = "No instant summary was returned; open the live search results page in your browser.",
                            url = fallbackUrl,
                            source = "Search Link"
                        )
                    )
                )
            } else {
                Result.success(results.take(maxResults))
            }
        }
}
