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
import com.nextcloud.talk.dagger.modules.ViewModelFactoryWithParams
import com.nextcloud.talk.models.json.conversations.ConversationDto
import com.nextcloud.talk.openconversations.viewmodels.OpenConversationsViewModel
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.adjustUIForAPILevel35
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class ListOpenConversationsActivity : BaseActivity() {

    @Inject
    lateinit var viewModelFactory: OpenConversationsViewModel.Factory

    @Inject
    lateinit var userManager: UserManager

    private lateinit var openConversationsViewModel: OpenConversationsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        adjustUIForAPILevel35()
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        applyUserTheme()

        val user = runBlocking { userManager.getUserWithId(resolveUserIdFromIntent()) }
        if (user == null) {
            finish()
            return
        }

        openConversationsViewModel = ViewModelProvider(
            this,
            ViewModelFactoryWithParams(OpenConversationsViewModel::class.java) { viewModelFactory.build(user) }
        )[OpenConversationsViewModel::class.java]
        openConversationsViewModel.fetchConversations()

        setContent {
            val colorScheme = viewThemeUtils.getColorScheme(this)
            val viewState by openConversationsViewModel.viewState.collectAsStateWithLifecycle()
            val searchTerm by openConversationsViewModel.searchTerm.collectAsStateWithLifecycle()

            MaterialTheme(colorScheme = colorScheme) {
                ColoredStatusBar()
                OpenConversationsScreen(
                    viewState = viewState,
                    searchTerm = searchTerm,
                    userBaseUrl = user.baseUrl,
                    listenerInput = OpenConversationsScreenListenerInput(
                        onSearchTermChange = { term ->
                            openConversationsViewModel.updateSearchTerm(term)
                            openConversationsViewModel.fetchConversations()
                        },
                        onConversationClick = { conversation -> navigateToChat(user.id!!, conversation) },
                        onBackClick = { onBackPressedDispatcher.onBackPressed() }
                    )
                )
            }
        }
    }

    private fun navigateToChat(userId: Long, conversation: ConversationDto) {
        val chatIntent = ChatActivity.createIntent(this, userId, conversation.token)
        chatIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(chatIntent)
    }
}
