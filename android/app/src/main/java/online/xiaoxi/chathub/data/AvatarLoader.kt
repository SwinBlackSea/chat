package online.xiaoxi.chathub.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 头像图片加载：内存 LRU + OkHttp 磁盘缓存（两级缓存，均按 URL 为键）。
 *
 * 服务端头像 URL 带 ?v=<mtime_ns> 版本参数，且 /avatars 返回
 * Cache-Control: max-age=1y, immutable：
 * - URL 不变（头像未换）→ 命中内存/磁盘缓存，不重复拉取
 * - URL 变（换了头像）→ 新 URL 全新拉取，不会显示旧图
 * - 上传成功后调用 clearAll() 兜底清空两级缓存
 */
object AvatarLoader {
    private const val DISK_CACHE_BYTES = 20L * 1024 * 1024

    private lateinit var appContext: Context

    /** 进程启动时调用一次（MainActivity），用于建磁盘缓存目录。 */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // 内存 LRU：上限 8MB（按字节计）
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val http by lazy {
        val cacheDir = File(appContext.cacheDir, "avatars")
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .cache(Cache(cacheDir, DISK_CACHE_BYTES))
            .build()
    }

    fun cached(url: String): Bitmap? = cache.get(url)

    /** 清空内存 + 磁盘缓存（换头像后调用，兜底防旧图残留）。 */
    fun clearAll() {
        cache.evictAll()
        if (::appContext.isInitialized) {
            try {
                http.cache?.evictAll()
            } catch (_: Exception) {
            }
        }
    }

    /** 加载头像；失败返回 null（调用方回退字母头像）。 */
    suspend fun load(url: String): Bitmap? = withContext(Dispatchers.IO) {
        cache.get(url) ?: run {
            try {
                val resp = http.newCall(Request.Builder().url(url).build()).execute()
                val bmp = resp.use { BitmapFactory.decodeStream(it.body?.byteStream()) }
                if (bmp != null) cache.put(url, bmp)
                bmp
            } catch (_: Exception) {
                null
            }
        }
    }
}
