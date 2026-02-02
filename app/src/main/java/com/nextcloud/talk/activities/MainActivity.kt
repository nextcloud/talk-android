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
import android.net.Uri
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
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.ClosedInterfaceImpl
import com.nextcloud.talk.utils.DeepLinkHandler
import com.nextcloud.talk.utils.SecurityUtils
import com.nextcloud.talk.utils.ShortcutManagerHelper
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
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

    private val disposables = CompositeDisposable()

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

    override fun onDestroy() {
        super.onDestroy()
        disposables.dispose()
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

                    val currentUser = currentUserProviderOld.currentUser.blockingGet()
                    if (currentUser?.baseUrl?.endsWith(baseUrl) == true) {
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

        val disposable = ncApi.createRoom(
            credentials,
            retrofitBucket.url,
            retrofitBucket.queryMap
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { roomOverall ->
                    if (isFinishing || isDestroyed) return@subscribe
                    val bundle = Bundle()
                    bundle.putString(KEY_ROOM_TOKEN, roomOverall.ocs!!.data!!.token)

                    val chatIntent = Intent(context, ChatActivity::class.java)
                    chatIntent.putExtras(bundle)
                    startActivity(chatIntent)
                },
                { e ->
                    Log.e(TAG, "Error creating room", e)
                }
            )
        disposables.add(disposable)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent Activity: " + System.identityHashCode(this).toString())
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        // Handle deep links first (nextcloudtalk:// scheme)
        if (handleDeepLink(intent)) {
            return
        }

        handleActionFromContact(intent)

        val internalUserId = intent.extras?.getLong(BundleKeys.KEY_INTERNAL_USER_ID)

        var user: User? = null
        if (internalUserId != null && internalUserId != 0L) {
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
            val disposable = userManager.users
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { users ->
                        if (isFinishing || isDestroyed) return@subscribe
                        if (users.isNotEmpty()) {
                            ClosedInterfaceImpl().setUpPushTokenRegistration()
                            openConversationList()
                        } else {
                            launchServerSelection()
                        }
                    },
                    { e ->
                        Log.e(TAG, "Error loading existing users", e)
                        if (isFinishing || isDestroyed) return@subscribe
                        Toast.makeText(
                            context,
                            context.resources.getString(R.string.nc_common_error_sorry),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            disposables.add(disposable)
        }
    }

    /**
     * Handles deep link URIs for opening conversations.
     *
     * Supports:
     * - nextcloudtalk://[user@]server/call/token
     *
     * @param intent The intent to process
     * @return true if the intent was handled as a deep link, false otherwise
     */
    private fun handleDeepLink(intent: Intent): Boolean {
        val deepLinkResult = intent.data?.let { DeepLinkHandler.parseDeepLink(it) } ?: return false

        Log.d(TAG, "Handling deep link: token=${deepLinkResult.roomToken}, server=${deepLinkResult.serverUrl}")

        val disposable = userManager.users
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { users ->
                    if (isFinishing || isDestroyed) return@subscribe

                    if (users.isEmpty()) {
                        launchServerSelection()
                        return@subscribe
                    }

                    val targetUser = resolveTargetUser(users, deepLinkResult)

                    if (targetUser == null) {
                        Toast.makeText(
                            context,
                            context.resources.getString(R.string.nc_no_account_for_server),
                            Toast.LENGTH_LONG
                        ).show()
                        openConversationList()
                        return@subscribe
                    }

                    if (userManager.setUserAsActive(targetUser).blockingGet()) {
                        // Report shortcut usage for ranking
                        targetUser.id?.let { userId ->
                            ShortcutManagerHelper.reportShortcutUsed(
                                context,
                                deepLinkResult.roomToken,
                                userId
                            )
                        }

                        if (isFinishing || isDestroyed) return@subscribe

                        val chatIntent = Intent(context, ChatActivity::class.java)
                        chatIntent.putExtra(KEY_ROOM_TOKEN, deepLinkResult.roomToken)
                        chatIntent.putExtra(BundleKeys.KEY_INTERNAL_USER_ID, targetUser.id)
                        startActivity(chatIntent)
                    } else {
                        Toast.makeText(
                            context,
                            context.resources.getString(R.string.nc_common_error_sorry),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                { e ->
                    Log.e(TAG, "Error loading users for deep link", e)
                    if (isFinishing || isDestroyed) return@subscribe
                    Toast.makeText(
                        context,
                        context.resources.getString(R.string.nc_common_error_sorry),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        disposables.add(disposable)

        return true
    }

    /**
     * Resolves which user account to use for a deep link.
     *
     * Priority:
     * 1. User matching both username and server URL
     * 2. User matching the server URL only
     * 3. Current active user as fallback (if server matches)
     */
    private fun resolveTargetUser(users: List<User>, deepLinkResult: DeepLinkHandler.DeepLinkResult): User? {
        val deepLinkHost = Uri.parse(deepLinkResult.serverUrl).host?.lowercase()
        if (deepLinkHost.isNullOrBlank()) {
            return currentUserProviderOld.currentUser.blockingGet()
        }

        // Priority: exact match (username + server) > server match > current user fallback
        val username = deepLinkResult.username
        val exactMatch = if (username != null) {
            users.find { user ->
                val userHost = user.baseUrl?.let { Uri.parse(it).host?.lowercase() }
                userHost == deepLinkHost && user.username?.lowercase() == username.lowercase()
            }
        } else {
            null
        }

        val serverMatch = users.find { user ->
            val userHost = user.baseUrl?.let { Uri.parse(it).host?.lowercase() }
            userHost == deepLinkHost
        }

        val currentUser = currentUserProviderOld.currentUser.blockingGet()
        val currentUserMatch = currentUser?.takeIf {
            it.baseUrl?.let { url -> Uri.parse(url).host?.lowercase() } == deepLinkHost
        }

        return exactMatch ?: serverMatch ?: currentUserMatch
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
    }
}
