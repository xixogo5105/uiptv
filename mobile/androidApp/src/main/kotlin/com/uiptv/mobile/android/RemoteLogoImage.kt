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
import kotlinx.coroutines.withContext

@Composable
fun RemoteLogoImage(
    logoUrl: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    fallbackLogoUrl: String = "",
    fallbackLoader: (suspend () -> String)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val cache = remember(context) { ThumbnailCache.getInstance(context) }

    var bitmap by remember(logoUrl, fallbackLogoUrl, fallbackLoader) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(logoUrl, fallbackLogoUrl, fallbackLoader) {
        bitmap = loadLogoBitmap(cache, logoUrl, fallbackLogoUrl, fallbackLoader)
    }

    val loadedBitmap = bitmap?.asImageBitmap()
    if (loadedBitmap == null) {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = fallbackInitials(contentDescription),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Fit
        )
    }
}

internal suspend fun loadLogoBitmap(cache: ThumbnailCache, primary: String, fallback: String, fallbackLoader: (suspend () -> String)? = null): Bitmap? =
    withContext(kotlinx.coroutines.Dispatchers.IO) {
        for (source in listOf(primary, fallback).filter { it.isNotBlank() }.distinct()) {
            val cached = cache.get(source) ?: cache.getFromDisk(source)
            if (cached != null) return@withContext cached
            cache.fetchAsync(source).join()
            cache.get(source)?.let { return@withContext it }
        }
        val resolved = try { fallbackLoader?.invoke().orEmpty() }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { "" }
        if (resolved.isBlank() || resolved == primary || resolved == fallback) null
        else loadLogoBitmap(cache, resolved, "")
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
