/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024-2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.components

import android.content.res.Configuration
import android.view.ContextThemeWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.FormatColorReset
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.nextcloud.talk.R
import com.nextcloud.talk.emojipicker.EmojiPickerPanel
import com.nextcloud.talk.utils.ColorGenerator
import kotlin.math.roundToInt

/**
 * A single bottom sheet combining emoji and background-color selection for a conversation
 * avatar, with a live preview and explicit save/discard actions - mirrors the iOS emoji
 * avatar picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiAvatarPickerBottomSheet(
    initialEmoji: String?,
    initialColor: Int?,
    onConfirm: (emoji: String, color: Int?) -> Unit,
    onDismiss: () -> Unit
) {
    val paletteColors = remember { predefinedPaletteColors() }
    var emoji by rememberSaveable { mutableStateOf(initialEmoji ?: DEFAULT_EMOJI) }
    var color by rememberSaveable { mutableStateOf(initialColor) }
    var showCustomColorPicker by rememberSaveable { mutableStateOf(false) }
    val initialHsv = remember {
        val hsv = FloatArray(HSV_COMPONENT_COUNT)
        android.graphics.Color.colorToHSV(initialColor ?: paletteColors.first(), hsv)
        hsv
    }
    var hue by rememberSaveable { mutableStateOf(initialHsv[0]) }
    var saturation by rememberSaveable { mutableStateOf(initialHsv[1]) }
    var value by rememberSaveable { mutableStateOf(initialHsv[2].coerceAtLeast(MIN_VALUE)) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (showCustomColorPicker) Modifier else Modifier.fillMaxHeight(SHEET_HEIGHT_FRACTION))
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            EmojiAvatarPickerTopBar(onDiscard = onDismiss, onSave = { onConfirm(emoji, color) })
            Spacer(modifier = Modifier.height(8.dp))
            EmojiAvatarPickerBody(
                state = EmojiPickerBodyState(
                    showCustomColorPicker = showCustomColorPicker,
                    isLandscape = isLandscape,
                    emoji = emoji,
                    color = color,
                    paletteColors = paletteColors,
                    hsv = HsvColor(hue, saturation, value)
                ),
                onHsvChange = { newHsv ->
                    hue = newHsv.hue
                    saturation = newHsv.saturation
                    value = newHsv.value
                    color = newHsv.toColorInt()
                },
                callbacks = EmojiPickerCallbacks(
                    onColorSelected = { color = it },
                    onShowCustomColorPickerChange = { showCustomColorPicker = it },
                    onEmojiSelected = { emoji = it }
                )
            )
        }
    }
}

private data class EmojiPickerBodyState(
    val showCustomColorPicker: Boolean,
    val isLandscape: Boolean,
    val emoji: String,
    val color: Int?,
    val paletteColors: List<Int>,
    val hsv: HsvColor
)

private data class EmojiPickerCallbacks(
    val onColorSelected: (Int?) -> Unit,
    val onShowCustomColorPickerChange: (Boolean) -> Unit,
    val onEmojiSelected: (String) -> Unit
)

@Composable
private fun ColumnScope.EmojiAvatarPickerBody(
    state: EmojiPickerBodyState,
    onHsvChange: (HsvColor) -> Unit,
    callbacks: EmojiPickerCallbacks
) {
    when {
        state.showCustomColorPicker -> {
            CustomColorSubScreen(
                state = state,
                onHsvChange = onHsvChange,
                onBack = { callbacks.onShowCustomColorPickerChange(false) }
            )
        }
        state.isLandscape -> {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Column(modifier = Modifier.width(landscapeSidePanelWidth).fillMaxHeight()) {
                    EmojiAvatarPreview(emoji = state.emoji, color = state.color)
                    Spacer(modifier = Modifier.height(16.dp))
                    ColorSwatchRow(
                        paletteColors = state.paletteColors,
                        selectedColor = state.color,
                        onColorSelected = callbacks.onColorSelected,
                        onCustomColorClick = { callbacks.onShowCustomColorPickerChange(true) },
                        wrap = true
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                EmojiGrid(
                    onEmojiSelected = callbacks.onEmojiSelected,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        }
        else -> {
            EmojiAvatarPreview(emoji = state.emoji, color = state.color)
            Spacer(modifier = Modifier.height(16.dp))
            ColorSwatchRow(
                paletteColors = state.paletteColors,
                selectedColor = state.color,
                onColorSelected = callbacks.onColorSelected,
                onCustomColorClick = { callbacks.onShowCustomColorPickerChange(true) }
            )
            Spacer(modifier = Modifier.height(16.dp))
            EmojiGrid(onEmojiSelected = callbacks.onEmojiSelected, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun EmojiAvatarPickerTopBar(onDiscard: () -> Unit, onSave: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onDiscard) {
            Icon(imageVector = Icons.Filled.Close, contentDescription = stringResource(id = R.string.nc_cancel))
        }
        IconButton(onClick = onSave) {
            Icon(imageVector = Icons.Filled.Check, contentDescription = stringResource(id = R.string.save))
        }
    }
}

@Composable
private fun EmojiAvatarPreview(emoji: String, color: Int?) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(previewSize)
                .clip(CircleShape)
                .background(color?.let { Color(it) } ?: MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(text = emoji, fontSize = previewEmojiFontSize)
        }
    }
}

@Composable
private fun ColorSwatchRow(
    paletteColors: List<Int>,
    selectedColor: Int?,
    onColorSelected: (Int?) -> Unit,
    onCustomColorClick: () -> Unit,
    wrap: Boolean = false
) {
    val swatches: @Composable () -> Unit = {
        NoColorSwatch(isSelected = selectedColor == null, onClick = { onColorSelected(null) })
        CustomColorSwatch(onClick = onCustomColorClick)
        paletteColors.forEach { paletteColor ->
            ColorSwatch(
                color = paletteColor,
                isSelected = selectedColor == paletteColor,
                onClick = { onColorSelected(paletteColor) }
            )
        }
    }

    if (wrap) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(swatchSpacing),
            verticalArrangement = Arrangement.spacedBy(swatchSpacing)
        ) {
            swatches()
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(swatchSpacing)
        ) {
            swatches()
        }
    }
}

@Composable
private fun ColorSwatch(color: Int, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(swatchSize)
            .clip(CircleShape)
            .background(Color(color))
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                shape = CircleShape
            )
            .clickable(onClick = onClick)
    )
}

@Composable
private fun NoColorSwatch(isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(swatchSize)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.FormatColorReset,
            contentDescription = stringResource(id = R.string.nc_remove_color),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun CustomColorSwatch(onClick: () -> Unit) {
    val rainbowColors = remember { hueGradientColors() }
    Box(
        modifier = Modifier
            .size(swatchSize)
            .clip(CircleShape)
            .background(Brush.sweepGradient(rainbowColors))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Palette,
            contentDescription = stringResource(id = R.string.nc_custom_color),
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun EmojiGrid(onEmojiSelected: (String) -> Unit, modifier: Modifier = Modifier) {
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerLow.toArgb()
    val selectedTabColor = MaterialTheme.colorScheme.primary.toArgb()
    val unselectedTabColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val hintTextColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            EmojiPickerPanel(ContextThemeWrapper(ctx, R.style.ThemeOverlay_App_EmojiPicker)).apply {
                searchEnabled = true
                backspaceEnabled = false
                onEmojiPicked = onEmojiSelected
                applyTheme(backgroundColor, selectedTabColor, unselectedTabColor, hintTextColor, textColor)
            }
        }
    )
}

@Composable
private fun CustomColorSubScreen(state: EmojiPickerBodyState, onHsvChange: (HsvColor) -> Unit, onBack: () -> Unit) {
    val hsv = state.hsv
    val saturationValuePicker: @Composable () -> Unit = {
        SaturationValuePicker(
            hue = hsv.hue,
            saturation = hsv.saturation,
            value = hsv.value,
            onSaturationValueChange = { newSaturation, newValue ->
                onHsvChange(hsv.copy(saturation = newSaturation, value = newValue))
            }
        )
    }
    val hueSlider: @Composable () -> Unit = {
        HueSlider(hue = hsv.hue, onHueChange = { onHsvChange(hsv.copy(hue = it)) })
    }
    val backButton: @Composable () -> Unit = {
        IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(id = R.string.back_button))
        }
    }

    if (state.isLandscape) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.width(landscapeSidePanelWidth)) {
                backButton()
                Spacer(modifier = Modifier.height(12.dp))
                EmojiAvatarPreview(emoji = state.emoji, color = state.color)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                saturationValuePicker()
                Spacer(modifier = Modifier.height(12.dp))
                hueSlider()
            }
        }
    } else {
        backButton()
        Spacer(modifier = Modifier.height(12.dp))
        EmojiAvatarPreview(emoji = state.emoji, color = state.color)
        Spacer(modifier = Modifier.height(16.dp))
        saturationValuePicker()
        Spacer(modifier = Modifier.height(12.dp))
        hueSlider()
    }
}

@Composable
private fun SaturationValuePicker(
    hue: Float,
    saturation: Float,
    value: Float,
    onSaturationValueChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val hueColor = remember(hue) { Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f))) }
    val density = LocalDensity.current
    val thumbRadiusPx = with(density) { (thumbSize / 2).toPx() }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(saturationValueHeight)
            .clip(RoundedCornerShape(8.dp))
            .onSizeChanged { boxSize = it }
            .background(hueColor)
            .background(Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            .pointerInput(Unit) {
                fun update(x: Float, y: Float) {
                    val clampedX = x.coerceIn(0f, size.width.toFloat())
                    val clampedY = y.coerceIn(0f, size.height.toFloat())
                    onSaturationValueChange(clampedX / size.width, 1f - clampedY / size.height)
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    update(down.position.x, down.position.y)
                    drag(down.id) { change ->
                        change.consume()
                        update(change.position.x, change.position.y)
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (saturation * boxSize.width - thumbRadiusPx).roundToInt(),
                        ((1f - value) * boxSize.height - thumbRadiusPx).roundToInt()
                    )
                }
                .size(thumbSize)
                .clip(CircleShape)
                .border(2.dp, Color.White, CircleShape)
        )
    }
}

@Composable
private fun HueSlider(hue: Float, onHueChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val hueColors = remember { hueGradientColors() }
    val density = LocalDensity.current
    val thumbRadiusPx = with(density) { (thumbSize / 2).toPx() }
    var sliderWidth by remember { mutableStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(thumbSize)
            .onSizeChanged { sliderWidth = it.width }
            .clip(RoundedCornerShape(thumbSize / 2))
            .background(Brush.horizontalGradient(hueColors))
            .pointerInput(Unit) {
                fun update(x: Float) {
                    val clampedX = x.coerceIn(0f, size.width.toFloat())
                    onHueChange(clampedX / size.width * MAX_HUE)
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    update(down.position.x)
                    drag(down.id) { change ->
                        change.consume()
                        update(change.position.x)
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset((hue / MAX_HUE * sliderWidth - thumbRadiusPx).roundToInt(), 0) }
                .size(thumbSize)
                .clip(CircleShape)
                .border(2.dp, Color.White, CircleShape)
        )
    }
}

private data class HsvColor(val hue: Float, val saturation: Float, val value: Float) {
    fun toColorInt(): Int = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))
}

private fun hueGradientColors(): List<Color> =
    (0..HUE_GRADIENT_STEPS).map { step ->
        Color(android.graphics.Color.HSVToColor(floatArrayOf(step * MAX_HUE / HUE_GRADIENT_STEPS, 1f, 1f)))
    }

private fun predefinedPaletteColors(): List<Int> {
    val allColors = ColorGenerator.paletteColors()
    val step = allColors.size / PREDEFINED_COLOR_COUNT
    return (0 until PREDEFINED_COLOR_COUNT).map { allColors[it * step] }
}

private const val DEFAULT_EMOJI = "🙂"
private const val SHEET_HEIGHT_FRACTION = 0.85f
private const val MAX_HUE = 360f
private const val MIN_VALUE = 0.15f
private const val HUE_GRADIENT_STEPS = 6
private const val HSV_COMPONENT_COUNT = 3
private const val PREDEFINED_COLOR_COUNT = 6
private val thumbSize = 24.dp
private val saturationValueHeight = 160.dp
private val swatchSize = 36.dp
private val swatchSpacing = 8.dp
private val previewSize = 88.dp
private val previewEmojiFontSize = 40.sp
private val landscapeSidePanelWidth = 160.dp
