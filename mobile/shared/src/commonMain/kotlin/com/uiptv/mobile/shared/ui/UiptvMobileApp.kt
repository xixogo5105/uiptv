package com.uiptv.mobile.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun UiptvMobileApp() {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF101418),
            contentColor = Color(0xFFF4F7FA)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                BrowseHeader()
                BrowsePreview(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
                BottomTabs()
            }
        }
    }
}

@Composable
private fun BrowseHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF182028))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text("UIPTV", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Channels", color = Color(0xFFAEB8C2), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun BrowsePreview(modifier: Modifier = Modifier) {
    val categories = listOf("All", "News", "Sports", "Movies", "Kids", "Documentary")
    val channels = listOf(
        ChannelPreview("BBC News", "News", "Ready"),
        ChannelPreview("Sky Sports Main Event", "Sports", "Ready"),
        ChannelPreview("Discovery", "Documentary", "Cached"),
        ChannelPreview("Film Premier", "Movies", "Ready"),
        ChannelPreview("Kids Zone", "Kids", "Cached")
    )

    Row(modifier = modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LazyColumn(
            modifier = Modifier
                .width(108.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { category ->
                CategoryPill(category)
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(channels) { channel ->
                ChannelRow(channel)
            }
        }
    }
}

@Composable
private fun CategoryPill(name: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(Color(0xFF202A33))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(name, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ChannelRow(channel: ChannelPreview) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(Color(0xFF172029))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(channel.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(channel.category, color = Color(0xFFAEB8C2), style = MaterialTheme.typography.bodySmall)
        }
        Text(channel.state, color = Color(0xFF7BDCB5), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun BottomTabs() {
    NavigationBar(containerColor = Color(0xFF182028), contentColor = Color(0xFFF4F7FA)) {
        listOf("Channels", "Bookmarks", "Watching", "Accounts").forEachIndexed { index, label ->
            NavigationBarItem(
                selected = index == 0,
                onClick = {},
                icon = {},
                label = { Text(label) }
            )
        }
    }
}

private data class ChannelPreview(val name: String, val category: String, val state: String)
