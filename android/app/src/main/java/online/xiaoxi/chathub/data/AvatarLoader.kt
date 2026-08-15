package online.xiaoxi.chathub.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 头像图片加载：OkHttp 拉取 + 内存 LRU 缓存（按 URL）。
 * 头像 URL 由服务端带 ?v=<mtime> 版本参数：换头像 → 新文件新 mtime → URL 必变，
 * 缓存自动失效；上传成功后也可主动 clearAll() 兜底。不做磁盘缓存。
 */
object AvatarLoader {
    // 上限 8MB（按字节计）；此前 64 是 KB 级上限，任何头像解码后都立即被驱逐，缓存形同虚设
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun cached(url: String): Bitmap? = cache.get(url)

    /** 清空内存缓存（换头像后调用，兜底防旧图残留）。 */
    fun clearAll() {
        cache.evictAll()
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
