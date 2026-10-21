package com.nhapp.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nhapp.app.data.Downloads
import com.nhapp.app.data.Downloads.Status

/** 書櫃：已下載和下載中的作品，點了離線閱讀，長按可暫停／刪除 */
@Composable
fun BookshelfScreen() {
    val items = Downloads.items
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("書櫃是空的\n在作品頁按「下載」，下載完不用網路也能看", color = Color.Gray, textAlign = TextAlign.Center)
        }
        return
    }
    var menu by remember { mutableStateOf<Downloads.Item?>(null) }
    var size by remember { mutableLongStateOf(0L) }
    val doneCount = items.count { it.status == Status.Done }
    LaunchedEffect(doneCount, items.size) { size = Downloads.totalSize() }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(118.dp),
        state = rememberLazyGridState(),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        fullWidth("info") {
            Text(
                "共 ${items.size} 本 · ${formatSize(size)} · 長按可暫停或刪除",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(4.dp),
            )
        }
        items(items, key = { it.id }) { item ->
            val summary = remember(item.id, item.status) { Downloads.summaryOf(item) }
            GalleryCard(
                summary,
                onLongClick = { menu = item },
                overlay = { if (item.status != Status.Done) ProgressOverlay(item) },
            ) {
                when (item.status) {
                    Status.Paused, Status.Failed -> Downloads.resume(item.id)
                    else -> Nav.push(Route.Detail(item.id, summary))
                }
            }
        }
    }

    menu?.let { item ->
        val title = item.gallery.prettyTitle.ifBlank { item.gallery.englishTitle }
        val active = item.status == Status.Queued || item.status == Status.Running
        AlertDialog(
            onDismissRequest = { menu = null },
            title = { Text(title, maxLines = 2, fontSize = 16.sp) },
            text = { Text("已下載 ${item.done} / ${item.total} 頁" + (item.error?.let { "\n$it" } ?: "")) },
            confirmButton = {
                TextButton(onClick = { Downloads.delete(item.id); menu = null }) { Text("刪除", color = Accent) }
            },
            dismissButton = {
                Row {
                    when {
                        active -> TextButton(onClick = { Downloads.pause(item.id); menu = null }) { Text("暫停") }
                        item.status != Status.Done -> TextButton(onClick = { Downloads.resume(item.id); menu = null }) { Text("繼續下載") }
                    }
                    TextButton(onClick = { menu = null }) { Text("取消") }
                }
            },
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.ProgressOverlay(item: Downloads.Item) {
    Column(
        Modifier
            .matchParentSize()
            .background(Color(0x99000000))
            .padding(10.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            when (item.status) {
                Status.Queued -> "排隊中"
                Status.Running -> "下載中"
                Status.Paused -> "已暫停\n點一下繼續"
                Status.Failed -> "下載失敗\n點一下重試"
                Status.Done -> ""
            },
            color = Color.White,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
        Text("${item.done}/${item.total}", color = Color.LightGray, fontSize = 12.sp)
        LinearProgressIndicator(
            progress = { if (item.total > 0) item.done.toFloat() / item.total else 0f },
            color = Accent,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
    }
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.0f MB".format(bytes / (1L shl 20).toDouble())
    else -> "${bytes / 1024} KB"
}
