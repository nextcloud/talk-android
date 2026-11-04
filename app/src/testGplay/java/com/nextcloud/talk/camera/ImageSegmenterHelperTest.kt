/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.camera

import com.nextcloud.talk.camera.ImageSegmenterHelper.Companion.MASK_BACKGROUND
import com.nextcloud.talk.camera.ImageSegmenterHelper.Companion.MASK_PERSON
import com.nextcloud.talk.camera.ImageSegmenterHelper.Companion.MODEL_SIZE
import com.nextcloud.talk.camera.ImageSegmenterHelper.Companion.confidenceToMask
import com.nextcloud.talk.camera.ImageSegmenterHelper.Companion.writeModelInput
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ImageSegmenterHelperTest {

    @Test
    fun writeModelInputScalesRgbaFrameToNormalisedRgb() {
        // 512x2 frame: left half red, right half blue (alpha 255 everywhere)
        val width = 512
        val height = 2
        val frame = ByteBuffer.allocateDirect(width * height * 4)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val isLeft = x < width / 2
                frame.put(if (isLeft) 255.toByte() else 0)
                frame.put(0)
                frame.put(if (isLeft) 0 else 255.toByte())
                frame.put(255.toByte())
            }
        }
        frame.position(7)
        val out = ByteBuffer.allocateDirect(MODEL_SIZE * MODEL_SIZE * 3 * 4).order(ByteOrder.nativeOrder())

        writeModelInput(frame, width, height, out)

        val floats = out.asFloatBuffer()
        assertEquals(floatArrayOf(1f, 0f, 0f).toList(), (0 until 3).map { floats.get(it) })
        val lastPixel = (MODEL_SIZE * MODEL_SIZE - 1) * 3
        assertEquals(floatArrayOf(0f, 0f, 1f).toList(), (0 until 3).map { floats.get(lastPixel + it) })
        assertEquals("frame position must stay untouched for the GPU thread", 7, frame.position())
    }

    @Test
    fun confidenceToMaskMarksBackgroundAndScalesToFrameSize() {
        // person (high confidence) in the top half of the model output
        val confidence = FloatArray(MODEL_SIZE * MODEL_SIZE) { if (it < MODEL_SIZE * MODEL_SIZE / 2) 0.9f else 0.1f }
        val width = 640
        val height = 480

        val mask = confidenceToMask(confidence, width, height)

        assertEquals(width * height, mask.size)
        assertEquals(MASK_PERSON, mask[0])
        assertEquals(MASK_PERSON, mask[(height / 2 - 1) * width + width - 1])
        assertEquals(MASK_BACKGROUND, mask[(height / 2) * width])
        assertEquals(MASK_BACKGROUND, mask[mask.size - 1])
    }
}
