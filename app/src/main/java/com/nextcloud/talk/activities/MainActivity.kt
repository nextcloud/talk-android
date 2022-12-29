/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Andy Scherzinger
 * Copyright (C) 2021 Andy Scherzinger (infoi@andy-scherzinger.de)
 * Copyright (C) 2017 Mario Danic (mario@lovelyhq.com)
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
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.ContactsContract
import android.text.TextUtils
import android.util.Log
import autodagger.AutoInjector
import com.bluelinelabs.conductor.Conductor
import com.bluelinelabs.conductor.Router
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.bluelinelabs.conductor.changehandler.VerticalChangeHandler
import com.google.android.material.snackbar.Snackbar
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.R
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.controllers.ConversationsListController
import com.nextcloud.talk.controllers.LockedController
import com.nextcloud.talk.controllers.ServerSelectionController
import com.nextcloud.talk.controllers.SettingsController
import com.nextcloud.talk.controllers.WebViewLoginController
import com.nextcloud.talk.controllers.base.providers.ActionBarProvider
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.ActivityMainBinding
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.SecurityUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ACTIVE_CONVERSATION
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_USER_ENTITY
import com.nextcloud.talk.utils.remapchat.ConductorRemapping.remapChatController
import io.reactivex.Observer
import io.reactivex.SingleObserver
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.parceler.Parcels
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class MainActivity : BaseActivity(), ActionBarProvider {
    lateinit var binding: ActivityMainBinding

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var userManager: UserManager

    public var router: Router? = null

    @Suppress("Detekt.TooGenericExceptionCaught")
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate: Activity: " + System.identityHashCode(this).toString())

        super.onCreate(savedInstanceState)
        // Set the default theme to replace the launch screen theme.
        setTheme(R.style.AppTheme)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        setSupportActionBar(binding.toolbar)

        router = Conductor.attachRouter(this, binding.controllerContainer, savedInstanceState)

        if (intent.hasExtra(BundleKeys.KEY_FROM_NOTIFICATION_START_CALL)) {
            onNewIntent(intent)
        } else if (!router!!.hasRootController()) {
            if (!appPreferences.isDbRoomMigrated) {
                appPreferences.isDbRoomMigrated = true
            }

            userManager.users.subscribe(object : SingleObserver<List<User>> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                override fun onSuccess(users: List<User>) {
                    if (users.isNotEmpty()) {
                        runOnUiThread {
                            setDefaultRootController()
                        }
                    } else {
                        runOnUiThread {
                            launchLoginScreen()
                        }
                    }
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, "Error loading existing users", e)
                }
            })
        }
    }

    private fun launchLoginScreen() {
        if (!TextUtils.isEmpty(resources.getString(R.string.weblogin_url))) {
            router!!.pushController(
                RouterTransaction.with(
                    WebViewLoginController(resources.getString(R.string.weblogin_url), false)
                )
                    .pushChangeHandler(HorizontalChangeHandler())
                    .popChangeHandler(HorizontalChangeHandler())
            )
        } else {
            router!!.setRoot(
                RouterTransaction.with(ServerSelectionController())
                    .pushChangeHandler(HorizontalChangeHandler())
                    .popChangeHandler(HorizontalChangeHandler())
            )
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart: Activity: " + System.identityHashCode(this).toString())
        logRouterBackStack(router!!)
        lockScreenIfConditionsApply()
    }

    override fun onResume() {
        Log.d(TAG, "onResume: Activity: " + System.identityHashCode(this).toString())
        super.onResume()
    }

    override fun onPause() {
        Log.d(TAG, "onPause: Activity: " + System.identityHashCode(this).toString())
        super.onPause()
    }

    override fun onStop() {
        Log.d(TAG, "onStop: Activity: " + System.identityHashCode(this).toString())
        super.onStop()
    }

    private fun setDefaultRootController() {
        router!!.setRoot(
            RouterTransaction.with(ConversationsListController(Bundle()))
                .pushChangeHandler(HorizontalChangeHandler())
                .popChangeHandler(HorizontalChangeHandler())
                .tag("ConversationListController")
        )
    }

    fun resetConversationsList() {
        userManager.users.subscribe(object : SingleObserver<List<User>> {
            override fun onSubscribe(d: Disposable) {
                // unused atm
            }

            override fun onSuccess(users: List<User>) {
                if (users.isNotEmpty()) {
                    runOnUiThread {
                        setDefaultRootController()
                    }
                }
            }

            override fun onError(e: Throwable) {
                Log.e(TAG, "Error loading existing users", e)
            }
        })
    }

    fun openSettings() {
        router!!.pushController(
            RouterTransaction.with(SettingsController())
                .pushChangeHandler(HorizontalChangeHandler())
                .popChangeHandler(HorizontalChangeHandler())
        )
    }

    fun addAccount() {
        router!!.pushController(
            RouterTransaction.with(ServerSelectionController())
                .pushChangeHandler(VerticalChangeHandler())
                .popChangeHandler(VerticalChangeHandler())
        )
    }

    private fun handleActionFromContact(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {

            val cursor = contentResolver.query(intent.data!!, null, null, null, null)

            var userId = ""
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    // userId @ server
                    userId = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Data.DATA1))
                }

                cursor.close()
            }

            when (intent.type) {
                "vnd.android.cursor.item/vnd.com.nextcloud.talk2.chat" -> {
                    val user = userId.substringBeforeLast("@")
                    val baseUrl = userId.substringAfterLast("@")

                    if (userManager.currentUser.blockingGet()?.baseUrl?.endsWith(baseUrl) == true) {
                        startConversation(user)
                    } else {
                        Snackbar.make(
                            binding.controllerContainer,
                            R.string.nc_phone_book_integration_account_not_found,
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun startConversation(userId: String) {
        val roomType = "1"

        val currentUser = userManager.currentUser.blockingGet()

        val apiVersion = ApiUtils.getConversationApiVersion(currentUser, intArrayOf(ApiUtils.APIv4, 1))
        val credentials = ApiUtils.getCredentials(currentUser?.username, currentUser?.token)
        val retrofitBucket = ApiUtils.getRetrofitBucketForCreateRoom(
            apiVersion, currentUser?.baseUrl, roomType,
            null, userId, null
        )

        ncApi.createRoom(
            credentials,
            retrofitBucket.url, retrofitBucket.queryMap
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<RoomOverall> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                override fun onNext(roomOverall: RoomOverall) {
                    val bundle = Bundle()
                    bundle.putParcelable(KEY_USER_ENTITY, currentUser)
                    bundle.putString(KEY_ROOM_TOKEN, roomOverall.ocs!!.data!!.token)
                    bundle.putString(KEY_ROOM_ID, roomOverall.ocs!!.data!!.roomId)

                    // FIXME once APIv2 or later is used only, the createRoom already returns all the data
                    ncApi.getRoom(
                        credentials,
                        ApiUtils.getUrlForRoom(
                            apiVersion,
                            currentUser?.baseUrl,
                            roomOverall.ocs!!.data!!.token
                        )
                    )
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(object : Observer<RoomOverall> {
                            override fun onSubscribe(d: Disposable) {
                                // unused atm
                            }

                            override fun onNext(roomOverall: RoomOverall) {
                                bundle.putParcelable(
                                    KEY_ACTIVE_CONVERSATION,
                                    Parcels.wrap(roomOverall.ocs!!.data)
                                )
                                remapChatController(
                                    router!!, currentUser!!.id!!,
                                    roomOverall.ocs!!.data!!.token!!, bundle, true
                                )
                            }

                            override fun onError(e: Throwable) {
                                // unused atm
                            }

                            override fun onComplete() {
                                // unused atm
                            }
                        })
                }

                override fun onError(e: Throwable) {
                    // unused atm
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    fun lockScreenIfConditionsApply() {
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
                    logRouterBackStack(router!!)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent Activity: " + System.identityHashCode(this).toString())
        handleActionFromContact(intent)
        if (intent.hasExtra(BundleKeys.KEY_FROM_NOTIFICATION_START_CALL)) {
            if (intent.getBooleanExtra(BundleKeys.KEY_FROM_NOTIFICATION_START_CALL, false)) {
                if (!router!!.hasRootController()) {
                    setDefaultRootController()
                }
                val callNotificationIntent = Intent(this, CallNotificationActivity::class.java)
                intent.extras?.let { callNotificationIntent.putExtras(it) }
                startActivity(callNotificationIntent)
            } else {
                logRouterBackStack(router!!)
                remapChatController(
                    router!!,
                    intent.getParcelableExtra<User>(KEY_USER_ENTITY)!!.id!!,
                    intent.getStringExtra(KEY_ROOM_TOKEN)!!,
                    intent.extras!!,
                    true,
                    true
                )
                logRouterBackStack(router!!)
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

    private fun logRouterBackStack(router: Router) {
        if (BuildConfig.DEBUG) {
            val backstack = router.backstack
            var routerTransaction: RouterTransaction?
            Log.d(TAG, "   backstack size: " + router.backstackSize)
            for (i in 0 until router.backstackSize) {
                routerTransaction = backstack[i]
                Log.d(TAG, "     controller: " + routerTransaction.controller)
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
