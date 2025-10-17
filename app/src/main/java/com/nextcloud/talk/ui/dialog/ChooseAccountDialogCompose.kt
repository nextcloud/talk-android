/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.dialog

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import autodagger.AutoInjector
import coil.compose.AsyncImage
import com.nextcloud.talk.R
import com.nextcloud.talk.account.ServerSelectionActivity
import com.nextcloud.talk.account.data.model.AccountItem
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chooseaccount.StatusUiState
import com.nextcloud.talk.chooseaccount.StatusViewModel
import com.nextcloud.talk.contacts.loadImage
import com.nextcloud.talk.conversationlist.ConversationsListActivity
import com.nextcloud.talk.data.network.NetworkMonitor
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.invitation.viewmodels.InvitationsViewModel
import com.nextcloud.talk.models.json.status.Status
import com.nextcloud.talk.settings.SettingsActivity
import com.nextcloud.talk.ui.StatusDrawable
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.CapabilitiesUtil
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import java.net.CookieManager
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class ChooseAccountDialogCompose {
    init {
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
    }

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var currentUserProvider: CurrentUserProviderNew

    @Inject
    lateinit var cookieManager: CookieManager

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    @Inject
    lateinit var invitationsViewModel: InvitationsViewModel

    @Inject
    lateinit var statusViewModel: StatusViewModel

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    private val userItems = mutableStateListOf<AccountItem>()

    @Composable
    @Suppress("LongMethod")
    fun GetChooseAccountDialog(shouldDismiss: MutableState<Boolean>, activity: Activity) {
        if (shouldDismiss.value) return
        val colorScheme = viewThemeUtils.getColorScheme(activity)
        val status = remember { mutableStateOf<Status?>(null) }
        val context = LocalContext.current
        val statusViewState by statusViewModel.statusViewState.collectAsState()
        val invitationsState by invitationsViewModel.getInvitationsViewState.collectAsState()
        val isOnline by networkMonitor.isOnline.collectAsState()
        val currentUser = currentUserProvider.currentUser.blockingGet()!!
        val isStatusAvailable = CapabilitiesUtil.isUserStatusAvailable(currentUser)

        LaunchedEffect(currentUser) {
            val users = userManager.users.blockingGet()
            users.forEach { user ->
                if (!user.current) {
                    invitationsViewModel.getInvitations(user)
                }
            }
            if (isStatusAvailable) {
                statusViewModel.getStatus()
            }
        }
        LaunchedEffect(invitationsState) {
            userItems.clear()
            setupAccounts(invitationsState)
        }
        handleStatusState(statusViewState, status)
        MaterialTheme(colorScheme = colorScheme) {
            ChooseAccountDialogContent(
                shouldDismiss = shouldDismiss,
                colorScheme = colorScheme,
                currentUser = currentUser,
                status = status.value,
                isStatusAvailable = isStatusAvailable,
                isOnline = isOnline,
                accountItems = userItems,
                onCurrentUserClick = { shouldDismiss.value = true },
                onSetOnlineStatusClick = {
                    shouldDismiss.value = true
                    openSetOnlineStatusFragment(status.value, activity)
                },
                onSetStatusMessageClick = {
                    shouldDismiss.value = true
                    openSetStatusMessageFragment(status.value, activity)
                },
                onAddAccountClick = {
                    shouldDismiss.value = true
                    addAccount(activity)
                },
                onOpenSettingsClick = {
                    shouldDismiss.value = true
                    openSettings(activity)
                },
                accountRowContent = { user ->
                    AccountRow(user, activity) { shouldDismiss.value = true }
                },
                statusIndicator = { modifier ->
                    StatusIndicator(modifier = modifier, status = status.value, context = context)
                },
                context = context
            )
        }
    }

    private fun setupAccounts(invitationsUiState: InvitationsViewModel.ViewState) {
        userManager.users.blockingGet().forEach { user ->
            if (!user.current) {
                val pendingCount = getPendingInvitations(invitationsUiState)
                addAccountToList(user, user.userId ?: user.username, pendingCount)
            }
        }
    }

    private fun handleStatusState(statusViewState: StatusUiState, status: MutableState<Status?>) {
        when (statusViewState) {
            is StatusUiState.Success -> {
                status.value = statusViewState.status.ocs?.data!!
            }

            is StatusUiState.Error -> {
                Log.e(TAG, "Failed to get account status")
            }

            StatusUiState.None -> {
            }
        }
    }

    private fun openSetOnlineStatusFragment(status: Status?, activity: Activity) {
        val fragmentActivity = activity as FragmentActivity
        status?.let {
            val setStatusDialog = OnlineStatusBottomDialogFragment.newInstance(it)
            setStatusDialog.show(fragmentActivity.supportFragmentManager, "fragment_set_status")
        } ?: Log.w(TAG, "status was null")
    }

    private fun openSetStatusMessageFragment(status: Status?, activity: Activity) {
        val fragmentActivity = activity as FragmentActivity
        status?.let {
            val setStatusDialog = StatusMessageBottomDialogFragment.newInstance(it)
            setStatusDialog.show(fragmentActivity.supportFragmentManager, "fragment_set_status")
        } ?: Log.w(TAG, "status was null")
    }

    private fun addAccount(activity: Activity) {
        val intent = Intent(activity, ServerSelectionActivity::class.java)
        intent.putExtra(BundleKeys.ADD_ADDITIONAL_ACCOUNT, true)
        activity.startActivity(intent)
    }

    private fun openSettings(activity: Activity) {
        val intent = Intent(activity, SettingsActivity::class.java)
        activity.startActivity(intent)
    }

    @Composable
    private fun AccountRow(userItem: AccountItem, activity: Activity, onSelected: () -> Unit) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (userManager.setUserAsActive(userItem.user).blockingGet()) {
                        cookieManager.cookieStore.removeAll()
                        val intent = Intent(activity, ConversationsListActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        activity.startActivity(intent)
                        onSelected()
                    }
                }
                .padding(8.dp)
        ) {
            AsyncImage(
                model = ApiUtils.getUrlForAvatar(
                    userItem.user.baseUrl,
                    userItem.user.userId ?: userItem.user.username,
                    true
                ),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            )
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    text = userItem.user.displayName ?: userItem.user.username ?: ""
                )
                Text(
                    text = userItem.user.baseUrl!!.toUri().host ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorResource(id = R.color.low_emphasis_text)
                )
            }

            if (userItem.pendingInvitation > 0) {
                Box(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color.Red)
                )
            }
        }
    }

    fun getPendingInvitations(invitationsUiState: InvitationsViewModel.ViewState): Int =
        when (invitationsUiState) {
            is InvitationsViewModel.GetInvitationsSuccessState -> {
                invitationsUiState.invitations.size
            }

            else -> {
                0
            }
        }

    @Composable
    private fun StatusIndicator(modifier: Modifier = Modifier, status: Status?, context: Context) {
        status?.let {
            val size = remember { DisplayUtils.convertDpToPixel(STATUS_SIZE_DP, context) }
            val drawable = remember(it) { StatusDrawable(it.status, it.icon, size, 0, context) }
            viewThemeUtils.talk.themeStatusDrawable(context, drawable)
            AndroidView(
                factory = { ImageView(it) },
                modifier = modifier.size(16.dp)
            ) { imageView ->
                imageView.setImageDrawable(drawable)
            }
        }
    }
    companion object {
        private const val STATUS_SIZE_DP = 9f
        private val TAG = ChooseAccountDialogCompose::class.simpleName
    }

    private fun addAccountToList(user: User, userId: String?, pendingInvitations: Int) {
        userItems.add(AccountItem(user, userId, pendingInvitations))
    }
}

