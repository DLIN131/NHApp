package com.nhapp.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nhapp.app.data.Downloads
import com.nhapp.app.data.Downloads.Status
import com.nhapp.app.data.Library
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.nhapp.app.net.Api
import com.nhapp.app.net.Gallery
import com.nhapp.app.net.Tag
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TAG_TYPES = listOf(
    "parody" to "原作",
    "character" to "角色",
    "tag" to "標籤",
    "artist" to "作者",
    "group" to "社團",
    "language" to "語言",
    "category" to "分類",
)

@Composable
fun DetailScreen(route: Route.Detail) {
    // 重試按鈕會把 error 清掉，重新觸發這裡
    LaunchedEffect(route, route.error) {
        if (route.gallery == null && route.error == null) load(route)
    }
    val g = route.gallery
    val fav = Library.favorites.any { it.id == route.id }
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        TopBar("#${route.id}") {
            IconButton(onClick = {
                val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(android.content.Intent.EXTRA_TEXT, "${Api.BASE}/g/${route.id}/")
                context.startActivity(android.content.Intent.createChooser(send, null))
            }) { Icon(Icons.Filled.Share, "分享") }
            IconButton(onClick = { (g?.toSummary() ?: route.preview)?.let { Library.toggleFavorite(it) } }) {
                Icon(if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "收藏", tint = if (fav) Accent else Color.White)
            }
        }
        when {
            g != null -> Content(route, g)
            route.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ErrorBox(route.error!!) { route.error = null }
            }
            else -> CenterLoading()
        }
    }
}

private suspend fun load(route: Route.Detail) {
    // 書櫃裡有的直接讀本機資料，不用網路
    Downloads.find(route.id)?.let {
        route.gallery = Downloads.localize(it.gallery)
        return
    }
    try {
        route.gallery = Api.gallery(route.id)
    } catch (e: Exception) {
        route.error = e.message ?: e.toString()
    }
}

private fun openReader(g: Gallery, page: Int) {
    Library.addHistory(Downloads.find(g.id)?.let { Downloads.summaryOf(it) } ?: g.toSummary())
    Nav.push(Route.Reader(Downloads.localize(g), page))
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun Content(route: Route.Detail, g: Gallery) {
    val progress = Library.progressOf(g.id)
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(100.dp),
        state = route.grid,
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        fullWidth("head") {
            Row {
                AsyncImage(
                    model = g.cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(130.dp)
                        .aspectRatio(0.7f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Surface)
                        .clickable { openReader(g, 0) },
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        g.englishTitle.ifBlank { g.japaneseTitle },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.combinedClickable(onClick = {}, onLongClick = {
                            clipboard.setText(AnnotatedString(g.englishTitle))
                            android.widget.Toast.makeText(context, "已複製標題", android.widget.Toast.LENGTH_SHORT).show()
                        }),
                    )
                    if (g.japaneseTitle.isNotBlank() && g.japaneseTitle != g.englishTitle) {
                        Spacer(Modifier.height(4.dp))
                        Text(g.japaneseTitle, color = Color.LightGray, fontSize = 13.sp, lineHeight = 17.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(g.uploadDate * 1000))
                    Text("${g.numPages} 頁 · ♥ ${g.numFavorites} · $date", color = Color.Gray, fontSize = 12.sp)
                }
            }
        }
        fullWidth("buttons") {
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { openReader(g, progress) },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (progress > 0) "繼續閱讀（第 ${progress + 1} 頁）" else "開始閱讀")
                }
                if (progress > 0) OutlinedButton(onClick = { openReader(g, 0) }) { Text("從頭") }
            }
        }
        fullWidth("download") { DownloadButton(g) }
        fullWidth("tags") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TAG_TYPES.forEach { (type, label) ->
                    val list = g.tags.filter { it.type == type }
                    if (list.isNotEmpty()) TagLine(label, list)
                }
            }
        }
        fullWidth("pagesTitle") { SectionTitle("全部頁面") }
        itemsIndexed(g.pages, key = { _, p -> "p${p.number}" }) { i, p ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Surface)
                    .clickable { openReader(g, i) }
                    .aspectRatio(if (p.width > 0 && p.height > 0) p.width.toFloat() / p.height else 0.7f)
            ) {
                AsyncImage(model = p.thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Text(
                    "${p.number}",
                    color = Color.White,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(Color(0xAA000000))
                        .padding(horizontal = 4.dp),
                )
            }
        }
        if (g.related.isNotEmpty()) {
            fullWidth("related") {
                Column(Modifier.padding(top = 12.dp)) {
                    SectionTitle("相關作品")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(g.related, key = { it.id }) { r -> GalleryCard(r, Modifier.width(120.dp)) { Nav.openGallery(r) } }
                    }
                }
            }
        }
        fullWidth("bottomSpace") { Spacer(Modifier.height(24.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagLine(label: String, tags: List<Tag>) {
    Row {
        Text(label, color = Color.Gray, fontSize = 13.sp, modifier = Modifier.width(40.dp).padding(top = 5.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            tags.forEach { t ->
                Row(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Surface)
                        .clickable { openTag(t) }
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(t.name, color = Color.White, fontSize = 13.sp)
                    if (t.count > 0) Text(" ${formatCount(t.count)}", color = Color.Gray, fontSize = 11.sp)
                }
            }
        }
    }
}

private fun formatCount(n: Int): String = when {
    n >= 1000 -> "${n / 1000}K"
    else -> n.toString()
}

@Composable
private fun DownloadButton(g: Gallery) {
    val context = LocalContext.current
    val item = Downloads.find(g.id)
    var confirmDelete by remember { mutableStateOf(false) }
    // Android 13 起要通知權限才看得到下載進度；拒絕也照樣下載
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        Downloads.enqueue(g)
    }
    fun start() {
        val need = android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (need) notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else Downloads.enqueue(g)
    }
    Column(Modifier.padding(bottom = 8.dp)) {
        OutlinedButton(
            onClick = {
                when (item?.status) {
                    null -> start()
                    Status.Queued, Status.Running -> Downloads.pause(g.id)
                    Status.Paused, Status.Failed -> Downloads.resume(g.id)
                    Status.Done -> confirmDelete = true
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                when (item?.status) {
                    null -> "下載到書櫃"
                    Status.Queued -> "排隊中（點一下暫停）"
                    Status.Running -> "下載中 ${item.done}/${item.total}（點一下暫停）"
                    Status.Paused -> "已暫停 ${item.done}/${item.total}（點一下繼續）"
                    Status.Failed -> "下載失敗（點一下重試）"
                    Status.Done -> "✓ 已在書櫃（點一下刪除）"
                }
            )
        }
        if (item != null && item.status != Status.Done && item.total > 0) {
            LinearProgressIndicator(
                progress = { item.done.toFloat() / item.total },
                color = Accent,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
        item?.error?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 12.sp) }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("從書櫃刪除？") },
            text = { Text("下載的圖片會被刪掉，之後要看需要重新載入。") },
            confirmButton = { TextButton(onClick = { Downloads.delete(g.id); confirmDelete = false }) { Text("刪除", color = Accent) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}
