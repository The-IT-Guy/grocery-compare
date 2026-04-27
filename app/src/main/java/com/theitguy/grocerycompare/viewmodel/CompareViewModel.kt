package com.theitguy.grocerycompare.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theitguy.grocerycompare.data.location.LocationService
import com.theitguy.grocerycompare.data.models.*
import com.theitguy.grocerycompare.data.repository.PriceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the comparison screen.
 */
data class CompareUiState(
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val comparison: ComparisonResult? = null,
    val storeResults: Map<Store, StoreResult> = emptyMap(),
    val enabledStores: Set<Store> = Store.entries.toSet(),
    val searchHistory: List<SearchHistoryEntry> = emptyList(),
    val error: String? = null,
    val sortBy: SortOption = SortOption.PRICE_LOW
)

enum class SortOption(val label: String) {
    PRICE_LOW("Price: Low to High"),
    PRICE_HIGH("Price: High to Low"),
    STORE_NAME("Store Name"),
    AVAILABILITY("Availability")
}

class CompareViewModel : ViewModel() {

    private val repository = PriceRepository()

    private val _uiState = MutableStateFlow(CompareUiState())
    val uiState: StateFlow<CompareUiState> = _uiState.asStateFlow()

    private val _userLocation = MutableStateFlow<UserLocation?>(null)
    val userLocation: StateFlow<UserLocation?> = _userLocation.asStateFlow()

    /**
     * Fetch user's GPS location.
     */
    fun updateLocation(context: Context) {
        viewModelScope.launch {
            val location = LocationService.getCurrentLocation(context)
            _userLocation.value = location
        }
    }

    /**
     * Check if location permission is granted.
     */
    fun hasLocationPermission(context: Context): Boolean {
        return LocationService.hasLocationPermission(context)
    }

    /**
     * Search for products by UPC barcode or product name/keywords.
     * Results stream in as each store completes.
     */
    fun searchByUpc(query: String) {
        val cleanQuery = query.trim()

        if (cleanQuery.isEmpty()) {
            _uiState.update { it.copy(error = "Please enter a UPC code or product name") }
            return
        }

        _uiState.update {
            it.copy(
                searchQuery = cleanQuery,
                isSearching = true,
                comparison = null,
                storeResults = it.enabledStores.associateWith { store ->
                    StoreResult(store = store, isLoading = true)
                },
                error = null
            )
        }

        viewModelScope.launch {
            try {
                val result = repository.search(
                    query = cleanQuery,
                    enabledStores = _uiState.value.enabledStores,
                    onStoreResult = { storeResult ->
                        // Update individual store result as it comes in
                        _uiState.update { state ->
                            val updated = state.storeResults.toMutableMap()
                            updated[storeResult.store] = storeResult
                            state.copy(storeResults = updated)
                        }
                    }
                )

                // Add to search history
                val historyEntry = SearchHistoryEntry(
                    upc = cleanQuery,
                    productName = result.bestPrice?.productName
                        ?: result.results.firstOrNull { it.productName.isNotEmpty() }?.productName
                        ?: cleanQuery,
                    timestamp = System.currentTimeMillis(),
                    lowestPrice = result.bestPrice?.price
                )

                _uiState.update {
                    it.copy(
                        isSearching = false,
                        comparison = result,
                        searchHistory = (listOf(historyEntry) + it.searchHistory).take(50)
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        error = "Search failed: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Handle a barcode scan result.
     */
    fun onBarcodeScan(barcode: String) {
        _uiState.update { it.copy(searchQuery = barcode) }
        searchByUpc(barcode)
    }

    /**
     * Update search query text.
     */
    fun updateQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query, error = null) }
    }

    /**
     * Toggle a store on/off.
     */
    fun toggleStore(store: Store) {
        _uiState.update { state ->
            val updated = state.enabledStores.toMutableSet()
            if (store in updated && updated.size > 1) {
                updated.remove(store)
            } else {
                updated.add(store)
            }
            state.copy(enabledStores = updated)
        }
    }

    /**
     * Change sort order.
     */
    fun setSortOption(option: SortOption) {
        _uiState.update { it.copy(sortBy = option) }
    }

    /**
     * Get sorted results based on current sort option.
     */
    fun getSortedResults(): List<StoreResult> {
        val state = _uiState.value
        val results = state.storeResults.values.toList()

        return when (state.sortBy) {
            SortOption.PRICE_LOW -> results.sortedWith(
                compareBy<StoreResult> { it.price == null }.thenBy { it.price }
            )
            SortOption.PRICE_HIGH -> results.sortedWith(
                compareBy<StoreResult> { it.price == null }.thenByDescending { it.price }
            )
            SortOption.STORE_NAME -> results.sortedBy { it.store.displayName }
            SortOption.AVAILABILITY -> results.sortedWith(
                compareBy<StoreResult> {
                    when (it.stockStatus) {
                        StockStatus.IN_STOCK -> 0
                        StockStatus.LIMITED_STOCK -> 1
                        StockStatus.UNKNOWN -> 2
                        StockStatus.OUT_OF_STOCK -> 3
                    }
                }.thenBy { it.price }
            )
        }
    }

    /**
     * Clear current results.
     */
    fun clearResults() {
        _uiState.update {
            it.copy(
                searchQuery = "",
                comparison = null,
                storeResults = emptyMap(),
                error = null,
                isSearching = false
            )
        }
    }

    /**
     * Clear error state.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Clear search history.
     */
    fun clearHistory() {
        _uiState.update { it.copy(searchHistory = emptyList()) }
    }
}
