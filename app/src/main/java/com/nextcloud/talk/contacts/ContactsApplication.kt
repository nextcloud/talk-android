/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.contacts

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.util.DebugLogger
import com.nextcloud.talk.utils.ContactUtils

class ContactsApplication :
    Application(),
    ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        val imageLoader = ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(ContactUtils.CACHE_MEMORY_SIZE_PERCENTAGE)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .maxSizePercent(ContactUtils.CACHE_DISK_SIZE_PERCENTAGE)
                    .directory(cacheDir)
                    .build()
            }
            .logger(DebugLogger())
            .build()
        return imageLoader
    }
}
