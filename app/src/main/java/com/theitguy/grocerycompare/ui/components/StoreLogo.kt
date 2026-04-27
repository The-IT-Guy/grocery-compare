package com.theitguy.grocerycompare.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.theitguy.grocerycompare.data.models.Store

/**
 * Displays a store logo loaded from the web via Coil.
 * Falls back to a colored rounded square with the store's initial if the logo can't load.
 */
@Composable
fun StoreLogo(
    store: Store,
    size: Dp = 32.dp,
    modifier: Modifier = Modifier
) {
    SubcomposeAsyncImage(
        model = store.logoUrl,
        contentDescription = "${store.displayName} logo",
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp)),
        contentScale = ContentScale.Fit,
        loading = {
            LogoFallback(store = store, size = size)
        },
        error = {
            LogoFallback(store = store, size = size)
        }
    )
}

@Composable
private fun LogoFallback(store: Store, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(store.brandColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = store.initial,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.4f).sp
        )
    }
}
