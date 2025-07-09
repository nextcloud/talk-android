/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import com.nextcloud.talk.R

class TextDrawable(val context: Context, private var text: String) : Drawable() {
    private val paint = Paint()
    private val bounds: Rect

    init {
        paint.color = context.getColor(R.color.textColorOnPrimaryBackground)
        paint.isAntiAlias = true
        paint.textSize = TEXT_SIZE
        bounds = Rect()
    }

    override fun draw(canvas: Canvas) {
        if (text.isNotEmpty()) {
            paint.getTextBounds(
                text,
                0,
                text.length,
                bounds
            )
            val x: Int = (getBounds().width() - bounds.width()) / 2
            val y: Int = ((getBounds().height() + bounds.height()) / 2) + Y_OFFSET
            canvas.drawText(text, x.toFloat(), y.toFloat(), paint)
        }
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.setColorFilter(colorFilter)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.OPAQUE", "android.graphics.PixelFormat"))
    override fun getOpacity(): Int = PixelFormat.OPAQUE

    companion object {
        private const val Y_OFFSET = 5
        private const val TEXT_SIZE = 50f
    }
}
