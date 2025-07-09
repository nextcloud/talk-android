/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json

import android.os.Parcel
import kotlinx.parcelize.Parceler

class AnyParceler : Parceler<Any?> {
    override fun create(parcel: Parcel): Any? = parcel.readValue(Any::class.java.getClassLoader())

    override fun Any?.write(parcel: Parcel, flags: Int) {
        parcel.writeValue(parcel)
    }
}
