/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.contacts

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import autodagger.AutoInjector
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.ChatActivity
import com.nextcloud.talk.components.ColoredStatusBar
import com.nextcloud.talk.contacts.CompanionClass.Companion.KEY_HIDE_ALREADY_EXISTING_PARTICIPANTS
import com.nextcloud.talk.dagger.modules.assistedViewModels
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.extensions.getParcelableArrayListExtraProvider
import com.nextcloud.talk.models.json.autocomplete.AutocompleteUserDto
import com.nextcloud.talk.utils.bundle.BundleKeys
import kotlinx.coroutines.launch
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class ContactsActivity : BaseActivity() {

    @Inject
    lateinit var viewModelFactory: ContactsViewModel.Factory

    private lateinit var user: User

    private val contactsViewModel: ContactsViewModel by assistedViewModels { viewModelFactory.build(user) }

    @SuppressLint("UnrememberedMutableState")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        user = setUpBoundUserOrFinish() ?: return
        observeCreatedRoom()
        setContent {
            val isAddParticipants = intent.getBooleanExtra(BundleKeys.KEY_ADD_PARTICIPANTS, false)
            val hideAlreadyAddedParticipants = intent.getBooleanExtra(KEY_HIDE_ALREADY_EXISTING_PARTICIPANTS, false)
            contactsViewModel.getContactsFromSearchParams()
            contactsViewModel.updateIsAddParticipants(isAddParticipants)
            contactsViewModel.hideAlreadyAddedParticipants(hideAlreadyAddedParticipants)
            if (isAddParticipants) {
                val onlyLocal = intent.getBooleanExtra(BundleKeys.KEY_ONLY_LOCAL_PARTICIPANTS, false)
                val shareTypes = mutableListOf(ShareType.Group.shareType, ShareType.Circle.shareType)
                if (!onlyLocal) {
                    shareTypes.add(ShareType.Email.shareType)
                }
                contactsViewModel.updateShareTypes(shareTypes)
                contactsViewModel.getContactsFromSearchParams()
            }
            val colorScheme = viewThemeUtils.getColorScheme(this)
            val uiState = contactsViewModel.contactsViewState.collectAsStateWithLifecycle()

            val selectedParticipants = remember {
                intent?.getParcelableArrayListExtraProvider<AutocompleteUserDto>("selectedParticipants")
                    ?: emptyList()
            }.toSet().toMutableList()
            contactsViewModel.updateSelectedParticipants(selectedParticipants)

            MaterialTheme(
                colorScheme = colorScheme
            ) {
                ColoredStatusBar()
                ContactsScreen(
                    contactsViewModel = contactsViewModel,
                    uiState = uiState.value
                )
            }
        }
    }

    private fun observeCreatedRoom() {
        lifecycleScope.launch {
            // Only while visible: a conversation created meanwhile is still opened when the screen is started again.
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                contactsViewModel.roomViewState.collect { state ->
                    if (state is ContactsViewModel.RoomUiState.Success) {
                        state.conversation?.token?.let { token ->
                            val chatIntent = ChatActivity.createIntent(this@ContactsActivity, state.userId, token)
                            chatIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            startActivity(chatIntent)
                        }
                        contactsViewModel.clearRoomState()
                    }
                }
            }
        }
    }
}

class CompanionClass {
    companion object {
        internal val TAG = ContactsActivity::class.simpleName
        internal const val ROOM_TYPE_ONE_ONE = "1"
        const val KEY_HIDE_ALREADY_EXISTING_PARTICIPANTS: String = "KEY_HIDE_ALREADY_EXISTING_PARTICIPANTS"
    }
}
