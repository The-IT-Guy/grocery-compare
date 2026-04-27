package com.theitguy.grocerycompare.data.repository

import com.theitguy.grocerycompare.data.models.*
import com.theitguy.grocerycompare.data.scrapers.*
import kotlinx.coroutines.coroutineScope

/**
 * Central repository that coordinates product lookups.
 *
 * Supports both UPC barcode and text search (product names, keywords).
 */
class PriceRepository {

    /**
     * Smart search: detects if input is UPC or text and routes accordingly.
     */
    suspend fun search(
        query: String,
        enabledStores: Set<Store> = Store.entries.toSet(),
        onStoreResult: ((StoreResult) -> Unit)? = null
    ): ComparisonResult = coroutineScope {

        val cleanQuery = query.trim()
        
        // Route to UPC or text search based on input format
        if (isValidUpc(cleanQuery)) {
            searchByUpc(cleanQuery, enabledStores, onStoreResult)
        } else {
            searchByText(cleanQuery, enabledStores, onStoreResult)
        }
    }

    /**
     * Search by UPC barcode.
     */
    private suspend fun searchByUpc(
        upc: String,
        enabledStores: Set<Store>,
        onStoreResult: ((StoreResult) -> Unit)?
    ): ComparisonResult = coroutineScope {
        val cleanUpc = upc.replace("-", "").replace(" ", "")

        // Query APIs for product data and pricing
        val apiResult = UpcLookupService.lookupUpc(cleanUpc)

        // Filter to enabled stores only
        val filteredResults = mutableMapOf<Store, StoreResult>()
        for ((store, result) in apiResult.storeResults) {
            if (store in enabledStores) {
                filteredResults[store] = result
                onStoreResult?.invoke(result)
            }
        }

        val resultsList = filteredResults.values.toList()
        val bestPrice = resultsList
            .filter { it.price != null && it.stockStatus != StockStatus.OUT_OF_STOCK }
            .minByOrNull { it.price!! }

        ComparisonResult(
            upc = cleanUpc,
            query = cleanUpc,
            results = resultsList,
            bestPrice = bestPrice
        )
    }

    /**
     * Search by text (product name, keywords, category).
     */
    private suspend fun searchByText(
        text: String,
        enabledStores: Set<Store>,
        onStoreResult: ((StoreResult) -> Unit)?
    ): ComparisonResult = coroutineScope {
        
        // For text search, we can't use UPC APIs, so generate store search cards
        val results = mutableListOf<StoreResult>()
        
        for (store in enabledStores) {
            val result = StoreResult(
                store = store,
                productName = text,
                productUrl = UpcLookupService.buildStoreSearchUrl(store, text),
                stockStatus = StockStatus.UNKNOWN,
                fulfillmentOptions = getDefaultFulfillment(store),
                membershipRequired = store == Store.SAMS_CLUB || store == Store.COSTCO,
                isTextSearch = true // Mark as text search
            )
            results.add(result)
            onStoreResult?.invoke(result)
        }

        ComparisonResult(
            upc = "",
            query = text,
            results = results,
            bestPrice = null
        )
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

    /**
     * Validate if input looks like a UPC code.
     */
    fun isValidUpc(input: String): Boolean {
        val cleaned = input.trim().replace("-", "").replace(" ", "")
        return cleaned.length in 8..14 && cleaned.all { it.isDigit() }
    }

    /**
     * Get all available stores.
     */
    fun getAvailableStores(): List<Store> = Store.entries
}
