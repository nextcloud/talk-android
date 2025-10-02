/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models

/**
 * Enum representing different levels of image compression for file uploads.
 * Each level defines specific quality and resolution parameters.
 */
enum class ImageCompressionLevel(val key: String, val quality: Int, val maxWidth: Int, val maxHeight: Int) {
    /**
     * No compression - original image is uploaded
     */
    NONE("none", 100, Int.MAX_VALUE, Int.MAX_VALUE),

    /**
     * Light compression - better quality than WhatsApp
     * Suitable for high-quality sharing while reducing file size
     */
    LIGHT("light", 95, 1920, 1920),

    /**
     * Medium compression - similar to WhatsApp compression
     * Good balance between quality and file size
     */
    MEDIUM("medium", 90, 1600, 1600),

    /**
     * Strong compression - more aggressive than WhatsApp
     * Best for quick sharing and data-conscious scenarios
     */
    STRONG("strong", 80, 1280, 1280);

    companion object {
        /**
         * Returns the compression level from its key string
         * @param key The key string of the compression level
         * @return The matching ImageCompressionLevel or NONE if not found
         */
        fun fromKey(key: String): ImageCompressionLevel = values().find { it.key == key } ?: NONE

        /**
         * Returns the default compression level
         */
        fun getDefault(): ImageCompressionLevel = NONE
    }

    /**
     * Returns true if compression should be applied (i.e., not NONE)
     */
    fun shouldCompress(): Boolean = this != NONE
}
