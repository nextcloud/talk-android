/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.dialog

import android.content.res.Configuration
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import com.nextcloud.talk.R
import com.nextcloud.talk.adapters.ReactionItem
import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.reactions.ReactionVoter
import com.nextcloud.talk.utils.ApiUtils
import java.util.Collections

private const val TAG = "ShowReactionsSheet"

@Suppress("LongParameterList")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowReactionsModalBottomSheet(
    chatMessage: ChatMessage,
    user: User,
    roomToken: String,
    hasReactPermission: Boolean,
    ncApiCoroutines: NcApiCoroutines,
    onDeleteReaction: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        ReactionsSheetContent(
            chatMessage = chatMessage,
            user = user,
            roomToken = roomToken,
            hasReactPermission = hasReactPermission,
            ncApiCoroutines = ncApiCoroutines,
            onDeleteReaction = onDeleteReaction,
            onDismiss = onDismiss
        )
    }
}

@Suppress("LongMethod", "LongParameterList", "TooGenericExceptionCaught")
@Composable
internal fun ReactionsSheetContent(
    chatMessage: ChatMessage,
    user: User,
    roomToken: String,
    hasReactPermission: Boolean,
    ncApiCoroutines: NcApiCoroutines,
    onDeleteReaction: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val allReactions = chatMessage.reactions ?: return
    val reactions = LinkedHashMap(allReactions.filter { it.value > 0 })
    if (reactions.isEmpty()) return

    val emojiList = reactions.keys.toList()
    val tabs: List<String?> = listOf(null) + emojiList
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var reactionItems by remember { mutableStateOf<List<ReactionItem>>(emptyList()) }
    val selectedEmoji = tabs[selectedTabIndex]

    val credentials = remember(user) { ApiUtils.getCredentials(user.username, user.token) }
    val reactionApiUrl = remember(user, roomToken, chatMessage) {
        ApiUtils.getUrlForMessageReaction(
            baseUrl = user.baseUrl!!,
            roomToken = roomToken,
            messageId = chatMessage.jsonMessageId.toString()
        )
    }

    LaunchedEffect(selectedEmoji) {
        try {
            val reactionsOverall = ncApiCoroutines.getReactions(credentials, reactionApiUrl, selectedEmoji)
            val voters = buildList {
                val map = reactionsOverall.ocs?.data ?: return@buildList
                for (key in map.keys) {
                    for (voter in map[key]!!) {
                        add(ReactionItem(voter, key))
                    }
                }
            }
            reactionItems = ArrayList(voters).also { Collections.sort(it, ReactionComparator(user.userId)) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load reactions", e)
        }
    }

    ReactionsSheetLayout(
        reactions = reactions,
        reactionItems = reactionItems,
        selectedTabIndex = selectedTabIndex,
        onTabSelected = { selectedTabIndex = it },
        user = user,
        credentials = credentials,
        hasReactPermission = hasReactPermission,
        onDeleteReaction = onDeleteReaction,
        onDismiss = onDismiss
    )
}

@Suppress("LongParameterList")
@Composable
internal fun ReactionsSheetLayout(
    reactions: LinkedHashMap<String, Int>,
    reactionItems: List<ReactionItem>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    user: User,
    credentials: String?,
    hasReactPermission: Boolean,
    onDeleteReaction: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val tabs: List<String?> = listOf(null) + reactions.keys.toList()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
    ) {
        val reactionsTotal = reactions.values.sum()
        PrimaryScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            tabs.forEachIndexed { index, emoji ->
                val count = if (emoji == null) reactionsTotal else reactions[emoji] ?: 0
                val label = if (emoji == null) {
                    "${stringResource(R.string.reactions_tab_all)} $count"
                } else {
                    "$emoji $count"
                }
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { onTabSelected(index) },
                    text = { Text(label) }
                )
            }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 288.dp)
        ) {
            items(reactionItems) { reactionItem ->
                ReactionVoterRow(
                    reactionItem = reactionItem,
                    user = user,
                    credentials = credentials,
                    hasReactPermission = hasReactPermission,
                    onDeleteReaction = { emoji ->
                        onDeleteReaction(emoji)
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun ReactionVoterRow(
    reactionItem: ReactionItem,
    user: User,
    credentials: String?,
    hasReactPermission: Boolean,
    onDeleteReaction: (String) -> Unit
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val canDelete = hasReactPermission && reactionItem.reactionVoter.actorId == user.userId
    val guestLabel = stringResource(R.string.nc_guest)

    val avatarUrl = remember(reactionItem, isDark) {
        when (reactionItem.reactionVoter.actorType) {
            ReactionVoter.ReactionActorType.GUESTS -> {
                val displayName = reactionItem.reactionVoter.actorDisplayName
                    ?.takeIf { it.isNotEmpty() }
                    ?: guestLabel
                ApiUtils.getUrlForGuestAvatar(user.baseUrl, displayName, false)
            }
            ReactionVoter.ReactionActorType.USERS -> {
                ApiUtils.getUrlForAvatar(user.baseUrl, reactionItem.reactionVoter.actorId, false, isDark)
            }
            else -> null
        }
    }

    val avatarRequest = remember(avatarUrl, credentials) {
        avatarUrl?.let {
            ImageRequest.Builder(context)
                .data(it)
                .transformations(CircleCropTransformation())
                .addHeader("Authorization", credentials ?: "")
                .build()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canDelete && reactionItem.reaction != null) {
                reactionItem.reaction?.let(onDeleteReaction)
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = avatarRequest ?: R.drawable.account_circle_96dp,
            contentDescription = null,
            placeholder = painterResource(R.drawable.account_circle_96dp),
            error = painterResource(R.drawable.account_circle_96dp),
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = reactionItem.reactionVoter.actorDisplayName ?: "",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = reactionItem.reaction ?: "",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

private class ReactionComparator(private val activeUser: String?) : Comparator<ReactionItem> {
    @Suppress("ReturnCount")
    override fun compare(item1: ReactionItem?, item2: ReactionItem?): Int {
        if (item1 == null && item2 == null) return 0
        if (item1 == null) return -1
        if (item2 == null) return 1

        val reaction = compareNullableStrings(item1.reaction, item2.reaction)
        if (reaction != 0) return reaction

        val ownAccount = compareOwnAccount(item1.reactionVoter.actorId, item2.reactionVoter.actorId)
        if (ownAccount != 0) return ownAccount

        val displayName = compareNullableStrings(
            item1.reactionVoter.actorDisplayName,
            item2.reactionVoter.actorDisplayName
        )
        if (displayName != 0) return displayName

        val timestamp = compareNullableLongs(item1.reactionVoter.timestamp, item2.reactionVoter.timestamp)
        if (timestamp != 0) return timestamp

        return compareNullableStrings(item1.reactionVoter.actorId, item2.reactionVoter.actorId)
    }

    @Suppress("ReturnCount")
    private fun compareOwnAccount(actorId1: String?, actorId2: String?): Int {
        val vote1Active = activeUser == actorId1
        val vote2Active = activeUser == actorId2
        if (vote1Active == vote2Active) return 0
        if (activeUser == null) return 0
        return if (vote1Active) 1 else -1
    }

    @Suppress("ReturnCount")
    private fun compareNullableStrings(s1: String?, s2: String?): Int {
        if (s1 == null && s2 == null) return 0
        if (s1 == null) return -1
        if (s2 == null) return 1
        return s1.lowercase().compareTo(s2.lowercase())
    }

    @Suppress("ReturnCount")
    private fun compareNullableLongs(l1: Long?, l2: Long?): Int {
        if (l1 == null && l2 == null) return 0
        if (l1 == null) return -1
        if (l2 == null) return 1
        return l1.compareTo(l2)
    }
}

private val previewUser = User(
    id = 1,
    userId = "alice",
    username = "alice",
    baseUrl = "https://nextcloud.example.com",
    displayName = "Alice"
)

@Suppress("MagicNumber")
private val previewReactions = linkedMapOf("👍" to 3, "❤️" to 2, "😂" to 1)

private val previewReactionItems = listOf(
    ReactionItem(
        ReactionVoter(ReactionVoter.ReactionActorType.USERS, "alice", "Alice", 0),
        "👍"
    ),
    ReactionItem(
        ReactionVoter(ReactionVoter.ReactionActorType.USERS, "bob", "Bob", 0),
        "👍"
    ),
    ReactionItem(
        ReactionVoter(ReactionVoter.ReactionActorType.USERS, "carol", "Carol", 0),
        "👍"
    ),
    ReactionItem(
        ReactionVoter(ReactionVoter.ReactionActorType.USERS, "dave", "Dave", 0),
        "❤️"
    ),
    ReactionItem(
        ReactionVoter(ReactionVoter.ReactionActorType.GUESTS, "guest1", "مروة", 0),
        "😂"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "RTL · Arabic", locale = "ar")
@Composable
private fun PreviewReactionsSheet() {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        Surface {
            ReactionsSheetLayout(
                reactions = previewReactions,
                reactionItems = previewReactionItems,
                selectedTabIndex = 0,
                onTabSelected = {},
                user = previewUser,
                credentials = null,
                hasReactPermission = true,
                onDeleteReaction = {},
                onDismiss = {}
            )
        }
    }
}
