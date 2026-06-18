package com.ice.iwaramanager.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ice.iwaramanager.data.model.VideoItem
import java.io.File

private const val HighlightFadeMillis = 450

@Composable
fun VideoListContent(
    videos: List<VideoItem>,
    listState: LazyListState,
    contentPadding: PaddingValues,
    onOpenVideo: (VideoItem) -> Unit,
    onMatchVideo: (VideoItem) -> Unit,
    onPlayVideo: (VideoItem) -> Unit,
    showRematchButtonInList: Boolean = true,
    highlightedVideoUri: String? = null
) {
    LazyColumn(
        state = listState,
        contentPadding = contentPadding
    ) {
        items(
            items = videos,
            key = { it.uriString }
        ) { video ->
            VideoListItem(
                video = video,
                onClick = {
                    onOpenVideo(video)
                },
                onPlay = {
                    onPlayVideo(video)
                },
                onMatch = {
                    onMatchVideo(video)
                },
                showRematchButton = video.matchedIwaraId == null || showRematchButtonInList,
                highlighted = highlightedVideoUri == video.uriString
            )
        }
    }
}

@Composable
fun VideoGridContent(
    videos: List<VideoItem>,
    gridState: LazyGridState,
    gridColumns: Int,
    contentPadding: PaddingValues,
    onOpenVideo: (VideoItem) -> Unit,
    onMatchVideo: (VideoItem) -> Unit,
    onPlayVideo: (VideoItem) -> Unit,
    highlightedVideoUri: String? = null
) {
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(gridColumns.coerceIn(2, 6)),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(
            items = videos,
            key = { it.uriString }
        ) { video ->
            VideoGridItem(
                video = video,
                highlighted = highlightedVideoUri == video.uriString,
                onClick = {
                    onOpenVideo(video)
                }
            )
        }
    }
}

@Composable
private fun VideoListItem(
    video: VideoItem,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onMatch: () -> Unit,
    showRematchButton: Boolean,
    highlighted: Boolean
) {
    val containerColor = animatedHighlightColor(highlighted)

    ListItem(
        colors = ListItemDefaults.colors(
            containerColor = containerColor
        ),
        leadingContent = {
            VideoCover(
                video = video,
                modifier = Modifier
                    .width(112.dp)
                    .height(63.dp)
            )
        },
        headlineContent = {
            Text(
                text = video.title ?: video.displayName,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = buildVideoInfoLine(video),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            if (showRematchButton) {
                TextButton(
                    onClick = onMatch
                ) {
                    Text(if (video.matchedIwaraId == null) "匹配" else "重匹配")
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    )
}

@Composable
private fun VideoGridItem(
    video: VideoItem,
    highlighted: Boolean,
    onClick: () -> Unit
) {
    val overlayColor = animatedHighlightOverlayColor(highlighted)

    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        VideoCover(
            video = video,
            modifier = Modifier.fillMaxSize()
        )

        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .background(overlayColor)
        )
    }
}

@Composable
private fun animatedHighlightColor(
    highlighted: Boolean
): Color {
    val targetColor = if (highlighted) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    val color by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = HighlightFadeMillis),
        label = "videoHighlight"
    )
    return color
}

@Composable
private fun animatedHighlightOverlayColor(
    highlighted: Boolean
): Color {
    val targetColor = if (highlighted) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f)
    } else {
        Color.Transparent
    }
    val color by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = HighlightFadeMillis),
        label = "videoHighlightOverlay"
    )
    return color
}

@Composable
private fun VideoCover(
    video: VideoItem,
    modifier: Modifier
) {
    val coverPath = video.coverFilePath

    if (coverPath != null) {
        AsyncImage(
            model = File(coverPath),
            contentDescription = video.displayName,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    } else {
        Column(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "无封面",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
