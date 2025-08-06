/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe (dev@mhibbe.de)
 * SPDX-FileCopyrightText: 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.ui.dialog

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.DialogFragment
import autodagger.AutoInjector
import coil.compose.AsyncImage
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nextcloud.talk.R
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.conversationlist.ConversationsListActivity
import com.nextcloud.talk.data.network.NetworkMonitor
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.invitation.data.InvitationsModel
import com.nextcloud.talk.invitation.data.InvitationsRepository
import com.nextcloud.talk.models.json.status.Status
import com.nextcloud.talk.models.json.status.StatusOverall
import com.nextcloud.talk.contacts.loadImage
import com.nextcloud.talk.ui.StatusDrawable
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.CapabilitiesUtil
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import java.net.CookieManager
import javax.inject.Inject
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import androidx.core.net.toUri
import com.nextcloud.talk.account.ServerSelectionActivity
import com.nextcloud.talk.settings.SettingsActivity
import com.nextcloud.talk.utils.bundle.BundleKeys.ADD_ADDITIONAL_ACCOUNT

@AutoInjector(NextcloudTalkApplication::class)
class ChooseAccountDialogFragment : DialogFragment() {
    companion object {
        const val TAG = "ChooseAccountDialogFragment"
        private const val STATUS_SIZE_IN_DP = 9f
        fun newInstance() = ChooseAccountDialogFragment()
    }

    @Inject
    lateinit var userManager: UserManager
    @Inject lateinit var currentUserProvider: CurrentUserProviderNew
    @Inject lateinit var cookieManager: CookieManager
    @Inject lateinit var ncApi: NcApi
    @Inject lateinit var viewThemeUtils: ViewThemeUtils
    @Inject lateinit var invitationsRepository: InvitationsRepository
    @Inject lateinit var networkMonitor: NetworkMonitor

    private var disposable: Disposable? = null

