/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.components

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsAnimation
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nextcloud.talk.data.network.ServerConnectionStatus
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Shows a [banner] for the server connection status above the content of a screen, which is added to [contentFrame].
 *
 * While the banner is shown, it sits at the top of the window instead of the content, so it takes over the top inset
 * of the status bar and display cutout: the content gets insets without that part and doesn't pad for the status bar a
 * second time below the banner. Only the part not yet applied as padding by a parent, e.g. by the decor view of view
 * based screens, is taken over, so the content gets the same insets as without banner in that case.
 */
@SuppressLint("ViewConstructor")
class StatusBannerLayout(context: Context, banner: @Composable (ServerConnectionStatus) -> Unit) :
    LinearLayout(context) {

    private val status = MutableStateFlow(ServerConnectionStatus.CONNECTED)

    private val bannerView = ComposeView(context).apply {
        setContent {
            val currentStatus by status.collectAsStateWithLifecycle()
            banner(currentStatus)
        }
    }

    val contentFrame = FrameLayout(context)

    private val isBannerShown: Boolean
        get() = status.value != ServerConnectionStatus.CONNECTED

    init {
        orientation = VERTICAL
        addView(bannerView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(contentFrame, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    /** Shows the status of [statusFlow] while [lifecycleOwner] is started. */
    fun showStatusWhileStarted(lifecycleOwner: LifecycleOwner, statusFlow: suspend () -> Flow<ServerConnectionStatus>) {
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                statusFlow().withUnreachableDelayed().collect { newStatus ->
                    val wasBannerShown = isBannerShown
                    status.value = newStatus
                    if (isBannerShown != wasBannerShown) {
                        ViewCompat.requestApplyInsets(this@StatusBannerLayout)
                    }
                }
            }
        }
    }

    override fun dispatchApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val (bannerInsets, contentInsets) = splitInsets(insets)
        bannerView.dispatchApplyWindowInsets(bannerInsets)
        contentFrame.dispatchApplyWindowInsets(contentInsets)
        return insets
    }

    /**
     * Passes the insets of a running animation, e.g. of the keyboard, on like [dispatchApplyWindowInsets]. They are
     * dispatched separately, so without this the content would get the full top inset while the animation runs.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    override fun dispatchWindowInsetsAnimationProgress(
        insets: WindowInsets,
        runningAnimations: MutableList<WindowInsetsAnimation>
    ): WindowInsets {
        val (bannerInsets, contentInsets) = splitInsets(insets)
        bannerView.dispatchWindowInsetsAnimationProgress(bannerInsets, runningAnimations)
        contentFrame.dispatchWindowInsetsAnimationProgress(contentInsets, runningAnimations)
        return insets
    }

    /** The insets for the banner and for the content. */
    private fun splitInsets(insets: WindowInsets): Pair<WindowInsets, WindowInsets> {
        val windowInsets = WindowInsetsCompat.toWindowInsetsCompat(insets, this)
        val appliedByParents = paddingTopOfParents()
        val bannerInsets = windowInsets.withoutTop(appliedByParents)
        val bannerTopInset = bannerInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top
        val contentInsets = if (isBannerShown) {
            windowInsets.withoutTop(bannerTopInset)
        } else {
            windowInsets
        }
        return bannerInsets.toWindowInsets()!! to contentInsets.toWindowInsets()!!
    }

    private fun paddingTopOfParents(): Int {
        var padding = 0
        var parent = parent
        while (parent is View) {
            padding += parent.paddingTop
            parent = parent.parent
        }
        return padding
    }

    private fun WindowInsetsCompat.withoutTop(top: Int): WindowInsetsCompat {
        if (top == 0) return this
        val builder = WindowInsetsCompat.Builder(this)
        for (type in TOP_INSET_TYPES) {
            val typeInsets = getInsets(type)
            builder.setInsets(
                type,
                Insets.of(typeInsets.left, (typeInsets.top - top).coerceAtLeast(0), typeInsets.right, typeInsets.bottom)
            )
        }
        return builder.build()
    }

    companion object {
        private val TOP_INSET_TYPES = listOf(
            WindowInsetsCompat.Type.statusBars(),
            WindowInsetsCompat.Type.displayCutout()
        )
    }
}

private const val UNREACHABLE_DELAY_MILLIS = 2000L

/**
 * Passes [ServerConnectionStatus.SERVER_UNREACHABLE] on only once it lasted [UNREACHABLE_DELAY_MILLIS], so the banner
 * doesn't flash while a single failed request, e.g. a gateway error of a proxy, is corrected by a connection check.
 */
@OptIn(FlowPreview::class)
internal fun Flow<ServerConnectionStatus>.withUnreachableDelayed(): Flow<ServerConnectionStatus> =
    debounce { status -> if (status == ServerConnectionStatus.SERVER_UNREACHABLE) UNREACHABLE_DELAY_MILLIS else 0L }
        .distinctUntilChanged()
