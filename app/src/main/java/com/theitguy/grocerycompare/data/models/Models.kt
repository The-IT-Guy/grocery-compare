package com.theitguy.grocerycompare.data.models

import androidx.compose.ui.graphics.Color

/**
 * Supported grocery stores for price comparison.
 * Logo URLs use Clearbit's free logo API for clean, consistent store icons.
 */
enum class Store(
    val displayName: String,
    val brandColor: Color,
    val baseUrl: String,
    val logoUrl: String,
    val initial: String,
    val appPackage: String?
) {
    HEB(
        displayName = "H-E-B",
        brandColor = Color(0xFFEE3A2D),
        baseUrl = "https://www.heb.com",
        logoUrl = "https://logo.clearbit.com/heb.com",
        initial = "H",
        appPackage = "com.heb.grocery"
    ),
    WALMART(
        displayName = "Walmart",
        brandColor = Color(0xFF0071CE),
        baseUrl = "https://www.walmart.com",
        logoUrl = "https://logo.clearbit.com/walmart.com",
        initial = "W",
        appPackage = "com.walmart.android"
    ),
    SAMS_CLUB(
        displayName = "Sam's Club",
        brandColor = Color(0xFF0060A9),
        baseUrl = "https://www.samsclub.com",
        logoUrl = "https://logo.clearbit.com/samsclub.com",
        initial = "S",
        appPackage = "com.rfi.sams.android"
    ),
    COSTCO(
        displayName = "Costco",
        brandColor = Color(0xFFE31837),
        baseUrl = "https://www.costco.com",
        logoUrl = "https://logo.clearbit.com/costco.com",
        initial = "C",
        appPackage = "com.costco.app.android"
    ),
    TARGET(
        displayName = "Target",
        brandColor = Color(0xFFCC0000),
        baseUrl = "https://www.target.com",
        logoUrl = "https://logo.clearbit.com/target.com",
        initial = "T",
        appPackage = "com.target.ui"
    ),
    SPROUTS(
        displayName = "Sprouts",
        brandColor = Color(0xFF3B7D23),
        baseUrl = "https://www.sprouts.com",
        logoUrl = "https://logo.clearbit.com/sprouts.com",
        initial = "SP",
        appPackage = "com.sprouts.android"
    );
}

enum class StockStatus {
    IN_STOCK,
    OUT_OF_STOCK,
    LIMITED_STOCK,
    UNKNOWN
}

enum class FulfillmentType {
    PICKUP,
    SHIPPING,
    DELIVERY,
    IN_STORE_ONLY
}

/**
 * Result from a single store lookup.
 */
data class StoreResult(
    val store: Store,
    val productName: String = "",
    val price: Double? = null,
    val unitPrice: String? = null,
    val imageUrl: String? = null,
    val productUrl: String? = null,
    val stockStatus: StockStatus = StockStatus.UNKNOWN,
    val fulfillmentOptions: List<FulfillmentType> = emptyList(),
    val membershipRequired: Boolean = false,
    val errorMessage: String? = null,
    val isLoading: Boolean = false,
    val isTextSearch: Boolean = false // True for text searches, false for UPC scans
)

/**
 * Aggregated comparison result for a single UPC lookup.
 */
data class ComparisonResult(
    val upc: String,
    val query: String,
    val results: List<StoreResult>,
    val timestamp: Long = System.currentTimeMillis(),
    val bestPrice: StoreResult? = null
)

/**
 * Search history entry.
 */
data class SearchHistoryEntry(
    val upc: String,
    val productName: String,
    val timestamp: Long,
    val lowestPrice: Double?
)

/**
 * User location for store proximity.
 */
data class UserLocation(
    val latitude: Double,
    val longitude: Double,
    val city: String = "",
    val state: String = "",
    val zip: String = ""
) {
    val displayName: String
        get() = when {
            city.isNotEmpty() && state.isNotEmpty() -> "$city, $state"
            zip.isNotEmpty() -> "ZIP: $zip"
            else -> "%.4f, %.4f".format(java.util.Locale.US, latitude, longitude)
        }
}
