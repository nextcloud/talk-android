/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Andy Scherzinger
 * @author Marcel Hibbe
 * Copyright (C) 2023 Marcel Hibbe <dev@mhibbe.de>
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

package com.nextcloud.talk.lock

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import autodagger.AutoInjector
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.databinding.ActivityLockedBinding
import com.nextcloud.talk.utils.SecurityUtils
import com.nextcloud.talk.utils.preferences.AppPreferences
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class LockedActivity : AppCompatActivity() {

    @Inject
    lateinit var context: Context

    @Inject
    lateinit var appPreferences: AppPreferences

    private lateinit var binding: ActivityLockedBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        binding = ActivityLockedBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    override fun onResume() {
        super.onResume()

        binding.unlockContainer.setOnClickListener {
            checkIfWeAreSecure()
        }
        checkIfWeAreSecure()
    }

    private fun checkIfWeAreSecure() {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager?
        if (keyguardManager?.isKeyguardSecure == true && appPreferences.isScreenLocked) {
            if (!SecurityUtils.checkIfWeAreAuthenticated(appPreferences.screenLockTimeout)) {
                Log.d(TAG, "showBiometricDialog because 'we are NOT authenticated'...")
                showBiometricDialog()
            } else {
                finish()
            }
        }
    }

    private fun showBiometricDialog() {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
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
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Log.d(TAG, "Fingerprint recognised successfully")
                    finish()
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

    private fun showAuthenticationScreen() {
        Log.d(TAG, "showAuthenticationScreen")
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager?
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
                    finish()
                }
            } else {
                Log.d(TAG, "Authorization failed")
            }
        }
    }

    companion object {
        private val TAG = LockedActivity::class.java.simpleName
        private const val REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS = 112
    }
}
