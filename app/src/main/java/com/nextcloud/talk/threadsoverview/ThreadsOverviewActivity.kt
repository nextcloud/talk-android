/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.threadsoverview

import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import autodagger.AutoInjector
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.ChatActivity
import com.nextcloud.talk.chat.ChatActivity.Companion.TAG
import com.nextcloud.talk.components.ColoredStatusBar
import com.nextcloud.talk.components.StandardAppBar
import com.nextcloud.talk.contacts.loadImage
import com.nextcloud.talk.models.json.threads.ThreadInfo
import com.nextcloud.talk.threadsoverview.components.ThreadRow
import com.nextcloud.talk.threadsoverview.viewmodels.ThreadsOverviewViewModel
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
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

    var roomToken: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        threadsOverviewViewModel = ViewModelProvider(
            this,
            viewModelFactory
        )[ThreadsOverviewViewModel::class.java]

        val colorScheme = viewThemeUtils.getColorScheme(this)

        val extras: Bundle? = intent.extras
        roomToken = extras?.getString(KEY_ROOM_TOKEN).orEmpty()

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
                            title = stringResource(R.string.recent_threads),
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
                                },
                                threadsOverviewViewModel
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
        threadsOverviewViewModel.init(roomToken)
    }

    companion object {
        val TAG = ThreadsOverviewActivity::class.java.simpleName
    }
}

@Composable
fun ThreadsOverviewScreen(
    uiState: ThreadsOverviewViewModel.ThreadsListUiState,
    roomToken: String,
    onThreadClick: (roomToken: String, threadId: Int) -> Unit,
    threadsOverviewViewModel: ThreadsOverviewViewModel
) {
    when (val state = uiState) {
        is ThreadsOverviewViewModel.ThreadsListUiState.None -> {
            LoadingIndicator()
        }

        is ThreadsOverviewViewModel.ThreadsListUiState.Success -> {
            ThreadsList(
                threads = state.threadsList!!,
                roomToken = roomToken,
                onThreadClick = onThreadClick,
                threadsOverviewViewModel
            )
        }

        is ThreadsOverviewViewModel.ThreadsListUiState.Error -> {
            Log.e(TAG, "Error when retrieving threads", uiState.exception)
            ErrorView(message = stringResource(R.string.nc_common_error_sorry))
        }
    }
}

@Composable
fun ThreadsList(
    threads: List<ThreadInfo>,
    roomToken: String,
    onThreadClick: (roomToken: String, threadId: Int) -> Unit,
    threadsOverviewViewModel: ThreadsOverviewViewModel
) {
    val context = LocalContext.current
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
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = threads,
            key = { threadInfo -> threadInfo.thread!!.id }
        ) { threadInfo ->
            val imageUri = ApiUtils.getUrlForAvatar(
                threadsOverviewViewModel.currentUser.baseUrl,
                threadInfo.first?.actorId,
                true
            )
            val errorPlaceholderImage: Int = R.drawable.account_circle_96dp
            val imageRequest = loadImage(imageUri, context, errorPlaceholderImage)

            ThreadRow(
                roomToken = roomToken,
                threadId = threadInfo.thread!!.id,
                firstLineTitle = threadInfo.first?.actorDisplayName.orEmpty(),
                firstLine = threadInfo.first?.message.orEmpty(),
                numReplies = String.format(
                    stringResource(
                        R.string.thread_replies_amount,
                        threadInfo.thread?.numReplies ?: 0
                    )
                ),
                secondLineTitle = threadInfo.last?.actorDisplayName?.let { "$it:" }.orEmpty(),
                secondLine = threadInfo.last?.message.orEmpty(),
                date = getLastActivityDate(threadInfo), // replace with value from api when available
                imageRequest = imageRequest,
                onClick = onThreadClick
            )
        }
    }
}

@Suppress("MagicNumber")
private fun getLastActivityDate(threadInfo: ThreadInfo): String {
    val oneSecond = 1000L

    val lastActivityTimestamp = threadInfo.last?.timestamp
        ?: threadInfo.first?.timestamp
        ?: 0

    val lastActivityDate = DateUtils.getRelativeTimeSpanString(
        lastActivityTimestamp.times(oneSecond),
        System.currentTimeMillis(),
        0,
        DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()
    return lastActivityDate
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
