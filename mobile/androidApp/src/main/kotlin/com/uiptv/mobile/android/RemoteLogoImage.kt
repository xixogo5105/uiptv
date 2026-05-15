package com.uiptv.mobile.android

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

@Composable
fun RemoteLogoImage(logoUrl: String, contentDescription: String, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, logoUrl) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(logoUrl).openConnection().apply {
                    connectTimeout = 4_000
                    readTimeout = 4_000
                }
                connection.getInputStream().use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }

    val loaded = bitmap
    if (loaded == null) {
        Box(
            modifier = modifier.background(Color(0xFF24313C)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                fallbackInitials(contentDescription),
                color = Color(0xFFD1E4FF),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    } else {
        Image(
            bitmap = loaded,
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
        words.size >= 2 -> words.take(2).joinToString("") { it.first().uppercase() }
        words.size == 1 -> words.first().take(2).uppercase()
        else -> "TV"
    }
}
