package com.fesht3.zafiro.app.ui.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HSV ↔ ARGB 转换。
 *
 * 这类代码错了不会崩，只会让取色器「拖到某个位置颜色就不对」或者「色相环跨界时跳变」，
 * 靠肉眼在真机上调极其低效，所以把边界都钉死。
 */
class SkinHsvTest {

    @Test
    fun `primary hues map to pure channels`() {
        assertEquals(0xFFFF0000.toInt(), SkinHsv.toArgb(0f, 1f, 1f))
        assertEquals(0xFFFFFF00.toInt(), SkinHsv.toArgb(60f, 1f, 1f))
        assertEquals(0xFF00FF00.toInt(), SkinHsv.toArgb(120f, 1f, 1f))
        assertEquals(0xFF00FFFF.toInt(), SkinHsv.toArgb(180f, 1f, 1f))
        assertEquals(0xFF0000FF.toInt(), SkinHsv.toArgb(240f, 1f, 1f))
        assertEquals(0xFFFF00FF.toInt(), SkinHsv.toArgb(300f, 1f, 1f))
    }

    @Test
    fun `output is always fully opaque`() {
        listOf(0f, 37f, 120f, 250f, 359f).forEach { hue ->
            listOf(0f, 0.5f, 1f).forEach { s ->
                listOf(0f, 0.5f, 1f).forEach { v ->
                    val alpha = (SkinHsv.toArgb(hue, s, v) ushr 24) and 0xFF
                    assertEquals("alpha 必须不透明，否则取色器选出来的颜色是隐形/半透的", 255, alpha)
                }
            }
        }
    }

    @Test
    fun `saturation zero collapses to gray`() {
        val argb = SkinHsv.toArgb(210f, 0f, 0.6f)
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        assertTrue("饱和度为 0 时三通道必须相等，实际 r=$r g=$g b=$b", r == g && g == b)
    }

    @Test
    fun `value zero collapses to black`() {
        assertEquals(0xFF000000.toInt(), SkinHsv.toArgb(123f, 1f, 0f))
    }

    @Test
    fun `saturation and value are clamped`() {
        // 越界不该产生溢出到别的通道的乱码（乘 255 后取整溢出是最常见的坑）
        assertEquals(SkinHsv.toArgb(200f, 1f, 1f), SkinHsv.toArgb(200f, 5f, 9f))
        assertEquals(SkinHsv.toArgb(200f, 0f, 0f), SkinHsv.toArgb(200f, -3f, -1f))
    }

    @Test
    fun `hue wraps in both directions`() {
        assertEquals(SkinHsv.toArgb(0f, 1f, 1f), SkinHsv.toArgb(360f, 1f, 1f))
        assertEquals(SkinHsv.toArgb(30f, 1f, 1f), SkinHsv.toArgb(390f, 1f, 1f))
        // 负角度：色相条拖到最左再往回一点会给出负值
        assertEquals(SkinHsv.toArgb(330f, 1f, 1f), SkinHsv.toArgb(-30f, 1f, 1f))
    }

    @Test
    fun `normalizeHue handles degenerate input`() {
        assertEquals(0f, SkinHsv.normalizeHue(Float.NaN), 0.0001f)
        assertEquals(0f, SkinHsv.normalizeHue(0f), 0.0001f)
        assertEquals(180f, SkinHsv.normalizeHue(180f), 0.0001f)
        assertEquals(359.5f, SkinHsv.normalizeHue(359.5f), 0.0001f)
        assertEquals(0.5f, SkinHsv.normalizeHue(360.5f), 0.0001f)
    }

    @Test
    fun `round trip preserves color`() {
        // 取色器的工作方式就是「解出 HSV → 用户拖动 → 转回 ARGB」，
        // 所以往返必须在整个色域上稳定，否则拖一下颜色自己就漂了
        val samples = listOf(
            0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(),
            0xFF123456.toInt(), 0xFFABCDEF.toInt(), 0xFF7F7F7F.toInt(),
            0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF52DBC9.toInt(),
        )
        samples.forEach { argb ->
            val hsv = SkinHsv.hsvOf(argb)
            val back = SkinHsv.toArgb(hsv[0], hsv[1], hsv[2])
            assertTrue(
                "往返漂移过大：%08X -> %08X".format(argb, back),
                SkinHsv.channelDistance(argb, back) <= 1,
            )
        }
    }

    @Test
    fun `hsvOf gray reports zero saturation and no NaN hue`() {
        val hsv = SkinHsv.hsvOf(0xFF808080.toInt())
        assertEquals(0f, hsv[1], 0.0001f)
        // 灰色没有色相：必须是 0 而不是 NaN，否则调用方拿它做色相条定位会炸
        assertTrue("hue 不能是 NaN", !hsv[0].isNaN())
        assertEquals(0f, hsv[0], 0.0001f)
    }

    @Test
    fun `hsvOf ignores alpha`() {
        // 半透明的红与不透明的红应给出同样的 HSV：取色器不带 alpha 编辑
        val opaque = SkinHsv.hsvOf(0xFFFF0000.toInt())
        val translucent = SkinHsv.hsvOf(0x33FF0000.toInt())
        assertEquals(opaque[0], translucent[0], 0.0001f)
        assertEquals(opaque[1], translucent[1], 0.0001f)
        assertEquals(opaque[2], translucent[2], 0.0001f)
    }
}
