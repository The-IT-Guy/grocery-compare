package com.theitguy.grocerycompare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.theitguy.grocerycompare.data.models.StoreResult
import com.theitguy.grocerycompare.ui.components.*
import com.theitguy.grocerycompare.ui.theme.*
import com.theitguy.grocerycompare.viewmodel.CompareUiState
import com.theitguy.grocerycompare.viewmodel.SortOption
import java.util.Locale

@Composable
fun ResultsScreen(
    uiState: CompareUiState,
    sortedResults: List<StoreResult>,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onScanClick: () -> Unit,
    onSortChange: (SortOption) -> Unit,
    onBackClick: () -> Unit,
    onOpenWebView: ((com.theitguy.grocerycompare.data.models.Store, String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val comparison = uiState.comparison
    val bestPriceStore = comparison?.bestPrice?.store

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // Top bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                Text(
                    text = "Price Comparison",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        }

        // Search bar (for re-searching)
        item {
            SearchBar(
                query = uiState.searchQuery,
                onQueryChange = onQueryChange,
                onSearch = onSearch,
                onScanClick = onScanClick,
                isSearching = uiState.isSearching
            )
        }

        // Store count indicator
        item {
            val enabledCount = uiState.enabledStores.size
            val totalStores = 6
            if (enabledCount < totalStores) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Primary.copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⚠️ Only searching $enabledCount of $totalStores stores",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Text(
                            "Enable in Settings",
                            style = MaterialTheme.typography.labelSmall,
                            color = Primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Savings summary card
        if (comparison != null && comparison.results.count { it.price != null } >= 2) {
            item {
                SavingsSummary(comparison.results)
            }
        }

        // Sort bar
        item {
            SortBar(
                currentSort = uiState.sortBy,
                onSortChange = onSortChange,
                resultCount = sortedResults.size
            )
        }

        // Store result cards
        items(
            items = sortedResults,
            key = { it.store.name }
        ) { result ->
            StoreResultCard(
                result = result,
                isBestPrice = result.store == bestPriceStore && result.price != null,
                onOpenWebView = onOpenWebView,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
            )
        }

        // No results message
        if (sortedResults.isEmpty() && !uiState.isSearching) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.SearchOff,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No results found",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Try a different UPC code",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun SavingsSummary(results: List<StoreResult>) {
    val pricesWithStores = results
        .filter { it.price != null }
        .sortedBy { it.price }

    if (pricesWithStores.size < 2) return

    val lowest = pricesWithStores.first()
    val highest = pricesWithStores.last()
    val savings = (highest.price!! - lowest.price!!)

    if (savings <= 0) return

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(14.dp),
        color = BestPriceBg
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Savings,
                contentDescription = null,
                tint = BestPriceGlow,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Potential Savings",
                    style = MaterialTheme.typography.labelMedium,
                    color = BestPriceGlow.copy(alpha = 0.8f)
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$${String.format(Locale.US, "%.2f", savings)}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = BestPriceGlow
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "less at ${lowest.store.displayName} vs ${highest.store.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
        }
    }
}
