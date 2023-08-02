/*
 * Nextcloud Talk application
 *
 * @author Julius Linus
 * Copyright (C) 2023 Julius Linus <julius.linus@nextcloud.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.ui

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import androidx.annotation.ColorInt
import androidx.appcompat.widget.AppCompatSeekBar
import com.nextcloud.talk.utils.AudioUtils
import kotlin.math.roundToInt

class WaveformSeekBar : AppCompatSeekBar {

    @ColorInt
    private var primary: Int = Color.parseColor("#679ff5")

    @ColorInt
    private var secondary: Int = Color.parseColor("#a6c6f7")
    private var waveData: FloatArray = floatArrayOf()
    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    fun setColors(@ColorInt p: Int, @ColorInt s: Int) {
        primary = p
        secondary = s
        invalidate()
    }

    /**
     * Sets the wave data of the seekbar. Shrinks the data to a calculated number of bars based off the width of the
     * seekBar. The greater the width, the more bars displayed.
     *
     * Note: bar gap = (usableWidth - waveData.size * DEFAULT_BAR_WIDTH) / (waveData.size - 1).toFloat()
     * therefore, the gap is determined by the width of the seekBar by extension.
     */
    fun setWaveData(data: FloatArray) {
        val usableWidth = width - paddingLeft - paddingRight
        if (usableWidth > 0) {
            val numBars = if (usableWidth > VALUE_100) (usableWidth / WIDTH_DIVISOR) else usableWidth / 2f
            waveData = AudioUtils.shrinkFloatArray(data, numBars.roundToInt())
            invalidate()
        }
    }

    private fun init() {
        paint.apply {
            strokeCap = Paint.Cap.ROUND
            strokeWidth = DEFAULT_BAR_WIDTH.dp.toFloat()
            color = Color.RED
        }
    }

    override fun onDraw(canvas: Canvas?) {
        if (waveData.isEmpty() || waveData[0].toString() == "NaN") {
            super.onDraw(canvas)
        } else {
            if (progressDrawable != null) {
                super.setProgressDrawable(null)
            }

            drawWaveformSeekbar(canvas)
            super.onDraw(canvas)
        }
    }

    private fun drawWaveformSeekbar(canvas: Canvas?) {
        val usableHeight = height - paddingTop - paddingBottom
        val usableWidth = width - paddingLeft - paddingRight
        val midpoint = usableHeight / 2f
        val maxHeight: Float = usableHeight / MAX_HEIGHT_DIVISOR
        val barGap: Float = (usableWidth - waveData.size * DEFAULT_BAR_WIDTH) / (waveData.size - 1).toFloat()

        canvas?.apply {
            save()
            translate(paddingLeft.toFloat(), paddingTop.toFloat())
            for (i in waveData.indices) {
                val x: Float = i * (DEFAULT_BAR_WIDTH + barGap) + DEFAULT_BAR_WIDTH / 2f
                val y: Float = waveData[i] * maxHeight
                val progress = (x / usableWidth)
                paint.color = if (progress * max < getProgress()) primary else secondary
                canvas.drawLine(x, midpoint - y, x, midpoint + y, paint)
            }

            restore()
        }
    }

    companion object {
        private const val DEFAULT_BAR_WIDTH: Int = 2
        private const val MAX_HEIGHT_DIVISOR: Float = 4.0f
        private const val WIDTH_DIVISOR = 20f
        private const val VALUE_100 = 100
        private val Int.dp: Int
            get() = (this * Resources.getSystem().displayMetrics.density).roundToInt()
    }
}
