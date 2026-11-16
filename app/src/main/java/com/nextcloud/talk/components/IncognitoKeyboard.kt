/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.components

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputMethodRequest

/**
 * Asks the keyboard not to learn from what is typed into the text fields of [content] while [enabled], like
 * `BaseActivity` does for the text fields of views.
 */
@Composable
fun IncognitoKeyboard(enabled: Boolean, content: @Composable () -> Unit) {
    InterceptPlatformTextInput(
        interceptor = { request, nextHandler ->
            // a changed flag passes a new interceptor, which restarts an active session with it
            if (enabled) {
                nextHandler.startInputMethod(
                    object : PlatformTextInputMethodRequest {
                        override fun createInputConnection(outAttributes: EditorInfo): InputConnection {
                            val inputConnection = request.createInputConnection(outAttributes)
                            outAttributes.imeOptions =
                                outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                            return inputConnection
                        }
                    }
                )
            } else {
                nextHandler.startInputMethod(request)
            }
        },
        content = content
    )
}
