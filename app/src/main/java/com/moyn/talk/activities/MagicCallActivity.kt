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

package com.moyn.talk.activities

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowManager
import autodagger.AutoInjector
import com.bluelinelabs.conductor.Conductor
import com.bluelinelabs.conductor.Router
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.moyn.talk.R
import com.moyn.talk.application.NextcloudTalkApplication
import com.moyn.talk.controllers.CallController
import com.moyn.talk.controllers.CallNotificationController
import com.moyn.talk.controllers.ChatController
import com.moyn.talk.databinding.ActivityMagicCallBinding
import com.moyn.talk.events.ConfigurationChangeEvent
import com.moyn.talk.utils.bundle.BundleKeys

@AutoInjector(NextcloudTalkApplication::class)
class MagicCallActivity : BaseActivity() {
    lateinit var binding: ActivityMagicCallBinding

    private lateinit var chatController: ChatController

    private var router: Router? = null
    private var chatRouter: Router? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        setTheme(R.style.CallTheme)

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        window.decorView.systemUiVisibility = systemUiVisibility

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
                )
            }
        }

        val extras = intent.extras ?: Bundle()
        extras.putBoolean("showToggleChat", true)

        chatController = ChatController(extras)
        chatRouter = Conductor.attachRouter(this, binding.chatControllerView, savedInstanceState)
        chatRouter!!.setRoot(
            RouterTransaction.with(chatController)
                .pushChangeHandler(HorizontalChangeHandler())
                .popChangeHandler(HorizontalChangeHandler())
        )
    }

    fun showChat() {
        binding.chatControllerView.visibility = View.VISIBLE
        binding.controllerContainer.visibility = View.GONE
        chatController.wasDetached = false
        chatController.pullChatMessages(1)
    }

    fun showCall() {
        binding.controllerContainer.visibility = View.VISIBLE
        binding.chatControllerView.visibility = View.GONE
        chatController.wasDetached = true
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        eventBus.post(ConfigurationChangeEvent())
    }

    companion object {
        private val TAG = "MagicCallActivity"

        private val systemUiVisibility: Int
            get() {
                var flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN
                flags = flags or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                return flags
            }
    }
}
