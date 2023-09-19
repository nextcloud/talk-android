/*
 * Nextcloud Talk application
 *
 * @author Samanwith KSN
 * Copyright (C) 2023 Samanwith KSN <samanwith21@gmail.com>
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
