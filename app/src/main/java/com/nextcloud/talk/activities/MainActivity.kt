/*
 *
 *   Nextcloud Talk application
 *
 *   @author Mario Danic
 *   Copyright (C) 2017 Mario Danic (mario@lovelyhq.com)
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.nextcloud.talk.activities

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import butterknife.BindView
import butterknife.ButterKnife
import butterknife.OnClick
import com.bluelinelabs.conductor.Conductor
import com.bluelinelabs.conductor.Router
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.bluelinelabs.conductor.changehandler.VerticalChangeHandler
import com.google.android.material.appbar.MaterialToolbar
import com.nextcloud.talk.R
import com.nextcloud.talk.controllers.CallNotificationController
import com.nextcloud.talk.controllers.LockedController
import com.nextcloud.talk.controllers.base.BaseController
import com.nextcloud.talk.controllers.base.providers.ActionBarProvider
import com.nextcloud.talk.newarch.domain.repository.offline.UsersRepository
import com.nextcloud.talk.newarch.features.account.serverentry.ServerEntryView
import com.nextcloud.talk.newarch.features.contactsflow.contacts.ContactsView
import com.nextcloud.talk.newarch.features.conversationsList.ConversationsListView
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.utils.ConductorRemapping
import com.nextcloud.talk.utils.SecurityUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

class MainActivity : BaseActivity(), ActionBarProvider {

    @BindView(R.id.toolbar)
    lateinit var toolbar: MaterialToolbar

    @BindView(R.id.controller_container)
    lateinit var container: ViewGroup

    val usersRepository: UsersRepository by inject()

    private var router: Router? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ButterKnife.bind(this)
        setSupportActionBar(toolbar)

        router = Conductor.attachRouter(this, container, savedInstanceState)

        if (router?.hasRootController() == false) {
            GlobalScope.launch {
                if (usersRepository.getUsers().count() > 0) {
                    runOnUiThread {
                        router!!.setRoot(
                                RouterTransaction.with(ConversationsListView())
                                        .pushChangeHandler(HorizontalChangeHandler())
                                        .popChangeHandler(HorizontalChangeHandler())
                        )

                        onNewIntent(intent)
                    }
                } else {
                    runOnUiThread {
                        router!!.setRoot(
                                RouterTransaction.with(ServerEntryView())
                                        .pushChangeHandler(HorizontalChangeHandler())
                                        .popChangeHandler(HorizontalChangeHandler())
                        )
                    }
                }
            }
        }
    }

    @OnClick(R.id.floatingActionButton)
    fun onFloatingActionButtonClick() {
        val backstack = router?.backstack
        backstack?.let {
            if (it.size > 0) {
                val currentController = it[it.size - 1].controller() as BaseController
                currentController.onFloatingActionButtonClick()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkIfWeAreSecure()
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    fun checkIfWeAreSecure() {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (keyguardManager.isKeyguardSecure && appPreferences.isScreenLocked) {
            if (!SecurityUtils.checkIfWeAreAuthenticated(appPreferences.screenLockTimeout)) {
                if (router != null && router!!.getControllerWithTag(LockedController.TAG) == null) {
                    router!!.pushController(
                            RouterTransaction.with(LockedController())
                                    .pushChangeHandler(VerticalChangeHandler())
                                    .popChangeHandler(VerticalChangeHandler())
                                    .tag(LockedController.TAG)
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (intent.action == BundleKeys.KEY_NEW_CONVERSATION) {
            openNewConversationScreen()
        } else if (intent.action == BundleKeys.KEY_OPEN_CONVERSATION) {
            GlobalScope.launch {
                val user: UserNgEntity? = usersRepository.getUserWithId(intent.getLongExtra(BundleKeys.KEY_INTERNAL_USER_ID, -1))
                user?.let {
                    // due to complications with persistablebundle not supporting complex types we do this magic
                    // remove this once we rewrite chat magic
                    val extras = intent.extras!!
                    extras.putParcelable(BundleKeys.KEY_USER_ENTITY, it)
                    withContext(Dispatchers.Main) {
                        ConductorRemapping.remapChatController(
                                router!!, it.id,
                                intent.getStringExtra(BundleKeys.KEY_CONVERSATION_TOKEN)!!, extras, false)
                    }
                }
            }
        } else if (intent.action == BundleKeys.KEY_OPEN_INCOMING_CALL) {
            router?.pushController(
                    RouterTransaction.with(CallNotificationController(intent.extras!!))
                            .pushChangeHandler(HorizontalChangeHandler())
                            .popChangeHandler(HorizontalChangeHandler())
            )
        }
    }

    override fun onBackPressed() {
        if (router!!.getControllerWithTag(LockedController.TAG) != null) {
            return
        }

        if (!router!!.handleBack()) {
            super.onBackPressed()
        }
    }

    private fun openNewConversationScreen() {
        router?.pushController(
                RouterTransaction.with(ContactsView())
                        .pushChangeHandler(HorizontalChangeHandler())
                        .popChangeHandler(HorizontalChangeHandler())
        )
    }

    companion object {
        private val TAG = "MainActivity"
    }
}
