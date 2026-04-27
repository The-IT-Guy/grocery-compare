package com.theitguy.grocerycompare.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.theitguy.grocerycompare.ui.theme.*

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onScanClick: () -> Unit,
    isSearching: Boolean,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Text input field
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = {
                Text(
                    "UPC or product name...",
                    color = TextMuted
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = TextMuted
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            Icons.Filled.Clear,
                            contentDescription = "Clear",
                            tint = TextMuted
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(
                onSearch = {
                    keyboardController?.hide()
                    onSearch()
                }
            ),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Primary,
                unfocusedBorderColor = DividerColor,
                cursorColor = Primary,
                focusedContainerColor = DarkCard,
                unfocusedContainerColor = DarkCard,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            enabled = !isSearching
        )

        // Scan button
        FilledIconButton(
            onClick = onScanClick,
            modifier = Modifier.size(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Primary,
                contentColor = DarkBackground
            ),
            enabled = !isSearching
        ) {
            Icon(
                Icons.Filled.QrCodeScanner,
                contentDescription = "Scan Barcode",
                modifier = Modifier.size(26.dp)
            )
        }

        // Search / Go button
        FilledIconButton(
            onClick = {
                keyboardController?.hide()
                onSearch()
            },
            modifier = Modifier.size(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Secondary,
                contentColor = DarkBackground
            ),
            enabled = !isSearching && query.isNotEmpty()
        ) {
            if (isSearching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = DarkBackground,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    Icons.Filled.Send,
                    contentDescription = "Compare Prices",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun SortBar(
    currentSort: com.theitguy.grocerycompare.viewmodel.SortOption,
    onSortChange: (com.theitguy.grocerycompare.viewmodel.SortOption) -> Unit,
    resultCount: Int,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$resultCount stores checked",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )

        Box {
            TextButton(onClick = { expanded = true }) {
                Icon(
                    Icons.Filled.Sort,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = currentSort.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(DarkCardElevated)
            ) {
                com.theitguy.grocerycompare.viewmodel.SortOption.entries.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                option.label,
                                color = if (option == currentSort) Primary else TextPrimary
                            )
                        },
                        onClick = {
                            onSortChange(option)
                            expanded = false
                        },
                        leadingIcon = if (option == currentSort) {
                            {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = Primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null
                    )
                }
            }
        }
    }
}
