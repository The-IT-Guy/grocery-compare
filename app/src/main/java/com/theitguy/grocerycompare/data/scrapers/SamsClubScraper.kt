package com.theitguy.grocerycompare.data.scrapers

import com.google.gson.JsonParser
import com.theitguy.grocerycompare.data.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * Scraper for Sam's Club product data.
 *
 * Strategy: Sam's Club renders search results server-side and also embeds
 * product data in script tags. We parse both approaches.
 * Note: Sam's Club requires membership for most purchases.
 */
class SamsClubScraper : StoreScraper {

    override val store = Store.SAMS_CLUB

    override fun buildSearchUrl(upc: String): String {
        return "https://www.samsclub.com/s/$upc"
    }

    /**
     * Sam's Club has a search API endpoint.
     */
    private fun buildApiUrl(upc: String): String {
        return "https://www.samsclub.com/api/node/vivaldi/v2/search?q=$upc&limit=5&offset=0"
    }

    override suspend fun lookupByUpc(upc: String): StoreResult {
        return withContext(Dispatchers.IO) {
            try {
                // Strategy 1: Try the search API
                val apiResult = tryApiLookup(upc)
                if (apiResult != null) return@withContext apiResult

                // Strategy 2: Parse HTML search results
                val html = HttpClientProvider.fetch(
                    buildSearchUrl(upc),
                    mapOf(
                        "Referer" to "https://www.samsclub.com/",
                        "sec-fetch-dest" to "document",
                        "sec-fetch-mode" to "navigate"
                    )
                ) ?: return@withContext errorResult("Could not reach Sam's Club")

                val doc = Jsoup.parse(html)

                // Check for no results
                if (doc.text().contains("didn't match any products") ||
                    doc.select("[class*='noResults'], [class*='no-results']").isNotEmpty()
                ) {
                    return@withContext StoreResult(
                        store = Store.SAMS_CLUB,
                        stockStatus = StockStatus.OUT_OF_STOCK,
                        errorMessage = "Product not found at Sam's Club",
                        membershipRequired = true
                    )
                }

                // Parse product cards
                val productCard = doc.select(
                    "[data-testid='productCard'], .sc-product-card, [class*='ProductCard'], " +
                    "[class*='product-card'], .sc-plp-cards-grid li"
                ).first()

                if (productCard != null) {
                    val name = productCard.select(
                        "[data-testid='productName'], [class*='ProductTitle'], " +
                        "[class*='product-title'], .sc-product-card-title, a span"
                    ).text().ifEmpty { "Sam's Club Product" }

                    val priceText = productCard.select(
                        "[data-testid='productPrice'], [class*='Price'], " +
                        "[class*='product-price'], .sc-product-card-price"
                    ).text()

                    val price = extractPrice(priceText)

                    val imgUrl = productCard.select("img").first()?.attr("src") ?: ""
                    val link = productCard.select("a[href*='/p/']").attr("href")
                    val productUrl = if (link.startsWith("http")) link
                        else if (link.isNotEmpty()) "https://www.samsclub.com$link"
                        else buildSearchUrl(upc)

                    // Sam's Club fulfillment
                    val fulfillment = mutableListOf<FulfillmentType>()
                    val badges = productCard.text().lowercase()
                    if (badges.contains("pickup") || badges.contains("club")) {
                        fulfillment.add(FulfillmentType.PICKUP)
                    }
                    if (badges.contains("shipping") || badges.contains("free ship")) {
                        fulfillment.add(FulfillmentType.SHIPPING)
                    }
                    if (badges.contains("delivery")) {
                        fulfillment.add(FulfillmentType.DELIVERY)
                    }
                    if (fulfillment.isEmpty()) {
                        fulfillment.addAll(listOf(FulfillmentType.PICKUP, FulfillmentType.SHIPPING))
                    }

                    return@withContext StoreResult(
                        store = Store.SAMS_CLUB,
                        productName = name.take(120),
                        price = price,
                        imageUrl = imgUrl.ifEmpty { null },
                        productUrl = productUrl,
                        stockStatus = if (price != null) StockStatus.IN_STOCK else StockStatus.UNKNOWN,
                        fulfillmentOptions = fulfillment,
                        membershipRequired = true
                    )
                }

                // Strategy 3: Look for __NEXT_DATA__ or similar embedded JSON
                val scriptTags = doc.select("script:containsData(searchResult), script:containsData(products)")
                for (script in scriptTags) {
                    val parsed = parseEmbeddedJson(script.data(), upc)
                    if (parsed != null) return@withContext parsed
                }

                errorResult("Could not parse Sam's Club results")
            } catch (e: Exception) {
                errorResult("Error: ${e.message?.take(80)}")
            }
        }
    }

