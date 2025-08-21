/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.utils.preview

import android.content.Context
import com.github.aurae.retrofit2.LoganSquareConverterFactory
import com.nextcloud.android.common.ui.color.ColorUtil
import com.nextcloud.android.common.ui.theme.MaterialSchemes
import com.nextcloud.android.common.ui.theme.utils.AndroidViewThemeUtils
import com.nextcloud.android.common.ui.theme.utils.AndroidXViewThemeUtils
import com.nextcloud.android.common.ui.theme.utils.DialogViewThemeUtils
import com.nextcloud.android.common.ui.theme.utils.MaterialViewThemeUtils
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.chat.data.ChatMessageRepository
import com.nextcloud.talk.chat.data.io.AudioFocusRequestManager
import com.nextcloud.talk.chat.data.io.MediaRecorderManager
import com.nextcloud.talk.chat.data.network.ChatNetworkDataSource
import com.nextcloud.talk.chat.data.network.OfflineFirstChatRepository
import com.nextcloud.talk.chat.data.network.RetrofitChatNetwork
import com.nextcloud.talk.chat.viewmodels.ChatViewModel
import com.nextcloud.talk.contacts.ContactsRepository
import com.nextcloud.talk.contacts.ContactsRepositoryImpl
import com.nextcloud.talk.contacts.ContactsViewModel
import com.nextcloud.talk.conversationlist.data.OfflineConversationsRepository
import com.nextcloud.talk.conversationlist.data.network.ConversationsNetworkDataSource
import com.nextcloud.talk.conversationlist.data.network.OfflineFirstConversationsRepository
import com.nextcloud.talk.conversationlist.data.network.RetrofitConversationsNetwork
import com.nextcloud.talk.data.database.dao.ChatBlocksDao
import com.nextcloud.talk.data.database.dao.ChatMessagesDao
import com.nextcloud.talk.data.database.dao.ConversationsDao
import com.nextcloud.talk.data.network.NetworkMonitor
import com.nextcloud.talk.data.network.NetworkMonitorImpl
import com.nextcloud.talk.data.user.UsersDao
import com.nextcloud.talk.data.user.UsersRepository
import com.nextcloud.talk.data.user.UsersRepositoryImpl
import com.nextcloud.talk.repositories.reactions.ReactionsRepository
import com.nextcloud.talk.repositories.reactions.ReactionsRepositoryImpl
import com.nextcloud.talk.serverstatus.ServerStatusRepository
import com.nextcloud.talk.serverstatus.ServerStatusRepositoryImpl
import com.nextcloud.talk.threadsoverview.data.ThreadsRepository
import com.nextcloud.talk.threadsoverview.data.ThreadsRepositoryImpl
import com.nextcloud.talk.ui.theme.MaterialSchemesProviderImpl
import com.nextcloud.talk.ui.theme.TalkSpecificViewThemeUtils
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.database.user.CurrentUserProviderImpl
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import com.nextcloud.talk.utils.message.MessageUtils
import com.nextcloud.talk.utils.preferences.AppPreferences
import com.nextcloud.talk.utils.preferences.AppPreferencesImpl
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory

/**
 * TODO - basically a reimplementation of common dependencies for use in Previewing Advanced Compose Views
 * It's a hard coded Dependency Injector
 *
 */
class ComposePreviewUtils private constructor(context: Context) {
    private val mContext = context

