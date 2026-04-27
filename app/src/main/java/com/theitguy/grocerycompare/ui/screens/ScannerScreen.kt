package com.theitguy.grocerycompare.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.theitguy.grocerycompare.ui.components.BarcodeScannerView

@Composable
fun ScannerScreen(
    onBarcodeScanned: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    BarcodeScannerView(
        onBarcodeScanned = onBarcodeScanned,
        onClose = onClose,
        modifier = modifier
    )
}
