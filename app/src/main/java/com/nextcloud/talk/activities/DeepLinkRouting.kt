/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.activities

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.os.BundleCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nextcloud.talk.R
import com.nextcloud.talk.account.ServerSelectionActivity
import com.nextcloud.talk.activities.DeepLinkRoutingViewModel.Destination
import com.nextcloud.talk.activities.DeepLinkRoutingViewModel.Request
import com.nextcloud.talk.activities.DeepLinkRoutingViewModel.RoutingState
import com.nextcloud.talk.chat.ChatActivity
import com.nextcloud.talk.conversationlist.ConversationsListActivity
import com.nextcloud.talk.utils.AppTasks
import com.nextcloud.talk.utils.ShortcutManagerHelper

/**
 * Shows the routing of the deep links and contact chats that [DeepLinkActivity] passes on to the conversation list
 * [activity], see [DeepLinkRoutingViewModel]: asks its questions as dialogs over the list and opens the chat on top.
 * The list shows the account [currentUserId] gives, so a chat of another account opens with the list of that one.
 */
class DeepLinkRouting(
    private val activity: BaseActivity,
    private val viewModel: DeepLinkRoutingViewModel,
    private val currentUserId: () -> Long?
) {

    private var confirmationDialog: AlertDialog? = null

    // the question the confirmation dialog asks, so a newer one replaces it
    private var shownQuestion: RoutingState.Question? = null

    // from opening a screen above the list until the list is stopped below it
    private var isOpeningScreen = false

    // the launch screen is kept at most MAX_LAUNCH_SCREEN_MS from here
    private val createdAt = SystemClock.elapsedRealtime()

    /**
     * Whether the launch screen of a deep link that starts the app is kept: while it is routed, see
     * [DeepLinkRoutingViewModel.keepsLaunchScreen], and until the screen it opens covers the list. At most
     * [MAX_LAUNCH_SCREEN_MS], e.g. while a slow server creates the conversation with a contact: the list shows then
     * until the chat opens.
     */
    val keepsLaunchScreen: Boolean
        get() = (viewModel.keepsLaunchScreen || isOpeningScreen) &&
            SystemClock.elapsedRealtime() - createdAt < MAX_LAUNCH_SCREEN_MS

    init {
        // the chat or question of a deep link that started the app follows after unlocking
        activity.collectWhileUnlocked(viewModel.state, ::render)
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                isOpeningScreen = false
            }

            override fun onDestroy(owner: LifecycleOwner) {
                // dismissing doesn't decline the question: it is asked again if the activity is recreated
                confirmationDialog?.dismiss()
            }
        })
    }

    /** Routes the [intent] the list was created with, unless it was routed already or is still being routed. */
    fun start(intent: Intent, savedInstanceState: Bundle?) {
        val state = viewModel.state.value
        // by the view model kept across a configuration change, or before process death
        val isRoutedAlready = state != RoutingState.Idle || savedInstanceState?.getBoolean(KEY_ROUTING_DONE) == true
        if (isRoutedAlready) return
        val routedIntent = savedInstanceState?.let {
            BundleCompat.getParcelable(it, KEY_ROUTED_INTENT, Intent::class.java)
        }
        // the intent of a task relaunched from the recents was routed already
        val routed = routedIntent ?: intent.takeUnless(AppTasks::isRelaunchedFromRecents) ?: return
        route(routed)
    }

    /** Routes the new [intent] of the list and returns true if it asks for a deep link or contact chat. */
    fun onNewIntent(intent: Intent): Boolean {
        if (Request.of(intent) == null) return false
        route(intent)
        return true
    }

    fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_ROUTING_DONE, !viewModel.isRouting)
        // recreated after process death, the list gets the intent it was started with, not a newer one it routes
        if (viewModel.isRouting) {
            outState.putParcelable(KEY_ROUTED_INTENT, viewModel.routedIntent)
        }
    }

    private fun route(intent: Intent) {
        val request = Request.of(intent) ?: return
        viewModel.routedIntent = intent
        viewModel.route(request)
    }

    private fun render(state: RoutingState) {
        if (state != shownQuestion) {
            // e.g. answered, or replaced by the routing of a newer intent, also while the activity was stopped, when
            // the question of that intent can follow the old one directly. Dismissing doesn't trigger the cancel
            // listener.
            confirmationDialog?.dismiss()
            confirmationDialog = null
            shownQuestion = null
        }
        when (state) {
            is RoutingState.Question -> ask(state)
            is RoutingState.Open -> {
                state.message?.let {
                    Toast.makeText(activity, it, if (state.isLongMessage) Toast.LENGTH_LONG else Toast.LENGTH_SHORT)
                        .show()
                }
                open(state.destination)
                viewModel.onOpened()
            }
            RoutingState.Idle, RoutingState.Routing, RoutingState.Done -> Unit
        }
    }

    private fun open(destination: Destination) {
        when (destination) {
            Destination.ConversationList -> Unit
            Destination.Login -> {
                isOpeningScreen = true
                activity.startActivity(ServerSelectionActivity.createLoginIntent(activity))
            }
            is Destination.Chat -> {
                isOpeningScreen = true
                val chatIntent = ChatActivity.createIntent(activity, destination.userId, destination.roomToken)
                if (destination.userId == currentUserId()) {
                    activity.startActivity(chatIntent)
                } else {
                    // the conversation list of its account below the chat, so back shows its conversations
                    val listIntent = ConversationsListActivity.createAccountSwitchIntent(activity, destination.userId)
                    activity.startActivities(arrayOf(listIntent, chatIntent))
                }
            }
            // the list of its account asks whether to join the call, see ConversationsListActivity.handleConversation
            is Destination.VoiceRoom -> activity.startActivity(
                ShortcutManagerHelper.createConversationIntent(activity, destination.roomToken, destination.userId)
            )
        }
    }

    /** Asks [question], unless it is shown already, e.g. when the activity is started again. */
    private fun ask(question: RoutingState.Question) {
        if (confirmationDialog?.isShowing == true) return
        when (question) {
            is RoutingState.AskToJoin -> showConfirmationDialog(
                title = R.string.nc_deep_link_join_title,
                message = activity.getString(R.string.nc_deep_link_join_message, question.server),
                confirmButton = R.string.nc_deep_link_join
            )
            is RoutingState.AskToStartContactChat -> showConfirmationDialog(
                title = R.string.nc_contact_chat_start_title,
                message = activity.getString(R.string.nc_contact_chat_start_message, question.cloudId),
                confirmButton = R.string.nc_contact_chat_start
            )
        }
        shownQuestion = question
    }

    private fun showConfirmationDialog(@StringRes title: Int, message: String, @StringRes confirmButton: Int) {
        val dialogBuilder = MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(confirmButton) { _, _ -> viewModel.confirm() }
            .setNegativeButton(R.string.nc_cancel) { _, _ -> viewModel.decline() }
            .setOnCancelListener { viewModel.decline() }
        activity.viewThemeUtils.dialog.colorMaterialAlertDialogBackground(activity, dialogBuilder)
        val dialog = dialogBuilder.show()
        confirmationDialog = dialog
        activity.viewThemeUtils.platform.colorTextButtons(
            dialog.getButton(AlertDialog.BUTTON_POSITIVE),
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        )
    }

    companion object {
        private const val MAX_LAUNCH_SCREEN_MS = 2000L

        // saved state: whether the intent was routed when the activity was stopped
        private const val KEY_ROUTING_DONE = "KEY_DEEP_LINK_ROUTING_DONE"

        // saved state: the intent that was still being routed when the activity was stopped
        private const val KEY_ROUTED_INTENT = "KEY_DEEP_LINK_ROUTED_INTENT"
    }
}