    private suspend fun tryApiLookup(upc: String): StoreResult? {
        return try {
            val json = HttpClientProvider.fetchJson(
                buildApiUrl(upc),
                mapOf(
                    "Referer" to "https://www.samsclub.com/s/$upc",
                    "Origin" to "https://www.samsclub.com"
                )
            ) ?: return null

            val root = JsonParser.parseString(json).asJsonObject
            val records = root.getAsJsonObject("payload")
                ?.getAsJsonArray("records")
                ?: return null

            if (records.size() == 0) return null

            val item = records[0].asJsonObject
            val name = item.get("productName")?.asString
                ?: item.get("title")?.asString
                ?: "Sam's Club Product"

            val price = item.get("finalPrice")?.asDouble
                ?: item.getAsJsonObject("price")?.get("finalPrice")?.asDouble

            val imageUrl = item.get("listImageUrl")?.asString
                ?: item.get("imageUrl")?.asString

            val isAvailable = item.get("isAvailableOnline")?.asBoolean ?: true

            StoreResult(
                store = Store.SAMS_CLUB,
                productName = name.take(120),
                price = price,
                imageUrl = imageUrl,
                productUrl = buildSearchUrl(upc),
                stockStatus = if (isAvailable) StockStatus.IN_STOCK else StockStatus.OUT_OF_STOCK,
                fulfillmentOptions = listOf(FulfillmentType.PICKUP, FulfillmentType.SHIPPING),
                membershipRequired = true
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseEmbeddedJson(data: String, upc: String): StoreResult? {
        return try {
            // Try to find a JSON object with product data
            val jsonStart = data.indexOf('{')
            val jsonEnd = data.lastIndexOf('}')
            if (jsonStart < 0 || jsonEnd <= jsonStart) return null

            val json = data.substring(jsonStart, jsonEnd + 1)
            val root = JsonParser.parseString(json).asJsonObject

            // Navigate to product data - structure varies
            val product = root.getAsJsonObject("props")
                ?.getAsJsonObject("pageProps")
                ?.getAsJsonObject("initialData")
                ?.getAsJsonArray("records")
                ?.get(0)?.asJsonObject
                ?: return null

            val name = product.get("productName")?.asString ?: "Sam's Club Product"
            val price = product.get("finalPrice")?.asDouble

            StoreResult(
                store = Store.SAMS_CLUB,
                productName = name.take(120),
                price = price,
                productUrl = buildSearchUrl(upc),
                stockStatus = if (price != null) StockStatus.IN_STOCK else StockStatus.UNKNOWN,
                fulfillmentOptions = listOf(FulfillmentType.PICKUP, FulfillmentType.SHIPPING),
                membershipRequired = true
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun extractPrice(text: String): Double? {
        val cleaned = text.replace("$", "").replace(",", "").trim()
        return Regex("""(\d+\.?\d*)""").find(cleaned)?.groupValues?.get(1)?.toDoubleOrNull()
    }

    private fun errorResult(message: String) = StoreResult(
        store = Store.SAMS_CLUB,
        errorMessage = message,
        membershipRequired = true
    )
}
