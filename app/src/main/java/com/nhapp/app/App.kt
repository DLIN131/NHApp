package com.nhapp.app

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.nhapp.app.net.Api
import android.os.Build

class App : Application(), ImageLoaderFactory {
    companion object {
        lateinit var instance: App
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(Api.http)
        .crossfade(true)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("images"))
                .maxSizeBytes(512L * 1024 * 1024)
                .build()
        }
        .components {
            if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
        }
        .build()
}

/** 偏好設定 */
object Prefs {
    private val sp: SharedPreferences by lazy {
        App.instance.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    }

    /** 語言過濾：""＝全部，否則是 chinese / japanese / english */
    var language: String
        get() = sp.getString("language", "") ?: ""
        set(value) = sp.edit().putString("language", value).apply()

    /** 閱讀模式：true＝上下捲動，false＝左右翻頁 */
    var vertical: Boolean
        get() = sp.getBoolean("vertical", true)
        set(value) = sp.edit().putBoolean("vertical", value).apply()

    /** 左右翻頁時由右往左（日漫順序） */
    var rightToLeft: Boolean
        get() = sp.getBoolean("rtl", false)
        set(value) = sp.edit().putBoolean("rtl", value).apply()

    /** 使用者選「略過這個版本」的 versionCode，自動檢查時不再提示這一版 */
    var skippedVersion: Int
        get() = sp.getInt("skipped_version", 0)
        set(value) = sp.edit().putInt("skipped_version", value).apply()

    /** 列表標題用日文 */
    var japaneseTitle: Boolean
        get() = sp.getBoolean("jp_title", false)
        set(value) = sp.edit().putBoolean("jp_title", value).apply()
}
