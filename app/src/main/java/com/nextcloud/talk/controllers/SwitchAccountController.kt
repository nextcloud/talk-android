/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Andy Scherzinger
 * Copyright (C) 2022 Andy Scherzinger (info@andy-scherzinger.de)
 * Copyright (C) 2017 Mario Danic <mario@lovelyhq.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Parts related to account import were either copied from or inspired by the great work done by David Luhmer at:
 * https://github.com/nextcloud/ownCloud-Account-Importer
 */
package com.nextcloud.talk.controllers

import android.accounts.Account
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import autodagger.AutoInjector
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.nextcloud.talk.R
import com.nextcloud.talk.adapters.items.AdvancedUserItem
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.controllers.base.NewBaseController
import com.nextcloud.talk.controllers.util.viewBinding
import com.nextcloud.talk.databinding.ControllerGenericRvBinding
import com.nextcloud.talk.models.ImportAccount
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.utils.AccountUtils.findAccounts
import com.nextcloud.talk.utils.AccountUtils.getInformationFromAccount
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_BASE_URL
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_IS_ACCOUNT_IMPORT
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_TOKEN
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_USERNAME
import com.nextcloud.talk.utils.database.user.UserUtils
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.common.SmoothScrollLinearLayoutManager
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import io.reactivex.Observer
import io.reactivex.disposables.Disposable
import org.osmdroid.config.Configuration
import java.net.CookieManager
import java.util.ArrayList
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class SwitchAccountController(args: Bundle? = null) :
    NewBaseController(
        R.layout.controller_generic_rv,
        args
    ) {
    private val binding: ControllerGenericRvBinding by viewBinding(ControllerGenericRvBinding::bind)

    @Inject
    lateinit var userUtils: UserUtils

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
            val userEntity = (userItems[position] as AdvancedUserItem).entity
            userUtils.createOrUpdateUser(
                null,
                null, null, null,
                null, java.lang.Boolean.TRUE, null, userEntity.id, null, null, null
            )
                .subscribe(object : Observer<UserEntity> {
                    override fun onSubscribe(d: Disposable) {
                        // unused atm
                    }
                    override fun onNext(userEntity: UserEntity) {
                        cookieManager.cookieStore.removeAll()
                        userUtils.disableAllUsersWithoutId(userEntity.id)
                        if (activity != null) {
                            activity!!.runOnUiThread { router.popCurrentController() }
                        }
                    }

                    override fun onError(e: Throwable) {
                        // unused atm
                    }
                    override fun onComplete() {
                        // unused atm
                    }
                })
        }
        true
    }

    init {
        setHasOptionsMenu(true)
        sharedApplication!!.componentApplication.inject(this)
        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
        if (args?.containsKey(KEY_IS_ACCOUNT_IMPORT) == true) {
            isAccountImport = true
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                router.popCurrentController()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onViewBound(view: View) {
        super.onViewBound(view)
        binding.swipeRefreshLayout.isEnabled = false

        actionBar?.show()

        if (adapter == null) {
            adapter = FlexibleAdapter(userItems, activity, false)
            var userEntity: UserEntity?
            var participant: Participant
            if (!isAccountImport) {
                for (userEntityObject in userUtils.users) {
                    userEntity = userEntityObject as UserEntity?
                    if (!userEntity!!.current) {
                        var userId: String?
                        userId = if (userEntity.userId != null) {
                            userEntity.userId
                        } else {
                            userEntity.username
                        }
                        participant = Participant()
                        participant.setActorType(Participant.ActorType.USERS)
                        participant.setActorId(userId)
                        participant.setDisplayName(userEntity.displayName)
                        userItems.add(AdvancedUserItem(participant, userEntity, null))
                    }
                }
                adapter!!.addListener(onSwitchItemClickListener)
                adapter!!.updateDataSet(userItems, false)
            } else {
                var account: Account
                var importAccount: ImportAccount
                for (accountObject in findAccounts(userUtils.users as List<UserEntity>)) {
                    account = accountObject
                    importAccount = getInformationFromAccount(account)
                    participant = Participant()
                    participant.setActorType(Participant.ActorType.USERS)
                    participant.setActorId(importAccount.getUsername())
                    participant.setDisplayName(importAccount.getUsername())
                    userEntity = UserEntity()
                    userEntity.baseUrl = importAccount.getBaseUrl()
                    userItems.add(AdvancedUserItem(participant, userEntity, account))
                }
                adapter!!.addListener(onImportItemClickListener)
                adapter!!.updateDataSet(userItems, false)
            }
        }
        prepareViews()
    }

    private fun prepareViews() {
        val layoutManager: LinearLayoutManager = SmoothScrollLinearLayoutManager(activity)
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.adapter = adapter
        binding.swipeRefreshLayout.isEnabled = false
    }

    private fun reauthorizeFromImport(account: Account?) {
        val importAccount = getInformationFromAccount(account!!)
        val bundle = Bundle()
        bundle.putString(KEY_BASE_URL, importAccount.getBaseUrl())
        bundle.putString(KEY_USERNAME, importAccount.getUsername())
        bundle.putString(KEY_TOKEN, importAccount.getToken())
        bundle.putBoolean(KEY_IS_ACCOUNT_IMPORT, true)
        router.pushController(
            RouterTransaction.with(AccountVerificationController(bundle))
                .pushChangeHandler(HorizontalChangeHandler())
                .popChangeHandler(HorizontalChangeHandler())
        )
    }

    override val title: String
        get() =
            resources!!.getString(R.string.nc_select_an_account)
}
