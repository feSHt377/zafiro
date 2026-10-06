package com.fesht3.zafiro.repo

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 皮肤图片的保真存储。
 *
 * **为什么不复用 `ImageCodec`（agent-runtime 的图片 ingest 管线）**：那条管线是给
 * 聊天附件设计的，产出统一为 JPEG q80、长边 ≤1600px，并且会把透明通道拍成白底。
 * 用作全屏背景会有可见压缩噪点、透明 PNG 直接丢 alpha，而且它是 `internal`，`:app`
 * 本来就取不到。皮肤图必须原样保留格式与 alpha。
 *
 * 存储策略：
 * - **内容寻址**：文件名 = 内容 SHA-256 前 16 字节。同一张图导入多次只占一份，
 *   换皮肤/重复导入不放大占用。
 * - **不进 [StorageKind]**：那是「缓存」分类，`StorageApi.clear` 会整目录清空。
 *   皮肤图是用户数据，被清掉等于皮肤悄悄坏掉。代价是它不出现在存储页——可接受，
 *   毕竟总量受皮肤数量约束。
 * - **文件丢失必须可控**：用户可能通过其它途径清掉目录。绘制侧一律按「可能为空」
 *   处理，取不到就退化成纯材质，不能崩。
 */
class SkinImageStore internal constructor(
    private val repo: XRepo,
) {
    /** 单文件上限：比聊天附件宽得多（背景图动辄几 MB），但要有天花板防止误选超大文件。 */
    private val maxBytes: Long get() = MAX_IMAGE_BYTES

    /** 解码缓存。按字节数计价，上限取可用堆的 1/8——全屏位图较大，必须限制在 2 张量级。 */
    private val decodeCache = object : LruCache<String, ImageBitmap>(
        (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(MAX_CACHE_BYTES).toInt()
    ) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.height * value.width * 4
    }

    // suspend：repo.context() 可能要 await ContextProvider（跨进程场景），不能绕过
    private suspend fun skinsDir(): File = File(repo.context().filesDir, SKINS_DIR_NAME).apply {
        if (!exists()) mkdirs()
    }

    /**
     * 从相册 uri 导入一张皮肤图，返回沙箱内绝对路径；失败返回 null。
     *
     * 只做「读字节 → 校验 → 落盘」，**不解码也不重编码**：解码交给绘制侧按需降采样，
     * 避免为了存一张图就先把它整个读进内存。
     */
    suspend fun import(uri: Uri): String? = withContext(Dispatchers.IO) {
        val context = repo.context()
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext null
            if (bytes.isEmpty() || bytes.size > maxBytes) return@withContext null
            // 用 BitmapFactory 读真实格式：相册 uri 的 MIME 常是 image/* 泛型，不可靠
            val mime = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                .also { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, it) }
                .outMimeType
                ?: return@withContext null
            if (mime !in SkinImageFormat.supportedMime) return@withContext null

            val target = File(skinsDir(), SkinImageFormat.contentName(bytes) + SkinImageFormat.extensionFor(mime))
            if (!target.exists()) target.writeBytes(bytes)
            target.absolutePath
        }.getOrNull()
    }

    /** 路径是否仍指向一个存在的皮肤图。 */
    fun exists(path: String?): Boolean = !path.isNullOrBlank() && File(path).isFile

    /**
     * 解码为绘制用位图，按 [targetDimension] 降采样；文件不存在或解码失败返回 null。
     *
     * [targetDimension] 传「图片实际会被画到的最大边长」的像素值。传 0 表示取原尺寸
     * （仅测试用，真实绘制必须给值，否则全屏图会一次吃掉几十 MB）。
     */
    suspend fun decode(path: String, targetDimension: Int): ImageBitmap? =
        withContext(Dispatchers.IO) {
            if (!exists(path)) return@withContext null
            val cacheKey = "$path@$targetDimension"
            decodeCache.get(cacheKey)?.let { return@withContext it }

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            val options = BitmapFactory.Options().apply {
                inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                inSampleSize = SkinImageFormat.sampleSizeFor(
                    srcWidth = bounds.outWidth,
                    srcHeight = bounds.outHeight,
                    targetDimension = targetDimension,
                )
            }
            val bitmap = BitmapFactory.decodeFile(path, options) ?: return@withContext null
            bitmap.asImageBitmap().also { decodeCache.put(cacheKey, it) }
        }

    /**
     * 取这张图的主色，供「从背景图取色」用。
     *
     * 按 ~32px 粗采样解码再统计：只要颜色分布，不需要细节。全尺寸解码纯属浪费，
     * 一张 4000×3000 的原图在这里会白吃几十 MB。
     */
    suspend fun dominantColor(path: String): Int? = withContext(Dispatchers.IO) {
        if (!exists(path)) return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        val options = BitmapFactory.Options().apply {
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
            inSampleSize = SkinImageFormat.sampleSizeFor(
                srcWidth = bounds.outWidth,
                srcHeight = bounds.outHeight,
                targetDimension = DOMINANT_SAMPLE_DIMENSION,
            )
        }
        val bitmap = BitmapFactory.decodeFile(path, options) ?: return@withContext null
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            SkinImageFormat.dominantColor(pixels)
        } finally {
            // 这里不走 LruCache：主色只需算一次，缓存这张小图没有意义，反而占额
            bitmap.recycle()
        }
    }

    /**
     * 删除不再被任何皮肤引用的图片文件。
     *
     * [referenced] 传**删除之后**仍被引用的全部路径。调用方必须给全量集合：
     * 靠这里自己猜引用关系必然漏，漏一次就把别人的背景删了。
     */
    suspend fun deleteUnreferenced(referenced: Set<String>) = withContext(Dispatchers.IO) {
        val keep = referenced.filter { it.isNotBlank() }.mapTo(mutableSetOf()) { File(it).name }
        skinsDir().listFiles()?.forEach { file ->
            if (file.isFile && file.name !in keep) {
                runCatching { file.delete() }
                decodeCache.snapshot().keys
                    .filter { it.substringBefore('@').endsWith(file.name) }
                    .forEach { decodeCache.remove(it) }
            }
        }
    }

    internal fun clearCacheForTest() = decodeCache.evictAll()

    private companion object {
        const val SKINS_DIR_NAME = "skins"
        const val MAX_IMAGE_BYTES = 32L * 1024 * 1024
        const val MAX_CACHE_BYTES = 48L * 1024 * 1024

        /** 取主色时的采样目标边长：只要颜色分布，32px 量级足够。 */
        const val DOMINANT_SAMPLE_DIMENSION = 32
    }
}

