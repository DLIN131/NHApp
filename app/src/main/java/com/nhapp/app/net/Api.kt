package com.nhapp.app.net

import com.nhapp.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** 列表裡的一本 */
data class GallerySummary(
    val id: Int,
    val mediaId: String,
    val englishTitle: String,
    val japaneseTitle: String,
    val thumb: String,
    val thumbWidth: Int,
    val thumbHeight: Int,
    val numPages: Int,
    val numFavorites: Int,
    val tagIds: List<Int>,
)

data class Tag(val id: Int, val type: String, val name: String, val count: Int)

data class Page(val number: Int, val url: String, val thumb: String, val width: Int, val height: Int)

data class Gallery(
    val id: Int,
    val mediaId: String,
    val englishTitle: String,
    val japaneseTitle: String,
    val prettyTitle: String,
    val cover: String,
    val thumb: String,
    val uploadDate: Long,
    val numPages: Int,
    val numFavorites: Int,
    val tags: List<Tag>,
    val pages: List<Page>,
    val related: List<GallerySummary>,
    /** API 原始 JSON，下載時存成 meta.json 供離線使用 */
    val raw: String = "",
) {
    fun toSummary() = GallerySummary(
        id, mediaId, englishTitle, japaneseTitle, thumb, 250, 353, numPages, numFavorites, tags.map { it.id },
    )
}

class PageResult(val items: List<GallerySummary>, val numPages: Int)

/**
 * 官方 v2 REST API（/api/v2），不需要過 Cloudflare，直接用 OkHttp。
 * 圖片在 i1~i4 / t1~t4 子網域，路徑由 API 給（galleries/<media_id>/...）。
 */
object Api {
    /** 網址以 Base64 存放，原始碼裡不出現明文 */
    val HOST: String = decode("bmhlbnRhaS5uZXQ=")
    val BASE = "https://$HOST"
    private val API = "$BASE/api/v2"

