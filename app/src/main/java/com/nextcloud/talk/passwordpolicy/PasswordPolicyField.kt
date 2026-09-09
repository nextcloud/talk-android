/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.passwordpolicy

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.nextcloud.talk.R

/**
 * A password input that reports what the server made of the password as it is typed.
 */
@Composable
fun PasswordPolicyField(
    password: String,
    onPasswordChange: (String) -> Unit,
    validationState: PasswordValidationState,
    label: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        modifier = modifier,
        label = { Text(text = label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        isError = validationState is PasswordValidationState.Error,
        supportingText = { PasswordPolicyFeedback(validationState) }
    )
}

@Composable
private fun PasswordPolicyFeedback(validationState: PasswordValidationState) {
    when (validationState) {
        is PasswordValidationState.Success -> Text(
            text = validationState.result.reason ?: stringResource(R.string.nc_password_secure),
            color = if (validationState.isPasswordAccepted) {
                colorResource(id = R.color.nc_darkGreen)
            } else {
                colorResource(id = R.color.nc_darkRed)
            },
            style = MaterialTheme.typography.bodySmall
        )

        is PasswordValidationState.Error -> Text(
            text = stringResource(R.string.nc_common_error_sorry),
            color = colorResource(id = R.color.nc_darkRed),
            style = MaterialTheme.typography.bodySmall
        )

        PasswordValidationState.None, PasswordValidationState.NoPolicy -> Unit
    }
}
