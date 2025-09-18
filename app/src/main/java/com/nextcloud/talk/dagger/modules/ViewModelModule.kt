/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-FileCopyrightText: 2022 Nextcloud GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.dagger.modules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nextcloud.talk.account.viewmodels.BrowserLoginActivityViewModel
import com.nextcloud.talk.chat.viewmodels.ChatViewModel
import com.nextcloud.talk.chat.viewmodels.MessageInputViewModel
import com.nextcloud.talk.contacts.ContactsViewModel
import com.nextcloud.talk.contextchat.ContextChatViewModel
import com.nextcloud.talk.conversationcreation.ConversationCreationViewModel
import com.nextcloud.talk.conversationinfo.viewmodel.ConversationInfoViewModel
import com.nextcloud.talk.conversationinfoedit.viewmodel.ConversationInfoEditViewModel
import com.nextcloud.talk.conversationlist.viewmodels.ConversationsListViewModel
import com.nextcloud.talk.diagnose.DiagnoseViewModel
import com.nextcloud.talk.invitation.viewmodels.InvitationsViewModel
import com.nextcloud.talk.messagesearch.MessageSearchViewModel
import com.nextcloud.talk.openconversations.viewmodels.OpenConversationsViewModel
import com.nextcloud.talk.polls.viewmodels.PollCreateViewModel
import com.nextcloud.talk.polls.viewmodels.PollMainViewModel
import com.nextcloud.talk.polls.viewmodels.PollResultsViewModel
import com.nextcloud.talk.polls.viewmodels.PollVoteViewModel
import com.nextcloud.talk.raisehand.viewmodel.RaiseHandViewModel
import com.nextcloud.talk.remotefilebrowser.viewmodels.RemoteFileBrowserItemsViewModel
import com.nextcloud.talk.shareditems.viewmodels.SharedItemsViewModel
import com.nextcloud.talk.threadsoverview.viewmodels.ThreadsOverviewViewModel
import com.nextcloud.talk.translate.viewmodels.TranslateViewModel
import com.nextcloud.talk.viewmodels.CallRecordingViewModel
import dagger.Binds
import dagger.MapKey
import dagger.Module
import dagger.multibindings.IntoMap
import javax.inject.Inject
import javax.inject.Provider
import kotlin.reflect.KClass

class ViewModelFactory @Inject constructor(
    private val viewModels: MutableMap<Class<out ViewModel>, Provider<ViewModel>>
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModels[modelClass]?.get() as T
}

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
@Retention(AnnotationRetention.RUNTIME)
@MapKey
internal annotation class ViewModelKey(val value: KClass<out ViewModel>)

@Module
@Suppress("TooManyFunctions")
abstract class ViewModelModule {

    @Binds
    abstract fun bindViewModelFactory(factory: ViewModelFactory): ViewModelProvider.Factory

    @Binds
    @IntoMap
    @ViewModelKey(SharedItemsViewModel::class)
    abstract fun sharedItemsViewModel(viewModel: SharedItemsViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(MessageSearchViewModel::class)
    abstract fun messageSearchViewModel(viewModel: MessageSearchViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(PollMainViewModel::class)
    abstract fun pollViewModel(viewModel: PollMainViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(PollVoteViewModel::class)
    abstract fun pollVoteViewModel(viewModel: PollVoteViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(PollResultsViewModel::class)
    abstract fun pollResultsViewModel(viewModel: PollResultsViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(PollCreateViewModel::class)
    abstract fun pollCreateViewModel(viewModel: PollCreateViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(RemoteFileBrowserItemsViewModel::class)
    abstract fun remoteFileBrowserItemsViewModel(viewModel: RemoteFileBrowserItemsViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(CallRecordingViewModel::class)
    abstract fun callRecordingViewModel(viewModel: CallRecordingViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(RaiseHandViewModel::class)
    abstract fun raiseHandViewModel(viewModel: RaiseHandViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(TranslateViewModel::class)
    abstract fun translateViewModel(viewModel: TranslateViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(OpenConversationsViewModel::class)
    abstract fun openConversationsViewModel(viewModel: OpenConversationsViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ConversationsListViewModel::class)
    abstract fun conversationsListViewModel(viewModel: ConversationsListViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ChatViewModel::class)
    abstract fun chatViewModel(viewModel: ChatViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(MessageInputViewModel::class)
    abstract fun messageInputViewModel(viewModel: MessageInputViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ConversationInfoViewModel::class)
    abstract fun conversationInfoViewModel(viewModel: ConversationInfoViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ConversationInfoEditViewModel::class)
    abstract fun conversationInfoEditViewModel(viewModel: ConversationInfoEditViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(InvitationsViewModel::class)
    abstract fun invitationsViewModel(viewModel: InvitationsViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ContactsViewModel::class)
    abstract fun contactsViewModel(viewModel: ContactsViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ConversationCreationViewModel::class)
    abstract fun conversationCreationViewModel(viewModel: ConversationCreationViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(DiagnoseViewModel::class)
    abstract fun diagnoseViewModel(viewModel: DiagnoseViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ThreadsOverviewViewModel::class)
    abstract fun threadsOverviewViewModel(viewModel: ThreadsOverviewViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(BrowserLoginActivityViewModel::class)
    abstract fun browserLoginActivityViewModel(viewModel: BrowserLoginActivityViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ContextChatViewModel::class)
    abstract fun contextChatViewModel(viewModel: ContextChatViewModel): ViewModel
}
