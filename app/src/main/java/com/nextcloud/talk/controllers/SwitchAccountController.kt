/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
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
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import butterknife.BindView
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.nextcloud.talk.R
import com.nextcloud.talk.adapters.items.AdvancedUserItem
import com.nextcloud.talk.controllers.base.BaseController
import com.nextcloud.talk.models.ImportAccount
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.newarch.domain.repository.offline.UsersRepository
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.newarch.local.models.other.UserStatus
import com.nextcloud.talk.utils.AccountUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.database.user.UserUtils
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.common.SmoothScrollLinearLayoutManager
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import java.net.CookieManager
import java.util.*

class SwitchAccountController : BaseController {
    @JvmField
    @BindView(R.id.recyclerView)
    internal var recyclerView: RecyclerView? = null

    val cookieManager: CookieManager by inject()
    val usersRepository: UsersRepository by inject()

    @JvmField
    @BindView(R.id.swipe_refresh_layout)
    internal var swipeRefreshLayout: SwipeRefreshLayout? = null
    private var adapter: FlexibleAdapter<AbstractFlexibleItem<*>>? = null
    private val userItems = ArrayList<AbstractFlexibleItem<*>>()

    private var isAccountImport = false

    private val onImportItemClickListener = FlexibleAdapter.OnItemClickListener { view, position ->
        if (userItems.size > position) {
            val account = (userItems[position] as AdvancedUserItem).account
            reauthorizeFromImport(account)
        }

        true
    }

    private val onSwitchItemClickListener = FlexibleAdapter.OnItemClickListener { view, position ->
        if (userItems.size > position) {
            val userEntity = (userItems[position] as AdvancedUserItem).entity
            GlobalScope.launch {
                usersRepository.setUserAsActiveWithId(userEntity!!.id!!)
                cookieManager.cookieStore.removeAll()
                withContext(Dispatchers.Main) {
                    router.popCurrentController()
                }
            }
        }

        true
    }

    constructor() {
        setHasOptionsMenu(true)
    }

    constructor(args: Bundle) : super() {
        setHasOptionsMenu(true)

        if (args.containsKey(BundleKeys.KEY_IS_ACCOUNT_IMPORT)) {
            isAccountImport = true
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                router.popCurrentController()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun inflateView(inflater: LayoutInflater, container: ViewGroup): View {
        return inflater.inflate(R.layout.controller_generic_rv, container, false)
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup
    ): View {
        swipeRefreshLayout?.isEnabled = false
        actionBar?.show()

        adapter = FlexibleAdapter(userItems, activity, false)
        GlobalScope.launch {
            val users = usersRepository.getUsers()
            var userEntity: UserNgEntity
            var participant: Participant

            if (isAccountImport) {
                var account: Account
                var importAccount: ImportAccount
                for (accountObject in AccountUtils.findAccounts(users)) {
                    account = accountObject
                    importAccount = AccountUtils.getInformationFromAccount(account)

                    participant = Participant()
                    participant.name = importAccount.username
                    participant.userId = importAccount.username
                    userEntity = UserNgEntity(-1, "!", "!", importAccount.baseUrl)
                    userItems.add(AdvancedUserItem(participant, userEntity, account))
                }

                adapter!!.addListener(onImportItemClickListener)
                withContext(Dispatchers.Main) {
                    adapter!!.updateDataSet(userItems, false)
                }


            } else {
                for (userEntityObject in users) {
                    userEntity = userEntityObject
                    if (userEntity.status != UserStatus.ACTIVE) {
                        participant = Participant()
                        participant.name = userEntity.displayName

                        val userId: String

                        if (userEntity.userId != null) {
                            userId = userEntity.userId
                        } else {
                            userId = userEntity.username
                        }
                        participant.userId = userId
                        userItems.add(AdvancedUserItem(participant, userEntity, null))
                    }
                }

                adapter!!.addListener(onSwitchItemClickListener)
                withContext(Dispatchers.Main) {
                    adapter!!.updateDataSet(userItems, false)
                }

            }

        }
        return super.onCreateView(inflater, container)
    }
        override fun onViewBound(view: View) {
        super.onViewBound(view)
        swipeRefreshLayout!!.isEnabled = false

        if (actionBar != null) {
            actionBar!!.show()
        }

        /*

        if (adapter == null) {
            adapter = FlexibleAdapter(userItems, activity, false)

            var userEntity: UserNgEntity
            var participant: Participant

            if (!isAccountImport) {
                for (userEntityObject in userUtils!!.users) {
                    userEntity = userEntityObject as UserEntity
                    if (!userEntity.getCurrent()) {
                        participant = Participant()
                        participant.setName(userEntity.displayName)

                        val userId: String

                        if (userEntity.userId != null) {
                            userId = userEntity.userId
                        } else {
                            userId = userEntity.username
                        }
                        participant.setUserId(userId)
                        userItems.add(AdvancedUserItem(participant, userEntity, null))
                    }
                }

                adapter!!.addListener(onSwitchItemClickListener)
                adapter!!.updateDataSet(userItems, false)
            } else {
                var account: Account
                var importAccount: ImportAccount
                for (accountObject in AccountUtils.findAccounts(userUtils!!.users)) {
                    account = accountObject
                    importAccount = AccountUtils.getInformationFromAccount(account)

                    participant = Participant()
                    participant.name = importAccount.username
                    participant.userId = importAccount.username
                    userEntity = UserEntity()
                    userEntity.baseUrl = importAccount.getBaseUrl()
                    userItems.add(AdvancedUserItem(participant, userEntity, account))
                }

                adapter!!.addListener(onImportItemClickListener)
                adapter!!.updateDataSet(userItems, false)
            }
        }*/

        prepareViews()
    }

    private fun prepareViews() {
        val layoutManager = SmoothScrollLinearLayoutManager(activity!!)
        recyclerView!!.layoutManager = layoutManager
        recyclerView!!.setHasFixedSize(true)
        recyclerView!!.adapter = adapter

        swipeRefreshLayout!!.isEnabled = false
    }

    private fun reauthorizeFromImport(account: Account?) {
        val importAccount = AccountUtils.getInformationFromAccount(account!!)
        val bundle = Bundle()
        bundle.putString(BundleKeys.KEY_BASE_URL, importAccount.baseUrl)
        bundle.putString(BundleKeys.KEY_USERNAME, importAccount.username)
        bundle.putString(BundleKeys.KEY_TOKEN, importAccount.token)
        bundle.putBoolean(BundleKeys.KEY_IS_ACCOUNT_IMPORT, true)
        router.pushController(RouterTransaction.with(AccountVerificationController(bundle))
                .pushChangeHandler(HorizontalChangeHandler())
                .popChangeHandler(HorizontalChangeHandler()))
    }

    override fun getTitle(): String? {
        return resources!!.getString(R.string.nc_select_an_account)
    }
}
