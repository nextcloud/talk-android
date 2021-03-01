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

package com.nextcloud.talk.activities

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import autodagger.AutoInjector
import butterknife.BindView
import butterknife.ButterKnife
import com.bluelinelabs.conductor.Conductor
import com.bluelinelabs.conductor.Router
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.controllers.CallController
import com.nextcloud.talk.controllers.CallNotificationController
import com.nextcloud.talk.controllers.ChatController
import com.nextcloud.talk.events.ConfigurationChangeEvent
import com.nextcloud.talk.jobs.UploadAndShareFilesWorker
import com.nextcloud.talk.utils.bundle.BundleKeys

@AutoInjector(NextcloudTalkApplication::class)
class MagicCallActivity : BaseActivity() {

    private lateinit var chatController: ChatController

    @BindView(R.id.controller_container)
    lateinit var container: ViewGroup
    
    @BindView(R.id.chatControllerView)
    lateinit var chatContainer: ViewGroup

    private var router: Router? = null
    private var chatRouter: Router? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        window.decorView.systemUiVisibility = systemUiVisibility

        setContentView(R.layout.activity_magic_call)

        ButterKnife.bind(this)
        router = Conductor.attachRouter(this, container, savedInstanceState)
        router!!.setPopsLastView(false)

        if (!router!!.hasRootController()) {
            if (intent.getBooleanExtra(BundleKeys.KEY_FROM_NOTIFICATION_START_CALL, false)) {
                Log.d(TAG, "call CallNotificationController with BundleKeys.KEY_FROM_NOTIFICATION_START_CALL")

                router!!.setRoot(RouterTransaction.with(CallNotificationController(intent.extras))
                        .pushChangeHandler(HorizontalChangeHandler())
                        .popChangeHandler(HorizontalChangeHandler()))
            } else {
                Log.d(TAG, "call CallNotificationController")

                router!!.setRoot(RouterTransaction.with(CallController(intent.extras))
                        .pushChangeHandler(HorizontalChangeHandler())
                        .popChangeHandler(HorizontalChangeHandler()))
            }
        }

        val extras = intent.extras ?: Bundle()
        extras.putBoolean("showToggleChat", true)
        
        chatController = ChatController(extras)
        chatRouter = Conductor.attachRouter(this, chatContainer, savedInstanceState)
        chatRouter!!.setRoot(RouterTransaction.with(chatController)
                .pushChangeHandler(HorizontalChangeHandler())
                .popChangeHandler(HorizontalChangeHandler()))
    }
    
    fun showChat() {
        chatContainer.visibility = View.VISIBLE
        container.visibility = View.GONE
        chatController.wasDetached = false
        chatController.pullChatMessages(1)
    }

    fun showCall() {
        container.visibility = View.VISIBLE
        chatContainer.visibility = View.GONE
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
