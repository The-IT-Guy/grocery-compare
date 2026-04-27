package com.theitguy.grocerycompare.data.scrapers

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.theitguy.grocerycompare.data.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * Scraper for Walmart product data.
 *
 * Strategy: Walmart embeds product data in __NEXT_DATA__ JSON within search results.
 * We parse the search page HTML, extract the embedded JSON, and pull price/stock/fulfillment.
 */
class WalmartScraper : StoreScraper {

    override val store = Store.WALMART

    override fun buildSearchUrl(upc: String): String {
        return "https://www.walmart.com/search?q=$upc"
    }

    override suspend fun lookupByUpc(upc: String): StoreResult {
        return withContext(Dispatchers.IO) {
            try {
                val html = HttpClientProvider.fetch(buildSearchUrl(upc))
                    ?: return@withContext errorResult("Could not reach Walmart")

                // Parse with Jsoup
                val doc = Jsoup.parse(html)

                // Strategy 1: Try __NEXT_DATA__ JSON blob
                val nextDataScript = doc.select("script#__NEXT_DATA__").first()
                if (nextDataScript != null) {
                    val parsed = parseNextData(nextDataScript.data(), upc)
                    if (parsed != null) return@withContext parsed
                }

                // Strategy 2: Parse search result cards directly from HTML
                val productCard = doc.select("[data-item-id], .search-result-gridview-item, [data-testid='list-view']").first()
                if (productCard != null) {
                    val name = productCard.select(
                        "[data-automation-id='product-title'], .product-title-link, span[data-automation-id='name']"
                    ).text().ifEmpty { "Product found" }

                    val priceText = productCard.select(
                        "[data-automation-id='product-price'] .f2, .price-main .visuallyhidden, " +
                        "[itemprop='price'], .price-characteristic"
                    ).text()

                    val price = extractPrice(priceText)
                    val productLink = productCard.select("a[href*='/ip/']").attr("href")
                    val imgUrl = productCard.select("img[data-testid='productTileImage'], img.product-image").attr("src")

                    return@withContext StoreResult(
                        store = Store.WALMART,
                        productName = name.take(120),
                        price = price,
                        imageUrl = imgUrl.ifEmpty { null },
                        productUrl = if (productLink.isNotEmpty()) "https://www.walmart.com$productLink" else buildSearchUrl(upc),
                        stockStatus = if (price != null) StockStatus.IN_STOCK else StockStatus.UNKNOWN,
                        fulfillmentOptions = listOf(FulfillmentType.PICKUP, FulfillmentType.SHIPPING, FulfillmentType.DELIVERY)
                    )
                }

                // Strategy 3: Check if page has "no results" indicator
                val noResults = doc.select(
                    "[data-testid='zero-results'], .zero-results-container, .search-no-results"
                ).isNotEmpty()

                if (noResults) {
                    return@withContext StoreResult(
                        store = Store.WALMART,
                        stockStatus = StockStatus.OUT_OF_STOCK,
                        errorMessage = "Product not found at Walmart"
                    )
                }

                errorResult("Could not parse Walmart results")
            } catch (e: Exception) {
                errorResult("Error: ${e.message?.take(80)}")
            }
        }
    }

    /**
     * Parse Walmart's __NEXT_DATA__ JSON for structured product data.
     */
    private fun parseNextData(json: String, upc: String): StoreResult? {
        return try {
            val root = JsonParser.parseString(json).asJsonObject
            val searchResult = root
                .getAsJsonObject("props")
                ?.getAsJsonObject("pageProps")
                ?.getAsJsonObject("initialData")
                ?.getAsJsonObject("searchResult")
                ?: return null

            val items = searchResult
                .getAsJsonObject("itemStacks")
                ?.getAsJsonArray("items")
                ?: searchResult.getAsJsonArray("items")
                ?: return null

            if (items.size() == 0) return null

            val item = items[0].asJsonObject
            val name = item.get("name")?.asString ?: "Unknown Product"
            val priceInfo = item.getAsJsonObject("priceInfo")
            val price = priceInfo?.get("currentPrice")?.getAsJsonObject()?.get("price")?.asDouble
                ?: priceInfo?.get("linePrice")?.asString?.let { extractPrice(it) }

            val unitPrice = priceInfo?.get("unitPrice")?.asString
            val imageUrl = item.get("image")?.asString
                ?: item.getAsJsonArray("imageInfo")?.get(0)?.asJsonObject?.get("thumbnailUrl")?.asString
            val productUrl = item.get("canonicalUrl")?.asString?.let { "https://www.walmart.com$it" }

            val availStatus = item.get("availabilityStatusV2")?.asJsonObject
            val stockStatus = when {
                availStatus?.get("value")?.asString == "IN_STOCK" -> StockStatus.IN_STOCK
                availStatus?.get("value")?.asString == "OUT_OF_STOCK" -> StockStatus.OUT_OF_STOCK
                price != null -> StockStatus.IN_STOCK
                else -> StockStatus.UNKNOWN
            }

            val fulfillment = mutableListOf<FulfillmentType>()
            item.getAsJsonArray("fulfillmentBadges")?.forEach { badge ->
                when (badge.asString.lowercase()) {
                    "pickup" -> fulfillment.add(FulfillmentType.PICKUP)
                    "shipping", "twoday", "nextday" -> fulfillment.add(FulfillmentType.SHIPPING)
                    "delivery" -> fulfillment.add(FulfillmentType.DELIVERY)
                }
            }
            if (fulfillment.isEmpty() && stockStatus == StockStatus.IN_STOCK) {
                fulfillment.addAll(listOf(FulfillmentType.PICKUP, FulfillmentType.SHIPPING))
            }

            StoreResult(
                store = Store.WALMART,
                productName = name.take(120),
                price = price,
                unitPrice = unitPrice,
                imageUrl = imageUrl,
                productUrl = productUrl ?: buildSearchUrl(upc),
                stockStatus = stockStatus,
                fulfillmentOptions = fulfillment
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
        store = Store.WALMART,
        errorMessage = message
    )
}
