/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Andy Scherzinger
 * Copyright (C) 2021 Andy Scherzinger <info@andy-scherzinger.de>
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
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.PromptInfo
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.FragmentActivity
import autodagger.AutoInjector
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.controllers.base.BaseController
import com.nextcloud.talk.controllers.util.viewBinding
import com.nextcloud.talk.databinding.ControllerLockedBinding
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.SecurityUtils
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@AutoInjector(NextcloudTalkApplication::class)
class LockedController : BaseController(R.layout.controller_locked) {
    private val binding: ControllerLockedBinding? by viewBinding(ControllerLockedBinding::bind)

    override val appBarLayoutType: AppBarLayoutType
        get() = AppBarLayoutType.EMPTY

    companion object {
        const val TAG = "LockedController"
        private const val REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS = 112
    }

    override fun onViewBound(view: View) {
        super.onViewBound(view)
        sharedApplication!!.componentApplication.inject(this)
        binding?.unlockContainer?.setOnClickListener {
            unlock()
        }
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        Log.d(TAG, "onAttach")
        if (activity != null && resources != null) {
            DisplayUtils.applyColorToStatusBar(
                activity,
                ResourcesCompat.getColor(resources!!, R.color.colorPrimary, null)
            )
            DisplayUtils.applyColorToNavigationBar(
                activity!!.window,
                ResourcesCompat.getColor(resources!!, R.color.colorPrimary, null)
            )
        }
        checkIfWeAreSecure()
    }

    override fun onDetach(view: View) {
        super.onDetach(view)
        Log.d(TAG, "onDetach")
    }

    fun unlock() {
        checkIfWeAreSecure()
    }

    private fun showBiometricDialog() {
        val context: Context? = activity
        if (context != null) {
            val promptInfo = PromptInfo.Builder()
                .setTitle(
                    String.format(
                        context.getString(R.string.nc_biometric_unlock),
                        context.getString(R.string.nc_app_product_name)
                    )
                )
                .setNegativeButtonText(context.getString(R.string.nc_cancel))
                .build()
            val executor: Executor = Executors.newSingleThreadExecutor()
            val biometricPrompt = BiometricPrompt(
                (context as FragmentActivity?)!!, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
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

    private fun checkIfWeAreSecure() {
        val keyguardManager = activity?.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager?
        if (keyguardManager?.isKeyguardSecure == true && appPreferences.isScreenLocked) {
            if (!SecurityUtils.checkIfWeAreAuthenticated(appPreferences.screenLockTimeout)) {
                Log.d(TAG, "showBiometricDialog because 'we are NOT authenticated'...")
                showBiometricDialog()
            } else {
                Log.d(
                    TAG,
                    "popCurrentController because 'we are authenticated'. backstacksize= " +
                        router.backstack.size
                )
                router.popToRoot()
            }
        }
    }

    private fun showAuthenticationScreen() {
        Log.d(TAG, "showAuthenticationScreen")
        val keyguardManager = activity?.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager?
        val intent = keyguardManager?.createConfirmDeviceCredentialIntent(null, null)
        if (intent != null) {
            startActivityForResult(intent, REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS) {
            if (resultCode == Activity.RESULT_OK) {
                if (
                    SecurityUtils.checkIfWeAreAuthenticated(appPreferences.screenLockTimeout)
                ) {
                    Log.d(TAG, "All went well, dismiss locked controller")
                    router.popCurrentController()
                }
            } else {
                Log.d(TAG, "Authorization failed")
            }
        }
    }
}
