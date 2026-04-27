package com.theitguy.grocerycompare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.theitguy.grocerycompare.data.models.Store
import com.theitguy.grocerycompare.ui.components.StoreLogo
import com.theitguy.grocerycompare.ui.theme.*

@Composable
fun SettingsScreen(
    enabledStores: Set<Store>,
    onToggleStore: (Store) -> Unit,
    onBackClick: () -> Unit,
    onOpenMembershipLogin: ((Store) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
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
                    text = "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        }

        // Stores section
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "STORES",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted
                )
                Text(
                    text = "${enabledStores.size} of ${Store.entries.size} enabled",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (enabledStores.size == Store.entries.size) InStockGreen else Primary
                )
            }
        }

        items(Store.entries) { store ->
            StoreToggleRow(
                store = store,
                isEnabled = store in enabledStores,
                onToggle = { onToggleStore(store) },
                canDisable = enabledStores.size > 1 || store !in enabledStores
            )
        }

        // Membership login section
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "MEMBERSHIP LOGINS",
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }

        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                color = DarkCard
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Log in to see member prices",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Sam's Club login button
                    OutlinedButton(
                        onClick = { onOpenMembershipLogin?.invoke(Store.SAMS_CLUB) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Store.SAMS_CLUB.brandColor
                        )
                    ) {
                        StoreLogo(store = Store.SAMS_CLUB, size = 24.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Login to Sam's Club",
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Costco login button
                    OutlinedButton(
                        onClick = { onOpenMembershipLogin?.invoke(Store.COSTCO) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Store.COSTCO.brandColor
                        )
                    ) {
                        StoreLogo(store = Store.COSTCO, size = 24.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Login to Costco",
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // Info section
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "ABOUT",
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }

        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                color = DarkCard
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Grocery Compare and Save",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Version 1.0.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Built by The IT Guy • calltheitguy.tech",
                        style = MaterialTheme.typography.bodySmall,
                        color = Primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = DividerColor)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Prices are fetched from each store's website and may not " +
                               "reflect in-store pricing. Membership-only stores (Sam's Club, " +
                               "Costco) require valid membership for purchase. Availability " +
                               "varies by location.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }
        }

        // How it works
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                color = DarkCard
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "How It Works",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    HowItWorksStep(
                        number = "1",
                        title = "Scan or Enter UPC",
                        description = "Use the camera to scan a product barcode or type the UPC number manually."
                    )
                    HowItWorksStep(
                        number = "2",
                        title = "Automatic Lookup",
                        description = "The app checks all enabled stores simultaneously for pricing and availability."
                    )
                    HowItWorksStep(
                        number = "3",
                        title = "Compare & Save",
                        description = "See prices side-by-side with stock status, fulfillment options, and savings."
                    )
                }
            }
        }
    }
}

@Composable
private fun StoreToggleRow(
    store: Store,
    isEnabled: Boolean,
    onToggle: () -> Unit,
    canDisable: Boolean
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp),
        shape = RoundedCornerShape(12.dp),
        color = DarkCard
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StoreLogo(store = store, size = 28.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = store.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                if (store == Store.SAMS_CLUB || store == Store.COSTCO) {
                    Text(
                        text = "Membership required",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = {
                    if (canDisable || !isEnabled) onToggle()
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Primary,
                    checkedTrackColor = Primary.copy(alpha = 0.3f),
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = DarkCardElevated
                )
            )
        }
    }
}

@Composable
private fun HowItWorksStep(
    number: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = CircleShape,
            color = Primary.copy(alpha = 0.15f),
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    text = number,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Primary,
                    textAlign = TextAlign.Center
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}
