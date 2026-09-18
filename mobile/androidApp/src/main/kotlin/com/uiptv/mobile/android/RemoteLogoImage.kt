package com.uiptv.mobile.android

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

@Composable
fun RemoteLogoImage(
    logoUrl: String,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val cache = remember(context) { ThumbnailCache.getInstance(context) }

    var bitmap by remember(logoUrl) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(logoUrl) {
        if (logoUrl.isBlank()) return@LaunchedEffect
        bitmap = cache.get(logoUrl) ?: cache.getFromDisk(logoUrl)
        if (bitmap == null) {
            try {
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    cache.fetchAsync(logoUrl).join()
                }
                bitmap = cache.get(logoUrl)
            } catch (_: CancellationException) {
                // Composable left the composition before fetch completed
            }
        }
    }

    val loadedBitmap = bitmap?.asImageBitmap()
    if (loadedBitmap == null) {
        Box(
            modifier = modifier.background(Color(0xFF24313C)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = fallbackInitials(contentDescription),
                color = Color(0xFFD1E4FF),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    } else {
        Image(
            bitmap = loadedBitmap,
            contentDescription = contentDescription,
            modifier = modifier.background(Color(0xFF24313C)),
            contentScale = ContentScale.Fit
        )
    }
}

private fun fallbackInitials(contentDescription: String): String {
    val label = contentDescription.removePrefix("Logo").trim()
    val words = label.split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        words.size >= 2 -> words.take(2).joinToString("") { it.first().uppercaseChar().toString() }
        words.size == 1 -> words.first().take(2).uppercase()
        else -> "TV"
    }
}
