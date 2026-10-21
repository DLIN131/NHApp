package com.nhapp.app.ui

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.LazyListState
import com.nhapp.app.net.Api
import com.nhapp.app.net.Gallery
import com.nhapp.app.net.Tag
import com.nhapp.app.net.GallerySummary
import com.nhapp.app.net.PageResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 可無限往下捲的列表。狀態（含捲動位置）存在物件裡，
 * 跟著導覽堆疊走，回上一頁時列表和位置都還在。
 */
class Feed(private val scope: CoroutineScope, private val loader: suspend (page: Int) -> PageResult) {
    val items = mutableStateListOf<GallerySummary>()
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var end by mutableStateOf(false)
        private set
    val grid = LazyGridState()
    private var page = 0
    private var job: Job? = null

    fun loadMore() {
        if (loading || end) return
        loading = true
        error = null
        job = scope.launch {
            try {
                val r = loader(page + 1)
                page++
                val seen = items.mapTo(HashSet()) { it.id }
                items.addAll(r.items.filter { it.id !in seen })
                if (r.items.isEmpty() || page >= r.numPages) end = true
            } catch (e: Exception) {
                error = e.message ?: e.toString()
            } finally {
                loading = false
            }
        }
    }

    fun refresh() {
        job?.cancel()
        loading = false
        items.clear()
        page = 0
        end = false
        error = null
        scope.launch { grid.scrollToItem(0) }
        loadMore()
    }
}

/** 標籤清單（分類頁），跟 [Feed] 一樣捲到底載下一頁 */
class TagFeed(private val scope: CoroutineScope, private val type: String) {
    val items = mutableStateListOf<Tag>()
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var end by mutableStateOf(false)
        private set
    val list = LazyListState()
    private var page = 0

    fun loadMore() {
        if (loading || end) return
        loading = true
        error = null
        scope.launch {
            try {
                val (tags, pages) = Api.tags(type, page + 1)
                page++
                items.addAll(tags)
                if (tags.isEmpty() || page >= pages) end = true
            } catch (e: Exception) {
                error = e.message ?: e.toString()
            } finally {
                loading = false
            }
        }
    }
}

/** 排序選項：API 參數 to 顯示名稱 */
val SORTS = listOf(
    "date" to "最新",
    "popular-today" to "今日熱門",
    "popular-week" to "本週熱門",
    "popular-month" to "本月熱門",
    "popular" to "歷來熱門",
)

sealed class Route {
    /** 給 SaveableStateHolder 用的鍵，必須是可存進 Bundle 的型別 */
    val key: String = "route${nextKey++}"

    object Home : Route()

    /** 搜尋結果或某個標籤底下的作品 */
    class Results(
        val title: String,
        val query: String?,
        val tagId: Int?,
        sort: String,
    ) : Route() {
        var sort by mutableStateOf(sort)
        var feed: Feed? = null
    }

    class Detail(val id: Int, val preview: GallerySummary?) : Route() {
        var gallery by mutableStateOf<Gallery?>(null)
        var error by mutableStateOf<String?>(null)
        val grid = LazyGridState()
    }

    class Reader(val gallery: Gallery, val startPage: Int) : Route()

    /** 分類瀏覽：知名作者、社團、原作… */
    class Tags(type: String) : Route() {
        var type by mutableStateOf(type)
        val feeds = HashMap<String, TagFeed>()
    }

    object Settings : Route()
}

private var nextKey = 0

object Nav {
    val stack = mutableStateListOf<Route>(Route.Home)
    val top: Route get() = stack.last()

    fun push(r: Route) {
        stack.add(r)
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun openGallery(g: GallerySummary) = push(Route.Detail(g.id, g))
    fun openGallery(id: Int) = push(Route.Detail(id, null))
}
