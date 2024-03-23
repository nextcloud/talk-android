/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Samanwith KSN <samanwith21@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class VibrationUtilsTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockVibrator: Vibrator

    @Before
    fun setup() {
        Mockito.`when`(mockContext.getSystemService(Context.VIBRATOR_SERVICE)).thenReturn(mockVibrator)
    }

    @Test
    fun testVibrateShort() {
        VibrationUtils.vibrateShort(mockContext)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Mockito.verify(mockVibrator)
                .vibrate(
                    VibrationEffect
                        .createOneShot(
                            VibrationUtils.SHORT_VIBRATE,
                            VibrationEffect.DEFAULT_AMPLITUDE
                        )
                )
        } else {
            Mockito.verify(mockVibrator).vibrate(VibrationUtils.SHORT_VIBRATE)
        }
    }
}
