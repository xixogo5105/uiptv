package com.uiptv.mobile.shared.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual fun formatEpochSeconds(epochSeconds: Long): String {
    val formatter = SimpleDateFormat.getDateTimeInstance(
        SimpleDateFormat.SHORT,
        SimpleDateFormat.SHORT,
        Locale.getDefault()
    )
    return formatter.format(Date(epochSeconds * 1000))
}