    companion object {
        fun getInstance(context: Context) = ComposePreviewUtils(context)
        val TAG: String = ComposePreviewUtils::class.java.simpleName
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val appPreferences: AppPreferences
        get() = AppPreferencesImpl(mContext)

    val context: Context = mContext

    val userRepository: UsersRepository
        get() = UsersRepositoryImpl(usersDao)

    val userManager: UserManager
        get() = UserManager(userRepository)

    val userProvider: CurrentUserProviderNew
        get() = CurrentUserProviderImpl(userManager)

    val colorUtil: ColorUtil
        get() = ColorUtil(mContext)

    val materialScheme: MaterialSchemes
        get() = MaterialSchemesProviderImpl(userProvider, colorUtil).getMaterialSchemesForCurrentUser()

    val viewThemeUtils: ViewThemeUtils
        get() {
            val android = AndroidViewThemeUtils(materialScheme, colorUtil)
            val material = MaterialViewThemeUtils(materialScheme, colorUtil)
            val androidx = AndroidXViewThemeUtils(materialScheme, android)
            val talk = TalkSpecificViewThemeUtils(materialScheme, androidx)
            val dialog = DialogViewThemeUtils(materialScheme)
            return ViewThemeUtils(materialScheme, android, material, androidx, talk, dialog)
        }

    val messageUtils: MessageUtils
        get() = MessageUtils(mContext)

    val retrofit: Retrofit
        get() {
            val retrofitBuilder = Retrofit.Builder()
                .client(OkHttpClient.Builder().build())
                .baseUrl("https://nextcloud.com")
                .addCallAdapterFactory(RxJava2CallAdapterFactory.createWithScheduler(Schedulers.io()))
                .addConverterFactory(LoganSquareConverterFactory.create())

            return retrofitBuilder.build()
        }

    val ncApi: NcApi
        get() = retrofit.create(NcApi::class.java)

    val ncApiCoroutines: NcApiCoroutines
        get() = retrofit.create(NcApiCoroutines::class.java)

    val chatNetworkDataSource: ChatNetworkDataSource
        get() = RetrofitChatNetwork(ncApi, ncApiCoroutines)

    val usersDao: UsersDao
        get() = DummyUserDaoImpl()

    val chatMessagesDao: ChatMessagesDao
        get() = DummyChatMessagesDaoImpl()

    val chatBlocksDao: ChatBlocksDao
        get() = DummyChatBlocksDaoImpl()

    val conversationsDao: ConversationsDao
        get() = DummyConversationDaoImpl()

    val networkMonitor: NetworkMonitor
        get() = NetworkMonitorImpl(mContext)

    val serverStatusRepository: ServerStatusRepository
        get() = ServerStatusRepositoryImpl(ncApiCoroutines, userProvider)

    val chatRepository: ChatMessageRepository
        get() = OfflineFirstChatRepository(
            chatMessagesDao,
            chatBlocksDao,
            chatNetworkDataSource,
            networkMonitor,
            serverStatusRepository,
            userProvider
        )

    val threadsRepository: ThreadsRepository
        get() = ThreadsRepositoryImpl(ncApiCoroutines, userProvider)

    val conversationNetworkDataSource: ConversationsNetworkDataSource
        get() = RetrofitConversationsNetwork(ncApi)

    val conversationRepository: OfflineConversationsRepository
        get() = OfflineFirstConversationsRepository(
            conversationsDao,
            conversationNetworkDataSource,
            chatNetworkDataSource,
            networkMonitor,
            userProvider
        )

    val reactionsRepository: ReactionsRepository
        get() = ReactionsRepositoryImpl(ncApi, userProvider, chatMessagesDao)

    val mediaRecorderManager: MediaRecorderManager
        get() = MediaRecorderManager()

    val audioFocusRequestManager: AudioFocusRequestManager
        get() = AudioFocusRequestManager(mContext)

    val chatViewModel: ChatViewModel
        get() = ChatViewModel(
            appPreferences,
            chatNetworkDataSource,
            chatRepository,
            threadsRepository,
            conversationRepository,
            reactionsRepository,
            mediaRecorderManager,
            audioFocusRequestManager,
            userProvider
        )

    val contactsRepository: ContactsRepository
        get() = ContactsRepositoryImpl(ncApiCoroutines, userProvider)

    val contactsViewModel: ContactsViewModel
        get() = ContactsViewModel(contactsRepository)
}
