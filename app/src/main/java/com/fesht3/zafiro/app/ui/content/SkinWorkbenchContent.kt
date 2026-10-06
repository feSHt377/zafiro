package com.fesht3.zafiro.app.ui.content

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Square
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.niki914.uikit.base.skin.SkinColor
import com.niki914.uikit.base.skin.SkinImageFit
import com.niki914.uikit.infra.ConfirmationLiquidDialog
import com.niki914.uikit.infra.LiquidDialog
import com.niki914.uikit.infra.component.LiquidTextField
import com.niki914.uikit.infra.component.MaterialTintLiquidButton
import com.niki914.uikit.infra.component.SettingNavigationItem
import com.niki914.uikit.infra.component.SettingsGroupCard
import com.fesht3.zafiro.app.R
import com.fesht3.zafiro.app.ui.model.SkinBackgroundStyle
import com.fesht3.zafiro.app.ui.model.SkinCorner
import com.fesht3.zafiro.app.ui.model.SkinGlass
import com.fesht3.zafiro.app.ui.model.SkinSurface
import com.fesht3.zafiro.app.ui.model.Skins
import com.fesht3.zafiro.app.ui.model.ThemeController
import com.fesht3.zafiro.app.ui.model.ThemePrefs
import com.fesht3.zafiro.app.ui.model.skinImageFitOf
import com.fesht3.zafiro.app.ui.model.skinNameRes
import com.fesht3.zafiro.app.ui.model.toStorageKey
import com.fesht3.zafiro.repo.SavedSkinRecord
import com.fesht3.zafiro.repo.XRepo
import kotlinx.coroutines.launch

/**
 * 拼接工作台：把皮肤库与四个部件的选择器拼在一起。
 *
 * 交互模型：**部件改动直接写穿到生效皮肤**，没有「保存」动作。
 * 生效项是内置预设时，第一次改动会先派生成一份用户皮肤（内置不可变），
 * 界面上用一行提示提前说明，避免「我明明没点新建，怎么多了一套」。
 */
