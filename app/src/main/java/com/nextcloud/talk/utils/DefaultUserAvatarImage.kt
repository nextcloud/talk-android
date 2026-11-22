/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.nextcloud.talk.R
import java.io.ByteArrayOutputStream

class DefaultUserAvatarImage(private val context: Context) {
    private val images = mutableMapOf<Boolean, ByteArray>()

    @Synchronized
    fun bytes(dark: Boolean): ByteArray =
        images.getOrPut(dark) {
            val configuration = Configuration(context.resources.configuration)
            val night = if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            configuration.uiMode = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
            val themedContext = context.createConfigurationContext(configuration)
            val drawable = requireNotNull(ContextCompat.getDrawable(themedContext, R.drawable.account_circle_96dp))
            val bitmap = drawable.toBitmap(AVATAR_PIXELS, AVATAR_PIXELS)
            ByteArrayOutputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, output)
                bitmap.recycle()
                output.toByteArray()
            }
        }

    companion object {
        private const val AVATAR_PIXELS = 256
        private const val PNG_QUALITY = 100
    }
}
