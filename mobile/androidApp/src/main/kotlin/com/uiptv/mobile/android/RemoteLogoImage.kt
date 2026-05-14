package com.uiptv.mobile.android

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
        Box(modifier = modifier.background(Color(0xFF24313C)))
    } else {
        Image(
            bitmap = loaded,
            contentDescription = contentDescription,
            modifier = modifier.background(Color(0xFF24313C)),
            contentScale = ContentScale.Fit
        )
    }
}
