package com.theitguy.grocerycompare.data.scrapers

import com.google.gson.JsonParser
import com.theitguy.grocerycompare.data.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * Product lookup with API identification + web scraping fallback for pricing.
 *
 * Strategy:
 * 1. Identify product via Open Food Facts + UPC Item DB
 * 2. Get API pricing when available (UPC Item DB offers)
 * 3. For stores without pricing, attempt to scrape their search pages
 * 4. Fall back to manual search links if scraping fails
 */
object UpcLookupService {

    private const val UPCITEMDB_URL = "https://api.upcitemdb.com/prod/trial/lookup"
    private const val OPENFOODFACTS_URL = "https://world.openfoodfacts.org/api/v2/product"
    private const val SCRAPE_TIMEOUT_MS = 8000 // 8 second timeout per store

    /**
     * Quick product identification for scanner preview.
     */
    suspend fun quickLookup(upc: String): Triple<String, String?, String>? {
        return withContext(Dispatchers.IO) {
            try {
                val result = tryOpenFoodFacts(upc)
                if (result != null) return@withContext Triple(result.first, result.second, "")
                val upcDb = tryUpcItemDb(upc)
                if (upcDb != null && upcDb.productName.isNotEmpty()) {
                    Triple(upcDb.productName, upcDb.imageUrl, "")
                } else null
            } catch (_: Exception) { null }
        }
    }

    /**
     * Full lookup: identify product + get pricing via API or scraping.
     */
    suspend fun lookupUpc(upc: String): UpcLookupResult = coroutineScope {
        var productName = ""
        var imageUrl: String? = null
        val storeResults = mutableMapOf<Store, StoreResult>()

        // Step 1: Identify product
        try {
            val offResult = withContext(Dispatchers.IO) { tryOpenFoodFacts(upc) }
            if (offResult != null) {
                productName = offResult.first
                imageUrl = offResult.second
            }
        } catch (_: Exception) { }

        try {
            val upcDbResult = withContext(Dispatchers.IO) { tryUpcItemDb(upc) }
            if (upcDbResult != null) {
                if (productName.isEmpty()) productName = upcDbResult.productName
                if (imageUrl == null) imageUrl = upcDbResult.imageUrl
                storeResults.putAll(upcDbResult.storeResults)
            }
        } catch (_: Exception) { }

        // Step 2: For stores without API pricing, try scraping
        val searchQuery = simplifyProductName(productName).ifEmpty { upc }
        val missingStores = Store.entries.filter { it !in storeResults }

        if (missingStores.isNotEmpty()) {
            val scrapeJobs = missingStores.map { store ->
                async(Dispatchers.IO) {
                    try {
                        // Attempt to scrape price from store search page
                        scrapeStorePrice(store, searchQuery, productName, imageUrl)
                    } catch (_: Exception) {
                        // Scraping failed - generate manual search link
                        StoreResult(
                            store = store,
                            productName = productName.ifEmpty { "UPC $upc" },
                            imageUrl = imageUrl,
                            productUrl = buildStoreSearchUrl(store, searchQuery),
                            stockStatus = StockStatus.UNKNOWN,
                            fulfillmentOptions = getDefaultFulfillment(store),
                            membershipRequired = store == Store.SAMS_CLUB || store == Store.COSTCO
                        )
                    }
                }
            }

            for (job in scrapeJobs) {
                val result = job.await()
                storeResults[result.store] = result
            }
        }

        UpcLookupResult(
            success = productName.isNotEmpty() || storeResults.any { it.value.price != null },
            productName = productName,
            imageUrl = imageUrl,
            storeResults = storeResults
        )
    }

    // ---- Web Scraping Layer ----

    /**
     * Attempt to scrape price from a store's search results page.
     * Uses realistic browser headers to avoid detection.
     */
    private suspend fun scrapeStorePrice(
        store: Store,
        searchQuery: String,
        productName: String,
        imageUrl: String?
    ): StoreResult {
        val searchUrl = buildStoreSearchUrl(store, searchQuery)
        
        try {
            val doc = Jsoup.connect(searchUrl)
                .userAgent("Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5")
                .header("Referer", store.baseUrl)
                .timeout(SCRAPE_TIMEOUT_MS)
                .get()

            // Try store-specific selectors
            val price = when (store) {
                Store.WALMART -> extractWalmartPrice(doc)
                Store.TARGET -> extractTargetPrice(doc)
                Store.HEB -> extractHebPrice(doc)
                Store.SPROUTS -> extractSproutsPrice(doc)
                else -> null // Sam's/Costco require login, skip scraping
            }

            if (price != null && price > 0) {
                return StoreResult(
                    store = store,
                    productName = productName.ifEmpty { "Found on ${store.displayName}" },
                    price = price,
                    imageUrl = imageUrl,
                    productUrl = searchUrl,
                    stockStatus = StockStatus.IN_STOCK,
                    fulfillmentOptions = getDefaultFulfillment(store)
                )
            }
        } catch (_: Exception) {
            // Scraping failed - return fallback
        }

        // Fallback: manual search link
        return StoreResult(
            store = store,
            productName = productName.ifEmpty { "Search results" },
            imageUrl = imageUrl,
            productUrl = searchUrl,
            stockStatus = StockStatus.UNKNOWN,
            fulfillmentOptions = getDefaultFulfillment(store),
            membershipRequired = store == Store.SAMS_CLUB || store == Store.COSTCO
        )
    }

