/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.activities

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextcloud.talk.R
import com.nextcloud.talk.contacts.ContactsRepository
import com.nextcloud.talk.conversationlist.ConversationsListActivity
import com.nextcloud.talk.conversationlist.data.OfflineConversationsRepository
import com.nextcloud.talk.conversationlist.data.network.OfflineFirstConversationsRepository.ConversationResult
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.logger.Logger
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.users.DefaultAccountProvider
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ConversationUtils.checkIfVoiceRoom
import com.nextcloud.talk.utils.DeepLinkHandler
import com.nextcloud.talk.utils.InternalEntryPoint
import com.nextcloud.talk.utils.ShortcutManagerHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject

/**
 * Routes the nextcloudtalk:// deep links and phone book contact chats that [DeepLinkActivity] passes on to the
 * conversation list: works out which screen a [Request] opens, asks first where opening it would join or start a
 * conversation, and keeps doing so across configuration changes. The list shows the [state] through
 * [DeepLinkRouting] and opens its [RoutingState.Open.destination].
 */
@Suppress("TooManyFunctions")
class DeepLinkRoutingViewModel @Inject constructor(
    private val context: Context,
    private val userManager: UserManager,
    private val defaultAccountProvider: DefaultAccountProvider,
    private val conversationsRepository: OfflineConversationsRepository,
    private val contactsRepository: ContactsRepository,
    private val logger: Logger
) : ViewModel() {

    /** What a passed on intent asks for, see [of]. */
    sealed interface Request {
        /** A nextcloudtalk:// deep link to a conversation. */
        data class DeepLink(val link: DeepLinkHandler.DeepLinkResult) : Request

        /** The chat with the phone book contact of [contactUri], from the contacts app. */
        data class ContactChat(val contactUri: Uri) : Request

        companion object {
            /**
             * The request of [intent], or null if it asks for none: only the uri of a VIEW intent of another app that
             * DeepLinkActivity passed on through the internal entry point of the conversation list asks for one.
             */
            fun of(intent: Intent): Request? {
                val uri = intent.data
                return when {
                    !InternalEntryPoint.isStartedThrough(intent, ConversationsListActivity::class.java) -> null
                    intent.action != Intent.ACTION_VIEW || uri == null -> null
                    else -> DeepLinkHandler.parseDeepLink(uri)?.let { DeepLink(it) }
                        ?: if (isContactChat(intent, uri)) ContactChat(uri) else null
                }
            }

            private fun isContactChat(intent: Intent, uri: Uri) =
                uri.scheme == ContentResolver.SCHEME_CONTENT &&
                    uri.authority == ContactsContract.AUTHORITY &&
                    intent.type == CONTACT_CHAT_MIME_TYPE
        }
    }

    /** A screen that the conversation list opens. */
    sealed interface Destination {
        /** The conversation list itself, which is shown already. */
        data object ConversationList : Destination

        data object Login : Destination

        /** The chat of [roomToken], above the conversation list of the account [userId]. */
        data class Chat(val userId: Long, val roomToken: String) : Destination

        /**
         * The voice room [roomToken] of the account [userId], which its conversation list opens like a conversation
         * shortcut: it asks whether to join the call, which opening its chat would start right away.
         */
        data class VoiceRoom(val userId: Long, val roomToken: String) : Destination
    }

    sealed interface RoutingState {
        /** No request yet. */
        data object Idle : RoutingState

        data object Routing : RoutingState

        /** A question whether to open what the request asks for, answered with [confirm] or [decline]. */
        sealed interface Question : RoutingState

        /** Whether to open the conversation on [server] that is not in the list, which can join it. */
        data class AskToJoin(val server: String) : Question

        /** Whether to start a conversation with the phone book contact [cloudId], which adds it to both lists. */
        data class AskToStartContactChat(val cloudId: String) : Question

        /** [destination] is to be opened, with a toast of [message]; the list reports it with [onOpened]. */
        data class Open(
            val destination: Destination,
            @StringRes val message: Int? = null,
            val isLongMessage: Boolean = false
        ) : RoutingState

        /** The request was routed. */
        data object Done : RoutingState
    }

    private val _state = MutableStateFlow<RoutingState>(RoutingState.Idle)
    val state: StateFlow<RoutingState> = _state.asStateFlow()

    /** Whether a request is still being routed, including the question whether to open it and the opening. */
    val isRouting: Boolean
        get() = _state.value.let { it != RoutingState.Idle && it != RoutingState.Done }

    /**
     * Whether the launch screen is kept while the request is routed: until what it opens is opened, but not while a
     * question waits for an answer.
     */
    val keepsLaunchScreen: Boolean
        get() = _state.value.let { it == RoutingState.Routing || it is RoutingState.Open }

    /** The intent of the request that is routed, kept for a recreation of the list after process death. */
    var routedIntent: Intent? = null

    // the coroutine that routes the latest request: a newer one cancels it, so a slower older one opens nothing
    private var routingJob: Job? = null

    // what confirming the question of the state opens
    private var onConfirm: (suspend () -> Unit)? = null

    @VisibleForTesting
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    /** Routes [request], instead of a request that is still being routed. */
    fun route(request: Request) {
        launchRouting {
            when (request) {
                is Request.DeepLink -> routeDeepLink(request.link)
                is Request.ContactChat -> routeContactChat(request.contactUri)
            }
        }
    }

    /** Opens what the request of the current question asks for. */
    fun confirm() {
        val action = onConfirm?.takeIf { _state.value is RoutingState.Question } ?: return
        launchRouting(action)
    }

    /** Stays in the conversation list instead of opening what the request of the current question asks for. */
    fun decline() {
        if (_state.value !is RoutingState.Question) return
        onConfirm = null
        _state.value = RoutingState.Open(Destination.ConversationList)
    }

    /** Reports that the destination of [RoutingState.Open] was opened. */
    fun onOpened() {
        if (_state.value is RoutingState.Open) _state.value = RoutingState.Done
    }

    /**
     * Runs [block] to route the latest request. A step that fails, also one confirmed later, stays in the conversation
     * list with an error.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun launchRouting(block: suspend () -> Unit) {
        routingJob?.cancel()
        onConfirm = null
        _state.value = RoutingState.Routing
        routingJob = viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(TAG, "Error routing the intent", e)
                open(Destination.ConversationList, R.string.nc_common_error_sorry)
            }
        }
    }

    private fun open(destination: Destination, @StringRes message: Int? = null, isLongMessage: Boolean = false) {
        _state.value = RoutingState.Open(destination, message, isLongMessage)
    }

    private fun ask(question: RoutingState.Question, action: suspend () -> Unit) {
        onConfirm = action
        _state.value = question
    }

    /** Opens [destination] or, without an account, e.g. after the last one was removed meanwhile, the login. */
    private suspend fun openOrLogin(destination: Destination, @StringRes message: Int? = null) {
        if (userManager.getUsersNotScheduledForDeletion().isEmpty()) {
            open(Destination.Login)
            return
        }
        open(destination, message, isLongMessage = true)
    }

    private suspend fun routeDeepLink(link: DeepLinkHandler.DeepLinkResult) {
        val users = userManager.getUsersNotScheduledForDeletion()
        if (users.isEmpty()) {
            open(Destination.Login)
            return
        }
        val user = resolveTargetUser(users, link, defaultUserAmong(users))
        if (user == null) {
            open(Destination.ConversationList, R.string.nc_no_account_for_server, isLongMessage = true)
            return
        }
        val conversation = knownConversation(user, link.roomToken)
        when {
            conversation == null -> ask(RoutingState.AskToJoin(link.serverUrl.toUri().host ?: link.serverUrl)) {
                openDeepLinkConversation(user, link.roomToken)
            }
            // another app must not start a call
            conversation.checkIfVoiceRoom() -> open(Destination.VoiceRoom(user.id!!, link.roomToken))
            else -> openDeepLinkConversation(user, link.roomToken)
        }
    }

    /** The default account if it is one of [users], e.g. not when it is scheduled for deletion. */
    private suspend fun defaultUserAmong(users: List<User>): User? =
        defaultAccountProvider.getDefaultUser()?.takeIf { default -> users.any { it.id == default.id } }

    /** The conversation [roomToken] of [user], stored or after a sync like [isStoredOrSynced], else null. */
    private suspend fun knownConversation(user: User, roomToken: String): ConversationModel? {
        var conversation: ConversationModel? = null
        isStoredOrSynced(user) {
            val result = conversationsRepository.observeConversation(user.id!!, roomToken).first()
            conversation = (result as? ConversationResult.Found)?.conversation
            conversation != null
        }
        return conversation
    }

    // The account isn't made active here: while the lock screen covers the list, or while it is stopped, its
    // onRestart would relaunch it for the new default account and drop this pending chat. The list of the account that
    // Destination.Chat starts makes it active.
    private fun openDeepLinkConversation(user: User, roomToken: String) {
        // Report shortcut usage for ranking
        ShortcutManagerHelper.reportShortcutUsed(context, roomToken, user.id!!)
        open(Destination.Chat(user.id!!, roomToken))
    }

    private suspend fun routeContactChat(contactUri: Uri) {
        val (cloudId, account) = withContext(ioDispatcher) {
            val cloudId = readUserIdOfContact(contactUri)
            val users = userManager.getUsersNotScheduledForDeletion()
            cloudId to accountForContactServer(cloudId.substringAfterLast("@"), users, defaultUserAmong(users))
        }
        val contactUserId = cloudId.substringBeforeLast("@")
        when {
            account == null -> openOrLogin(
                Destination.ConversationList,
                R.string.nc_phone_book_integration_account_not_found
            )
            hasOneToOneConversation(account, contactUserId) -> openChatWithContact(contactUserId, account)
            else -> ask(RoutingState.AskToStartContactChat(cloudId)) { openChatWithContact(contactUserId, account) }
        }
    }

    // the contacts provider lives in another process: a binder call, not for the main thread
    // empty without the contacts permission, which any app can make it need by sending such an intent, and for a uri
    // of another app that is no contact data row of Talk, e.g. of a contact, an unknown path or a data row of another
    // type, such as the email address of a contact, which has the form of a user id at a server as well
    private fun readUserIdOfContact(contactUri: Uri): String {
        var userId = ""
        try {
            context.contentResolver.query(contactUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() &&
                    cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Data.MIMETYPE)) ==
                    CONTACT_CHAT_MIME_TYPE
                ) {
                    // userId @ server
                    userId = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Data.DATA1)).orEmpty()
                }
            }
        } catch (e: SecurityException) {
            logger.w(TAG, "Can't read the contact without the contacts permission", e)
        } catch (e: IllegalArgumentException) {
            logger.w(TAG, "Can't read the user id of the contact $contactUri", e)
        }
        return userId
    }

    /** Whether [user] has a one-to-one conversation with [userId], see [isStoredOrSynced]. */
    private suspend fun hasOneToOneConversation(user: User, userId: String): Boolean =
        isStoredOrSynced(user) {
            conversationsRepository.roomListFlow(user.id!!).first().any {
                it.type == ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL && it.name == userId
            }
        }

    /**
     * Whether [isStored] holds for the local conversations of [user], or after they were synced, as the local list can
     * miss a conversation, e.g. of an account whose conversations were not synced yet. A sync that fails or takes
     * longer than [ROOM_SYNC_TIMEOUT_MS] counts as no change.
     */
    private suspend fun isStoredOrSynced(user: User, isStored: suspend () -> Boolean): Boolean =
        isStored() ||
            conversationsRepository.syncRooms(user, roomListTimeoutMillis = ROOM_SYNC_TIMEOUT_MS) != null &&
            isStored()

    /**
     * Opens the one-to-one chat of [account] with the contact [contactUserId]. A conversation that can't be created,
     * e.g. offline or rejected by the server, stays in the list with an error, like any failed step.
     */
    private suspend fun openChatWithContact(contactUserId: String, account: User) {
        val roomToken = createRoomWithContact(contactUserId, account)
        if (roomToken == null) {
            logger.e(TAG, "The server returned no conversation with the contact")
            open(Destination.ConversationList, R.string.nc_common_error_sorry)
            return
        }
        openOrLogin(Destination.Chat(account.id!!, roomToken))
    }

    /** Creates or gets the one-to-one conversation of [account] with [userId], null without one in the response. */
    private suspend fun createRoomWithContact(userId: String, account: User): String? =
        contactsRepository.createRoom(
            account,
            roomType = ONE_TO_ONE_ROOM_TYPE,
            sourceType = null,
            userId = userId,
            conversationName = null
        ).ocs?.data?.token?.takeIf { it.isNotBlank() }

    companion object {
        private val TAG = DeepLinkRoutingViewModel::class.java.simpleName

        private const val ROOM_SYNC_TIMEOUT_MS = 5000L
        private const val ONE_TO_ONE_ROOM_TYPE = "1"

        private const val CONTACT_CHAT_MIME_TYPE = "vnd.android.cursor.item/vnd.com.nextcloud.talk2.chat"

        /**
         * Resolves which account of [users] opens [deepLinkResult], with [defaultUser] the current one.
         *
         * Priority:
         * 1. User matching both username and server URL
         * 2. Current active user, if it is on the server
         * 3. Another user on the server
         */
        @VisibleForTesting
        internal fun resolveTargetUser(
            users: List<User>,
            deepLinkResult: DeepLinkHandler.DeepLinkResult,
            defaultUser: User?
        ): User? {
            val deepLinkHost = deepLinkResult.serverUrl.toUri().host?.lowercase()
            if (deepLinkHost.isNullOrBlank()) {
                return defaultUser
            }
            val hostOf = { user: User -> user.baseUrl?.let { it.toUri().host?.lowercase() } }
            val username = deepLinkResult.username
            val exactMatch = username?.let {
                users.find { user -> hostOf(user) == deepLinkHost && user.username?.lowercase() == it.lowercase() }
            }
            val serverMatch = users.find { user -> hostOf(user) == deepLinkHost }
            val currentUserMatch = defaultUser?.takeIf { hostOf(it) == deepLinkHost }
            return exactMatch ?: currentUserMatch ?: serverMatch
        }

        /**
         * The account to chat with a phone book contact whose cloud ID is on [server] (host[:port][/path], without
         * scheme). The phone book integration syncs for the account that is the default at the time, so its entries
         * can belong to another account than the current default one: prefer the default account if it is on that
         * server, otherwise take another account on it. Null if no account is on that server.
         */
        @VisibleForTesting
        internal fun accountForContactServer(server: String, users: List<User>, defaultUser: User?): User? {
            if (server.isBlank() || "https://$server".toHttpUrlOrNull() == null) {
                return null
            }
            val isOnServer = { user: User? ->
                user?.baseUrl?.substringAfter("://")?.trimEnd('/').equals(server.trimEnd('/'), ignoreCase = true)
            }
            return defaultUser?.takeIf(isOnServer) ?: users.firstOrNull(isOnServer)
        }
    }
}
