/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.passwordpolicy

import com.nextcloud.talk.models.json.passwordResult.PasswordResult

/**
 * Outcome of checking a password against the server's password policy.
 */
sealed interface PasswordValidationState {
    data object None : PasswordValidationState

    /** The server advertises no password policy, so there is nothing to check against. */
    data object NoPolicy : PasswordValidationState
    data class Success(val result: PasswordResult) : PasswordValidationState
    data class Error(val message: String) : PasswordValidationState
}

/**
 * Whether the server accepted the password that was last validated.
 */
val PasswordValidationState.isPasswordAccepted: Boolean
    get() = when (this) {
        is PasswordValidationState.Success -> result.passed == true
        PasswordValidationState.NoPolicy -> true
        PasswordValidationState.None, is PasswordValidationState.Error -> false
    }