    // ---- Store-Specific Price Extractors ----
    // NOTE: These selectors break frequently when stores update their HTML.
    // Update them as needed or they'll fail silently and fall back to manual search.

    private fun extractWalmartPrice(doc: org.jsoup.nodes.Document): Double? {
        return try {
            // Try multiple possible selectors (Walmart changes these often)
            val selectors = listOf(
                "span[data-automation-id='product-price']",
                "[data-testid='price-integer']",
                ".price-characteristic",
                "span.price"
            )
            for (selector in selectors) {
                val element = doc.selectFirst(selector)
                if (element != null) {
                    return extractPrice(element.text())
                }
            }
            null
        } catch (_: Exception) { null }
    }

    private fun extractTargetPrice(doc: org.jsoup.nodes.Document): Double? {
        return try {
            val selectors = listOf(
                "[data-test='product-price']",
                ".h-text-bs",
                "span[class*='Price']"
            )
            for (selector in selectors) {
                val element = doc.selectFirst(selector)
                if (element != null) {
                    return extractPrice(element.text())
                }
            }
            null
        } catch (_: Exception) { null }
    }

    private fun extractHebPrice(doc: org.jsoup.nodes.Document): Double? {
        return try {
            val selectors = listOf(
                ".product-price",
                "[data-testid='price']",
                ".price"
            )
            for (selector in selectors) {
                val element = doc.selectFirst(selector)
                if (element != null) {
                    return extractPrice(element.text())
                }
            }
            null
        } catch (_: Exception) { null }
    }

    private fun extractSproutsPrice(doc: org.jsoup.nodes.Document): Double? {
        return try {
            val selectors = listOf(
                ".ProductCard-sellPrice",
                "[data-test='product-price']",
                ".price-now"
            )
            for (selector in selectors) {
                val element = doc.selectFirst(selector)
                if (element != null) {
                    return extractPrice(element.text())
                }
            }
            null
        } catch (_: Exception) { null }
    }

    // ---- Product Identification APIs ----

    private suspend fun tryUpcItemDb(upc: String): UpcLookupResult? {
        val url = "$UPCITEMDB_URL?upc=$upc"
        val json = HttpClientProvider.fetchJson(url, mapOf(
            "Accept" to "application/json",
            "Content-Type" to "application/json"
        )) ?: return null

        val root = JsonParser.parseString(json).asJsonObject
        val code = root.get("code")?.asString ?: ""
        if (code == "INVALID_UPC" || code == "EXCEED_LIMIT") return null

        val items = root.getAsJsonArray("items")
        if (items == null || items.size() == 0) return null

        val item = items[0].asJsonObject
        val title = item.get("title")?.asString ?: ""
        val brand = item.get("brand")?.asString ?: ""
        val fullName = when {
            brand.isNotEmpty() && title.isNotEmpty() && !title.startsWith(brand, true) -> "$brand $title"
            title.isNotEmpty() -> title
            else -> brand
        }

        val imgUrl = item.getAsJsonArray("images")?.let {
            if (it.size() > 0) it[0].asString else null
        }

        val storeResults = mutableMapOf<Store, StoreResult>()
        val offers = item.getAsJsonArray("offers")
        if (offers != null) {
            for (i in 0 until offers.size()) {
                val offer = offers[i].asJsonObject
                val merchant = offer.get("merchant")?.asString?.lowercase() ?: continue
                val price = offer.get("price")?.asDouble ?: continue
                if (price <= 0) continue
                val link = offer.get("link")?.asString
                val availability = offer.get("availability")?.asString ?: ""

                val store = mapMerchantToStore(merchant) ?: continue
                if (store in storeResults) continue

                storeResults[store] = StoreResult(
                    store = store,
                    productName = fullName,
                    price = price,
                    imageUrl = imgUrl,
                    productUrl = link ?: buildStoreSearchUrl(store, simplifyProductName(fullName).ifEmpty { upc }),
                    stockStatus = when {
                        availability.contains("in_stock", true) -> StockStatus.IN_STOCK
                        availability.contains("out_of_stock", true) -> StockStatus.OUT_OF_STOCK
                        else -> StockStatus.IN_STOCK
                    },
                    fulfillmentOptions = getDefaultFulfillment(store),
                    membershipRequired = store == Store.SAMS_CLUB || store == Store.COSTCO
                )
            }
        }

        return UpcLookupResult(success = fullName.isNotEmpty(), productName = fullName, imageUrl = imgUrl, storeResults = storeResults)
    }

