/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.app.Application
import android.content.Context
import android.widget.FrameLayout
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * With windowLayoutInDisplayCutoutMode=shortEdges the window reaches under the camera hole, so the content
 * must be padded by the cutout, otherwise it is drawn below the camera.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [31])
class DisplayCutoutPaddingTest {

    private fun dispatch(view: FrameLayout, cutout: Insets) {
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.displayCutout(), cutout)
            .build()
        ViewCompat.dispatchApplyWindowInsets(view, insets)
    }

    @Test
    fun contentIsPaddedByCutoutOnTheSide() {
        val view = FrameLayout(ApplicationProvider.getApplicationContext<Context>())
        view.setPadding(BASE_PADDING, TOP_PADDING, BASE_PADDING, 0)
        view.padForDisplayCutout()

        dispatch(view, Insets.of(0, 0, CUTOUT_WIDTH, 0))

        assertEquals(BASE_PADDING, view.paddingLeft)
        assertEquals(BASE_PADDING + CUTOUT_WIDTH, view.paddingRight)
        assertEquals(TOP_PADDING, view.paddingTop)
    }

    @Test
    fun paddingIsRemovedWhenCutoutMovesToTheOtherSide() {
        val view = FrameLayout(ApplicationProvider.getApplicationContext<Context>())
        view.padForDisplayCutout()

        dispatch(view, Insets.of(0, 0, CUTOUT_WIDTH, 0))
        dispatch(view, Insets.of(CUTOUT_WIDTH, 0, 0, 0))

        assertEquals(CUTOUT_WIDTH, view.paddingLeft)
        assertEquals(0, view.paddingRight)
    }

    companion object {
        private const val BASE_PADDING = 8
        private const val TOP_PADDING = 5
        private const val CUTOUT_WIDTH = 121
    }
}
