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
import android.os.Handler
import android.provider.ContactsContract
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
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textview.MaterialTextView
import com.nextcloud.talk.R
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.controllers.CallNotificationController
import com.nextcloud.talk.controllers.ConversationsListController
import com.nextcloud.talk.controllers.LockedController
import com.nextcloud.talk.controllers.ServerSelectionController
import com.nextcloud.talk.controllers.WebViewLoginController
import com.nextcloud.talk.controllers.base.providers.ActionBarProvider
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.ConductorRemapping
import com.nextcloud.talk.utils.ConductorRemapping.remapChatController
import com.nextcloud.talk.utils.SecurityUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ACTIVE_CONVERSATION
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_USER_ENTITY
import com.nextcloud.talk.utils.database.user.UserUtils
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.requery.Persistable
import io.requery.android.sqlcipher.SqlCipherDatabaseSource
import io.requery.reactivex.ReactiveEntityStore
import org.parceler.Parcels
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class MainActivity : BaseActivity(), ActionBarProvider {

    @BindView(R.id.appBar)
    lateinit var appBar: AppBarLayout
    @BindView(R.id.toolbar)
    lateinit var toolbar: MaterialToolbar
    @BindView(R.id.home_toolbar)
    lateinit var searchCardView: MaterialCardView
    @BindView(R.id.search_text)
    lateinit var searchInputText: MaterialTextView
    @BindView(R.id.switch_account_button)
    lateinit var settingsButton: MaterialButton
    @BindView(R.id.controller_container)
    lateinit var container: ViewGroup

    @Inject
    lateinit var userUtils: UserUtils
    @Inject
    lateinit var dataStore: ReactiveEntityStore<Persistable>
    @Inject
    lateinit var sqlCipherDatabaseSource: SqlCipherDatabaseSource
    @Inject
    lateinit var ncApi: NcApi

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
        
        handleActionFromContact(intent)
    }

    private fun handleActionFromContact(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {

            val cursor = contentResolver.query(intent.data!!, null, null, null, null)

            var userId = ""
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    // userId @ server
                    userId = cursor.getString(cursor.getColumnIndex(ContactsContract.Data.DATA1))
                }
                
                cursor.close()
            }

            when (intent.type) {
                "vnd.android.cursor.item/vnd.com.nextcloud.talk2.chat" -> {
                    val user = userId.substringBeforeLast("@")
                    val baseUrl = userId.substringAfterLast("@")
                    if (userUtils.currentUser?.baseUrl?.endsWith(baseUrl) == true) {
                        startConversation(user)
                    } else {
                        Snackbar.make(container, R.string.nc_phone_book_integration_account_not_found, Snackbar
                                .LENGTH_LONG).show()
                    }
                }
            }
        }
    }
    
    private fun startConversation(userId: String) {
        val roomType = "1"
        val currentUser = userUtils.currentUser ?: return

        val credentials = ApiUtils.getCredentials(currentUser.username, currentUser.token)
        val retrofitBucket = ApiUtils.getRetrofitBucketForCreateRoom(currentUser.baseUrl, roomType,
                userId, null)
        ncApi.createRoom(credentials,
                retrofitBucket.url, retrofitBucket.queryMap)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object : Observer<RoomOverall> {
                    override fun onSubscribe(d: Disposable) {}
                    override fun onNext(roomOverall: RoomOverall) {
                        val conversationIntent = Intent(context, MagicCallActivity::class.java)
                        val bundle = Bundle()
                        bundle.putParcelable(KEY_USER_ENTITY, currentUser)
                        bundle.putString(KEY_ROOM_TOKEN, roomOverall.ocs.data.token)
                        bundle.putString(KEY_ROOM_ID, roomOverall.ocs.data.roomId)
                        if (currentUser.hasSpreedFeatureCapability("chat-v2")) {
                            ncApi.getRoom(credentials,
                                    ApiUtils.getRoom(currentUser.baseUrl,
                                            roomOverall.ocs.data.token))
                                    .subscribeOn(Schedulers.io())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe(object : Observer<RoomOverall> {
                                        override fun onSubscribe(d: Disposable) {}
                                        override fun onNext(roomOverall: RoomOverall) {
                                            bundle.putParcelable(KEY_ACTIVE_CONVERSATION,
                                                    Parcels.wrap(roomOverall.ocs.data))
                                            remapChatController(router!!, currentUser.id,
                                                    roomOverall.ocs.data.token, bundle, true)
                                        }

                                        override fun onError(e: Throwable) {}
                                        override fun onComplete() {}
                                    })
                        } else {
                            conversationIntent.putExtras(bundle)
                            startActivity(conversationIntent)
                            Handler().postDelayed({
                                if (!isDestroyed) {
                                    router!!.popCurrentController()
                                }
                            }, 100)
                        }
                    }

                    override fun onError(e: Throwable) {}
                    override fun onComplete() {}
                })
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

        handleActionFromContact(intent)

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