    private var status by mutableStateOf<Status?>(null)
    private var currentUser by mutableStateOf<User?>(null)
    private var isOnline by mutableStateOf(true)
    private var isStatusAvailable by mutableStateOf(false)
    private val userItems = mutableStateListOf<AccountItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val composeView = ComposeView(requireContext())
        composeView.setContent {
            ChooseAccountDialogView()
        }
        return MaterialAlertDialogBuilder(requireContext())
            .setView(composeView)
            .create()
    }

    override fun onStart() {
        super.onStart()
        currentUser = currentUserProvider.currentUser.blockingGet()
        currentUser?.let { user ->
            isStatusAvailable = CapabilitiesUtil.isUserStatusAvailable(user)
            if (isStatusAvailable) {
                loadCurrentStatus(user)
            }
            setupAccounts(user)
        }
        networkMonitor.isOnlineLiveData.observe(this) { online ->
            isOnline = online
        }
    }

    override fun onPause() {
        super.onPause()
        userItems.clear()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (disposable != null && !disposable!!.isDisposed) {
            disposable!!.dispose()
        }

    }

    private fun setupAccounts(user: User) {
        userManager.users.blockingGet().forEach { u ->
            if (!u.current) {
                val userId = u.userId ?: u.username
                invitationsRepository.fetchInvitations(u)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(object : Observer<InvitationsModel> {
                        override fun onSubscribe(d: Disposable) {
                            disposable = d
                        }
                        override fun onNext(model: InvitationsModel) {
                            addAccountToList(u, userId, model.invitations.size)
                        }
                        override fun onError(e: Throwable) {
                            Log.e(TAG, "Failed to fetch invitations", e)
                            addAccountToList(u, userId, 0)
                        }
                        override fun onComplete() {}
                    })
            }
        }
    }

    private fun addAccountToList(user: User, userId: String?, actionsRequired: Int) {
        userItems.add(AccountItem(user, userId, actionsRequired))
    }

    private fun loadCurrentStatus(user: User) {
        val credentials = ApiUtils.getCredentials(user.username, user.token)
        ncApi.status(credentials, ApiUtils.getUrlForStatus(user.baseUrl!!))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<StatusOverall> {
                override fun onSubscribe(d: Disposable) {}
                override fun onNext(result: StatusOverall) {
                    result.ocs?.data?.let { status = it }
                }
                override fun onError(e: Throwable) {
                    Log.e(TAG, "Can't receive user status from server. ", e)
                }
                override fun onComplete() {}
            })
    }

    private fun switchAccount(user: User) {
        if (userManager.setUserAsActive(user).blockingGet()) {
            cookieManager.cookieStore.removeAll()
            val intent = Intent(context, ConversationsListActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
            dismiss()
        }
    }



    @Composable
    private fun ChooseAccountDialogView() {
        val context = LocalContext.current
        MaterialTheme(colorScheme = viewThemeUtils.getColorScheme(context)) {
            Surface {
                Column(modifier = Modifier.padding(4.dp)) {
                    currentUser?.let { user ->
                        CurrentAccount(user)
                    }
                    if (isStatusAvailable) {
                        TextButton(
                            onClick = { openStatus() },
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
                    HorizontalDivider()
                    LazyColumn {
                        items(userItems) { item ->
                            AccountItemRow(item)
                        }
                    }
                    if (isOnline) {
                        TextButton(onClick = { addAccount() }, modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(painterResource(R.drawable.ic_account_plus), contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text(stringResource(R.string.nc_account_chooser_add_account))
                            }
                        }
                    }
                    TextButton(onClick = { openSettings() }, modifier = Modifier.fillMaxWidth()) {
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

    @Composable
    private fun CurrentAccount(user: User) {
        val context = LocalContext.current
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { dismiss() }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                val avatarUrl = ApiUtils.getUrlForAvatar(user.baseUrl, user.userId, true)
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
                StatusIndicator(modifier = Modifier.align(Alignment.BottomEnd))
            }
            Column(modifier = Modifier.padding(start = 8.dp).weight(1f)) {
                Text(user.displayName ?: "")
                Text(user.baseUrl!!.toUri().host ?: "", style = MaterialTheme.typography.bodySmall)
                status?.message?.takeIf { it.isNotEmpty() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    @Composable
    private fun AccountItemRow(item: AccountItem) {
        val context = LocalContext.current
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { switchAccount(item.user) }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val avatarUrl = ApiUtils.getUrlForAvatar(item.user.baseUrl, item.userId, true)
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
            Column(modifier = Modifier.padding(start = 8.dp).weight(1f)) {
                Text(item.user.displayName ?: "")
                Text(item.user.baseUrl!!.toUri().host ?: "", style = MaterialTheme.typography.bodySmall)
            }
            if (item.actionsRequired > 0) {
                Icon(
                    painterResource(R.drawable.accent_circle),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = Color.Red
                )
            }
        }
    }

    private fun addAccount() {
        val intent = Intent(context, ServerSelectionActivity::class.java)
        intent.putExtra(ADD_ADDITIONAL_ACCOUNT, true)
        startActivity(intent)
        dismiss()
    }

    private fun openSettings() {
        val intent = Intent(context, SettingsActivity::class.java)
        startActivity(intent)
        dismiss()
    }

    private fun openStatus() {
        dismiss()
        status?.let {
            val setStatusDialog = SetStatusDialogFragment.newInstance(it)
            setStatusDialog.show(parentFragmentManager, "fragment_set_status")
        } ?: Log.w(TAG, "status was null")
    }



    @Composable
    private fun StatusIndicator(modifier: Modifier = Modifier) {
        status?.let {
            val context = LocalContext.current
            val size = remember { DisplayUtils.convertDpToPixel(STATUS_SIZE_IN_DP, context) }
            val drawable = remember(it) { StatusDrawable(it.status, it.icon, size, 0, context) }
            viewThemeUtils.talk.themeStatusDrawable(context, drawable)
            AndroidView(
                factory = { android.widget.ImageView(it) },
                modifier = modifier.size(16.dp)
            ) { imageView ->
                imageView.setImageDrawable(drawable)
            }
        }
    }

    data class AccountItem(val user: User, val userId: String?, val actionsRequired: Int)
}
