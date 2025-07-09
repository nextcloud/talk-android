/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Christian Reiner <foss@christian-reiner.info>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui

import android.content.Context
import android.util.AttributeSet
import autodagger.AutoInjector
import com.google.android.material.button.MaterialButton
import java.util.Locale
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import javax.inject.Inject

internal const val SPEED_FACTOR_SLOW = 0.8f
internal const val SPEED_FACTOR_NORMAL = 1.0f
internal const val SPEED_FACTOR_FASTER = 1.5f
internal const val SPEED_FACTOR_FASTEST = 2.0f

@AutoInjector(NextcloudTalkApplication::class)
class PlaybackSpeedControl @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : MaterialButton(context, attrs, defStyleAttr) {

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    private var currentSpeed = PlaybackSpeed.NORMAL

    init {
        NextcloudTalkApplication.sharedApplication?.componentApplication?.inject(this)
        text = currentSpeed.label
        viewThemeUtils.material.colorMaterialButtonText(this)
    }

    fun setSpeed(newSpeed: PlaybackSpeed) {
        currentSpeed = newSpeed
        text = currentSpeed.label
    }

    fun getSpeed(): PlaybackSpeed = currentSpeed
}

enum class PlaybackSpeed(val value: Float) {
    SLOW(SPEED_FACTOR_SLOW),
    NORMAL(SPEED_FACTOR_NORMAL),
    FASTER(SPEED_FACTOR_FASTER),
    FASTEST(SPEED_FACTOR_FASTEST);

    // no fixed, literal labels, since we want to obey numeric interpunctuation for different locales
    val label: String = String.format(Locale.getDefault(), "%01.1fx", value)

    fun next(): PlaybackSpeed = entries[(ordinal + 1) % entries.size]

    companion object {
        fun byName(name: String): PlaybackSpeed {
            for (speed in entries) {
                if (speed.name.equals(name, ignoreCase = true)) {
                    return speed
                }
            }
            return NORMAL
        }
    }
}
