/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.account

import android.accounts.Account
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.core.graphics.drawable.toDrawable
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import autodagger.AutoInjector
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.adapters.items.AdvancedUserItem
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.ActivitySwitchAccountBinding
import com.nextcloud.talk.models.ImportAccount
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.AccountUtils.findAvailableAccountsOnDevice
import com.nextcloud.talk.utils.AccountUtils.getInformationFromAccount
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_BASE_URL
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_IS_ACCOUNT_IMPORT
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_TOKEN
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_USERNAME
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.common.SmoothScrollLinearLayoutManager
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import org.osmdroid.config.Configuration
import java.net.CookieManager
import javax.inject.Inject

/**
 * Parts related to account import were either copied from or inspired by the great work done by David Luhmer at:
 * https://github.com/nextcloud/ownCloud-Account-Importer
 */
@AutoInjector(NextcloudTalkApplication::class)
class SwitchAccountActivity : BaseActivity() {
    private lateinit var binding: ActivitySwitchAccountBinding

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var cookieManager: CookieManager

    private var adapter: FlexibleAdapter<AbstractFlexibleItem<*>>? = null
    private val userItems: MutableList<AbstractFlexibleItem<*>> = ArrayList()
    private var isAccountImport = false

    private val onImportItemClickListener = FlexibleAdapter.OnItemClickListener { _, position ->
        if (userItems.size > position) {
            val account = (userItems[position] as AdvancedUserItem).account
            reauthorizeFromImport(account)
        }
        true
    }

    private val onSwitchItemClickListener = FlexibleAdapter.OnItemClickListener { _, position ->
        if (userItems.size > position) {
            val user = (userItems[position] as AdvancedUserItem).user

            if (userManager.setUserAsActive(user!!).blockingGet()) {
                cookieManager.cookieStore.removeAll()
                finish()
            }
        }
        true
    }

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedApplication!!.componentApplication.inject(this)
        binding = ActivitySwitchAccountBinding.inflate(layoutInflater)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setContentView(binding.root)
        setupActionBar()
        initSystemBars()

        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))

        handleIntent()
    }

    private fun handleIntent() {
        intent.extras?.let {
            if (it.containsKey(KEY_IS_ACCOUNT_IMPORT)) {
                isAccountImport = true
            }
        }
    }

    private fun setupActionBar() {
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setIcon(resources!!.getColor(R.color.transparent, null).toDrawable())
        supportActionBar?.title = resources!!.getString(R.string.nc_select_an_account)
    }

    @Suppress("Detekt.NestedBlockDepth")
    override fun onResume() {
        super.onResume()

        if (adapter == null) {
            adapter = FlexibleAdapter(userItems, this, false)
            var participant: Participant

            if (!isAccountImport) {
                for (user in userManager.users.blockingGet()) {
                    if (!user.current) {
                        val userId: String? = if (user.userId != null) {
                            user.userId
                        } else {
                            user.username
                        }
                        participant = Participant()
                        participant.actorType = Participant.ActorType.USERS
                        participant.actorId = userId
                        participant.displayName = user.displayName
                        userItems.add(AdvancedUserItem(participant, user, null, viewThemeUtils, 0))
                    }
                }
                adapter!!.addListener(onSwitchItemClickListener)
                adapter!!.updateDataSet(userItems, false)
            } else {
                var account: Account
                var importAccount: ImportAccount
                var user: User
                for (accountObject in findAvailableAccountsOnDevice(userManager.users.blockingGet())) {
                    account = accountObject
                    importAccount = getInformationFromAccount(account)
                    participant = Participant()
                    participant.actorType = Participant.ActorType.USERS
                    participant.actorId = importAccount.getUsername()
                    participant.displayName = importAccount.getUsername()
                    user = User()
                    user.baseUrl = importAccount.getBaseUrl()
                    userItems.add(AdvancedUserItem(participant, user, account, viewThemeUtils, 0))
                }
                adapter!!.addListener(onImportItemClickListener)
                adapter!!.updateDataSet(userItems, false)
            }
        }
        prepareViews()
    }

    private fun prepareViews() {
        val layoutManager: LinearLayoutManager = SmoothScrollLinearLayoutManager(this)
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.adapter = adapter
    }

    private fun reauthorizeFromImport(account: Account?) {
        val importAccount = getInformationFromAccount(account!!)
        val bundle = Bundle()
        bundle.putString(KEY_BASE_URL, importAccount.getBaseUrl())
        bundle.putString(KEY_USERNAME, importAccount.getUsername())
        bundle.putString(KEY_TOKEN, importAccount.getToken())
        bundle.putBoolean(KEY_IS_ACCOUNT_IMPORT, true)

        val intent = Intent(context, AccountVerificationActivity::class.java)
        intent.putExtras(bundle)
        startActivity(intent)
    }
}
