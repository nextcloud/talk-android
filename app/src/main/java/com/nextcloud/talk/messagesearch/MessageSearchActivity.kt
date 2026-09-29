/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Ezhil Shanmugham <ezhil56x.contact@gmail.com>
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-FileCopyrightText: 2022 Nextcloud GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.messagesearch

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.ViewModelProvider
import autodagger.AutoInjector
import com.google.android.material.snackbar.Snackbar
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.conversationlist.ConversationsListActivity
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.ActivityMessageSearchBinding
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.rx.SearchViewObservable.Companion.observeSearchView
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

    private lateinit var user: User

    private lateinit var viewModel: MessageSearchViewModel

    private var searchViewDisposable: Disposable? = null
    private lateinit var adapter: MessageSearchAdapter

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        binding = ActivityMessageSearchBinding.inflate(layoutInflater)
        setupActionBar()
        setContentView(binding.root)
        initSystemBars()

        viewModel = ViewModelProvider(this, viewModelFactory)[MessageSearchViewModel::class.java]
        user = currentUserProviderOld.currentUser.blockingGet()
        val roomToken = intent.getStringExtra(BundleKeys.KEY_ROOM_TOKEN)!!
        viewModel.initialize(roomToken)
        setupAdapter()
        setupStateObserver()

        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.refresh(searchView.query?.toString())
        }

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
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
        Snackbar.make(binding.root, "Error while searching", Snackbar.LENGTH_SHORT).show()
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

    private fun setupAdapter() {
        adapter = MessageSearchAdapter(
            user = user,
            viewThemeUtils = viewThemeUtils,
            onResultClick = { viewModel.selectMessage(it) },
            onLoadMoreClick = { viewModel.loadMore() }
        )
        binding.messageSearchRecycler.adapter = adapter
    }

    private fun setAdapterItems(state: MessageSearchViewModel.LoadedState) {
        val loadMoreItems = if (state.hasMore) {
            listOf(MessageSearchAdapter.Item.LoadMore)
        } else {
            emptyList()
        }
        adapter.submitList(state.results.map { MessageSearchAdapter.Item.Result(it) } + loadMoreItems)
    }

    private fun onFinish() {
        val state = viewModel.state.value
        if (state is MessageSearchViewModel.FinishedState) {
            val resultIntent = Intent().apply {
                putExtra(RESULT_KEY_MESSAGE_ID, state.selectedMessageId)
                putExtra(RESULT_KEY_THREAD_ID, state.selectedThreadId)
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
                onBackPressedDispatcher.onBackPressed()
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    override fun onDestroy() {
        super.onDestroy()
        searchViewDisposable?.dispose()
    }

    companion object {
        const val RESULT_KEY_MESSAGE_ID = "MessageSearchActivity.result.message"
        const val RESULT_KEY_THREAD_ID = "MessageSearchActivity.result.thread"
    }
}
