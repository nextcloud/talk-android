/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.extensions

import android.content.Context
import android.os.PowerManager

/** Whether the device is currently in battery saver mode. */
fun Context.isPowerSaveMode(): Boolean = (getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode
