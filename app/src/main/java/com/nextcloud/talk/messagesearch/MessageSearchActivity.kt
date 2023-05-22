/*
 * Nextcloud Talk application
 *
 * @author Álvaro Brey
 * @author Ezhil Shanmugham
 * Copyright (C) 2022 Álvaro Brey
 * Copyright (C) 2022 Nextcloud GmbH
 * Copyright (C) 2023 Ezhil Shanmugham <ezhil56x.contact@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.messagesearch

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.ViewModelProvider
import autodagger.AutoInjector
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.adapters.items.LoadMoreResultsItem
import com.nextcloud.talk.adapters.items.MessageResultItem
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.conversationlist.ConversationsListActivity
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.ActivityMessageSearchBinding
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import com.nextcloud.talk.utils.rx.SearchViewObservable.Companion.observeSearchView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.viewholders.FlexibleViewHolder
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class MessageSearchActivity : BaseActivity() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    @Inject
    lateinit var userProvider: CurrentUserProviderNew

    private lateinit var binding: ActivityMessageSearchBinding
    private lateinit var searchView: SearchView

    private lateinit var user: User

    private lateinit var viewModel: MessageSearchViewModel

    private var searchViewDisposable: Disposable? = null
    private var adapter: FlexibleAdapter<AbstractFlexibleItem<*>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        binding = ActivityMessageSearchBinding.inflate(layoutInflater)
        setupActionBar()
        setContentView(binding.root)
        setupSystemColors()

        viewModel = ViewModelProvider(this, viewModelFactory)[MessageSearchViewModel::class.java]
        user = userProvider.currentUser.blockingGet()
        val roomToken = intent.getStringExtra(BundleKeys.KEY_ROOM_TOKEN)!!
        viewModel.initialize(roomToken)
        setupStateObserver()

        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.refresh(searchView.query?.toString())
        }
    }

    private fun setupActionBar() {
        setSupportActionBar(binding.messageSearchToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val conversationName = intent.getStringExtra(BundleKeys.KEY_CONVERSATION_NAME)
        supportActionBar?.title = conversationName
        viewThemeUtils.material.themeToolbar(binding.messageSearchToolbar)
    }

    private fun setupStateObserver() {
        viewModel.state.observe(this) { state ->
            when (state) {
                MessageSearchViewModel.InitialState -> showInitial()
                MessageSearchViewModel.EmptyState -> showEmpty()
                is MessageSearchViewModel.LoadedState -> showLoaded(state)
                MessageSearchViewModel.LoadingState -> showLoading()
                MessageSearchViewModel.ErrorState -> showError()
                is MessageSearchViewModel.FinishedState -> onFinish()
            }
        }
    }

    private fun showError() {
        displayLoading(false)
        Toast.makeText(this, "Error while searching", Toast.LENGTH_SHORT).show()
    }

    private fun showLoading() {
        displayLoading(true)
    }

    private fun displayLoading(loading: Boolean) {
        binding.swipeRefreshLayout.isRefreshing = loading
    }

    private fun showLoaded(state: MessageSearchViewModel.LoadedState) {
        displayLoading(false)
        binding.emptyContainer.emptyListView.visibility = View.GONE
        binding.messageSearchRecycler.visibility = View.VISIBLE
        setAdapterItems(state)
    }

    private fun setAdapterItems(state: MessageSearchViewModel.LoadedState) {
        val loadMoreItems = if (state.hasMore) {
            listOf(LoadMoreResultsItem)
        } else {
            emptyList()
        }
        val newItems =
            state.results.map { MessageResultItem(this, user, it, false, viewThemeUtils) } + loadMoreItems

        if (adapter != null) {
            adapter!!.updateDataSet(newItems)
        } else {
            createAdapter(newItems)
        }
    }

    private fun createAdapter(items: List<AbstractFlexibleItem<out FlexibleViewHolder>>) {
        adapter = FlexibleAdapter(items)
        binding.messageSearchRecycler.adapter = adapter
        adapter!!.addListener(object : FlexibleAdapter.OnItemClickListener {
            override fun onItemClick(view: View?, position: Int): Boolean {
                val item = adapter!!.getItem(position)
                when (item?.itemViewType) {
                    LoadMoreResultsItem.VIEW_TYPE -> {
                        viewModel.loadMore()
                    }
                    MessageResultItem.VIEW_TYPE -> {
                        val messageItem = item as MessageResultItem
                        viewModel.selectMessage(messageItem.messageEntry)
                    }
                }
                return false
            }
        })
    }

    private fun onFinish() {
        val state = viewModel.state.value
        if (state is MessageSearchViewModel.FinishedState) {
            val resultIntent = Intent().apply {
                putExtra(RESULT_KEY_MESSAGE_ID, state.selectedMessageId)
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }

    private fun showInitial() {
        displayLoading(false)
        binding.messageSearchRecycler.visibility = View.GONE
        binding.emptyContainer.emptyListViewHeadline.text = getString(R.string.message_search_begin_typing)
        binding.emptyContainer.emptyListView.visibility = View.VISIBLE
    }

    private fun showEmpty() {
        displayLoading(false)
        binding.messageSearchRecycler.visibility = View.GONE
        binding.emptyContainer.emptyListViewHeadline.text = getString(R.string.message_search_begin_empty)
        binding.emptyContainer.emptyListView.visibility = View.VISIBLE
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_search, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val menuItem = menu.findItem(R.id.action_search)
        searchView = menuItem.actionView as SearchView
        setupSearchView()
        menuItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                searchView.requestFocus()
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                handleOnBackPressed()
                return false
            }
        })
        menuItem.expandActionView()
        return true
    }

    private fun setupSearchView() {
        searchView.queryHint = getString(R.string.message_search_hint)
        searchViewDisposable = observeSearchView(searchView)
            .debounce { query ->
                when {
                    TextUtils.isEmpty(query) -> Observable.empty()
                    else -> Observable.timer(
                        ConversationsListActivity.SEARCH_DEBOUNCE_INTERVAL_MS.toLong(),
                        TimeUnit.MILLISECONDS
                    )
                }
            }
            .distinctUntilChanged()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { newText -> viewModel.onQueryTextChange(newText) }
    }

    fun handleOnBackPressed() {
        setResult(Activity.RESULT_CANCELED)
        finishAffinity()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                handleOnBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        searchViewDisposable?.dispose()
    }

    companion object {
        const val RESULT_KEY_MESSAGE_ID = "MessageSearchActivity.result.message"
    }
}
