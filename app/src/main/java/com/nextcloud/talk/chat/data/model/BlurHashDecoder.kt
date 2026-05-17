/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * BlurHash decode algorithm adapted from https://github.com/woltapp/blurhash (Kotlin implementation).
 * Original work copyright (c) 2019 Wolt Enterprises; used under the MIT License.
 * See https://github.com/woltapp/blurhash/blob/master/License.txt
 */

package com.nextcloud.talk.chat.data.model

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.withSign
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set

private const val BLURHASH_CHARS =
    "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#\$%*+,-.:;=?@[]^_{|}~"
private const val BLURHASH_DECODE_WIDTH = 32

// BlurHash protocol constants (https://github.com/woltapp/blurhash/blob/master/Algorithm.md)
private const val BLURHASH_MIN_LENGTH = 6
private const val BLURHASH_BASE = 83
private const val BLURHASH_MAX_COMPONENTS = 9
private const val BLURHASH_HEADER_SIZE = 4
private const val BLURHASH_DC_END = 6
private const val BLURHASH_AC_QUANT_RANGE = 166f
private const val BLURHASH_RGB_CHANNELS = 3
private const val BLURHASH_AC_QUANT_STEPS = 19
private const val BLURHASH_AC_QUANT_BIAS = 9
private const val BLURHASH_AC_QUANT_BIAS_F = 9f

// sRGB IEC 61966-2-1 transfer function constants
private const val COLOR_RED_SHIFT = 16
private const val COLOR_GREEN_SHIFT = 8
private const val COLOR_BYTE_MASK = 0xFF
private const val SRGB_MAX_BYTE = 255f
private const val SRGB_LINEAR_THRESHOLD = 0.04045f
private const val SRGB_LINEAR_SCALE = 12.92f
private const val SRGB_GAMMA_OFFSET = 0.055f
private const val SRGB_GAMMA_SCALE = 1.055f
private const val SRGB_GAMMA = 2.4f
private const val SRGB_LINEAR_THRESHOLD_INV = 0.0031308f
private const val SRGB_ROUND = 0.5f

internal object BlurHashDecoder {

    fun decode(hash: String?, width: Int, height: Int): Bitmap? {
        if (hash.isNullOrBlank() || hash.length < BLURHASH_MIN_LENGTH) return null

        val numCompEnc = decode83(hash, 0, 1)
        val numCompX = (numCompEnc % BLURHASH_MAX_COMPONENTS) + 1
        val numCompY = (numCompEnc / BLURHASH_MAX_COMPONENTS) + 1

        return if (hash.length != BLURHASH_HEADER_SIZE + 2 * numCompX * numCompY) {
            null
        } else {
            val maxAc = (decode83(hash, 1, 2) + 1) / BLURHASH_AC_QUANT_RANGE
            val colors = Array(numCompX * numCompY) { FloatArray(BLURHASH_RGB_CHANNELS) }
            decodeDc(decode83(hash, 2, BLURHASH_DC_END), colors[0])
            for (i in 1 until colors.size) {
                val from = BLURHASH_HEADER_SIZE + i * 2
                decodeAc(decode83(hash, from, from + 2), maxAc, colors[i])
            }
            runCatching { composeBitmap(width, height, numCompX, numCompY, colors) }.getOrNull()
        }
    }

    private fun decode83(str: String, from: Int, to: Int): Int {
        var result = 0
        for (i in from until to) {
            val idx = BLURHASH_CHARS.indexOf(str[i])
            if (idx >= 0) result = result * BLURHASH_BASE + idx
        }
        return result
    }

    private fun decodeDc(value: Int, out: FloatArray) {
        out[0] = srgbToLinear((value shr COLOR_RED_SHIFT) and COLOR_BYTE_MASK)
        out[1] = srgbToLinear((value shr COLOR_GREEN_SHIFT) and COLOR_BYTE_MASK)
        out[2] = srgbToLinear(value and COLOR_BYTE_MASK)
    }

    private fun decodeAc(value: Int, maxAc: Float, out: FloatArray) {
        val steps = BLURHASH_AC_QUANT_STEPS
        val bias = BLURHASH_AC_QUANT_BIAS
        val biasF = BLURHASH_AC_QUANT_BIAS_F
        out[0] = signedPow2(((value / (steps * steps)) - bias) / biasF) * maxAc
        out[1] = signedPow2(((value / steps) % steps - bias) / biasF) * maxAc
        out[2] = signedPow2((value % steps - bias) / biasF) * maxAc
    }

    private fun srgbToLinear(v: Int): Float {
        val f = v / SRGB_MAX_BYTE
        return if (f <= SRGB_LINEAR_THRESHOLD) {
            f / SRGB_LINEAR_SCALE
        } else {
            ((f + SRGB_GAMMA_OFFSET) / SRGB_GAMMA_SCALE).pow(SRGB_GAMMA)
        }
    }

    private fun linearToSrgb(v: Float): Int {
        val c = v.coerceIn(0f, 1f)
        return if (c <= SRGB_LINEAR_THRESHOLD_INV) {
            (c * SRGB_LINEAR_SCALE * SRGB_MAX_BYTE + SRGB_ROUND).toInt()
        } else {
            ((SRGB_GAMMA_SCALE * c.pow(1f / SRGB_GAMMA) - SRGB_GAMMA_OFFSET) * SRGB_MAX_BYTE + SRGB_ROUND).toInt()
        }
    }

    private fun signedPow2(v: Float) = (v * v).withSign(v)

    private fun composeBitmap(
        width: Int,
        height: Int,
        numCompX: Int,
        numCompY: Int,
        colors: Array<FloatArray>
    ): Bitmap {
        fun computePixel(x: Int, y: Int): Int {
            var r = 0f
            var g = 0f
            var b = 0f
            for (cy in 0 until numCompY) {
                for (cx in 0 until numCompX) {
                    val basis = (cos(PI * x * cx / width) * cos(PI * y * cy / height)).toFloat()
                    val c = colors[cy * numCompX + cx]
                    r += c[0] * basis
                    g += c[1] * basis
                    b += c[2] * basis
                }
            }
            return Color.rgb(linearToSrgb(r), linearToSrgb(g), linearToSrgb(b))
        }

        val bitmap = createBitmap(width, height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                bitmap[x, y] = computePixel(x, y)
            }
        }
        return bitmap
    }
}

/**
 * Decodes [hash] into a small Bitmap sized to preserve the original aspect ratio.
 * Always decodes at [BLURHASH_DECODE_WIDTH] px wide — Coil scales it to fill the view.
 * Returns null when [hash] is absent, blank, or malformed.
 */
fun decodeBlurhashPlaceholder(hash: String?, imageWidth: Int?, imageHeight: Int?): Bitmap? {
    if (hash.isNullOrBlank()) return null
    val decodeHeight = if (imageWidth != null && imageHeight != null && imageWidth > 0) {
        (BLURHASH_DECODE_WIDTH * imageHeight / imageWidth).coerceAtLeast(1)
    } else {
        BLURHASH_DECODE_WIDTH
    }
    return BlurHashDecoder.decode(hash, BLURHASH_DECODE_WIDTH, decodeHeight)
}
