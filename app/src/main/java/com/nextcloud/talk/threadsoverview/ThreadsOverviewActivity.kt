/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.threadsoverview

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import autodagger.AutoInjector
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.ChatActivity
import com.nextcloud.talk.components.ColoredStatusBar
import com.nextcloud.talk.components.StandardAppBar
import com.nextcloud.talk.models.json.threads.ThreadInfo
import com.nextcloud.talk.threadsoverview.viewmodels.ThreadsOverviewViewModel
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_THREAD_ID
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class ThreadsOverviewActivity : BaseActivity() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var userManager: UserManager

    lateinit var threadsOverviewViewModel: ThreadsOverviewViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        threadsOverviewViewModel = ViewModelProvider(
            this,
            viewModelFactory
        )[ThreadsOverviewViewModel::class.java]

        val colorScheme = viewThemeUtils.getColorScheme(this)

        val extras: Bundle? = intent.extras
        val roomToken = extras?.getString(KEY_ROOM_TOKEN).orEmpty()

        threadsOverviewViewModel.init(roomToken)

        setContent {
            val backgroundColor = colorResource(id = R.color.bg_default)

            MaterialTheme(
                colorScheme = colorScheme
            ) {
                ColoredStatusBar()
                Scaffold(
                    modifier = Modifier
                        .statusBarsPadding(),
                    topBar = {
                        StandardAppBar(
                            title = stringResource(R.string.threads_overview),
                            null
                        )
                    },
                    content = { paddingValues ->
                        val uiState by threadsOverviewViewModel.threadsListState.collectAsState()

                        Column(
                            Modifier
                                .padding(0.dp, paddingValues.calculateTopPadding(), 0.dp, 0.dp)
                                .background(backgroundColor)
                                .fillMaxSize()
                        ) {
                            ThreadsOverviewScreen(
                                uiState,
                                roomToken,
                                onThreadClick = { roomToken, threadId ->
                                    navigateToChatActivity(roomToken, threadId)
                                }
                            )
                        }
                    }
                )
            }
        }
    }

    private fun navigateToChatActivity(roomToken: String, threadId: Int) {
        val bundle = Bundle()
        bundle.putString(KEY_ROOM_TOKEN, roomToken)
        bundle.putLong(KEY_THREAD_ID, threadId.toLong())
        val chatIntent = Intent(context, ChatActivity::class.java)
        chatIntent.putExtras(bundle)
        startActivity(chatIntent)
    }

    override fun onResume() {
        super.onResume()
        supportActionBar?.show()
    }

    companion object {
        val TAG = ThreadsOverviewActivity::class.java.simpleName
    }
}

@Composable
fun ThreadsOverviewScreen(
    uiState: ThreadsOverviewViewModel.ThreadsListUiState,
    roomToken: String,
    onThreadClick: (roomToken: String, threadId: Int) -> Unit
) {
    when (val state = uiState) {
        is ThreadsOverviewViewModel.ThreadsListUiState.None -> {
            LoadingIndicator()
        }
        is ThreadsOverviewViewModel.ThreadsListUiState.Success -> {
            ThreadsList(
                threads = state.threadsList!!,
                roomToken = roomToken,
                onThreadClick = onThreadClick
            )
        }
        is ThreadsOverviewViewModel.ThreadsListUiState.Error -> {
            ErrorView(message = state.message)
        }
    }
}

@Composable
fun ThreadsList(
    threads: List<ThreadInfo>,
    roomToken: String,
    onThreadClick: (roomToken: String, threadId: Int) -> Unit
) {
    if (threads.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No threads found.")
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = threads,
            key = { threadInfo -> threadInfo.thread!!.id }
        ) { thread ->
            ThreadRow(
                threadInfo = thread,
                roomToken = roomToken,
                onThreadClick = onThreadClick
            )
        }
    }
}

@Composable
fun ThreadRow(threadInfo: ThreadInfo, roomToken: String, onThreadClick: (roomToken: String, threadId: Int) -> Unit) {
    val threadId = threadInfo.thread?.id
    val isClickable = threadId != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isClickable) {
                    Modifier.clickable {
                        onThreadClick(roomToken, threadId)
                    }
                } else {
                    Modifier
                }
            )
            .padding(vertical = 8.dp)
    ) {
        Text(text = threadInfo.first?.actorDisplayName.orEmpty(), style = MaterialTheme.typography.titleMedium)
        Text(text = threadInfo.first?.message.toString(), style = MaterialTheme.typography.bodySmall)
        val numRepliesText = threadInfo.thread?.numReplies?.toString() ?: "0"
        Text(text = "Replies: $numRepliesText", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun LoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun ErrorView(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = message, color = MaterialTheme.colorScheme.error)
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewLoadingIndicator() {
    MaterialTheme {
        LoadingIndicator()
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewErrorView() {
    MaterialTheme {
        ErrorView("This is a preview error message.")
    }
}