    private val userAgent = "NHApp/${BuildConfig.VERSION_NAME} (Android personal reader)"

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", userAgent)
                    .header("Referer", "$BASE/")
                    .build()
            )
        }
        .build()

    /** 分散到 1~4 號伺服器，同一個路徑固定落在同一台，快取才有效 */
    private fun server(prefix: String, path: String): String {
        val n = (path.hashCode() and 0x7fffffff) % 4 + 1
        return "https://$prefix$n.$HOST/$path"
    }

    fun thumbUrl(path: String) = server("t", path)
    fun imageUrl(path: String) = server("i", path)

    private suspend fun get(path: String, params: Map<String, Any?> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val url = "$API$path".toHttpUrl().newBuilder().apply {
                params.forEach { (k, v) -> if (v != null) addQueryParameter(k, v.toString()) }
            }.build()
            http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                val body = r.body?.string().orEmpty()
                if (!r.isSuccessful) {
                    val msg = runCatching { JSONObject(body).optString("error") }.getOrNull()
                    throw Exception(
                        when {
                            r.code == 404 -> "找不到這本（可能已被刪除）"
                            r.code == 429 -> "請求太頻繁，稍等一下再試"
                            !msg.isNullOrBlank() -> "載入失敗：$msg"
                            else -> "載入失敗（HTTP ${r.code}）"
                        }
                    )
                }
                body
            }
        }

    /** 最新上傳 */
    suspend fun latest(page: Int): PageResult = parsePage(JSONObject(get("/galleries", mapOf("page" to page, "per_page" to 25))))

    /** 搜尋；query 支援站內語法，例如 language:chinese、tag:"big breasts"、-tag:xxx */
    suspend fun search(query: String, sort: String, page: Int): PageResult =
        parsePage(JSONObject(get("/search", mapOf("query" to query, "sort" to sort, "page" to page))))

    suspend fun tagged(tagId: Int, sort: String, page: Int): PageResult =
        parsePage(JSONObject(get("/galleries/tagged", mapOf("tag_id" to tagId, "sort" to sort, "page" to page, "per_page" to 25))))

    /** 首頁「現在熱門」那幾本 */
    suspend fun popularNow(): List<GallerySummary> {
        val arr = JSONArray(get("/galleries/popular"))
        return (0 until arr.length()).map { parseSummary(arr.getJSONObject(it)) }
    }

    /** 某類標籤依作品數排序，例如 artist＝知名作者；回傳（標籤, 總頁數） */
    suspend fun tags(type: String, page: Int): Pair<List<Tag>, Int> {
        val o = JSONObject(get("/tags/$type", mapOf("sort" to "popular", "page" to page)))
        val a = o.getJSONArray("result")
        val list = (0 until a.length()).map {
            val t = a.getJSONObject(it)
            Tag(t.getInt("id"), t.optString("type"), t.optString("name"), t.optInt("count"))
        }
        return list to o.optInt("num_pages", 1)
    }

    suspend fun random(): Int = JSONObject(get("/galleries/random")).getInt("id")

    suspend fun gallery(id: Int): Gallery = parseGallery(get("/galleries/$id", mapOf("include" to "related")))

    fun parseGallery(raw: String): Gallery {
        val o = JSONObject(raw)
        val title = o.getJSONObject("title")
        val tags = o.optJSONArray("tags")?.let { a ->
            (0 until a.length()).map {
                val t = a.getJSONObject(it)
                Tag(t.getInt("id"), t.optString("type"), t.optString("name"), t.optInt("count"))
            }
        }.orEmpty()
        val pages = o.optJSONArray("pages")?.let { a ->
            (0 until a.length()).map {
                val p = a.getJSONObject(it)
                Page(
                    p.getInt("number"),
                    imageUrl(p.getString("path")),
                    thumbUrl(p.optString("thumbnail")),
                    p.optInt("width"),
                    p.optInt("height"),
                )
            }
        }.orEmpty()
        val related = o.optJSONArray("related")?.let { a -> (0 until a.length()).map { parseSummary(a.getJSONObject(it)) } }.orEmpty()
        return Gallery(
            id = o.getInt("id"),
            mediaId = o.optString("media_id"),
            englishTitle = title.optString("english"),
            japaneseTitle = title.optString("japanese"),
            prettyTitle = title.optString("pretty"),
            cover = thumbUrl(o.getJSONObject("cover").getString("path")),
            thumb = thumbUrl(o.getJSONObject("thumbnail").getString("path")),
            uploadDate = o.optLong("upload_date"),
            numPages = o.optInt("num_pages"),
            numFavorites = o.optInt("num_favorites"),
            tags = tags,
            pages = pages,
            related = related,
            raw = raw,
        )
    }

    private fun parsePage(o: JSONObject): PageResult {
        val arr = o.getJSONArray("result")
        return PageResult((0 until arr.length()).map { parseSummary(arr.getJSONObject(it)) }, o.optInt("num_pages", 1))
    }

    fun parseSummary(o: JSONObject): GallerySummary {
        val ids = o.optJSONArray("tag_ids")
        return GallerySummary(
            id = o.getInt("id"),
            mediaId = o.optString("media_id"),
            englishTitle = o.optString("english_title"),
            japaneseTitle = o.optString("japanese_title"),
            thumb = o.optString("thumbnail").let { if (it.startsWith("http")) it else thumbUrl(it) },
            thumbWidth = o.optInt("thumbnail_width", 250),
            thumbHeight = o.optInt("thumbnail_height", 353),
            numPages = o.optInt("num_pages"),
            numFavorites = o.optInt("num_favorites"),
            tagIds = ids?.let { a -> (0 until a.length()).map { a.getInt(it) } }.orEmpty(),
        )
    }
}

internal fun decode(s: String): String = String(android.util.Base64.decode(s, android.util.Base64.DEFAULT), Charsets.UTF_8)
