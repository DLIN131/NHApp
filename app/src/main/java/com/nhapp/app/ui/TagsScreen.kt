package com.nhapp.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nhapp.app.net.Tag

/** 分類種類：API 的 tag_type to 顯示名稱 */
val TAG_CATEGORIES = listOf(
    "artist" to "知名作者",
    "group" to "社團",
    "parody" to "原作",
    "character" to "角色",
    "tag" to "標籤",
)

/** 開啟某個標籤底下的作品（依熱門排序） */
fun openTag(t: Tag) {
    val q = if (t.name.contains(' ')) "${t.type}:\"${t.name}\"" else "${t.type}:${t.name}"
    Nav.push(Route.Results(t.name, q, t.id, "popular"))
}

/** 首頁上方的分類入口 */
@Composable
fun CategoryEntryRow() {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        items(TAG_CATEGORIES) { (type, label) ->
            Chip(label, selected = type == "artist") { Nav.push(Route.Tags(type)) }
        }
    }
}

@Composable
fun TagsScreen(route: Route.Tags) {
    val feed = route.feeds.getOrPut(route.type) { TagFeed(Home.scope, route.type) }
    Column(Modifier.fillMaxSize()) {
        TopBar(TAG_CATEGORIES.firstOrNull { it.first == route.type }?.second ?: "分類")
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        ) {
            items(TAG_CATEGORIES) { (type, label) ->
                Chip(label, type == route.type) { route.type = type }
            }
        }
        TagList(feed)
    }
}

@Composable
private fun TagList(feed: TagFeed) {
    LaunchedEffect(feed) { if (feed.items.isEmpty() && !feed.end) feed.loadMore() }
    val nearEnd by remember(feed) {
        derivedStateOf {
            val last = feed.list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= feed.list.layoutInfo.totalItemsCount - 15
        }
    }
    LaunchedEffect(feed) {
        snapshotFlow { nearEnd }.collect { if (it && feed.items.isNotEmpty() && feed.error == null) feed.loadMore() }
    }
    LazyColumn(state = feed.list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 8.dp)) {
        itemsIndexed(feed.items, key = { _, t -> t.id }) { i, t ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Surface)
                    .clickable { openTag(t) }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${i + 1}",
                    color = if (i < 3) Accent else Color.Gray,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.width(40.dp),
                )
                Text(t.name, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text("${t.count} 本", color = Color.Gray, fontSize = 12.sp)
            }
        }
        item {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                when {
                    feed.error != null -> ErrorBox(feed.error!!) { feed.loadMore() }
                    feed.loading -> CircularProgressIndicator()
                }
            }
        }
    }
}
