/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Jindrich Kolman
 * Copyright (C) 2017-2022 Mario Danic <mario@lovelyhq.com>
 * Copyright (C) 2022 Jindrich Kolman <kolman.jindrich@gmail.com>
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

package com.nextcloud.talk.utils.unifiedpush

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.util.Log
import org.unifiedpush.android.connector.UnifiedPush

@SuppressLint("LongLogTag")
open class ProviderInstaller {

    interface ProviderInstallListener {

        fun onProviderInstalled() {
            // unused atm
        }

        fun onProviderInstallFailed(p0: Int, p1: Intent?) {
            // unused atm
        }

        companion object {
            const val TAG = "ProviderInstaller.ProviderInstallListener"
        }
    }

    companion object {
        lateinit var listener: ProviderInstallListener
        fun installIfNeededAsync(context: Context, listener: ProviderInstallListener) {
            this.listener = listener
            UnifiedPush.registerAppWithDialog(context)
        }

        fun isUnifiedPushAvailable(context: Context): Boolean {
            return UnifiedPush.getDistributors(context).isNotEmpty()
        }

        const val TAG = "ProviderInstaller"
    }
}
