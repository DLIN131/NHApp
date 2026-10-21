package com.nhapp.app.data

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nhapp.app.App
import com.nhapp.app.net.Api
import com.nhapp.app.net.Gallery
import com.nhapp.app.net.GallerySummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * 下載管理（書櫃）。每本存在 filesDir/downloads/<id>/：
 * meta.json（API 原始資料）、cover.*、1.webp、2.webp…，全部下載完會多一個 .complete。
 * 一次下載一本，每本同時抓 3 張。狀態都在主執行緒更新。
 */
object Downloads {
    enum class Status { Queued, Running, Paused, Failed, Done }

    class Item(val gallery: Gallery, status: Status, done: Int) {
        val id get() = gallery.id
        val total get() = gallery.pages.size
        var status by mutableStateOf(status)
        var done by mutableIntStateOf(done)
        var error by mutableStateOf<String?>(null)
    }

    /** 新加入的在前面 */
    val items = mutableStateListOf<Item>()

    private val root by lazy { File(App.instance.filesDir, "downloads").apply { mkdirs() } }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var runner: Job? = null
    private var current: Pair<Int, Job>? = null
    private var loaded = false

    fun load() {
        if (loaded) return
        loaded = true
        val dirs = root.listFiles()?.filter { it.isDirectory }.orEmpty().sortedByDescending { it.lastModified() }
        for (dir in dirs) {
            val g = runCatching { Api.parseGallery(File(dir, "meta.json").readText()) }.getOrNull() ?: continue
            val complete = File(dir, ".complete").exists()
            val done = g.pages.count { pageFile(dir, it.number, it.url).exists() }
            items.add(Item(g, if (complete) Status.Done else Status.Paused, done))
        }
    }

    fun find(id: Int): Item? = items.firstOrNull { it.id == id }

    fun isDownloaded(id: Int) = find(id)?.status == Status.Done

    private fun dirOf(id: Int) = File(root, id.toString())

    private fun ext(url: String) = url.substringAfterLast('/').substringAfter('.').substringBefore('.').ifBlank { "jpg" }

    private fun pageFile(dir: File, number: Int, url: String) = File(dir, "$number.${ext(url)}")

    private fun coverFile(dir: File) = dir.listFiles()?.firstOrNull { it.name.startsWith("cover.") }

    private fun File.uri() = Uri.fromFile(this).toString()

    /** 加入下載；已存在就繼續下載未完成的部分 */
    fun enqueue(g: Gallery) {
        val existing = find(g.id)
        if (existing != null) {
            if (existing.status == Status.Paused || existing.status == Status.Failed) {
                existing.status = Status.Queued
                existing.error = null
                kick()
            }
            return
        }
        val dir = dirOf(g.id).apply { mkdirs() }
        File(dir, "meta.json").writeText(g.raw)
        items.add(0, Item(g, Status.Queued, 0))
        kick()
    }

    fun resume(id: Int) {
        find(id)?.let { enqueue(it.gallery) }
    }

    fun pause(id: Int) {
        val item = find(id) ?: return
        if (current?.first == id) current?.second?.cancel()
        if (item.status == Status.Queued || item.status == Status.Running) item.status = Status.Paused
    }

    fun delete(id: Int) {
        pause(id)
        items.removeAll { it.id == id }
        scope.launch(Dispatchers.IO) { dirOf(id).deleteRecursively() }
    }

    /** 書櫃與離線用：有下載的頁面換成本機檔案 */
    fun localize(g: Gallery): Gallery {
        val item = find(g.id) ?: return g
        val base = item.gallery
        val dir = dirOf(g.id)
        val pages = base.pages.map { p ->
            val f = pageFile(dir, p.number, p.url)
            if (f.exists()) p.copy(url = f.uri(), thumb = f.uri()) else p
        }
        val cover = coverFile(dir)?.uri()
        return base.copy(pages = pages, cover = cover ?: base.cover, thumb = cover ?: base.thumb)
    }

    fun summaryOf(item: Item): GallerySummary {
        val s = item.gallery.toSummary()
        val cover = coverFile(dirOf(item.id))?.uri() ?: return s
        return s.copy(thumb = cover)
    }

    private fun kick() {
        if (runner?.isActive == true) return
        DownloadService.start()
        runner = scope.launch {
            while (true) {
                val next = items.lastOrNull { it.status == Status.Queued } ?: break
                val job = launch { run(next) }
                current = next.id to job
                job.join()
                current = null
                // 被暫停時 job 會被取消，狀態已經在 pause() 設好
                if (next.status == Status.Running) next.status = Status.Paused
            }
        }
    }

    private suspend fun run(item: Item) {
        item.status = Status.Running
        item.error = null
        val dir = dirOf(item.id).apply { mkdirs() }
        try {
            if (coverFile(dir) == null) {
                runCatching { fetch(item.gallery.cover, File(dir, "cover.${ext(item.gallery.cover)}")) }
            }
            val gate = Semaphore(3)
            coroutineScope {
                item.gallery.pages.map { p ->
                    async {
                        val f = pageFile(dir, p.number, p.url)
                        if (f.exists()) return@async
                        gate.withPermit {
                            retry(3) { fetch(p.url, f) }
                            item.done++
                            DownloadService.update(item)
                        }
                    }
                }.awaitAll()
            }
            item.done = item.gallery.pages.count { pageFile(dir, it.number, it.url).exists() }
            withContext(Dispatchers.IO) { File(dir, ".complete").createNewFile() }
            item.status = Status.Done
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            item.status = Status.Failed
            item.error = e.message ?: e.toString()
        }
    }

    private suspend fun retry(times: Int, block: suspend () -> Unit) {
        var last: Exception? = null
        repeat(times) {
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                last = e
                kotlinx.coroutines.delay(1000L * (it + 1))
            }
        }
        throw last ?: IOException("下載失敗")
    }

    /** 先寫到 .part 再改名，中斷時不會留下壞掉的圖 */
    private suspend fun fetch(url: String, dest: File) = withContext(Dispatchers.IO) {
        val tmp = File(dest.parentFile, dest.name + ".part")
        Api.http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("圖片下載失敗（HTTP ${r.code}）")
            r.body!!.byteStream().use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                    }
                }
            }
        }
        if (!tmp.renameTo(dest)) throw IOException("無法寫入檔案")
    }

    /** 書櫃佔用空間（位元組） */
    suspend fun totalSize(): Long = withContext(Dispatchers.IO) {
        root.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
    }
}