/**
 * 皮肤图片的纯逻辑（命名 / 降采样 / 扩展名）。与 IO 分离，照搬 `ImageFormat` 与
 * `ImageCodec` 的分工：纯计算可脱离 Android 框架单测，降采样算错不是 OOM 就是糊。
 */
internal object SkinImageFormat {

    val supportedMime: Set<String> = setOf(
        "image/jpeg", "image/png", "image/webp", "image/gif", "image/bmp", "image/heic", "image/heif",
    )

    fun extensionFor(mime: String): String = when (mime) {
        "image/png" -> ".png"
        "image/webp" -> ".webp"
        "image/gif" -> ".gif"
        "image/bmp" -> ".bmp"
        "image/heic", "image/heif" -> ".heic"
        else -> ".jpg"
    }

    /** 内容寻址文件名：取 SHA-256 前 16 字节，够短且碰撞概率可忽略。 */
    fun contentName(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .take(16)
            .joinToString("") { "%02x".format(it) }

    /**
     * 2 的幂次降采样（[BitmapFactory.Options.inSampleSize] 语义）。
     *
     * 返回保证「解码后长边仍 ≥ [targetDimension]」的最大采样率——粗缩只知道除以 2，
     * 过了目标就宁可大一点也不能小，否则图片会被永久缩糊。
     * [targetDimension] ≤ 0 视为不降采样。
     */
    fun sampleSizeFor(srcWidth: Int, srcHeight: Int, targetDimension: Int): Int {
        if (targetDimension <= 0) return 1
        var sample = 1
        var longest = maxOf(srcWidth, srcHeight)
        while (longest / 2 >= targetDimension) {
            sample *= 2
            longest /= 2
        }
        return sample
    }

    /**
     * 从像素里取主色。
     *
     * **不做全局平均**：那会把「蓝天 + 绿地」平均成一种脏灰绿，拿去当主题色是难看的。
     * 改成「4 bit/通道粗量化 → 取最值得的桶 → 桶内求平均」：既保留了主色调，
     * 又因为桶内平均而不会抖到某个极端像素上。
     *
     * 桶的得分是 `像素数 × (1 + 平均饱和度)`：偏向有彩色的区域，
     * 否则一张「大片灰天 + 一小块彩色主体」的图会取到灰色。整图本来就灰时，
     * 这个权重退化成纯计数，行为仍然合理。
     *
     * @param pixels ARGB_8888 打包像素
     * @return `0xAARRGGBB`；没有有效像素（全透明）时 null
     */
    fun dominantColor(pixels: IntArray): Int? {
        if (pixels.isEmpty()) return null
        // 4096 个桶：够粗以聚拢相近色，够细以区分主次
        val buckets = HashMap<Int, LongArray>(256)
        for (pixel in pixels) {
            // 半透明及全透明像素不计入：抠图背景与边缘羽化会把主色拉向黑
            if (((pixel ushr 24) and 0xFF) < 0x80) continue
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val key = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
            val acc = buckets.getOrPut(key) { LongArray(4) }
            acc[0] += r
            acc[1] += g
            acc[2] += b
            acc[3] += 1L
        }
        if (buckets.isEmpty()) return null

        var best: LongArray? = null
        var bestScore = -1.0
        for (acc in buckets.values) {
            val count = acc[3].coerceAtLeast(1L)
            val r = acc[0] / count
            val g = acc[1] / count
            val b = acc[2] / count
            val max = maxOf(r, g, b).toFloat()
            val min = minOf(r, g, b).toFloat()
            val saturation = if (max <= 0f) 0f else (max - min) / max
            val score = count * (1.0 + saturation)
            if (score > bestScore) {
                bestScore = score
                best = acc
            }
        }

        val acc = best ?: return null
        val count = acc[3].coerceAtLeast(1L)
        val r = (acc[0] / count).toInt().coerceIn(0, 255)
        val g = (acc[1] / count).toInt().coerceIn(0, 255)
        val b = (acc[2] / count).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
