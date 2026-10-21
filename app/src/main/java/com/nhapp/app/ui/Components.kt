package com.nhapp.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nhapp.app.Prefs
import com.nhapp.app.net.GallerySummary

val Accent = Color(0xFFED2553)
val Surface = Color(0xFF2B2B2B)
val Background = Color(0xFF1F1F1F)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color.White,
            secondary = Accent,
            background = Background,
            surface = Background,
            surfaceVariant = Surface,
            surfaceContainer = Surface,
            surfaceContainerHigh = Surface,
        ),
        content = content,
    )
}

/** 語言標籤 id → 旗子文字，列表上標示用 */
private val LANG_BADGE = mapOf(29963 to "中", 6346 to "日", 12227 to "EN")

fun GallerySummary.displayTitle(): String =
    if (Prefs.japaneseTitle && japaneseTitle.isNotBlank()) japaneseTitle else englishTitle.ifBlank { japaneseTitle }

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun GalleryCard(
    g: GallerySummary,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    overlay: @Composable (androidx.compose.foundation.layout.BoxScope.() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .aspectRatio(0.7f)
    ) {
        AsyncImage(
            model = g.thumb,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        LANG_BADGE.entries.firstOrNull { it.key in g.tagIds }?.let { (_, label) ->
            Text(
                label,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Accent)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        Text(
            "${g.numPages}P",
            color = Color.White,
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xAA000000))
                .padding(horizontal = 5.dp, vertical = 1.dp),
        )
        Text(
            g.displayTitle(),
            color = Color.White,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                .padding(start = 6.dp, end = 6.dp, top = 16.dp, bottom = 6.dp),
        )
        overlay?.invoke(this)
    }
}

fun LazyGridScope.fullWidth(key: Any? = null, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

/** 封面網格，捲到底自動載下一頁 */
@Composable
fun FeedGrid(
    feed: Feed,
    modifier: Modifier = Modifier,
    header: (LazyGridScope.() -> Unit)? = null,
) {
    LaunchedEffect(feed) { if (feed.items.isEmpty() && !feed.end) feed.loadMore() }
    val nearEnd by remember(feed) {
        derivedStateOf {
            val last = feed.grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= feed.grid.layoutInfo.totalItemsCount - 8
        }
    }
    LaunchedEffect(feed) {
        snapshotFlow { nearEnd }.collect { if (it && feed.items.isNotEmpty() && feed.error == null) feed.loadMore() }
    }
    GalleryGrid(feed.items, feed.grid, modifier, header) {
        fullWidth("footer") {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                when {
                    feed.error != null -> ErrorBox(feed.error!!) { feed.loadMore() }
                    feed.loading -> CircularProgressIndicator()
                    feed.end && feed.items.isEmpty() -> Text("沒有結果", color = Color.Gray)
                    feed.end -> Text("沒有更多了", color = Color.Gray, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun GalleryGrid(
    items: List<GallerySummary>,
    state: LazyGridState,
    modifier: Modifier = Modifier,
    header: (LazyGridScope.() -> Unit)? = null,
    footer: (LazyGridScope.() -> Unit)? = null,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(118.dp),
        state = state,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        header?.invoke(this)
        items(items, key = { it.id }) { g -> GalleryCard(g) { Nav.openGallery(g) } }
        footer?.invoke(this)
    }
}

@Composable
fun ErrorBox(message: String, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = Color(0xFFFF8A80), textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onRetry) { Text("重試") }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Accent else Surface),
    ) {
        Text(text, color = Color.White, fontSize = 13.sp)
    }
}

@Composable
fun SortChips(selected: String, onSelect: (String) -> Unit, hideDate: Boolean = false) {
    val sorts = if (hideDate) SORTS.drop(1) else SORTS
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        items(sorts.size) { i ->
            val (key, label) = sorts[i]
            Chip(label, key == selected) { onSelect(key) }
        }
    }
}

@Composable
fun CenterLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}
