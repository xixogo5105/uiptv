package com.uiptv.mobile.android

import android.app.Activity
import android.os.Bundle
import androidx.compose.ui.platform.ComposeView
import com.uiptv.mobile.shared.ui.UiptvMobileApp

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            ComposeView(this).apply {
                setContent {
                    UiptvMobileApp()
                }
            }
        )
    }
}
