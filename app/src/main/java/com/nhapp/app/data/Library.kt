package com.nhapp.app.data

import androidx.compose.runtime.mutableStateListOf
import com.nhapp.app.App
import com.nhapp.app.net.Api
import com.nhapp.app.net.GallerySummary
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 本機收藏與閱讀紀錄（不需要登入帳號）。
 * 以 JSON 存在 filesDir，數量不大，整份讀寫即可。
 */
object Library {
    private val favFile by lazy { File(App.instance.filesDir, "favorites.json") }
    private val historyFile by lazy { File(App.instance.filesDir, "history.json") }

    val favorites = mutableStateListOf<GallerySummary>()
    val history = mutableStateListOf<GallerySummary>()
    /** 每本讀到第幾頁（從 0 起算） */
    private val progress = HashMap<Int, Int>()

    private var loaded = false

    fun load() {
        if (loaded) return
        loaded = true
        favorites.addAll(readList(favFile))
        history.addAll(readList(historyFile))
        runCatching {
            val o = JSONObject(File(App.instance.filesDir, "progress.json").readText())
            o.keys().forEach { progress[it.toInt()] = o.getInt(it) }
        }
    }

    fun isFavorite(id: Int) = favorites.any { it.id == id }

    fun toggleFavorite(g: GallerySummary) {
        val i = favorites.indexOfFirst { it.id == g.id }
        if (i >= 0) favorites.removeAt(i) else favorites.add(0, g)
        writeList(favFile, favorites)
    }

    fun addHistory(g: GallerySummary) {
        history.removeAll { it.id == g.id }
        history.add(0, g)
        while (history.size > 300) history.removeAt(history.lastIndex)
        writeList(historyFile, history)
    }

    fun clearHistory() {
        history.clear()
        progress.clear()
        writeList(historyFile, history)
        saveProgress()
    }

    fun progressOf(id: Int): Int = progress[id] ?: 0

    fun setProgress(id: Int, page: Int) {
        if (progress[id] == page) return
        progress[id] = page
        saveProgress()
    }

    private fun saveProgress() {
        val o = JSONObject()
        progress.forEach { (k, v) -> o.put(k.toString(), v) }
        runCatching { File(App.instance.filesDir, "progress.json").writeText(o.toString()) }
    }

    private fun readList(f: File): List<GallerySummary> = runCatching {
        val a = JSONArray(f.readText())
        (0 until a.length()).map { Api.parseSummary(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun writeList(f: File, list: List<GallerySummary>) {
        val a = JSONArray()
        list.forEach { g ->
            a.put(
                JSONObject()
                    .put("id", g.id)
                    .put("media_id", g.mediaId)
                    .put("english_title", g.englishTitle)
                    .put("japanese_title", g.japaneseTitle)
                    .put("thumbnail", g.thumb)
                    .put("thumbnail_width", g.thumbWidth)
                    .put("thumbnail_height", g.thumbHeight)
                    .put("num_pages", g.numPages)
                    .put("num_favorites", g.numFavorites)
                    .put("tag_ids", JSONArray(g.tagIds))
            )
        }
        runCatching { f.writeText(a.toString()) }
    }
}
