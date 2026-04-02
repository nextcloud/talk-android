/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.camera

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class BlurBackgroundViewModel : ViewModel() {

    sealed interface ViewState

    object BackgroundBlurOn : ViewState
    object BackgroundBlurOff : ViewState

    private val _viewState: MutableLiveData<ViewState> = MutableLiveData(BackgroundBlurOff)
    val viewState: LiveData<ViewState>
        get() = _viewState

    fun toggleBackgroundBlur() {
        val isOn = _viewState.value == BackgroundBlurOn

        if (isOn) {
            _viewState.value = BackgroundBlurOff
        } else {
            _viewState.value = BackgroundBlurOn
        }
    }

    fun turnOffBlur() {
        _viewState.value = BackgroundBlurOff
    }
}
