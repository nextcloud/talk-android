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
import com.nextcloud.talk.utils.DeepLinkHandler
import com.nextcloud.talk.utils.SecurityUtils
import com.nextcloud.talk.utils.ShortcutManagerHelper
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

                    if (currentUserProviderOld.currentUser.blockingGet()?.baseUrl!!.endsWith(baseUrl) == true) {
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

        val currentUser = currentUserProviderOld.currentUser.blockingGet()

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
        // Handle deep links first (nctalk:// scheme and https:// web links)
        if (handleDeepLink(intent)) {
            return
        }

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

    /**
     * Handles deep link URIs for opening conversations.
     *
     * Supports:
     * - nctalk://conversation/{token}?user={userId}
     * - https://{server}/call/{token}
     * - https://{server}/index.php/call/{token}
     *
     * @param intent The intent to process
     * @return true if the intent was handled as a deep link, false otherwise
     */
    private fun handleDeepLink(intent: Intent): Boolean {
        val uri = intent.data ?: return false
        val deepLinkResult = DeepLinkHandler.parseDeepLink(uri) ?: return false

        Log.d(TAG, "Handling deep link: $uri -> token=${deepLinkResult.roomToken}")

        userManager.users.subscribe(object : SingleObserver<List<User>> {
            override fun onSubscribe(d: Disposable) {
                // unused atm
            }

            override fun onSuccess(users: List<User>) {
                if (users.isEmpty()) {
                    runOnUiThread {
                        launchServerSelection()
                    }
                    return
                }

                val targetUser = resolveTargetUser(users, deepLinkResult)

                if (targetUser == null) {
                    runOnUiThread {
                        Toast.makeText(
                            context,
                            context.resources.getString(R.string.nc_no_account_for_server),
                            Toast.LENGTH_LONG
                        ).show()
                        openConversationList()
                    }
                    return
                }

                if (userManager.setUserAsActive(targetUser).blockingGet()) {
                    // Report shortcut usage for ranking
                    ShortcutManagerHelper.reportShortcutUsed(
                        context,
                        deepLinkResult.roomToken,
                        targetUser.id!!
                    )

                    runOnUiThread {
                        val chatIntent = Intent(context, ChatActivity::class.java)
                        chatIntent.putExtra(KEY_ROOM_TOKEN, deepLinkResult.roomToken)
                        chatIntent.putExtra(BundleKeys.KEY_INTERNAL_USER_ID, targetUser.id)
                        startActivity(chatIntent)
                    }
                }
            }

            override fun onError(e: Throwable) {
                Log.e(TAG, "Error loading users for deep link", e)
                runOnUiThread {
                    Toast.makeText(
                        context,
                        context.resources.getString(R.string.nc_common_error_sorry),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })

        return true
    }

    /**
     * Resolves which user account to use for a deep link.
     *
     * Priority:
     * 1. User ID specified in deep link (for nctalk:// URIs)
     * 2. User matching the server URL (for https:// web links)
     * 3. Current active user as fallback
     */
    private fun resolveTargetUser(
        users: List<User>,
        deepLinkResult: DeepLinkHandler.DeepLinkResult
    ): User? {
        // If user ID is specified, use that user
        deepLinkResult.internalUserId?.let { userId ->
            return userManager.getUserWithId(userId).blockingGet()
        }

        // If server URL is specified, find matching account
        deepLinkResult.serverUrl?.let { serverUrl ->
            val matchingUser = users.find { user ->
                user.baseUrl?.lowercase()?.contains(serverUrl.lowercase()) == true
            }
            if (matchingUser != null) {
                return matchingUser
            }
        }

        // Fall back to current user
        return currentUserProviderOld.currentUser.blockingGet()
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
    }
}
