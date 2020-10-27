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
import android.text.TextUtils
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import autodagger.AutoInjector
import butterknife.BindView
import butterknife.ButterKnife
import com.bluelinelabs.conductor.Conductor
import com.bluelinelabs.conductor.Router
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.bluelinelabs.conductor.changehandler.VerticalChangeHandler
import com.google.android.material.appbar.MaterialToolbar
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.controllers.*
import com.nextcloud.talk.controllers.base.providers.ActionBarProvider
import com.nextcloud.talk.utils.ConductorRemapping
import com.nextcloud.talk.utils.SecurityUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.database.user.UserUtils
import io.requery.Persistable
import io.requery.android.sqlcipher.SqlCipherDatabaseSource
import io.requery.reactivex.ReactiveEntityStore
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class MainActivity : BaseActivity(), ActionBarProvider {

    @BindView(R.id.toolbar)
    lateinit var toolbar: MaterialToolbar
    @BindView(R.id.controller_container)
    lateinit var container: ViewGroup

    @Inject
    lateinit var userUtils: UserUtils
    @Inject
    lateinit var dataStore: ReactiveEntityStore<Persistable>
    @Inject
    lateinit var sqlCipherDatabaseSource: SqlCipherDatabaseSource

    private var router: Router? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        ButterKnife.bind(this)

        setSupportActionBar(toolbar)

        router = Conductor.attachRouter(this, container, savedInstanceState)

        var hasDb = true

        try {
            sqlCipherDatabaseSource.writableDatabase
        } catch (exception: Exception) {
            hasDb = false
        }

        if (intent.hasExtra(BundleKeys.KEY_FROM_NOTIFICATION_START_CALL)) {
            if (!router!!.hasRootController()) {
                router!!.setRoot(RouterTransaction.with(ConversationsListController())
                        .pushChangeHandler(HorizontalChangeHandler())
                        .popChangeHandler(HorizontalChangeHandler()))
            }
            onNewIntent(intent)
        } else if (!router!!.hasRootController()) {
            if (hasDb) {
                if (userUtils.anyUserExists()) {
                    router!!.setRoot(RouterTransaction.with(ConversationsListController())
                            .pushChangeHandler(HorizontalChangeHandler())
                            .popChangeHandler(HorizontalChangeHandler()))
                } else {
                    if (!TextUtils.isEmpty(resources.getString(R.string.weblogin_url))) {
                        router!!.pushController(RouterTransaction.with(
                                WebViewLoginController(resources.getString(R.string.weblogin_url), false))
                                .pushChangeHandler(HorizontalChangeHandler())
                                .popChangeHandler(HorizontalChangeHandler()))
                    } else {
                        router!!.setRoot(RouterTransaction.with(ServerSelectionController())
                                .pushChangeHandler(HorizontalChangeHandler())
                                .popChangeHandler(HorizontalChangeHandler()))
                    }
                }
            } else {
                if (!TextUtils.isEmpty(resources.getString(R.string.weblogin_url))) {
                    router!!.pushController(RouterTransaction.with(
                            WebViewLoginController(resources.getString(R.string.weblogin_url), false))
                            .pushChangeHandler(HorizontalChangeHandler())
                            .popChangeHandler(HorizontalChangeHandler()))
                } else {
                    router!!.setRoot(RouterTransaction.with(ServerSelectionController())
                            .pushChangeHandler(HorizontalChangeHandler())
                            .popChangeHandler(HorizontalChangeHandler()))
                }
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
                    router!!.pushController(RouterTransaction.with(LockedController())
                            .pushChangeHandler(VerticalChangeHandler())
                            .popChangeHandler(VerticalChangeHandler())
                            .tag(LockedController.TAG))
                }
            }
        }
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (intent.hasExtra(BundleKeys.KEY_FROM_NOTIFICATION_START_CALL)) {
            if (intent.getBooleanExtra(BundleKeys.KEY_FROM_NOTIFICATION_START_CALL, false)) {
                router!!.pushController(RouterTransaction.with(CallNotificationController(intent.extras))
                        .pushChangeHandler(HorizontalChangeHandler())
                        .popChangeHandler(HorizontalChangeHandler()))
            } else {
                ConductorRemapping.remapChatController(router!!, intent.getLongExtra(BundleKeys.KEY_INTERNAL_USER_ID, -1),
                        intent.getStringExtra(BundleKeys.KEY_ROOM_TOKEN), intent.extras!!, false)
            }
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

    companion object {
        private val TAG = "MainActivity"
    }
}
