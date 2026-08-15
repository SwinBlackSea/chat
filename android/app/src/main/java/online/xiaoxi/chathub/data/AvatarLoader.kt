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
 * 头像体积小（≤5MB 且通常几百 KB），进程内缓存即可，不做磁盘缓存。
 */
object AvatarLoader {
    private val cache = object : LruCache<String, Bitmap>(64) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun cached(url: String): Bitmap? = cache.get(url)

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
