package com.theitguy.grocerycompare.data.scrapers

import com.google.gson.JsonParser
import com.theitguy.grocerycompare.data.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * Scraper for Costco product data.
 *
 * Strategy: Costco's website is more restrictive with scraping but we can
 * parse their search results page which includes price and availability data.
 * Note: Costco requires membership for most purchases (except pharmacy/optical).
 */
class CostcoScraper : StoreScraper {

    override val store = Store.COSTCO

    override fun buildSearchUrl(upc: String): String {
        return "https://www.costco.com/CatalogSearch?dept=All&keyword=$upc"
    }

    override suspend fun lookupByUpc(upc: String): StoreResult {
        return withContext(Dispatchers.IO) {
            try {
                val html = HttpClientProvider.fetch(
                    buildSearchUrl(upc),
                    mapOf(
                        "Referer" to "https://www.costco.com/",
                        "sec-fetch-dest" to "document",
                        "sec-fetch-mode" to "navigate",
                        "sec-ch-ua-platform" to "\"Android\""
                    )
                ) ?: return@withContext errorResult("Could not reach Costco")

                val doc = Jsoup.parse(html)

                // Check for no results
                val noResults = doc.select(
                    ".no-results, [class*='noResults'], .search-no-results"
                ).isNotEmpty() || doc.text().contains("did not return any results")

                if (noResults) {
                    return@withContext StoreResult(
                        store = Store.COSTCO,
                        stockStatus = StockStatus.OUT_OF_STOCK,
                        errorMessage = "Product not found at Costco",
                        membershipRequired = true
                    )
                }

                // Strategy 1: Parse product grid/list items
                val productCard = doc.select(
                    ".product-tile, .product-card, [data-testid='product'], " +
                    ".product-list .product, [class*='ProductCard'], .product-tile-set .product"
                ).first()

                if (productCard != null) {
                    val name = productCard.select(
                        ".description a, .product-title, [data-testid='product-description'], " +
                        "a.product-tile-link span, .product-card-name"
                    ).text().ifEmpty {
                        productCard.select("a span, p.description").text()
                    }.ifEmpty { "Costco Product" }

                    val priceText = productCard.select(
                        ".price, [data-testid='product-price'], .your-price, " +
                        "[class*='price'], .product-card-price"
                    ).text()

                    val price = extractPrice(priceText)

                    val imgUrl = productCard.select("img.product-image, img[data-testid], img")
                        .first()?.let { it.attr("src").ifEmpty { it.attr("data-src") } } ?: ""

                    val link = productCard.select("a[href*='.product.']").attr("href")
                        .ifEmpty { productCard.select("a[href*='/p/']").attr("href") }

                    val productUrl = when {
                        link.startsWith("http") -> link
                        link.isNotEmpty() -> "https://www.costco.com$link"
                        else -> buildSearchUrl(upc)
                    }

                    // Check fulfillment - Costco online is mostly shipping
                    val fulfillment = mutableListOf<FulfillmentType>()
                    val tileText = productCard.text().lowercase()
                    if (tileText.contains("warehouse")) {
                        fulfillment.add(FulfillmentType.IN_STORE_ONLY)
                    }
                    if (tileText.contains("delivery") || tileText.contains("same-day")) {
                        fulfillment.add(FulfillmentType.DELIVERY)
                    }
                    if (tileText.contains("shipping") || tileText.contains("ships")) {
                        fulfillment.add(FulfillmentType.SHIPPING)
                    }
                    if (fulfillment.isEmpty()) {
                        fulfillment.addAll(listOf(FulfillmentType.SHIPPING, FulfillmentType.IN_STORE_ONLY))
                    }

                    // Check stock
                    val outOfStock = tileText.contains("out of stock") || tileText.contains("unavailable")

                    return@withContext StoreResult(
                        store = Store.COSTCO,
                        productName = name.take(120),
                        price = price,
                        imageUrl = imgUrl.ifEmpty { null },
                        productUrl = productUrl,
                        stockStatus = when {
                            outOfStock -> StockStatus.OUT_OF_STOCK
                            price != null -> StockStatus.IN_STOCK
                            else -> StockStatus.UNKNOWN
                        },
                        fulfillmentOptions = fulfillment,
                        membershipRequired = true
                    )
                }

                // Strategy 2: Check for JSON-LD
                val jsonLdScripts = doc.select("script[type='application/ld+json']")
                for (script in jsonLdScripts) {
                    val parsed = parseJsonLd(script.data(), upc)
                    if (parsed != null) return@withContext parsed
                }

                // Strategy 3: Parse embedded product data from script tags
                val dataScripts = doc.select("script:containsData(productData), script:containsData(itemPrice)")
                for (script in dataScripts) {
                    val parsed = parseEmbeddedData(script.data(), upc)
                    if (parsed != null) return@withContext parsed
                }

                errorResult("Could not parse Costco results")
            } catch (e: Exception) {
                errorResult("Error: ${e.message?.take(80)}")
            }
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
            val availability = offers?.get("availability")?.asString ?: ""
            val imageUrl = root.get("image")?.asString

            StoreResult(
                store = Store.COSTCO,
                productName = name.take(120),
                price = price,
                imageUrl = imageUrl,
                productUrl = buildSearchUrl(upc),
                stockStatus = when {
                    availability.contains("InStock") -> StockStatus.IN_STOCK
                    availability.contains("OutOfStock") -> StockStatus.OUT_OF_STOCK
                    price != null -> StockStatus.IN_STOCK
                    else -> StockStatus.UNKNOWN
                },
                fulfillmentOptions = listOf(FulfillmentType.SHIPPING, FulfillmentType.IN_STORE_ONLY),
                membershipRequired = true
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseEmbeddedData(data: String, upc: String): StoreResult? {
        return try {
            // Look for price data in script content
            val priceMatch = Regex(""""price"\s*:\s*"?(\d+\.?\d*)"?""").find(data)
            val nameMatch = Regex(""""name"\s*:\s*"([^"]+)"""").find(data)

            if (priceMatch == null && nameMatch == null) return null

            StoreResult(
                store = Store.COSTCO,
                productName = (nameMatch?.groupValues?.get(1) ?: "Costco Product").take(120),
                price = priceMatch?.groupValues?.get(1)?.toDoubleOrNull(),
                productUrl = buildSearchUrl(upc),
                stockStatus = if (priceMatch != null) StockStatus.IN_STOCK else StockStatus.UNKNOWN,
                fulfillmentOptions = listOf(FulfillmentType.SHIPPING, FulfillmentType.IN_STORE_ONLY),
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
        store = Store.COSTCO,
        errorMessage = message,
        membershipRequired = true
    )
}
