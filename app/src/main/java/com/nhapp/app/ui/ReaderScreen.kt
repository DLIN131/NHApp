package com.nhapp.app.ui

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.nhapp.app.Prefs
import com.nhapp.app.data.Library
import com.nhapp.app.net.Page
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReaderScreen(route: Route.Reader) {
    val g = route.gallery
    val pages = g.pages
    var vertical by rememberSaveable { mutableStateOf(Prefs.vertical) }
    var overlay by rememberSaveable { mutableStateOf(false) }
    var current by rememberSaveable { mutableIntStateOf(route.startPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))) }
    val scope = rememberCoroutineScope()

    ImmersiveMode()

    LaunchedEffect(current) { Library.setProgress(g.id, current) }

    // 兩種模式各自的捲動狀態；切換模式時帶著目前頁數過去
    val list = rememberLazyListState(initialFirstVisibleItemIndex = current)
    val pager = rememberPagerState(initialPage = current) { pages.size }
    LaunchedEffect(vertical) {
        if (vertical) {
            list.scrollToItem(current)
            snapshotFlow { list.firstVisibleItemIndex }.collect { current = it }
        } else {
            pager.scrollToPage(current)
            snapshotFlow { pager.currentPage }.collect { current = it }
        }
    }

    fun jump(i: Int) {
        current = i
        scope.launch { if (vertical) list.scrollToItem(i) else pager.scrollToPage(i) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (vertical) {
            Zoomable(onTap = { overlay = !overlay }, panOnlyWhenZoomed = false) { zoomMod ->
                LazyColumn(state = list, modifier = zoomMod.fillMaxSize()) {
                    itemsIndexed(pages, key = { _, p -> p.number }) { _, p -> PageImage(p, fit = false) }
                }
            }
        } else {
            HorizontalPager(
                state = pager,
                reverseLayout = Prefs.rightToLeft,
                beyondViewportPageCount = 2,
                modifier = Modifier.fillMaxSize(),
            ) { i ->
                Zoomable(onTap = { overlay = !overlay }, panOnlyWhenZoomed = true) { zoomMod ->
                    Box(zoomMod.fillMaxSize(), contentAlignment = Alignment.Center) { PageImage(pages[i], fit = true) }
                }
            }
        }

        // 不顯示選單時，右下角留個小小的頁碼
        if (!overlay) {
            Text(
                "${current + 1}/${pages.size}",
                color = Color.White,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(6.dp)
                    .background(Color(0x88000000))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }

        AnimatedVisibility(overlay, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier.fillMaxWidth().background(Color(0xCC000000)).statusBarsPadding().height(52.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { Nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White) }
                Text(
                    g.prettyTitle.ifBlank { g.englishTitle },
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    vertical = !vertical
                    Prefs.vertical = vertical
                }) { Text(if (vertical) "改左右翻頁" else "改上下捲動", color = Color.White) }
            }
        }
        AnimatedVisibility(overlay, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(Modifier.fillMaxWidth().background(Color(0xCC000000)).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                var drag by remember { mutableFloatStateOf(-1f) }
                val shown = if (drag >= 0) drag.roundToInt() else current
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("第 ${shown + 1} / ${pages.size} 頁", color = Color.White)
                    if (!vertical) TextButton(onClick = { Prefs.rightToLeft = !Prefs.rightToLeft; jump(current) }) {
                        Text(if (Prefs.rightToLeft) "由右往左" else "由左往右", color = Color.White)
                    }
                }
                if (pages.size > 1) {
                    Slider(
                        value = shown.toFloat(),
                        onValueChange = { drag = it },
                        onValueChangeFinished = {
                            if (drag >= 0) jump(drag.roundToInt())
                            drag = -1f
                        },
                        valueRange = 0f..(pages.size - 1).toFloat(),
                        colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
                    )
                }
            }
        }
    }
}

@Composable
private fun PageImage(p: Page, fit: Boolean) {
    val context = LocalContext.current
    var retry by remember { mutableIntStateOf(0) }
    val ratio = if (p.width > 0 && p.height > 0) p.width.toFloat() / p.height else 0.7f
    val request = remember(p.url, retry) {
        ImageRequest.Builder(context).data(p.url).setParameter("retry", retry, memoryCacheKey = null).build()
    }
    SubcomposeAsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = if (fit) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(ratio),
    ) {
        when (painter.state) {
            is AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
            is AsyncImagePainter.State.Error -> Box(
                Modifier.fillMaxWidth().aspectRatio(ratio).clickable { retry++ },
                contentAlignment = Alignment.Center,
            ) { Text("第 ${p.number} 頁載入失敗，點這裡重試", color = Color.Gray) }
            else -> Box(Modifier.fillMaxWidth().aspectRatio(ratio), contentAlignment = Alignment.Center) {
                Text("${p.number}", color = Color.DarkGray, fontSize = 32.sp)
            }
        }
    }
}

/**
 * 雙指縮放、雙擊放大。
 * panOnlyWhenZoomed＝true（左右翻頁）：沒放大時不吃手勢，讓 Pager 能滑。
 * 上下捲動模式：單指拖曳交給 LazyColumn，只處理雙指縮放與放大後的左右平移。
 */
@Composable
private fun Zoomable(onTap: () -> Unit, panOnlyWhenZoomed: Boolean, content: @Composable (Modifier) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { pos ->
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                            // 以點擊位置為中心放大
                            val center = Offset(size.width / 2f, size.height / 2f)
                            offset = clampOffset((center - pos) * (scale - 1f), scale, size.width, size.height, panOnlyWhenZoomed)
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val multi = event.changes.count { it.pressed } > 1
                        if (multi || scale > 1f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            val p = if (panOnlyWhenZoomed) pan else Offset(pan.x, 0f)
                            offset = clampOffset(offset + p, scale, size.width, size.height, panOnlyWhenZoomed)
                            // 上下捲動模式的單指拖曳不吃掉，讓列表照常捲
                            if (multi || panOnlyWhenZoomed) event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    if (scale < 1.05f) {
                        scale = 1f
                        offset = Offset.Zero
                    }
                }
            }
    ) {
        content(
            Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
        )
    }
}

private fun clampOffset(o: Offset, scale: Float, w: Int, h: Int, allowY: Boolean): Offset {
    val maxX = w * (scale - 1f) / 2f
    val maxY = h * (scale - 1f) / 2f
    return Offset(o.x.coerceIn(-maxX, maxX), if (allowY) o.y.coerceIn(-maxY, maxY) else 0f)
}

/** 閱讀時隱藏狀態列和導覽列，並保持螢幕常亮 */
@Composable
private fun ImmersiveMode() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        view.keepScreenOn = true
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            view.keepScreenOn = false
        }
    }
}
