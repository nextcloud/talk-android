/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.location

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class GeocodingResult(val lat: Double, val lon: Double, var displayName: String) : Parcelable
