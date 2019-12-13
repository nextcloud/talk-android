/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
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
package com.nextcloud.talk.controllers

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.PromptInfo
import androidx.fragment.app.FragmentActivity
import butterknife.OnClick
import com.nextcloud.talk.R
import com.nextcloud.talk.controllers.base.BaseController
import com.nextcloud.talk.utils.SecurityUtils
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class LockedController : BaseController() {
    override fun inflateView(inflater: LayoutInflater, container: ViewGroup): View {
        return inflater.inflate(R.layout.controller_locked, container, false)
    }

    override fun onViewBound(view: View) {
        super.onViewBound(view)
        if (actionBar != null) {
            actionBar!!.hide()
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    override fun onAttach(view: View) {
        super.onAttach(view)
        checkIfWeAreSecure()
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @OnClick(R.id.unlockTextView)
    fun unlock() {
        checkIfWeAreSecure()
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private fun showBiometricDialog() {
        val context: Context? = activity
        if (context != null) {
            val promptInfo = PromptInfo.Builder()
                    .setTitle(String.format(context.getString(R.string.nc_biometric_unlock),
                            context.getString(R.string.nc_app_name)))
                    .setNegativeButtonText(context.getString(R.string.nc_cancel))
                    .build()
            val executor: Executor = Executors.newSingleThreadExecutor()
            val biometricPrompt = BiometricPrompt((context as FragmentActivity?)!!, executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(
                                result: BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            Log.d(TAG, "Fingerprint recognised successfully")
                            Handler(Looper.getMainLooper()).post { router.popCurrentController() }
                        }

                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                            Log.d(TAG, "Fingerprint not recognised")
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            super.onAuthenticationError(errorCode, errString)
                            showAuthenticationScreen()
                        }
                    }
            )
            val cryptoObject = SecurityUtils.getCryptoObject()
            if (cryptoObject != null) {
                biometricPrompt.authenticate(promptInfo, cryptoObject)
            } else {
                biometricPrompt.authenticate(promptInfo)
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private fun checkIfWeAreSecure() {
        if (activity != null) {
            val keyguardManager = activity!!.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (keyguardManager != null && keyguardManager.isKeyguardSecure
                    && appPreferences.isScreenLocked) {
                if (!SecurityUtils.checkIfWeAreAuthenticated(appPreferences.screenLockTimeout)) {
                    showBiometricDialog()
                } else {
                    router.popCurrentController()
                }
            }
        }
    }

    private fun showAuthenticationScreen() {
        if (activity != null) {
            val keyguardManager = activity!!.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            val intent = keyguardManager.createConfirmDeviceCredentialIntent(null, null)
            intent?.let { startActivityForResult(it, REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS) }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS) {
            if (resultCode == Activity.RESULT_OK) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (SecurityUtils.checkIfWeAreAuthenticated(appPreferences.screenLockTimeout)) {
                        Log.d(TAG, "All went well, dismiss locked controller")
                        router.popCurrentController()
                    }
                }
            } else {
                Log.d(TAG, "Authorization failed")
            }
        }
    }

    companion object {
        const val TAG = "LockedController"
        private const val REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS = 112
    }
}