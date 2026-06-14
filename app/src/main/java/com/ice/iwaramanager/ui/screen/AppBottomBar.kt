package com.ice.iwaramanager.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.ice.iwaramanager.data.model.MainTab

@Composable
fun AppBottomBar(
    selectedTab: MainTab,
    onSelectTab: (MainTab) -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(48.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompactBottomBarItem(
                selected = selectedTab == MainTab.Library,
                icon = {
                    Icon(
                        imageVector = Icons.Filled.VideoLibrary,
                        contentDescription = "视频库",
                        modifier = Modifier.size(20.dp)
                    )
                },
                label = "视频库",
                onClick = {
                    onSelectTab(MainTab.Library)
                },
                modifier = Modifier.weight(1f)
            )

            CompactBottomBarItem(
                selected = selectedTab == MainTab.Filters,
                icon = {
                    Icon(
                        imageVector = Icons.Filled.FilterAlt,
                        contentDescription = "筛选",
                        modifier = Modifier.size(20.dp)
                    )
                },
                label = "筛选",
                onClick = {
                    onSelectTab(MainTab.Filters)
                },
                modifier = Modifier.weight(1f)
            )

            CompactBottomBarItem(
                selected = selectedTab == MainTab.Search,
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "搜索",
                        modifier = Modifier.size(20.dp)
                    )
                },
                label = "搜索",
                onClick = {
                    onSelectTab(MainTab.Search)
                },
                modifier = Modifier.weight(1f)
            )

            CompactBottomBarItem(
                selected = selectedTab == MainTab.Tasks,
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Assignment,
                        contentDescription = "任务",
                        modifier = Modifier.size(20.dp)
                    )
                },
                label = "任务",
                onClick = {
                    onSelectTab(MainTab.Tasks)
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CompactBottomBarItem(
    selected: Boolean,
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(onClick) {
                detectTapGestures(
                    onTap = {
                        onClick()
                    }
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CompositionLocalProvider(
            LocalContentColor provides contentColor
        ) {
            icon()

            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}