@Composable
internal fun SkinWorkbenchContent(
    prefs: ThemePrefs,
    isDarkTheme: Boolean,
) {
    val scope = rememberCoroutineScope()
    val active = prefs.activeRecord
    // 内置预设的名字要按语言取，派生时用作新皮肤的名字
    val activeLabel = skinLabel(active)
    var renamingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    var pickingColor by rememberSaveable { mutableStateOf(false) }
    var pendingBackgroundUri by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDialogUri by rememberSaveable { mutableStateOf<String?>(null) }

    // 相册选图。系统 photo picker 无需权限；拷进沙箱后 uri 授权过期也不影响。
    val backgroundPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) pendingBackgroundUri = uri.toString()
    }
    val dialogPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) pendingDialogUri = uri.toString()
    }

    // 导入是挂起操作（读字节 + 落盘），放在组合外的 effect 里做
    ImportPendingImage(pendingBackgroundUri) { uri ->
        pendingBackgroundUri = null
        scope.launch {
            XRepo.skinImages.import(uri)?.let { path ->
                ThemeController.editActivePart(activeLabel) {
                    it.copy(
                        backgroundStyle = SavedSkinRecord.BACKGROUND_IMAGE,
                        backgroundPath = path,
                    )
                }
            }
        }
    }
    ImportPendingImage(pendingDialogUri) { uri ->
        pendingDialogUri = null
        scope.launch {
            XRepo.skinImages.import(uri)?.let { path ->
                ThemeController.editActivePart(activeLabel) { it.copy(dialogPath = path) }
            }
        }
    }

    // ── 皮肤库 ──────────────────────────────────────────────────────────────
    SelectionGroupCard(
        title = stringResource(R.string.ui_skin_library_section),
        options = prefs.library.map { record ->
            SelectionOption(
                id = record.id,
                title = skinLabel(record),
                selected = prefs.activeSkinId == record.id ||
                        (prefs.activeSkinId == null && record.id == Skins.default.id),
                onClick = { scope.launch { ThemeController.selectSkin(record.id) } },
                leadingIconVector = skinIcon(record.id),
            )
        },
        isDarkTheme = isDarkTheme,
    )

    if (prefs.activeIsBuiltin) {
        Text(
            text = stringResource(R.string.ui_skin_builtin_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }

    // ── 部件 1：材质 ────────────────────────────────────────────────────────
    SkinAxisCard(
        titleRes = R.string.ui_theme_skin_glass_section,
        options = SkinGlass.entries.map { it.storageKey to skinGlassLabelRes(it) },
        selectedKey = SkinGlass.fromStorageKey(active.glass).storageKey,
        onSelect = { key ->
            scope.launch {
                ThemeController.editActivePart(activeLabel) { it.copy(glass = key) }
            }
        },
        isDarkTheme = isDarkTheme,
    )
    SkinAxisCard(
        titleRes = R.string.ui_theme_skin_surface_section,
        options = SkinSurface.entries.map { it.storageKey to skinSurfaceLabelRes(it) },
        selectedKey = SkinSurface.fromStorageKey(active.surface).storageKey,
        onSelect = { key ->
            scope.launch {
                ThemeController.editActivePart(activeLabel) { it.copy(surface = key) }
            }
        },
        isDarkTheme = isDarkTheme,
    )
    SkinAxisCard(
        titleRes = R.string.ui_theme_skin_corner_section,
        options = SkinCorner.entries.map { it.storageKey to skinCornerLabelRes(it) },
        selectedKey = SkinCorner.fromStorageKey(active.corner).storageKey,
        onSelect = { key ->
            scope.launch {
                ThemeController.editActivePart(activeLabel) { it.copy(corner = key) }
            }
        },
        isDarkTheme = isDarkTheme,
    )

    // ── 部件 2：主背景 ──────────────────────────────────────────────────────
    SkinAxisCard(
        titleRes = R.string.ui_theme_skin_backdrop_section,
        options = SkinBackgroundStyle.entries.map {
            it.storageKey to skinBackgroundLabelRes(it)
        },
        selectedKey = SkinBackgroundStyle.fromStorageKey(active.backgroundStyle).storageKey,
        onSelect = { key ->
            scope.launch {
                ThemeController.editActivePart(activeLabel) {
                    // 从图片档切走时保留路径但不显示：切回来不用重选
                    it.copy(backgroundStyle = key)
                }
            }
        },
        isDarkTheme = isDarkTheme,
    )
    if (SkinBackgroundStyle.fromStorageKey(active.backgroundStyle) == SkinBackgroundStyle.Image) {
        SkinImageRow(
            title = stringResource(R.string.ui_skin_background_pick),
            summary = active.backgroundPath.takeIf { it.isNotBlank() }
                ?.let { stringResource(R.string.ui_skin_image_selected) }
                ?: stringResource(R.string.ui_skin_image_none),
            onPick = {
                backgroundPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onClear = {
                scope.launch {
                    ThemeController.editActivePart(activeLabel) {
                        it.copy(backgroundPath = "")
                    }
                }
            },
        )
        SkinAxisCard(
            titleRes = R.string.ui_skin_image_fit_section,
            options = SkinImageFit.entries.map { it.toStorageKey() to skinFitLabelRes(it) },
            selectedKey = skinImageFitOf(active.backgroundFit).toStorageKey(),
            onSelect = { key ->
                scope.launch {
                    ThemeController.editActivePart(activeLabel) { it.copy(backgroundFit = key) }
                }
            },
            isDarkTheme = isDarkTheme,
        )
    }

    // ── 部件 3：对话框图 ────────────────────────────────────────────────────
    SettingsGroupCard(title = stringResource(R.string.ui_skin_dialog_section)) {
        SettingNavigationItem(
            title = stringResource(R.string.ui_skin_dialog_pick),
            summary = active.dialogPath.takeIf { it.isNotBlank() }
                ?.let { stringResource(R.string.ui_skin_image_selected) }
                ?: stringResource(R.string.ui_skin_image_none),
            onClick = {
                dialogPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
        )
        if (active.dialogPath.isNotBlank()) {
            SettingNavigationItem(
                title = stringResource(R.string.ui_skin_image_clear),
                summary = null,
                onClick = {
                    scope.launch {
                        ThemeController.editActivePart(activeLabel) { it.copy(dialogPath = "") }
                    }
                },
            )
        }
    }

    // ── 部件 4：主题色 ──────────────────────────────────────────────────────
    SkinColorCard(
        active = active,
        onSelect = { color ->
            scope.launch {
                ThemeController.editActivePart(activeLabel) { it.copy(color = color) }
            }
        },
        onOpenPicker = { pickingColor = true },
        isDarkTheme = isDarkTheme,
    )

    if (pickingColor) {
        // 起点：皮肤已有颜色就用它，否则用全局配色，再否则给个中性默认
        val initialArgb = active.color.toLongOrNull(16)?.toInt()
            ?: prefs.seedColor
            ?: 0xFF52DBC9.toInt()
        SkinColorPickerDialog(
            initialArgb = initialArgb,
            backgroundPath = active.backgroundPath.takeIf {
                active.backgroundStyle == SavedSkinRecord.BACKGROUND_IMAGE
            },
            onDismissRequest = { pickingColor = false },
            onConfirm = { argb ->
                pickingColor = false
                scope.launch {
                    ThemeController.editActivePart(activeLabel) {
                        it.copy(color = "%08X".format(argb))
                    }
                }
            },
        )
    }

    // ── 皮肤操作 ────────────────────────────────────────────────────────────
    SettingsGroupCard(title = stringResource(R.string.ui_skin_actions_section)) {
        SettingNavigationItem(
            title = stringResource(R.string.ui_skin_save_as_new),
            summary = null,
            onClick = { renamingId = NEW_SKIN_ID },
        )
        if (!prefs.activeIsBuiltin) {
            SettingNavigationItem(
                title = stringResource(R.string.ui_skin_rename),
                summary = null,
                onClick = { renamingId = active.id },
            )
            SettingNavigationItem(
                title = stringResource(R.string.ui_skin_delete),
                summary = null,
                onClick = { deletingId = active.id },
            )
        }
    }

    if (renamingId != null) {
        SkinNameDialog(
            initialName = if (renamingId == NEW_SKIN_ID) activeLabel else active.name,
            title = stringResource(
                if (renamingId == NEW_SKIN_ID) {
                    R.string.ui_skin_save_as_new
                } else {
                    R.string.ui_skin_rename
                }
            ),
            onDismissRequest = { renamingId = null },
            onConfirm = { name ->
                val target = renamingId
                renamingId = null
                scope.launch {
                    if (target == NEW_SKIN_ID) {
                        ThemeController.saveAsNew(name)
                    } else if (target != null) {
                        ThemeController.renameSkin(target, name)
                    }
                }
            },
        )
    }

    ConfirmationLiquidDialog(
        visible = deletingId != null,
        onDismissRequest = { deletingId = null },
        title = stringResource(R.string.ui_skin_delete_title),
        text = stringResource(R.string.ui_skin_delete_text),
        negativeButtonText = stringResource(R.string.dialog_cancel),
        positiveButtonText = stringResource(R.string.dialog_confirm_delete),
        onNegativeClick = { deletingId = null },
        onPositiveClick = {
            val target = deletingId
            deletingId = null
            if (target != null) {
                scope.launch { ThemeController.deleteSkin(target) }
            }
        },
    )
}

/** 一个离散部件轴：一组档位单选。 */
@Composable
private fun SkinAxisCard(
    @StringRes titleRes: Int,
    options: List<Pair<String, Int>>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    isDarkTheme: Boolean,
) {
    SelectionGroupCard(
        title = stringResource(titleRes),
        options = options.map { (storageKey, labelRes) ->
            SelectionOption(
                id = storageKey,
                title = stringResource(labelRes),
                selected = selectedKey == storageKey,
                onClick = { onSelect(storageKey) },
            )
        },
        isDarkTheme = isDarkTheme,
    )
}

/** 主题色部件：跟随全局配色 / 动态色 / 任选一个预设色 / 任意取色器。 */
@Composable
private fun SkinColorCard(
    active: SavedSkinRecord,
    onSelect: (String) -> Unit,
    onOpenPicker: () -> Unit,
    isDarkTheme: Boolean,
) {
    val selectedKey = active.color.ifBlank { COLOR_FOLLOW_GLOBAL }
    SelectionGroupCard(
        title = stringResource(R.string.ui_skin_color_section),
        options = buildList {
            add(
                SelectionOption(
                    id = COLOR_FOLLOW_GLOBAL,
                    title = stringResource(R.string.ui_skin_color_follow_global),
                    selected = selectedKey == COLOR_FOLLOW_GLOBAL,
                    onClick = { onSelect("") },
                )
            )
            add(
                SelectionOption(
                    id = SavedSkinRecord.COLOR_DYNAMIC,
                    title = stringResource(R.string.ui_theme_color_dynamic),
                    selected = selectedKey == SavedSkinRecord.COLOR_DYNAMIC,
                    onClick = { onSelect(SavedSkinRecord.COLOR_DYNAMIC) },
                )
            )
            ThemeSeedColors.forEachIndexed { index, argb ->
                val hex = "%08X".format(argb)
                add(
                    SelectionOption(
                        id = "skin-seed-$hex",
                        title = stringResource(ThemeColorLabelRes[index]),
                        leadingSwatchColor = Color(argb),
                        selected = selectedKey == hex,
                        onClick = { onSelect(hex) },
                    )
                )
            }
            // 任意取色：选中态交给对话内的预览圆点表达，这里只做入口
            add(
                SelectionOption(
                    id = COLOR_CUSTOM_PICKER,
                    title = stringResource(R.string.ui_skin_color_custom),
                    leadingIconVector = Icons.Rounded.Colorize,
                    selected = false,
                    onClick = onOpenPicker,
                )
            )
        },
        isDarkTheme = isDarkTheme,
    )
}

/** 「选图 / 清除」两行。 */
@Composable
private fun SkinImageRow(
    title: String,
    summary: String,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    SettingsGroupCard(title = title) {
        SettingNavigationItem(title = title, summary = summary, onClick = onPick)
        SettingNavigationItem(
            title = stringResource(R.string.ui_skin_image_clear),
            summary = null,
            onClick = onClear,
        )
    }
}

/**
 * 名字输入对话框：另存为与重命名共用。
 *
 * 用 [LiquidDialog] 而不是 `ConfirmationLiquidDialog`：后者只有纯文案、没有内容插槽，
 * 塞不进输入框。
 */
@Composable
private fun SkinNameDialog(
    initialName: String,
    title: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    LiquidDialog(
        visible = true,
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        content = {
            LiquidTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = stringResource(R.string.ui_skin_name_placeholder),
                singleLine = true,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        actions = {
            MaterialTintLiquidButton(
                text = stringResource(R.string.dialog_cancel),
                onClick = onDismissRequest,
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
            MaterialTintLiquidButton(
                text = stringResource(R.string.ui_skin_name_confirm),
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name.trim()) },
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    )
}

/**
 * 相册 uri 已到手 → 交给 [consume] 去导入。
 *
 * 单独抽出来是因为导入是挂起操作：必须在组合外的协程里做，不能在 launcher 回调里直接跑。
 */
@Composable
private fun ImportPendingImage(pendingUri: String?, consume: (Uri) -> Unit) {
    if (pendingUri.isNullOrBlank()) return
    val uri = Uri.parse(pendingUri)
    androidx.compose.runtime.LaunchedEffect(pendingUri) { consume(uri) }
}

/** 「另存为」在对话框标识里用的哨兵值：与真实皮肤 id 不可能撞（id 前缀是 skin-）。 */
private const val NEW_SKIN_ID = "__new__"

private const val COLOR_FOLLOW_GLOBAL = "__global__"

/** 「任意取色」在单选组里的 id：它不是一种颜色，只是个入口，故用哨兵值。 */
private const val COLOR_CUSTOM_PICKER = "__picker__"

@Composable
private fun skinLabel(record: SavedSkinRecord): String {
    val builtinRes = skinNameRes(record.id)
    return if (builtinRes != null) {
        stringResource(builtinRes)
    } else {
        record.name.ifBlank { stringResource(R.string.ui_skin_untitled) }
    }
}

private fun skinIcon(skinId: String) = when (skinId) {
    Skins.Solid.id -> Icons.Rounded.Square
    Skins.Frosted.id -> Icons.Rounded.BlurOn
    Skins.Flat.id -> Icons.Rounded.CropSquare
    Skins.Amoled.id -> Icons.Rounded.Contrast
    else -> Icons.Rounded.WaterDrop
}

@StringRes
private fun skinGlassLabelRes(glass: SkinGlass): Int = when (glass) {
    SkinGlass.Off -> R.string.ui_skin_glass_off
    SkinGlass.Light -> R.string.ui_skin_glass_light
    SkinGlass.Standard -> R.string.ui_skin_standard
    SkinGlass.Strong -> R.string.ui_skin_glass_strong
}

@StringRes
private fun skinSurfaceLabelRes(surface: SkinSurface): Int = when (surface) {
    SkinSurface.Translucent -> R.string.ui_skin_surface_translucent
    SkinSurface.Standard -> R.string.ui_skin_standard
    SkinSurface.Solid -> R.string.ui_skin_surface_solid
}

@StringRes
private fun skinCornerLabelRes(corner: SkinCorner): Int = when (corner) {
    SkinCorner.Sharp -> R.string.ui_skin_corner_sharp
    SkinCorner.Standard -> R.string.ui_skin_standard
    SkinCorner.Round -> R.string.ui_skin_corner_round
}

@StringRes
private fun skinBackgroundLabelRes(style: SkinBackgroundStyle): Int = when (style) {
    SkinBackgroundStyle.None -> R.string.ui_skin_backdrop_none
    SkinBackgroundStyle.Soft -> R.string.ui_skin_backdrop_soft
    SkinBackgroundStyle.Bold -> R.string.ui_skin_backdrop_bold
    SkinBackgroundStyle.Image -> R.string.ui_skin_backdrop_image
}

@StringRes
private fun skinFitLabelRes(fit: SkinImageFit): Int = when (fit) {
    SkinImageFit.Cover -> R.string.ui_skin_fit_cover
    SkinImageFit.Contain -> R.string.ui_skin_fit_contain
    SkinImageFit.Stretch -> R.string.ui_skin_fit_stretch
}
