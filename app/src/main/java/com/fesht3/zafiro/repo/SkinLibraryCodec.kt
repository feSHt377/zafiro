package com.fesht3.zafiro.repo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * 一套**拼接出来的**皮肤，落盘形态。
 *
 * 全部字段都是原语（String / Boolean / Int / Float），因为四个部件本来就由离散档位
 * 与路径构成：材质是三个档位 key、背景是「档位 + 路径」、主题色是 hex 或 "dynamic"。
 * 这样 kotlinx 直接序列化即可，不需要给 Dp 之类写自定义 serializer —— 也就不会出现
 * 「模型改了但 serializer 忘了跟」的那类静默错位。
 *
 * 字段只记「用户选了哪档」，不记展开后的具体数值：展开是渲染侧的事（见
 * `CustomSkin.kt` 的档位映射），这样以后调档位数值不会让存量皮肤的含义漂移。
 */
@Serializable
data class SavedSkinRecord(
    @SerialName("id")
    val id: String,
    /** 显示名。空串由 UI 回落成「未命名」。 */
    @SerialName("name")
    val name: String = "",
    /**
     * 主题色：`""` = 不指定（跟随独立的配色设置）/ `"dynamic"` / ARGB hex。
     * 三态用一个字符串表达，与既有 `theme_seed_color` 的 hex 约定一致。
     */
    @SerialName("color")
    val color: String = "",
    @SerialName("amoled")
    val amoled: Boolean = false,
    /** 材质三轴 storageKey；空串 = 各轴默认档。 */
    @SerialName("glass")
    val glass: String = "",
    @SerialName("surface")
    val surface: String = "",
    @SerialName("corner")
    val corner: String = "",
    /** 背景档位：[BACKGROUND_NONE] / [BACKGROUND_SOFT] / [BACKGROUND_BOLD] / [BACKGROUND_IMAGE]。 */
    @SerialName("bg_style")
    val backgroundStyle: String = BACKGROUND_NONE,
    /** 背景图沙箱路径；空串 = 无。仅 [BACKGROUND_IMAGE] 时读取。 */
    @SerialName("bg_path")
    val backgroundPath: String = "",
    /** 背景图铺法：""/"cover"/"contain"/"stretch"。 */
    @SerialName("bg_fit")
    val backgroundFit: String = "",
    @SerialName("bg_scrim")
    val backgroundScrim: Float = DEFAULT_BACKGROUND_SCRIM,
    /** 对话框背景图路径；空串 = 无（对话框没有渐变档，材质本身就是它的底）。 */
    @SerialName("dlg_path")
    val dialogPath: String = "",
    @SerialName("dlg_scrim")
    val dialogScrim: Float = DEFAULT_DIALOG_SCRIM,
    @SerialName("created_at")
    val createdAt: Long = 0L,
) {
    /** 图片可能被外部清掉：这里只判「记了路径」，是否真能画出来由渲染侧兜底。 */
    val hasBackgroundImage: Boolean get() = backgroundStyle == BACKGROUND_IMAGE && backgroundPath.isNotBlank()

    val hasDialogImage: Boolean get() = dialogPath.isNotBlank()

    /**
     * 这套皮肤引用到的图片路径。
     *
     * 用于清理孤立文件：图片按内容寻址，多套皮肤可能指向**同一份**文件，
     * 所以删文件前必须把所有皮肤引用的并集算出来，不能按单套皮肤删。
     */
    fun referencedImagePaths(): List<String> = listOfNotNull(
        backgroundPath.takeIf { it.isNotBlank() },
        dialogPath.takeIf { it.isNotBlank() },
    )

    companion object {
        const val BACKGROUND_NONE = "none"
        const val BACKGROUND_SOFT = "soft"
        const val BACKGROUND_BOLD = "bold"
        const val BACKGROUND_IMAGE = "image"

        const val COLOR_DYNAMIC = "dynamic"

        const val DEFAULT_BACKGROUND_SCRIM = 0.25f
        /** 对话框内容密、字号小，默认压得比背景狠。 */
        const val DEFAULT_DIALOG_SCRIM = 0.45f
    }
}

/**
 * 皮肤库文档：`{"active_id": "...", "skins": [...]}`。
 *
 * 与 `llm.saved_configs` 同构（active + 列表），因为两者的使用方式一致：
 * 多份配置、一份生效、随时切换。
 */
@Serializable
data class SkinLibraryDocument(
    @SerialName("active_id")
    val activeId: String? = null,
    @SerialName("skins")
    val skins: List<SavedSkinRecord> = emptyList(),
) {
    fun active(): SavedSkinRecord? = skins.firstOrNull { it.id == activeId }
}

internal object SkinLibraryCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun parse(raw: String): SkinLibraryDocument {
        return try {
            json.decodeFromString(SkinLibraryDocument.serializer(), raw)
        } catch (_: SerializationException) {
            SkinLibraryDocument()
        } catch (_: IllegalArgumentException) {
            SkinLibraryDocument()
        }
    }

    fun encode(document: SkinLibraryDocument): String =
        json.encodeToString(SkinLibraryDocument.serializer(), document)
}
