package com.fesht3.zafiro.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 皮肤图片的纯逻辑。
 *
 * 降采样单独测是因为它算错的两种后果都很难在真机上一眼看出来：偏小 = 图片永久缩糊，
 * 偏大 = 全屏位图一次吃掉几十 MB（低端机直接 OOM）。这里把边界都钉死。
 */
class SkinImageFormatTest {

    @Test
    fun `sample size keeps decoded longest side at or above target`() {
        // 4000×3000 的图放进 1080 宽的屏幕：除以 2 到 2000 仍 ≥1080，再除到 1000 就小于了
        assertEquals(2, SkinImageFormat.sampleSizeFor(4000, 3000, 1080))
        // 源图本身就是目标尺寸：不能再降
        assertEquals(1, SkinImageFormat.sampleSizeFor(1080, 1080, 1080))
        // 源长边正好是目标的两倍：降到恰好等于目标即可，不必再降
        assertEquals(2, SkinImageFormat.sampleSizeFor(2160, 1080, 1080))
        // 边界：降一半后刚好小于目标 → 不许降
        assertEquals(1, SkinImageFormat.sampleSizeFor(2159, 1080, 1080))
    }

    @Test
    fun `sample size never upscales small images`() {
        // 源图比目标小：必须保持 1，否则 BitmapFactory 会把它当采样率去缩，越缩越糊
        assertEquals(1, SkinImageFormat.sampleSizeFor(320, 240, 1080))
        assertEquals(1, SkinImageFormat.sampleSizeFor(1080, 240, 4000))
    }

    @Test
    fun `sample size handles non square and degenerate inputs`() {
        // 按长边算，不看短边
        assertEquals(4, SkinImageFormat.sampleSizeFor(8000, 100, 1080))
        // ≤0 = 不降采样（仅测试 / 明确要原尺寸的路径）
        assertEquals(1, SkinImageFormat.sampleSizeFor(4000, 3000, 0))
        assertEquals(1, SkinImageFormat.sampleSizeFor(4000, 3000, -1))
        // 非法尺寸不能死循环
        assertEquals(1, SkinImageFormat.sampleSizeFor(0, 0, 1080))
    }

    @Test
    fun `sample size is always a power of two`() {
        var dimension = 100
        while (dimension < 20000) {
            val sample = SkinImageFormat.sampleSizeFor(12000, 9000, dimension)
            assertTrue("sample=$sample 不是 2 的幂", sample > 0 && sample and (sample - 1) == 0)
            dimension *= 2
        }
    }

    @Test
    fun `content name is stable and content addressed`() {
        val a = SkinImageFormat.contentName(byteArrayOf(1, 2, 3))
        assertEquals("同样内容必须得到同样的名字（内容寻址去重的前提）", a, SkinImageFormat.contentName(byteArrayOf(1, 2, 3)))
        assertNotEquals("不同内容不能撞名", a, SkinImageFormat.contentName(byteArrayOf(1, 2, 4)))
        assertEquals("名字长度固定为 16 字节 hex", 32, a.length)
        assertTrue("必须是纯 hex", a.all { it in "0123456789abcdef" })
    }

    @Test
    fun `extension maps known mime and falls back to jpg`() {
        assertEquals(".png", SkinImageFormat.extensionFor("image/png"))
        assertEquals(".webp", SkinImageFormat.extensionFor("image/webp"))
        assertEquals(".heic", SkinImageFormat.extensionFor("image/heif"))
        // 未知一律 jpg：宁可是错的后缀也别产生没有后缀的文件（File 的 extension 会变空）
        assertEquals(".jpg", SkinImageFormat.extensionFor("image/avif"))
        assertEquals(".jpg", SkinImageFormat.extensionFor("application/octet-stream"))
    }

    @Test
    fun `supported mime excludes formats the app cannot render`() {
        assertTrue("image/png" in SkinImageFormat.supportedMime)
        assertTrue("image/jpeg" in SkinImageFormat.supportedMime)
        // 非图片与矢量不该进皮肤图：前者解不出来，后者不是位图（聊天管线才处理 svg）
        assertFalse("image/svg+xml" in SkinImageFormat.supportedMime)
        assertFalse("text/plain" in SkinImageFormat.supportedMime)
    }

    // ── 主色提取 ────────────────────────────────────────────────────────────

    @Test
    fun `dominant color of a solid image is that color`() {
        val pixels = IntArray(64) { 0xFF3A7BD5.toInt() }
        assertEquals(0xFF3A7BD5.toInt(), SkinImageFormat.dominantColor(pixels))
    }

    @Test
    fun `dominant color does not average two regions into mud`() {
        // 这是本函数存在的理由：全局平均会把蓝天+绿地变成脏灰绿。
        // 结果必须是两者之一，绝不能是中间的混合色。
        val pixels = IntArray(100) { if (it % 2 == 0) 0xFF2E86DE.toInt() else 0xFF27AE60.toInt() }
        val picked = SkinImageFormat.dominantColor(pixels)
        assertTrue(
            "取到的是混合色 %08X，说明退回了全局平均".format(picked),
            picked == 0xFF2E86DE.toInt() || picked == 0xFF27AE60.toInt(),
        )
    }

    @Test
    fun `dominant color ignores transparent pixels`() {
        // 抠图背景与边缘羽化的大片透明区不能参与统计，否则主色被拉向黑
        val opaqueRed = IntArray(16) { 0xFFFF0000.toInt() }
        val transparent = IntArray(400) { 0x00123456 }
        assertEquals(
            0xFFFF0000.toInt(),
            SkinImageFormat.dominantColor(transparent + opaqueRed),
        )
    }

    @Test
    fun `dominant color returns null when nothing usable`() {
        assertEquals(null, SkinImageFormat.dominantColor(IntArray(0)))
        // 全透明：没有可统计的像素
        assertEquals(null, SkinImageFormat.dominantColor(IntArray(50) { 0x00FFFFFF }))
    }

    @Test
    fun `majority still wins over saturation weighting`() {
        // 权重是 `数量 × (1 + 饱和度)`，不能反客为主：绝大多数的灰色该赢过少数高饱和色
        val gray = IntArray(200) { 0xFF808080.toInt() }
        val red = IntArray(10) { 0xFFFF0000.toInt() }
        val picked = SkinImageFormat.dominantColor(gray + red)
        assertTrue("多数灰应该赢，实际 %08X".format(picked), picked == 0xFF808080.toInt())
    }

    @Test
    fun `equally sized regions prefer the saturated one`() {
        // 同数量时权重决定胜负：有彩色比灰色更适合当主题色
        val gray = IntArray(50) { 0xFF808080.toInt() }
        val blue = IntArray(50) { 0xFF0000FF.toInt() }
        assertEquals(0xFF0000FF.toInt(), SkinImageFormat.dominantColor(gray + blue))
    }
}
