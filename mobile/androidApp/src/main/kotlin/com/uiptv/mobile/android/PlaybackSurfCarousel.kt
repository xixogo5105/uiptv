package com.uiptv.mobile.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uiptv.mobile.shared.i18n.MobileMessages
import com.uiptv.mobile.shared.playback.PlaybackTarget

/** A horizontally browsable queue centred on the currently playing item. */
@Composable
internal fun PlaybackSurfCarousel(
    targets: List<PlaybackTarget>,
    currentIndex: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    onClose: () -> Unit
) {
    if (targets.size < 2 || currentIndex !in targets.indices) return

    val swipeThresholdPx = with(LocalDensity.current) { 48.dp.toPx() }
    val selectedColor = MaterialTheme.colorScheme.primary
    val pointerColor = selectedColor.copy(alpha = 0.58f)
    val panelShape = RoundedCornerShape(20.dp)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(onClose) {
                var downwardDistance = 0f
                var closed = false
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        if (!closed && dragAmount > 0f) {
                            downwardDistance += dragAmount
                            if (downwardDistance >= swipeThresholdPx) {
                                closed = true
                                onClose()
                            }
                        } else if (dragAmount < 0f) {
                            downwardDistance = (downwardDistance + dragAmount).coerceAtLeast(0f)
                        }
                        change.consume()
                    },
                    onDragEnd = { downwardDistance = 0f; closed = false },
                    onDragCancel = { downwardDistance = 0f; closed = false }
                )
            }
    ) {
        val spacing = 4.dp
        val panelWidth = maxWidth * 0.94f
        val panelSideMargin = (maxWidth - panelWidth) / 2
        val availableRowWidth = (panelWidth - 16.dp).coerceAtLeast(0.dp)
        val cardWidth = ((availableRowWidth - spacing * 6) / 7).coerceAtMost(96.dp)
        val thumbnailHeight = (cardWidth * 1.1f).coerceAtMost(64.dp)
        val pointerHeight = 14.dp
        val pointerGap = 4.dp
        val thumbnailShape = RoundedCornerShape(8.dp)
        val firstVisible = (currentIndex - 3).coerceAtLeast(0)
        val missingPrevious = (3 - currentIndex).coerceAtLeast(0)
        val missingNext = (3 - (targets.lastIndex - currentIndex)).coerceAtLeast(0)
        val centeredPadding = (availableRowWidth - (cardWidth * 7 + spacing * 6)).coerceAtLeast(0.dp) / 2
        val edgePadding = centeredPadding + (cardWidth + spacing) * missingPrevious
        val endPadding = centeredPadding + (cardWidth + spacing) * missingNext
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = firstVisible)

        LaunchedEffect(currentIndex, targets.size) {
            listState.animateScrollToItem(firstVisible)
        }

        Box(
            modifier = Modifier
                .width(panelWidth)
                .fillMaxSize()
                .padding(top = 36.dp)
                .align(Alignment.Center)
                .clip(panelShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.58f), panelShape)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.20f), panelShape)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                LazyRow(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(start = edgePadding, end = endPadding, bottom = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(targets, key = { index, target ->
                        "$index:${target.accountId}:${target.mode}:${target.categoryProviderId}:${target.channelId}:${target.episodeId}"
                    }) { index, target ->
                        val selected = index == currentIndex
                        Column(
                            modifier = Modifier
                                .width(cardWidth)
                                .height(pointerHeight + pointerGap + thumbnailHeight + 34.dp)
                                .clickable(
                                    enabled = enabled && !selected,
                                    interactionSource = null,
                                    indication = null
                                ) { onSelect(index) },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(pointerHeight),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                if (selected) {
                                    Canvas(Modifier.size(width = 16.dp, height = 12.dp)) {
                                        val centerX = size.width / 2f
                                        val shaftHalfWidth = size.width * 0.14f
                                        val arrowHalfWidth = size.width * 0.48f
                                        val arrowTop = size.height * 0.43f
                                        val arrow = Path().apply {
                                            moveTo(centerX - shaftHalfWidth, 0f)
                                            lineTo(centerX + shaftHalfWidth, 0f)
                                            lineTo(centerX + shaftHalfWidth, arrowTop)
                                            lineTo(centerX + arrowHalfWidth, arrowTop)
                                            lineTo(centerX, size.height)
                                            lineTo(centerX - arrowHalfWidth, arrowTop)
                                            lineTo(centerX - shaftHalfWidth, arrowTop)
                                            close()
                                        }
                                        drawPath(arrow, pointerColor)
                                    }
                                }
                            }
                            Spacer(Modifier.height(pointerGap))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(thumbnailHeight)
                                    .clip(thumbnailShape)
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                        thumbnailShape
                                    )
                                    .then(
                                        if (selected) Modifier.border(2.dp, selectedColor, thumbnailShape)
                                        else Modifier
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                RemoteLogoImage(
                                    logoUrl = target.logo,
                                    contentDescription = target.title,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(2.dp)
                                )
                            }
                            Text(
                                text = target.title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 3.dp),
                                color = if (selected) selectedColor else MaterialTheme.colorScheme.onSurface,
                                fontSize = 10.sp,
                                lineHeight = 11.sp,
                                maxLines = 2,
                                minLines = 1,
                                textAlign = TextAlign.Center,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 3.dp - panelSideMargin)
                .offset { IntOffset(x = 20, y = 0) }
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onClose)
                .semantics { contentDescription = MobileMessages.text("commonClose") },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "×",
                    fontSize = 23.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
