package com.theitguy.grocerycompare.data.scrapers

import com.theitguy.grocerycompare.data.models.Store
import com.theitguy.grocerycompare.data.models.StoreResult

/**
 * Interface for store-specific web scrapers.
 * Each store implementation handles its own HTML parsing logic.
 */
interface StoreScraper {
    val store: Store

    /**
     * Look up a product by UPC code.
     * Returns a StoreResult with price, stock, and fulfillment info.
     */
    suspend fun lookupByUpc(upc: String): StoreResult

    /**
     * Build the search URL for a given UPC.
     */
    fun buildSearchUrl(upc: String): String
}
