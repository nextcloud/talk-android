/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.openconversations

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import autodagger.AutoInjector
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.ChatActivity
import com.nextcloud.talk.components.ColoredStatusBar
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.openconversations.viewmodels.OpenConversationsViewModel
import com.nextcloud.talk.utils.adjustUIForAPILevel35
import com.nextcloud.talk.utils.bundle.BundleKeys
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class ListOpenConversationsActivity : BaseActivity() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    private lateinit var openConversationsViewModel: OpenConversationsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        adjustUIForAPILevel35()
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        openConversationsViewModel = ViewModelProvider(this, viewModelFactory)[OpenConversationsViewModel::class.java]
        openConversationsViewModel.fetchConversations()

        val user = currentUserProviderOld.currentUser.blockingGet()

        setContent {
            val colorScheme = viewThemeUtils.getColorScheme(this)
            val viewState by openConversationsViewModel.viewState.collectAsStateWithLifecycle()
            val searchTerm by openConversationsViewModel.searchTerm.collectAsStateWithLifecycle()

            MaterialTheme(colorScheme = colorScheme) {
                ColoredStatusBar()
                OpenConversationsScreen(
                    viewState = viewState,
                    searchTerm = searchTerm,
                    userBaseUrl = user?.baseUrl,
                    listenerInput = OpenConversationsScreenListenerInput(
                        onSearchTermChange = { term ->
                            openConversationsViewModel.updateSearchTerm(term)
                            openConversationsViewModel.fetchConversations()
                        },
                        onConversationClick = { conversation -> navigateToChat(conversation) },
                        onBackClick = { onBackPressedDispatcher.onBackPressed() }
                    )
                )
            }
        }
    }

    private fun navigateToChat(conversation: Conversation) {
        val bundle = Bundle()
        bundle.putString(BundleKeys.KEY_ROOM_TOKEN, conversation.token)

        val chatIntent = Intent(this, ChatActivity::class.java)
        chatIntent.putExtras(bundle)
        chatIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(chatIntent)
    }
}
