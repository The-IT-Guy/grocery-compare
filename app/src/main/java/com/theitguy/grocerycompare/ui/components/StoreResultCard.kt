package com.theitguy.grocerycompare.ui.components

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.theitguy.grocerycompare.data.models.*
import com.theitguy.grocerycompare.ui.theme.*

@Composable
fun StoreResultCard(
    result: StoreResult,
    isBestPrice: Boolean = false,
    onOpenWebView: ((Store, String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val cardShape = RoundedCornerShape(16.dp)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isBestPrice) Modifier.border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(listOf(BestPriceGlow, Secondary)),
                    shape = cardShape
                ) else Modifier
            ),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isBestPrice) BestPriceBg else DarkCard
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isBestPrice) 4.dp else 1.dp)
    ) {
        if (result.isLoading) {
            LoadingCard(store = result.store)
            return@Card
        }

        // TEXT SEARCH LAYOUT - simplified, action-focused
        if (result.isTextSearch) {
            TextSearchCard(
                result = result,
                context = context,
                uriHandler = uriHandler,
                onOpenWebView = onOpenWebView
            )
            return@Card
        }

        // NORMAL LAYOUT - with pricing/stock info
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StoreLogo(store = result.store, size = 32.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = result.store.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                if (isBestPrice) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = BestPriceGlow.copy(alpha = 0.15f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Star, contentDescription = null, tint = BestPriceGlow, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("BEST", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = BestPriceGlow)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Product info
            Row(modifier = Modifier.fillMaxWidth()) {
                if (result.imageUrl != null) {
                    AsyncImage(
                        model = result.imageUrl,
                        contentDescription = result.productName,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.08f)),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    if (result.productName.isNotEmpty()) {
                        Text(
                            text = result.productName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    if (result.price != null) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "$",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isBestPrice) BestPriceGlow else Primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = String.format(java.util.Locale.US, "%.2f", result.price),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isBestPrice) BestPriceGlow else TextPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Stock + Fulfillment (ALWAYS SHOW)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StockStatusChip(status = result.stockStatus)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    result.fulfillmentOptions.take(3).forEach { FulfillmentChip(type = it) }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Buttons
            val webUrl = result.productUrl ?: result.store.baseUrl
            val hasApp = isAppInstalled(context, result.store.appPackage)
            val isMembershipStore = result.store == Store.SAMS_CLUB || result.store == Store.COSTCO

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (hasApp) {
                    OutlinedButton(
                        onClick = { openInApp(context, result.store, webUrl) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = result.store.brandColor
                        )
                    ) {
                        Icon(Icons.Filled.PhoneAndroid, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Open App", style = MaterialTheme.typography.labelMedium)
                    }
                }

                Button(
                    onClick = {
                        if (isMembershipStore && result.price == null && onOpenWebView != null) {
                            onOpenWebView(result.store, webUrl)
                        } else {
                            uriHandler.openUri(webUrl)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = result.store.brandColor.copy(alpha = 0.15f),
                        contentColor = result.store.brandColor
                    )
                ) {
                    Icon(
                        if (isMembershipStore && result.price == null) Icons.Filled.Lock else Icons.Filled.OpenInBrowser,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Open Web", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/**
 * Simplified card for text searches - action-focused, no fake data.
 */
@Composable
private fun TextSearchCard(
    result: StoreResult,
    context: Context,
    uriHandler: androidx.compose.ui.platform.UriHandler,
    onOpenWebView: ((Store, String) -> Unit)?
) {
    val webUrl = result.productUrl ?: result.store.baseUrl
    val hasApp = isAppInstalled(context, result.store.appPackage)
    val isMembershipStore = result.store == Store.SAMS_CLUB || result.store == Store.COSTCO

    Column(modifier = Modifier.padding(16.dp)) {
        // Header with logo
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StoreLogo(store = result.store, size = 32.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = result.store.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Search for \"${result.productName}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Big action button
        Button(
            onClick = {
                if (hasApp) {
                    openInApp(context, result.store, webUrl)
                } else if (isMembershipStore && onOpenWebView != null) {
                    onOpenWebView(result.store, webUrl)
                } else {
                    uriHandler.openUri(webUrl)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = result.store.brandColor,
                contentColor = Color.White
            )
        ) {
            Icon(
                if (hasApp) Icons.Filled.PhoneAndroid 
                else if (isMembershipStore) Icons.Filled.Lock 
                else Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                when {
                    hasApp -> "Open in ${result.store.displayName} App"
                    isMembershipStore -> "Login & Search"
                    else -> "Search on ${result.store.displayName}.com"
                },
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

private fun isAppInstalled(context: Context, packageName: String?): Boolean {
    if (packageName == null) return false
    return try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}

private fun openInApp(context: Context, store: Store, webUrl: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)).apply {
            setPackage(store.appPackage)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(store.appPackage ?: "")
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
            }
        } catch (_: Exception) { }
    }
}

@Composable
fun StockStatusChip(status: StockStatus) {
    val (text, color, icon) = when (status) {
        StockStatus.IN_STOCK -> Triple("In Stock", InStockGreen, Icons.Filled.CheckCircle)
        StockStatus.OUT_OF_STOCK -> Triple("Out of Stock", OutOfStockRed, Icons.Filled.Cancel)
        StockStatus.LIMITED_STOCK -> Triple("Limited", LimitedStockYellow, Icons.Filled.Warning)
        StockStatus.UNKNOWN -> Triple("Check Store", UnknownGray, Icons.Outlined.HelpOutline)
    }

    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.12f)) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = color)
        }
    }
}

@Composable
fun FulfillmentChip(type: FulfillmentType) {
    val (text, icon) = when (type) {
        FulfillmentType.PICKUP -> "Pickup" to Icons.Filled.Store
        FulfillmentType.SHIPPING -> "Ship" to Icons.Filled.LocalShipping
        FulfillmentType.DELIVERY -> "Delivery" to Icons.Filled.DeliveryDining
        FulfillmentType.IN_STORE_ONLY -> "In Store" to Icons.Filled.Storefront
    }

    Surface(shape = RoundedCornerShape(6.dp), color = DarkCardElevated) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(12.dp))
            Spacer(modifier = Modifier.width(3.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
    }
}

@Composable
fun LoadingCard(store: Store) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StoreLogo(store = store, size = 28.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(store.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Box(modifier = Modifier.fillMaxWidth(0.7f).height(16.dp).clip(RoundedCornerShape(4.dp)).background(ShimmerBase))
        Spacer(modifier = Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth(0.4f).height(28.dp).clip(RoundedCornerShape(4.dp)).background(ShimmerBase))
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)),
            color = store.brandColor.copy(alpha = 0.6f),
            trackColor = DarkCardElevated
        )
    }
}
