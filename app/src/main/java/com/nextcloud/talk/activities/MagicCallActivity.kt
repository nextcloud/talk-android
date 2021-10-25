/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Andy Scherzinger
 * Copyright (C) 2021 Andy Scherzinger (infoi@andy-scherzinger.de)
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

package com.nextcloud.talk.activities

import android.app.KeyguardManager
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.view.Window
import android.view.WindowManager
import androidx.annotation.RequiresApi
import autodagger.AutoInjector
import com.bluelinelabs.conductor.Conductor
import com.bluelinelabs.conductor.Router
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.controllers.CallController
import com.nextcloud.talk.controllers.CallNotificationController
import com.nextcloud.talk.databinding.ActivityMagicCallBinding
import com.nextcloud.talk.events.ConfigurationChangeEvent
import com.nextcloud.talk.utils.bundle.BundleKeys

@AutoInjector(NextcloudTalkApplication::class)
class MagicCallActivity : BaseActivity() {
    lateinit var binding: ActivityMagicCallBinding
    private var router: Router? = null
    var isInPipMode: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        setTheme(R.style.CallTheme)

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        dismissKeyguard()
        window.addFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        binding = ActivityMagicCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        router = Conductor.attachRouter(this, binding.controllerContainer, savedInstanceState)
        router!!.setPopsLastView(false)

        if (!router!!.hasRootController()) {
            if (intent.getBooleanExtra(BundleKeys.KEY_FROM_NOTIFICATION_START_CALL, false)) {
                router!!.setRoot(
                    RouterTransaction.with(CallNotificationController(intent.extras))
                        .pushChangeHandler(HorizontalChangeHandler())
                        .popChangeHandler(HorizontalChangeHandler())
                )
            } else {
                router!!.setRoot(
                    RouterTransaction.with(CallController(intent.extras))
                        .pushChangeHandler(HorizontalChangeHandler())
                        .popChangeHandler(HorizontalChangeHandler())
                        .tag("CallController")
                )
            }
        }

    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun enterPipMode() {
        enableKeyguard()
        enterPictureInPictureMode(getPipParams())
    }

    override fun onUserLeaveHint() {
        enableKeyguard()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPictureInPictureMode(getPipParams())
        } else {
            finish()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getPipParams(): PictureInPictureParams {
        val pipRatio = Rational(
            300,
            500
        )
        return PictureInPictureParams.Builder()
            .setAspectRatio(pipRatio)
            .build()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        eventBus.post(ConfigurationChangeEvent())
    }

    private fun dismissKeyguard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    private fun enableKeyguard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(false)
        } else {
            window.clearFlags(
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode

        val callController = router?.getControllerWithTag("CallController") as CallController
        if (isInPictureInPictureMode) {
            callController.updateUiForPipMode()
        } else {
            callController.updateUiForNormalMode()
        }
    }

    override fun onStop() {
        super.onStop()
        if (isInPipMode) {
            finish()
        }
    }

    companion object {
        private val TAG = "MagicCallActivity"
    }
}
