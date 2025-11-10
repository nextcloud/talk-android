/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.call

import org.webrtc.MediaConstraints

class MediaConstraintsHelper(constraints: MediaConstraints? = null) {

    private val constraints: MediaConstraints = constraints ?: MediaConstraints()

    fun copy(): MediaConstraintsHelper {
        val newConstraints = MediaConstraints()
        newConstraints.mandatory.addAll(
            this.constraints.mandatory.map {
                MediaConstraints.KeyValuePair(it.key, it.value)
            }
        )
        newConstraints.optional.addAll(
            this.constraints.optional.map {
                MediaConstraints.KeyValuePair(it.key, it.value)
            }
        )
        return MediaConstraintsHelper(newConstraints)
    }

    fun replaceOrAddConstraint(key: String, value: String, mandatoryList: Boolean = true): MediaConstraintsHelper {
        val list = if (mandatoryList) constraints.mandatory else constraints.optional
        val index = list.indexOfFirst { it.key == key }
        val newPair = MediaConstraints.KeyValuePair(key, value)
        if (index != -1) {
            list[index] = newPair
        } else {
            list.add(newPair)
        }
        return this
    }

    fun applyIf(condition: Boolean, block: MediaConstraintsHelper.() -> Unit): MediaConstraintsHelper {
        if (condition) block()
        return this
    }

    fun build(): MediaConstraints = constraints
}