    private suspend fun tryOpenFoodFacts(upc: String): Pair<String, String?>? {
        val url = "$OPENFOODFACTS_URL/$upc.json?fields=product_name,brands,image_url,image_front_url"
        val json = HttpClientProvider.fetchJson(url) ?: return null

        val root = JsonParser.parseString(json).asJsonObject
        if (root.get("status")?.asInt != 1) return null

        val product = root.getAsJsonObject("product") ?: return null
        val name = product.get("product_name")?.asString ?: ""
        val brands = product.get("brands")?.asString ?: ""
        val image = product.get("image_front_url")?.asString ?: product.get("image_url")?.asString

        if (name.isEmpty() && brands.isEmpty()) return null

        val fullName = when {
            brands.isNotEmpty() && name.isNotEmpty() && !name.startsWith(brands, true) -> "$brands $name"
            name.isNotEmpty() -> name
            else -> brands
        }
        return Pair(fullName, image)
    }

    // ---- Utilities ----

    private fun extractPrice(text: String): Double? {
        return try {
            val cleaned = text.replace("$", "").replace(",", "").trim()
            Regex("""(\d+\.?\d*)""").find(cleaned)?.groupValues?.get(1)?.toDoubleOrNull()
        } catch (_: Exception) { null }
    }

    private fun simplifyProductName(name: String): String {
        if (name.isEmpty()) return ""
        return name
            .replace(Regex("""\d+(\.\d+)?\s*(fl\s*oz|oz|ml|l|lb|kg|ct|count|pack|pk|ea|each)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\b\d{8,14}\b"""), "")
            .replace(Regex("""\([^)]*\)"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .split(" ").filter { it.length > 1 }.take(6).joinToString(" ")
    }

    fun buildStoreSearchUrl(store: Store, query: String): String {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        return when (store) {
            Store.HEB -> "https://www.heb.com/search/?q=$encoded"
            Store.WALMART -> "https://www.walmart.com/search?q=$encoded"
            Store.SAMS_CLUB -> "https://www.samsclub.com/s/$encoded"
            Store.COSTCO -> "https://www.costco.com/CatalogSearch?dept=All&keyword=$encoded"
            Store.TARGET -> "https://www.target.com/s?searchTerm=$encoded"
            Store.SPROUTS -> "https://shop.sprouts.com/search?search_term=$encoded"
        }
    }

    private fun mapMerchantToStore(merchant: String): Store? {
        return when {
            merchant.contains("walmart") && !merchant.contains("sam") -> Store.WALMART
            merchant.contains("sam's club") || merchant.contains("samsclub") || merchant.contains("sam's") -> Store.SAMS_CLUB
            merchant.contains("costco") -> Store.COSTCO
            merchant.contains("h-e-b") || merchant.contains("heb") || merchant.contains("h e b") -> Store.HEB
            merchant.contains("target") -> Store.TARGET
            merchant.contains("sprouts") -> Store.SPROUTS
            else -> null
        }
    }

    private fun getDefaultFulfillment(store: Store): List<FulfillmentType> {
        return when (store) {
            Store.HEB -> listOf(FulfillmentType.PICKUP, FulfillmentType.DELIVERY)
            Store.WALMART -> listOf(FulfillmentType.PICKUP, FulfillmentType.SHIPPING, FulfillmentType.DELIVERY)
            Store.SAMS_CLUB -> listOf(FulfillmentType.PICKUP, FulfillmentType.SHIPPING)
            Store.COSTCO -> listOf(FulfillmentType.SHIPPING, FulfillmentType.IN_STORE_ONLY)
            Store.TARGET -> listOf(FulfillmentType.PICKUP, FulfillmentType.SHIPPING, FulfillmentType.DELIVERY)
            Store.SPROUTS -> listOf(FulfillmentType.PICKUP, FulfillmentType.DELIVERY)
        }
    }
}

data class UpcLookupResult(
    val success: Boolean,
    val productName: String = "",
    val imageUrl: String? = null,
    val storeResults: Map<Store, StoreResult> = emptyMap(),
    val errorMessage: String? = null
)
