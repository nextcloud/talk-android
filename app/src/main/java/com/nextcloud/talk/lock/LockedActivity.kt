/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.lock

import android.app.Activity
import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.addCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import autodagger.AutoInjector
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.databinding.ActivityLockedBinding
import com.nextcloud.talk.utils.BrandingUtils
import com.nextcloud.talk.utils.SecurityUtils
import com.nextcloud.talk.utils.isShareTask
import com.nextcloud.talk.utils.preferences.AppPreferences
import com.nextcloud.talk.utils.taskIdCompat
import com.nextcloud.talk.utils.taskInfoOrNull
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class LockedActivity : AppCompatActivity() {

    @Inject
    lateinit var context: Context

    @Inject
    lateinit var appPreferences: AppPreferences

    private val startForCredentialsResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult
            ()
    ) {
        onConfirmDeviceCredentials(it)
    }

    private lateinit var binding: ActivityLockedBinding

    // set when the app is left from the screen lock of the device, so the resume that follows doesn't ask again
    private var isLeaving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        binding = ActivityLockedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onBackPressedDispatcher.addCallback(this) { leaveApp() }
    }

    /**
     * Leaves the app instead of closing the lock screen, which the still locked screen below would show again right
     * away. In the task of a share, also one of another app, only the screens of this app are left, which leaves the
     * share. In the task of another app, finishAffinity() would also finish the screens of that app below with the
     * same, empty task affinity.
     */
    private fun leaveApp() {
        val task = getSystemService(ActivityManager::class.java).appTasks
            .firstNotNullOfOrNull { it.taskInfoOrNull()?.takeIf { info -> info.taskIdCompat == taskId } }
        when {
            task == null -> NextcloudTalkApplication.sharedApplication!!.liveActivities.finishInTask(taskId)
            task.isShareTask -> finishAffinity()
            else -> moveTaskToBack(true)
        }
    }

    override fun onResume() {
        super.onResume()
        // unlocked meanwhile on the lock screen of another task, e.g. of a share
        if (!NextcloudTalkApplication.isLocked) {
            finish()
            return
        }
        hideLogoForBrandedClients()

        binding.unlockContainer.setOnClickListener {
            checkIfWeAreSecure()
        }
        if (!isLeaving) checkIfWeAreSecure()
    }

    override fun onStop() {
        super.onStop()
        isLeaving = false
    }

    private fun hideLogoForBrandedClients() {
        if (!BrandingUtils.isOriginalNextcloudClient(applicationContext)) {
            binding.appLogo.visibility = View.GONE
        }
    }

    private fun checkIfWeAreSecure() {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager?
        if (keyguardManager?.isKeyguardSecure == true && appPreferences.isScreenLocked) {
            // Prepare crypto object for biometric, then always require authentication since
            // this activity is only shown when the inactivity timeout has already elapsed.
            SecurityUtils.checkIfWeAreAuthenticated(appPreferences.screenLockTimeout)
            showBiometricDialog()
        } else {
            // e.g. the screen lock of the device was removed while the lock screen was shown, which then couldn't
            // be left
            unlock()
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
        val biometricPrompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Log.d(TAG, "Fingerprint recognised successfully")
                    unlock()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Log.d(TAG, "Fingerprint not recognised")
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // closed with Back or by the system: the lock screen stays, where Back leaves the app and a tap
                    // asks again
                    val isClosed = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    if (!isClosed) showAuthenticationScreen()
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
            startForCredentialsResult.launch(intent)
        }
    }

    private fun onConfirmDeviceCredentials(result: ActivityResult) {
        if (result.resultCode == Activity.RESULT_OK) {
            unlock()
        } else {
            // Left with Back, like the lock screen: shown again, it would ask again right away
            Log.d(TAG, "Authorization failed")
            isLeaving = true
            leaveApp()
        }
    }

    private fun unlock() {
        appPreferences.setLockTimestamp(System.currentTimeMillis())
        NextcloudTalkApplication.isLocked = false
        // e.g. set while the screen lock of the device was asked for, which would lock again with no timeout
        NextcloudTalkApplication.pendingLockCheck = false
        finish()
    }

    companion object {
        private val TAG = LockedActivity::class.java.simpleName
    }
}
