package com.ice.iwaramanager.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ice.iwaramanager.SearchUiState
import com.ice.iwaramanager.data.model.MainTab
import com.ice.iwaramanager.data.model.VideoItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: SearchUiState,
    selectedTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onOpenVideo: (VideoItem) -> Unit,
    onPlayVideo: (VideoItem) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("搜索")
                }
            )
        },
        bottomBar = {
            AppBottomBar(
                selectedTab = selectedTab,
                onSelectTab = onSelectTab
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            SearchRow(
                query = state.query,
                onQueryChange = onQueryChange,
                onClearQuery = onClearQuery
            )

            if (state.folderUriString == null) {
                Text(
                    text = "请先在设置中选择视频目录。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
                return@Column
            }

            if (state.query.isBlank()) {
                Text(
                    text = "输入标题、文件名、Iwara ID 或清晰度进行搜索。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
                return@Column
            }

            if (state.results.isEmpty()) {
                Text(
                    text = "没有搜索结果。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                VideoListContent(
                    videos = state.results,
                    listState = rememberLazyListState(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    onOpenVideo = onOpenVideo,
                    onMatchVideo = {},
                    onPlayVideo = onPlayVideo
                )
            }
        }
    }
}

@Composable
private fun SearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = {
                Text("搜索标题、文件名或 Iwara ID")
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null
                )
            },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )

        TextButton(
            onClick = onClearQuery,
            enabled = query.isNotBlank()
        ) {
            Text("清除")
        }
    }
}
