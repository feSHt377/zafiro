package com.niki914.zafiro.app.ui.model

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * HSV ↔ ARGB 的纯 Kotlin 实现。
 *
 * 不用 `android.graphics.Color.HSVToColor` 是因为那是 Android 框架 API：
 * 一旦用了，取色逻辑就只能跑在 Robolectric 下，而这里恰恰是最该被穷举测试的部分
 * （色相环跨界、饱和度为 0 时的退化、取整误差）。自己写 30 行换一个能纯 JVM 单测的模块。
 */
internal object SkinHsv {

    /**
     * @param hue 度，任意实数（会自动绕回 0..360）
     * @param saturation 0..1，超出会被夹住
     * @param value 亮度 0..1，超出会被夹住
     * @return 0xAARRGGBB
     */
    fun toArgb(hue: Float, saturation: Float, value: Float): Int {
        val h = normalizeHue(hue)
        val s = saturation.coerceIn(0f, 1f)
        val v = value.coerceIn(0f, 1f)

        val sector = h / 60f
        val index = sector.toInt() % 6
        val f = sector - sector.toInt()

        val p = v * (1f - s)
        val q = v * (1f - s * f)
        val t = v * (1f - s * (1f - f))

        val (r, g, b) = when (index) {
            0 -> Triple(v, t, p)
            1 -> Triple(q, v, p)
            2 -> Triple(p, v, t)
            3 -> Triple(p, q, v)
            4 -> Triple(t, p, v)
            else -> Triple(v, p, q)
        }
        // 标准 ARGB：A 最高位、R 次之。写反成 BGR 不会崩，只会让取色器显示错位的颜色。
        return ALPHA_OPAQUE or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
    }

    /** @return `[hue(度), saturation, value]`；ARGB 的 alpha 被忽略。 */
    fun hsvOf(argb: Int): FloatArray {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f

        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min

        val rawHue = when {
            // 灰色：色相无意义，定为 0 而不是 NaN，否则调用方拿去做 UI 定位会炸
            delta == 0f -> 0f
            max == r -> 60f * (((g - b) / delta) % 6f)
            max == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }
        val saturation = if (max == 0f) 0f else delta / max
        return floatArrayOf(normalizeHue(rawHue), saturation, max)
    }

    /** 度 → [0, 360)，接受任意实数（含负数）。 */
    fun normalizeHue(hue: Float): Float {
        if (hue.isNaN()) return 0f
        val wrapped = hue % 360f
        return if (wrapped < 0f) wrapped + 360f else wrapped
    }

    /** 判断两个颜色在「肉眼意义上」是否相同：同一 ARGB 自然算相同。 */
    fun sameColor(a: Int, b: Int): Boolean = a == b

    private fun channel(value: Float): Int =
        (value * 255f).roundToInt().coerceIn(0, 255)

    private const val ALPHA_OPAQUE = 0xFF shl 24

    /** 供断言使用：ARGB 的通道距离，用于「近似相等」判定。 */
    fun channelDistance(a: Int, b: Int): Int {
        val dr = abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF))
        val dg = abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF))
        val db = abs((a and 0xFF) - (b and 0xFF))
        return maxOf(dr, dg, db)
    }
}
