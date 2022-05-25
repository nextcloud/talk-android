/*
 * Nextcloud Talk application
 *
 * @author Álvaro Brey
 * Copyright (C) 2022 Álvaro Brey
 * Copyright (C) 2022 Nextcloud GmbH
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
import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.ViewModelProvider
import autodagger.AutoInjector
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.adapters.items.LoadMoreResultsItem
import com.nextcloud.talk.adapters.items.MessageResultItem
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.controllers.ConversationsListController
import com.nextcloud.talk.databinding.ActivityMessageSearchBinding
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
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

    private lateinit var binding: ActivityMessageSearchBinding
    private lateinit var searchView: SearchView

    private lateinit var user: UserEntity

    private lateinit var viewModel: MessageSearchViewModel

    private var searchViewDisposable: Disposable? = null
    private var adapter: FlexibleAdapter<AbstractFlexibleItem<*>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        binding = ActivityMessageSearchBinding.inflate(layoutInflater)
        setupActionBar()
        setupSystemColors()
        setContentView(binding.root)

        viewModel = ViewModelProvider(this, viewModelFactory)[MessageSearchViewModel::class.java]
        user = intent.getParcelableExtra(BundleKeys.KEY_USER_ENTITY)!!
        val roomToken = intent.getStringExtra(BundleKeys.KEY_ROOM_TOKEN)!!
        viewModel.initialize(user, roomToken)
        setupStateObserver()
    }

    private fun setupActionBar() {
        setSupportActionBar(binding.messageSearchToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val conversationName = intent.getStringExtra(BundleKeys.KEY_CONVERSATION_NAME)
        supportActionBar?.title = conversationName
    }

    private fun setupSystemColors() {
        DisplayUtils.applyColorToStatusBar(
            this,
            ResourcesCompat.getColor(
                resources, R.color.appbar, null
            )
        )
        DisplayUtils.applyColorToNavigationBar(
            this.window,
            ResourcesCompat.getColor(resources, R.color.bg_default, null)
        )
    }

    private fun setupStateObserver() {
        viewModel.state.observe(this) { state ->
            when (state) {
                MessageSearchViewModel.EmptyState -> showEmpty()
                MessageSearchViewModel.InitialState -> showInitial()
                is MessageSearchViewModel.LoadedState -> showLoaded(state)
                MessageSearchViewModel.LoadingState -> showLoading()
                MessageSearchViewModel.ErrorState -> showError()
            }
        }
    }

    private fun showError() {
        Toast.makeText(this, "Error while searching", Toast.LENGTH_SHORT).show()
    }

    private fun showLoading() {
        // TODO
        Toast.makeText(this, "LOADING", Toast.LENGTH_LONG).show()
    }

    private fun showLoaded(state: MessageSearchViewModel.LoadedState) {
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
            state.results.map { MessageResultItem(this, user, it) } + loadMoreItems

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
                if (item?.itemViewType == LoadMoreResultsItem.VIEW_TYPE) {
                    viewModel.loadMore()
                }
                return false
            }
        })
    }

    private fun showInitial() {
        binding.messageSearchRecycler.visibility = View.GONE
        binding.emptyContainer.emptyListViewHeadline.text = "Start typing to search..."
        binding.emptyContainer.emptyListView.visibility = View.VISIBLE
    }

    private fun showEmpty() {
        binding.messageSearchRecycler.visibility = View.GONE
        binding.emptyContainer.emptyListViewHeadline.text = "No search results"
        binding.emptyContainer.emptyListView.visibility = View.VISIBLE
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_search, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val menuItem = menu!!.findItem(R.id.action_search)
        searchView = menuItem.actionView as SearchView
        setupSearchView()
        menuItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                searchView.requestFocus()
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                onBackPressed()
                return false
            }
        })
        menuItem.expandActionView()
        return true
    }

    private fun setupSearchView() {
        searchView.queryHint = getString(R.string.nc_search_hint)
        searchViewDisposable = observeSearchView(searchView)
            .debounce { query ->
                when {
                    TextUtils.isEmpty(query) -> Observable.empty()
                    else -> Observable.timer(
                        ConversationsListController.SEARCH_DEBOUNCE_INTERVAL_MS.toLong(),
                        TimeUnit.MILLISECONDS
                    )
                }
            }
            .distinctUntilChanged()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { newText -> viewModel.onQueryTextChange(newText) }
    }

    override fun onBackPressed() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        searchViewDisposable?.dispose()
    }
}
