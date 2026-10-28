package com.podparar.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

private val ArtworkShape = RoundedCornerShape(12.dp)

/**
 * A show's or episode's square picture with rounded corners. Until it has loaded, when it
 * fails and when there is no [url], a grey square with the podcasts icon stands in.
 * Give it a size (or a width) through [modifier]; it stays square.
 */
@Composable
fun Artwork(url: String?, contentDescription: String?, modifier: Modifier = Modifier) {
    var loaded by remember(url) { mutableStateOf(false) }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(ArtworkShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            Icon(
                painter = painterResource(R.drawable.ic_podcasts),
                contentDescription = if (url == null) contentDescription else null,
                modifier = Modifier.fillMaxWidth(0.4f).aspectRatio(1f),
                tint = MaterialTheme.colorScheme.outline,
            )
        }
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onLoading = { loaded = false },
                onSuccess = { loaded = true },
                onError = { loaded = false },
            )
        }
    }
}
