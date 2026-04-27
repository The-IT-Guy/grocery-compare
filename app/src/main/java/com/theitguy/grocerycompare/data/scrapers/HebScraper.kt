package com.theitguy.grocerycompare.data.scrapers

import com.google.gson.JsonParser
import com.theitguy.grocerycompare.data.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * Scraper for H-E-B product data.
 *
 * Strategy: HEB uses a combination of server-rendered HTML and JSON-LD.
 * Their search also has an API endpoint that returns JSON for product tiles.
 * We try the API first, then fall back to HTML parsing.
 */
class HebScraper : StoreScraper {

    override val store = Store.HEB

    override fun buildSearchUrl(upc: String): String {
        return "https://www.heb.com/search/?q=$upc"
    }

    /**
     * HEB has an internal API for search results.
     */
    private fun buildApiUrl(upc: String): String {
        return "https://www.heb.com/commerce-api/v2/search/?query=$upc&page=1&pageSize=5"
    }

    override suspend fun lookupByUpc(upc: String): StoreResult {
        return withContext(Dispatchers.IO) {
            try {
                // Strategy 1: Try HEB's internal search API
                val apiResult = tryApiLookup(upc)
                if (apiResult != null) return@withContext apiResult

                // Strategy 2: Parse the HTML search page
                val html = HttpClientProvider.fetch(
                    buildSearchUrl(upc),
                    mapOf("X-Requested-With" to "XMLHttpRequest")
                ) ?: return@withContext errorResult("Could not reach H-E-B")

                val doc = Jsoup.parse(html)

                // Check for no results
                val noResults = doc.select(
                    ".no-results, .search-no-results, [data-testid='no-results']"
                ).isNotEmpty()
                if (noResults) {
                    return@withContext StoreResult(
                        store = Store.HEB,
                        stockStatus = StockStatus.OUT_OF_STOCK,
                        errorMessage = "Product not found at H-E-B"
                    )
                }

                // Parse product cards
                val productCard = doc.select(
                    ".product-card, .product-grid-item, [data-testid='product-card'], .productCard"
                ).first()

                if (productCard != null) {
                    val name = productCard.select(
                        ".product-card__title, .product-title, [data-testid='product-title'], h3, .product-name"
                    ).text().ifEmpty { "H-E-B Product" }

                    val priceText = productCard.select(
                        ".product-card__price, .product-price, [data-testid='product-price'], .price"
                    ).text()

                    val price = extractPrice(priceText)

                    val imgUrl = productCard.select("img").attr("src")
                        .ifEmpty { productCard.select("img").attr("data-src") }

                    val link = productCard.select("a[href*='/product-detail/']").attr("href")
                    val productUrl = if (link.startsWith("http")) link
                        else if (link.isNotEmpty()) "https://www.heb.com$link"
                        else buildSearchUrl(upc)

                    // Check fulfillment badges
                    val fulfillment = mutableListOf<FulfillmentType>()
                    val badges = productCard.select(
                        ".fulfillment-badge, .delivery-badge, [data-testid*='fulfillment']"
                    ).text().lowercase()

                    if (badges.contains("curbside") || badges.contains("pickup")) {
                        fulfillment.add(FulfillmentType.PICKUP)
                    }
                    if (badges.contains("delivery")) {
                        fulfillment.add(FulfillmentType.DELIVERY)
                    }
                    if (badges.contains("shipping") || badges.contains("ship")) {
                        fulfillment.add(FulfillmentType.SHIPPING)
                    }
                    if (fulfillment.isEmpty() && price != null) {
                        fulfillment.addAll(listOf(FulfillmentType.PICKUP, FulfillmentType.IN_STORE_ONLY))
                    }

                    return@withContext StoreResult(
                        store = Store.HEB,
                        productName = name.take(120),
                        price = price,
                        imageUrl = imgUrl.ifEmpty { null },
                        productUrl = productUrl,
                        stockStatus = if (price != null) StockStatus.IN_STOCK else StockStatus.UNKNOWN,
                        fulfillmentOptions = fulfillment
                    )
                }

                // Strategy 3: Try JSON-LD structured data
                val jsonLd = doc.select("script[type='application/ld+json']")
                for (script in jsonLd) {
                    val parsed = parseJsonLd(script.data(), upc)
                    if (parsed != null) return@withContext parsed
                }

                errorResult("Could not parse H-E-B results")
            } catch (e: Exception) {
                errorResult("Error: ${e.message?.take(80)}")
            }
        }
    }

    /**
     * Try HEB's internal commerce API.
     */
    private suspend fun tryApiLookup(upc: String): StoreResult? {
        return try {
            val json = HttpClientProvider.fetchJson(
                buildApiUrl(upc),
                mapOf(
                    "Referer" to "https://www.heb.com/search/?q=$upc",
                    "Origin" to "https://www.heb.com"
                )
            ) ?: return null

            val root = JsonParser.parseString(json).asJsonObject
            val products = root.getAsJsonArray("products")
                ?: root.getAsJsonObject("response")?.getAsJsonArray("products")
                ?: return null

            if (products.size() == 0) return null

            val product = products[0].asJsonObject
            val name = product.get("name")?.asString
                ?: product.get("description")?.asString
                ?: "H-E-B Product"

            val price = product.get("price")?.asDouble
                ?: product.getAsJsonObject("pricing")?.get("price")?.asDouble

            val imageUrl = product.get("imageUrl")?.asString
                ?: product.get("image")?.asString

            val inStock = product.get("inStock")?.asBoolean
                ?: product.get("available")?.asBoolean

            val fulfillment = mutableListOf<FulfillmentType>()
            product.getAsJsonArray("fulfillmentOptions")?.forEach { opt ->
                when (opt.asString?.lowercase()) {
                    "curbside", "pickup" -> fulfillment.add(FulfillmentType.PICKUP)
                    "delivery" -> fulfillment.add(FulfillmentType.DELIVERY)
                    "shipping" -> fulfillment.add(FulfillmentType.SHIPPING)
                }
            }
            if (fulfillment.isEmpty()) {
                fulfillment.addAll(listOf(FulfillmentType.PICKUP, FulfillmentType.IN_STORE_ONLY))
            }

            StoreResult(
                store = Store.HEB,
                productName = name.take(120),
                price = price,
                imageUrl = imageUrl,
                productUrl = buildSearchUrl(upc),
                stockStatus = when {
                    inStock == true -> StockStatus.IN_STOCK
                    inStock == false -> StockStatus.OUT_OF_STOCK
                    price != null -> StockStatus.IN_STOCK
                    else -> StockStatus.UNKNOWN
                },
                fulfillmentOptions = fulfillment
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseJsonLd(json: String, upc: String): StoreResult? {
        return try {
            val root = JsonParser.parseString(json).asJsonObject
            if (root.get("@type")?.asString != "Product") return null

            val name = root.get("name")?.asString ?: return null
            val offers = root.getAsJsonObject("offers")
                ?: root.getAsJsonArray("offers")?.get(0)?.asJsonObject
            val price = offers?.get("price")?.asDouble

            StoreResult(
                store = Store.HEB,
                productName = name.take(120),
                price = price,
                productUrl = buildSearchUrl(upc),
                stockStatus = if (price != null) StockStatus.IN_STOCK else StockStatus.UNKNOWN,
                fulfillmentOptions = listOf(FulfillmentType.PICKUP, FulfillmentType.IN_STORE_ONLY)
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
        store = Store.HEB,
        errorMessage = message
    )
}
