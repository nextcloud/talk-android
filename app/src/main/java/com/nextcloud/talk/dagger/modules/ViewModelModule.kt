/*
 * Nextcloud Talk application
 *
 * @author Álvaro Brey
 * Copyright (C) 2022 Álvaro Brey
 * Copyright (C) 2022 Nextcloud GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.dagger.modules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nextcloud.talk.messagesearch.MessageSearchViewModel
import com.nextcloud.talk.openconversations.viewmodels.OpenConversationsViewModel
import com.nextcloud.talk.polls.viewmodels.PollCreateViewModel
import com.nextcloud.talk.polls.viewmodels.PollMainViewModel
import com.nextcloud.talk.polls.viewmodels.PollResultsViewModel
import com.nextcloud.talk.polls.viewmodels.PollVoteViewModel
import com.nextcloud.talk.raisehand.viewmodel.RaiseHandViewModel
import com.nextcloud.talk.remotefilebrowser.viewmodels.RemoteFileBrowserItemsViewModel
import com.nextcloud.talk.shareditems.viewmodels.SharedItemsViewModel
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
    abstract fun openConversationsViewModelModel(viewModel: OpenConversationsViewModel): ViewModel
}
