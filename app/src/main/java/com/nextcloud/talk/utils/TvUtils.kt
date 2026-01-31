/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.utils

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import androidx.annotation.ColorInt

object TvUtils {
    
    /**
     * Check if the device is running in TV mode
     */
    fun isTvMode(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        return uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }

    /**
     * Check if device has a large screen suitable for TV
     */
    fun isLargeScreen(context: Context): Boolean {
        val screenLayout = context.resources.configuration.screenLayout
        val screenSize = screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK
        return screenSize >= Configuration.SCREENLAYOUT_SIZE_LARGE
    }

    /**
     * Get TV aspect ratio (16:9 is most common for TVs)
     */
    fun getTvAspectRatio(): Float {
        return 16f / 9f
    }

    /**
     * Apply TV-friendly focus highlighting to a view
     */
    fun applyTvFocusHighlight(view: View, @ColorInt focusColor: Int) {
        view.isFocusable = true
        view.isFocusableInTouchMode = false
        
        // Store the original background
        val originalBackground = view.background
        
        view.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                // Apply focus color with transparency and scale up
                val focusDrawable = ColorDrawable(Color.argb(60, Color.red(focusColor), Color.green(focusColor), Color.blue(focusColor)))
                v.background = focusDrawable
                v.scaleX = 1.1f
                v.scaleY = 1.1f
                v.elevation = 8f
            } else {
                // Restore original background and scale
                v.background = originalBackground
                v.scaleX = 1.0f
                v.scaleY = 1.0f
                v.elevation = 0f
            }
        }
    }

    /**
     * Setup D-pad navigation for a list of views
     * @param views List of views to setup navigation for
     * @param orientation Navigation orientation: HORIZONTAL or VERTICAL
     */
    fun setupDpadNavigation(views: List<View>, orientation: NavigationOrientation = NavigationOrientation.HORIZONTAL) {
        for (i in views.indices) {
            views[i].isFocusable = true
            views[i].isFocusableInTouchMode = false
            
            when (orientation) {
                NavigationOrientation.HORIZONTAL -> {
                    // Set up left/right navigation
                    if (i > 0) {
                        views[i].nextFocusLeftId = views[i - 1].id
                    }
                    if (i < views.size - 1) {
                        views[i].nextFocusRightId = views[i + 1].id
                    }
                }
                NavigationOrientation.VERTICAL -> {
                    // Set up up/down navigation
                    if (i > 0) {
                        views[i].nextFocusUpId = views[i - 1].id
                    }
                    if (i < views.size - 1) {
                        views[i].nextFocusDownId = views[i + 1].id
                    }
                }
            }
        }
    }
    
    enum class NavigationOrientation {
        HORIZONTAL,
        VERTICAL
    }

    /**
     * Request focus on the first focusable view
     */
    fun requestDefaultFocus(vararg views: View) {
        for (view in views) {
            if (view.isFocusable && view.visibility == View.VISIBLE) {
                view.requestFocus()
                break
            }
        }
    }

    /**
     * Calculate video dimensions to fit TV screen while maintaining aspect ratio
     */
    fun calculateTvVideoDimensions(
        screenWidth: Int,
        screenHeight: Int,
        videoWidth: Int,
        videoHeight: Int
    ): Pair<Int, Int> {
        val screenAspect = screenWidth.toFloat() / screenHeight.toFloat()
        val videoAspect = videoWidth.toFloat() / videoHeight.toFloat()

        return if (screenAspect > videoAspect) {
            // Screen is wider - fit to height
            val width = (screenHeight * videoAspect).toInt()
            Pair(width, screenHeight)
        } else {
            // Screen is taller - fit to width
            val height = (screenWidth / videoAspect).toInt()
            Pair(screenWidth, height)
        }
    }

    /**
     * Get recommended video resolution for TV
     * Most Android TVs support 1080p or 4K
     */
    fun getTvRecommendedResolution(): Pair<Int, Int> {
        return Pair(1920, 1080) // Full HD 1080p
    }
}
