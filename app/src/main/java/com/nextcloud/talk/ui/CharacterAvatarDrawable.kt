/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.nextcloud.talk.utils.ActorAvatar
import kotlin.math.min

/**
 * The character avatar as a drawable, for the views and notifications that cannot use the
 * [CharacterAvatar] composable.
 */
fun ActorAvatar.Character.toDrawable(context: Context): CharacterAvatarDrawable =
    CharacterAvatarDrawable(
        character = character,
        backgroundColor = ContextCompat.getColor(context, backgroundColor),
        textColor = ContextCompat.getColor(context, textColor)
    )

/**
 * Circular avatar drawn from a character, for actors without an avatar on the server - see
 * [com.nextcloud.talk.utils.CharacterAvatarUtils] for which actors those are.
 *
 * The character is scaled to the bounds it is handed rather than to a fixed bitmap, so the same
 * avatar stays sharp in a mention chip and in a call tile.
 */
class CharacterAvatarDrawable(
    private val character: String,
    @ColorInt private val backgroundColor: Int,
    @ColorInt private val textColor: Int
) : Drawable() {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = backgroundColor
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textAlign = Paint.Align.CENTER
    }

    private val textBounds = Rect()

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        fitTextTo(bounds)
    }

    /**
     * Sizes the character relative to the circle, then shrinks it if it is still too wide to sit
     * inside the circle - a single letter always fits, the two-character bot prompt does not.
     */
    private fun fitTextTo(bounds: Rect) {
        val diameter = min(bounds.width(), bounds.height())
        if (diameter <= 0 || character.isEmpty()) {
            return
        }

        textPaint.textSize = diameter * TEXT_SIZE_RATIO
        textPaint.getTextBounds(character, 0, character.length, textBounds)

        val maxTextWidth = diameter * MAX_TEXT_WIDTH_RATIO
        if (textBounds.width() > maxTextWidth) {
            textPaint.textSize *= maxTextWidth / textBounds.width()
        }
    }

    override fun draw(canvas: Canvas) {
        val diameter = min(bounds.width(), bounds.height())
        canvas.drawCircle(bounds.exactCenterX(), bounds.exactCenterY(), diameter / 2f, backgroundPaint)

        if (character.isEmpty()) {
            return
        }

        val baseline = bounds.exactCenterY() - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(character, bounds.exactCenterX(), baseline, textPaint)
    }

    override fun setAlpha(alpha: Int) {
        backgroundPaint.alpha = alpha
        textPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        backgroundPaint.colorFilter = colorFilter
        textPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Drawable", ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat"))
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = INTRINSIC_SIZE

    override fun getIntrinsicHeight(): Int = INTRINSIC_SIZE

    companion object {
        /**
         * Character height relative to the circle, matching the web client's font-size of half the
         * avatar size.
         */
        internal const val TEXT_SIZE_RATIO = 0.5f

        /**
         * Widest the character may get before it is scaled down, leaving the circle a margin.
         */
        internal const val MAX_TEXT_WIDTH_RATIO = 0.6f

        /**
         * Fallback size for views that size themselves to the drawable instead of the other way
         * round. Large enough to stay sharp on high density screens.
         */
        private const val INTRINSIC_SIZE = 128
    }
}
