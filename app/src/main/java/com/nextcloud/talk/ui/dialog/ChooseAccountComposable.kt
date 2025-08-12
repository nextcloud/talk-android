package com.nextcloud.talk.ui.dialog

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.ImageView
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import autodagger.AutoInjector
import coil.compose.AsyncImage
import com.nextcloud.talk.R
import com.nextcloud.talk.account.ServerSelectionActivity
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
    fun GetChooseAccountDialog(shouldDismiss: MutableState<Boolean>, activity: Activity) {
        if (shouldDismiss.value) return
        val colorScheme = viewThemeUtils.getColorScheme(activity)
        val currentUser = currentUserProvider.currentUser.blockingGet()
        val status = remember { mutableStateOf<Status?>(null) }
        val isStatusAvailable = CapabilitiesUtil.isUserStatusAvailable(currentUser)
        val context = LocalContext.current

        LaunchedEffect(currentUser) {
            userItems.clear()
            invitationsViewModel.getInvitations(currentUser)
            if (isStatusAvailable) {
                statusViewModel.getStatus()
            }
        }

        val statusViewState by statusViewModel.statusViewState.collectAsState()
        val invitationsState by invitationsViewModel.getInvitationsViewState.collectAsState()
        val isOnline by networkMonitor.isOnline.collectAsState()

        when (statusViewState) {
            is StatusUiState.Success -> {
                status.value = (statusViewState as StatusUiState.Success).status.ocs?.data!!
            }

            is StatusUiState.Error -> {
                Log.e("ChooseAccount", "Failed to get account status")
            }

            StatusUiState.None -> {
            }
        }

        SetupAccounts(invitationsState)

        MaterialTheme(colorScheme = colorScheme) {
            Dialog(onDismissRequest = { shouldDismiss.value = true }) {
                Surface(shape = RoundedCornerShape(24.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.clickable {
                                shouldDismiss.value = true
                            },
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
                                StatusIndicator(modifier = Modifier.align(Alignment.BottomEnd), status.value, context)
                            }
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text(text = currentUser.displayName ?: currentUser.username ?: "")
                                status.let { status ->
                                    Column {
                                        if (!status.value?.message.isNullOrEmpty()) {
                                            Text(
                                                text = status.value?.message!!,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        Text(
                                            currentUser.baseUrl!!.toUri().host ?: "",
                                            modifier = Modifier.padding(top = 4.dp),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))
                            Icon(
                                painterResource(id = R.drawable.ic_check_circle),
                                contentDescription = null,
                                tint = colorScheme.primary
                            )
                        }

                        if (isStatusAvailable) {
                            TextButton(
                                onClick = {
                                    shouldDismiss.value = true
                                    openStatus(status.value, activity)
                                },
                                enabled = status != null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(painterResource(R.drawable.ic_edit), contentDescription = null)
                                    Spacer(Modifier.size(8.dp))
                                    Text(stringResource(R.string.set_status))
                                }
                            }
                        }

                        // val hasInvitations =
                        //     (invitationsState as? InvitationsViewModel.FetchInvitationsSuccessState)
                        //         ?.invitations
                        //         ?.isNotEmpty() == true
                        // if (hasInvitations) {
                        //     Text(
                        //         text = activity.getString(R.string.nc_federation_pending_invitation_hint),
                        //         modifier = Modifier.padding(top = 4.dp)
                        //     )
                        // }
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                            items(userItems) { user ->
                                if (user.userId != currentUser.userId) {
                                    AccountRow(userItems, activity) { shouldDismiss.value = true }
                                }
                            }
                        }
                        if (isOnline) {
                            TextButton(onClick = {
                                shouldDismiss.value = true
                                addAccount(activity)
                            }, modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(painterResource(R.drawable.ic_account_plus), contentDescription = null)
                                    Spacer(Modifier.size(8.dp))
                                    Text(stringResource(R.string.nc_account_chooser_add_account))
                                }
                            }

                            TextButton(onClick = {
                                shouldDismiss.value = true
                                openSettings(activity)
                            }, modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(painterResource(R.drawable.ic_settings), contentDescription = null)
                                    Spacer(Modifier.size(8.dp))
                                    Text(stringResource(R.string.nc_settings))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun openStatus(status: Status?, activity: Activity) {
        val fragmentActivity = activity as FragmentActivity
        status?.let {
            val setStatusDialog = SetStatusDialogFragment.newInstance(it)
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
    private fun AccountRow(userItem: List<AccountItem>, activity: Activity, onSelected: () -> Unit) {
        userItem.forEach { userItem ->
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
                Text(
                    text = userItem.user.displayName ?: userItem.user.username ?: "",
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }

    @Composable
    private fun SetupAccounts(invitationsUiState: InvitationsViewModel.ViewState) {
        userManager.users.blockingGet().forEach { user ->
            if (!user.current) {
                addAccountToList(user, user.userId ?: user.username, 0)
            }
        }
        userManager.users.blockingGet().forEach { user ->
            if (!user.current) {
                val userId = user.userId ?: user.username

                when (invitationsUiState) {
                    InvitationsViewModel.GetInvitationsEmptyState -> {
                    }
                    is InvitationsViewModel.GetInvitationsErrorState -> {
                        addAccountToList(user, userId, 0)
                    }
                    InvitationsViewModel.GetInvitationsStartState -> {
                    }
                    is InvitationsViewModel.GetInvitationsSuccessState -> {
                        addAccountToList(user, userId, invitationsUiState.invitations.size)
                    }
                    else -> {
                    }
                }
            }
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

    private fun addAccountToList(user: User, userId: String?, actionsRequired: Int) {
        userItems.add(AccountItem(user, userId, actionsRequired))
    }
}

data class AccountItem(val user: User, val userId: String?, val pendingInvitations: Int)
