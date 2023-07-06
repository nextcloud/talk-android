/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Andy Scherzinger
 * @author Marcel Hibbe
 * @author Ezhil Shanmugham
 * Copyright (C) 2023 Marcel Hibbe <dev@mhibbe.de>
 * Copyright (C) 2021 Andy Scherzinger (infoi@andy-scherzinger.de)
 * Copyright (C) 2017 Mario Danic (mario@lovelyhq.com)
 * Copyright (C) 2023 Ezhil Shanmugham <ezhil56x.contact@gmail.com>
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
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
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
import com.nextcloud.talk.callnotification.CallNotificationActivity
import com.nextcloud.talk.chat.ChatActivity
import com.nextcloud.talk.controllers.ServerSelectionController
import com.nextcloud.talk.controllers.WebViewLoginController
import com.nextcloud.talk.controllers.base.providers.ActionBarProvider
import com.nextcloud.talk.conversationlist.ConversationsListActivity
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.ActivityMainBinding
import com.nextcloud.talk.lock.LockedActivity
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.SecurityUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.bundle.BundleKeys.ADD_ACCOUNT
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import io.reactivex.Observer
import io.reactivex.SingleObserver
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class MainActivity : BaseActivity(), ActionBarProvider {
    lateinit var binding: ActivityMainBinding

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var userManager: UserManager

    private var router: Router? = null

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (!router!!.handleBack()) {
                finish()
            }
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate: Activity: " + System.identityHashCode(this).toString())

        super.onCreate(savedInstanceState)

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                lockScreenIfConditionsApply()
            }
        })

        // Set the default theme to replace the launch screen theme.
        setTheme(R.style.AppTheme)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        setSupportActionBar(binding.toolbar)

        router = Conductor.attachRouter(this, binding.controllerContainer, savedInstanceState)

        handleIntent(intent)

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    fun lockScreenIfConditionsApply() {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (keyguardManager.isKeyguardSecure && appPreferences.isScreenLocked) {
            if (!SecurityUtils.checkIfWeAreAuthenticated(appPreferences.screenLockTimeout)) {
                val lockIntent = Intent(context, LockedActivity::class.java)
                startActivity(lockIntent)
            }
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
        Log.d(TAG, "onStart: Activity: " + System.identityHashCode(this).toString())
        super.onStart()
        logRouterBackStack(router!!)
    }

    override fun onResume() {
        Log.d(TAG, "onResume: Activity: " + System.identityHashCode(this).toString())
        super.onResume()

        if (appPreferences.isScreenLocked) {
            SecurityUtils.createKey(appPreferences.screenLockTimeout)
        }
    }

    override fun onPause() {
        Log.d(TAG, "onPause: Activity: " + System.identityHashCode(this).toString())
        super.onPause()
    }

    override fun onStop() {
        Log.d(TAG, "onStop: Activity: " + System.identityHashCode(this).toString())
        super.onStop()
    }

    private fun openConversationList() {
        val intent = Intent(this, ConversationsListActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.putExtras(Bundle())
        startActivity(intent)
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
            apiVersion,
            currentUser?.baseUrl,
            roomType,
            null,
            userId,
            null
        )

        ncApi.createRoom(
            credentials,
            retrofitBucket.url,
            retrofitBucket.queryMap
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<RoomOverall> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                override fun onNext(roomOverall: RoomOverall) {
                    val bundle = Bundle()
                    bundle.putString(KEY_ROOM_TOKEN, roomOverall.ocs!!.data!!.token)

                    val chatIntent = Intent(context, ChatActivity::class.java)
                    chatIntent.putExtras(bundle)
                    startActivity(chatIntent)
                }

                override fun onError(e: Throwable) {
                    // unused atm
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent Activity: " + System.identityHashCode(this).toString())
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        handleActionFromContact(intent)

        val internalUserId = intent.extras?.getLong(BundleKeys.KEY_INTERNAL_USER_ID)

        var user: User? = null
        if (internalUserId != null) {
            user = userManager.getUserWithId(internalUserId).blockingGet()
        }

        if (user != null && userManager.setUserAsActive(user).blockingGet()) {
            // this should be avoided (it's still from conductor architecture). activities should be opened directly.
            if (intent.hasExtra(BundleKeys.KEY_FROM_NOTIFICATION_START_CALL)) {
                if (intent.getBooleanExtra(BundleKeys.KEY_FROM_NOTIFICATION_START_CALL, false)) {
                    if (!router!!.hasRootController()) {
                        openConversationList()
                    }
                    val callNotificationIntent = Intent(this, CallNotificationActivity::class.java)
                    intent.extras?.let { callNotificationIntent.putExtras(it) }
                    startActivity(callNotificationIntent)
                } else {
                    logRouterBackStack(router!!)

                    val chatIntent = Intent(context, ChatActivity::class.java)
                    chatIntent.putExtras(intent.extras!!)
                    startActivity(chatIntent)

                    logRouterBackStack(router!!)
                }
            }
        } else if (intent.hasExtra(ADD_ACCOUNT) && intent.getBooleanExtra(ADD_ACCOUNT, false)) {
            addAccount()
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
                            openConversationList()
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
