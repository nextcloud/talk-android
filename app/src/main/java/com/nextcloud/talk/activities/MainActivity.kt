/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2023 Ezhil Shanmugham <ezhil56x.contact@gmail.com>
 * SPDX-FileCopyrightText: 2021 Andy Scherzinger <infoi@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.activities

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import android.provider.ContactsContract
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import autodagger.AutoInjector
import com.google.android.material.snackbar.Snackbar
import com.nextcloud.talk.R
import com.nextcloud.talk.account.BrowserLoginActivity
import com.nextcloud.talk.account.ServerSelectionActivity
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.ChatActivity
import com.nextcloud.talk.conversationlist.ConversationsListActivity
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.ActivityMainBinding
import com.nextcloud.talk.invitation.InvitationsActivity
import com.nextcloud.talk.lock.LockedActivity
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.ClosedInterfaceImpl
import com.nextcloud.talk.utils.SecurityUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import io.reactivex.Observer
import io.reactivex.SingleObserver
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class MainActivity :
    BaseActivity(),
    ActionBarProvider {

    lateinit var binding: ActivityMainBinding

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var userManager: UserManager

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            finish()
        }
    }

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

        handleIntent(intent)

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    fun lockScreenIfConditionsApply() {
        val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        if (keyguardManager.isKeyguardSecure && appPreferences.isScreenLocked) {
            if (!SecurityUtils.checkIfWeAreAuthenticated(appPreferences.screenLockTimeout)) {
                val lockIntent = Intent(context, LockedActivity::class.java)
                startActivity(lockIntent)
            }
        }
    }

    private fun launchServerSelection() {
        if (isBrandingUrlSet()) {
            val intent = Intent(context, BrowserLoginActivity::class.java)
            val bundle = Bundle()
            bundle.putString(BundleKeys.KEY_BASE_URL, resources.getString(R.string.weblogin_url))
            intent.putExtras(bundle)
            startActivity(intent)
        } else {
            val intent = Intent(context, ServerSelectionActivity::class.java)
            startActivity(intent)
        }
    }

    private fun isBrandingUrlSet() = !TextUtils.isEmpty(resources.getString(R.string.weblogin_url))

    override fun onStart() {
        Log.d(TAG, "onStart: Activity: " + System.identityHashCode(this).toString())
        super.onStart()
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

                    if (currentUserProvider.currentUser.blockingGet()?.baseUrl!!.endsWith(baseUrl) == true) {
                        startConversation(user)
                    } else {
                        Snackbar.make(
                            binding.root,
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

        val currentUser = currentUserProvider.currentUser.blockingGet()

        val apiVersion = ApiUtils.getConversationApiVersion(currentUser, intArrayOf(ApiUtils.API_V4, 1))
        val credentials = ApiUtils.getCredentials(currentUser?.username, currentUser?.token)
        val retrofitBucket = ApiUtils.getRetrofitBucketForCreateRoom(
            version = apiVersion,
            baseUrl = currentUser?.baseUrl!!,
            roomType = roomType,
            invite = userId
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
            if (intent.hasExtra(BundleKeys.KEY_REMOTE_TALK_SHARE)) {
                if (intent.getBooleanExtra(BundleKeys.KEY_REMOTE_TALK_SHARE, false)) {
                    val invitationsIntent = Intent(this, InvitationsActivity::class.java)
                    startActivity(invitationsIntent)
                }
            } else {
                val chatIntent = Intent(context, ChatActivity::class.java)
                chatIntent.putExtras(intent.extras!!)
                startActivity(chatIntent)
            }
        } else {
            userManager.users.subscribe(object : SingleObserver<List<User>> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                override fun onSuccess(users: List<User>) {
                    if (users.isNotEmpty()) {
                        ClosedInterfaceImpl().setUpPushTokenRegistration()
                        runOnUiThread {
                            openConversationList()
                        }
                    } else {
                        runOnUiThread {
                            launchServerSelection()
                        }
                    }
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, "Error loading existing users", e)
                    Toast.makeText(
                        context,
                        context.resources.getString(R.string.nc_common_error_sorry),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
        }
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
    }
}