@Suppress("LongParameterList")
@Composable
private fun ChooseAccountDialogContent(
    shouldDismiss: MutableState<Boolean>,
    colorScheme: ColorScheme,
    currentUser: User,
    status: Status?,
    isStatusAvailable: Boolean,
    isOnline: Boolean,
    accountItems: List<AccountItem>,
    onCurrentUserClick: () -> Unit,
    onSetOnlineStatusClick: () -> Unit,
    onSetStatusMessageClick: () -> Unit,
    onAddAccountClick: () -> Unit,
    onOpenSettingsClick: () -> Unit,
    accountRowContent: @Composable (AccountItem) -> Unit,
    statusIndicator: @Composable (Modifier) -> Unit,
    context: Context
) {
    Dialog(onDismissRequest = { shouldDismiss.value = true }) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column {
                CurrentUserSection(
                    currentUser = currentUser,
                    status = status,
                    onCurrentUserClick = onCurrentUserClick,
                    colorScheme = colorScheme,
                    statusIndicator = statusIndicator,
                    context = context
                )
                if (isStatusAvailable) {
                    StatusActionButtons(
                        onSetOnlineStatusClick = onSetOnlineStatusClick,
                        onSetStatusMessageClick = onSetStatusMessageClick
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                LazyColumn(modifier = Modifier.padding(start = 8.dp).weight(1f, fill = false)) {
                    items(accountItems) { user ->
                        if (user.userId != currentUser.userId) {
                            accountRowContent(user)
                        }
                    }
                }

                OnlineActions(
                    onAddAccountClick = onAddAccountClick,
                    onOpenSettingsClick = onOpenSettingsClick,
                    isOnline = isOnline
                )
            }
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun CurrentUserSection(
    currentUser: User,
    status: Status?,
    onCurrentUserClick: () -> Unit,
    colorScheme: ColorScheme,
    statusIndicator: @Composable (Modifier) -> Unit,
    context: Context
) {
    Row(
        modifier = Modifier
            .padding(all = 16.dp)
            .clickable { onCurrentUserClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            val avatarUrl = ApiUtils.getUrlForAvatar(currentUser.baseUrl, currentUser.userId, true)
            val request = loadImage(
                avatarUrl,
                context,
                R.drawable.account_circle_96dp
            )
            AsyncImage(
                model = request,
                contentDescription = stringResource(R.string.user_avatar),
                modifier = Modifier.size(48.dp)
            )
            statusIndicator(Modifier.align(Alignment.BottomEnd))
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(text = currentUser.displayName ?: currentUser.username ?: "")
            status?.let {
                Column {
                    if (!it.message.isNullOrEmpty()) {
                        Text(
                            text = it.message!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorResource(id = R.color.low_emphasis_text)
                        )
                    }
                    Text(
                        currentUser.baseUrl!!.toUri().host ?: "",
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorResource(id = R.color.low_emphasis_text)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Icon(
            painterResource(id = R.drawable.ic_check_circle),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = colorScheme.primary
        )
    }
}

@Composable
private fun StatusActionButtons(onSetOnlineStatusClick: () -> Unit, onSetStatusMessageClick: () -> Unit) {
    Row {
        TextButton(onClick = onSetOnlineStatusClick) {
            Icon(
                painter = painterResource(R.drawable.ic_check_circle_outlined),
                contentDescription = null,
                tint = colorResource(id = R.color.high_emphasis_text)
            )
            Spacer(modifier = Modifier.padding(end = 8.dp))
            Text(
                text = stringResource(R.string.online_status),
                color = colorResource(id = R.color.high_emphasis_text),
                fontWeight = FontWeight.Bold
            )
        }

        TextButton(onClick = onSetStatusMessageClick) {
            Icon(
                painter = painterResource(R.drawable.baseline_chat_bubble_outline_24),
                contentDescription = null,
                tint = colorResource(id = R.color.high_emphasis_text)
            )
            Spacer(modifier = Modifier.padding(end = 8.dp))
            Text(
                text = stringResource(R.string.status_message),
                color = colorResource(id = R.color.high_emphasis_text),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun OnlineActions(onAddAccountClick: () -> Unit, onOpenSettingsClick: () -> Unit, isOnline: Boolean) {
    if (isOnline) {
        TextButton(onClick = onAddAccountClick, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(start = 16.dp, top = 8.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painterResource(R.drawable.ic_account_plus),
                    contentDescription = null,
                    tint = colorResource(id = R.color.high_emphasis_text)
                )
                Spacer(Modifier.size(16.dp))
                Text(
                    stringResource(R.string.nc_account_chooser_add_account),
                    color = colorResource(id = R.color.high_emphasis_text),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    TextButton(onClick = onOpenSettingsClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painterResource(R.drawable.ic_settings),
                contentDescription = null,
                tint = colorResource(id = R.color.high_emphasis_text)
            )
            Spacer(Modifier.size(16.dp))
            Text(
                stringResource(R.string.nc_settings),
                color = colorResource(id = R.color.high_emphasis_text),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChooseAccountDialogContentPreview() {
    val shouldDismiss = remember { mutableStateOf(false) }
    val sampleUser = User(
        userId = "user1",
        username = "user1",
        baseUrl = "https://example.com",
        displayName = "Sample User"
    )
    val sampleStatus = Status(
        userId = "user1",
        message = "Working remotely",
        messageId = "message-id",
        messageIsPredefined = false,
        icon = "",
        clearAt = 0,
        status = "online",
        statusIsUserDefined = true
    )
    MaterialTheme {
        val context = LocalContext.current
        ChooseAccountDialogContent(
            shouldDismiss = shouldDismiss,
            colorScheme = MaterialTheme.colorScheme,
            currentUser = sampleUser,
            status = sampleStatus,
            isStatusAvailable = true,
            isOnline = true,
            accountItems = emptyList(),
            onCurrentUserClick = {},
            onSetOnlineStatusClick = {},
            onSetStatusMessageClick = {},
            onAddAccountClick = {},
            onOpenSettingsClick = {},
            accountRowContent = {},
            statusIndicator = { modifier -> SampleStatusIndicator(modifier) },
            context = context
        )
    }
}

@Composable
private fun SampleStatusIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(Color.Green)
    )
}
