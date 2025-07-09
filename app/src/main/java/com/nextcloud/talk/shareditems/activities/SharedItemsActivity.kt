/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2023 Ezhil Shanmugham <ezhil56x.contact@gmail.com>
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-FileCopyrightText: 2022 Tim Krüger <t@timkrueger.me>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.shareditems.activities

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import autodagger.AutoInjector
import com.google.android.material.tabs.TabLayout
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.ActivitySharedItemsBinding
import com.nextcloud.talk.shareditems.adapters.SharedItemsAdapter
import com.nextcloud.talk.shareditems.model.SharedItemType
import com.nextcloud.talk.shareditems.viewmodels.SharedItemsViewModel
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_CONVERSATION_NAME
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class SharedItemsActivity : BaseActivity() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    private lateinit var binding: ActivitySharedItemsBinding
    private lateinit var viewModel: SharedItemsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        val roomToken = intent.getStringExtra(KEY_ROOM_TOKEN)!!
        val conversationName = intent.getStringExtra(KEY_CONVERSATION_NAME)

        val user = currentUserProvider.currentUser.blockingGet()

        val isUserConversationOwnerOrModerator = intent.getBooleanExtra(KEY_USER_IS_OWNER_OR_MODERATOR, false)

        binding = ActivitySharedItemsBinding.inflate(layoutInflater)
        setSupportActionBar(binding.sharedItemsToolbar)
        setContentView(binding.root)

        initSystemBars()

        viewThemeUtils.material.themeToolbar(binding.sharedItemsToolbar)
        viewThemeUtils.material.themeTabLayoutOnSurface(binding.sharedItemsTabs)

        supportActionBar?.title = conversationName
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        viewModel = ViewModelProvider(this, viewModelFactory)[SharedItemsViewModel::class.java]

        viewModel.viewState.observe(this) { state ->
            handleModelChange(state, user, roomToken, isUserConversationOwnerOrModerator)
        }

        binding.imageRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (!recyclerView.canScrollVertically(1) && newState == RecyclerView.SCROLL_STATE_IDLE) {
                    viewModel.loadNextItems()
                }
            }
        })

        viewModel.initialize(user, roomToken)
    }

    private fun handleModelChange(
        state: SharedItemsViewModel.ViewState?,
        user: User,
        roomToken: String,
        isUserConversationOwnerOrModerator: Boolean
    ) {
        clearEmptyLoading()
        when (state) {
            is SharedItemsViewModel.LoadingItemsState, SharedItemsViewModel.InitialState -> {
                showLoading()
            }
            is SharedItemsViewModel.NoSharedItemsState -> {
                showEmpty()
            }
            is SharedItemsViewModel.LoadedState -> {
                val sharedMediaItems = state.items
                Log.d(TAG, "Items received: $sharedMediaItems")

                val showGrid = state.selectedType == SharedItemType.MEDIA
                val layoutManager = if (showGrid) {
                    GridLayoutManager(this, SPAN_COUNT)
                } else {
                    LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
                }

                val adapter = SharedItemsAdapter(
                    showGrid,
                    user,
                    roomToken,
                    isUserConversationOwnerOrModerator,
                    viewThemeUtils
                ).apply {
                    items = sharedMediaItems.items
                }
                binding.imageRecycler.adapter = adapter
                binding.imageRecycler.layoutManager = layoutManager
            }
            is SharedItemsViewModel.TypesLoadedState -> {
                initTabs(state.types)
            }
            else -> {}
        }

        viewThemeUtils.material.themeTabLayoutOnSurface(binding.sharedItemsTabs)
    }

    private fun clearEmptyLoading() {
        binding.sharedItemsTabs.visibility = View.VISIBLE
        binding.emptyContainer.emptyListView.visibility = View.GONE
    }

    private fun showLoading() {
        binding.emptyContainer.emptyListViewHeadline.text = getString(R.string.file_list_loading)
        binding.emptyContainer.emptyListView.visibility = View.VISIBLE
    }

    private fun showEmpty() {
        binding.emptyContainer.emptyListViewHeadline.text = getString(R.string.nc_shared_items_empty)
        binding.emptyContainer.emptyListView.visibility = View.VISIBLE
        binding.sharedItemsTabs.visibility = View.GONE
    }

    private fun initTabs(sharedItemTypes: Set<SharedItemType>) {
        binding.sharedItemsTabs.removeAllTabs()

        if (sharedItemTypes.contains(SharedItemType.MEDIA)) {
            val tabMedia: TabLayout.Tab = binding.sharedItemsTabs.newTab()
            tabMedia.tag = SharedItemType.MEDIA
            tabMedia.setText(R.string.shared_items_media)
            binding.sharedItemsTabs.addTab(tabMedia)
        }

        if (sharedItemTypes.contains(SharedItemType.FILE)) {
            val tabFile: TabLayout.Tab = binding.sharedItemsTabs.newTab()
            tabFile.tag = SharedItemType.FILE
            tabFile.setText(R.string.shared_items_file)
            binding.sharedItemsTabs.addTab(tabFile)
        }

        if (sharedItemTypes.contains(SharedItemType.AUDIO)) {
            val tabAudio: TabLayout.Tab = binding.sharedItemsTabs.newTab()
            tabAudio.tag = SharedItemType.AUDIO
            tabAudio.setText(R.string.shared_items_audio)
            binding.sharedItemsTabs.addTab(tabAudio)
        }

        if (sharedItemTypes.contains(SharedItemType.RECORDING)) {
            val tabRecording: TabLayout.Tab = binding.sharedItemsTabs.newTab()
            tabRecording.tag = SharedItemType.RECORDING
            tabRecording.setText(R.string.shared_items_recording)
            binding.sharedItemsTabs.addTab(tabRecording)
        }

        if (sharedItemTypes.contains(SharedItemType.VOICE)) {
            val tabVoice: TabLayout.Tab = binding.sharedItemsTabs.newTab()
            tabVoice.tag = SharedItemType.VOICE
            tabVoice.setText(R.string.shared_items_voice)
            binding.sharedItemsTabs.addTab(tabVoice)
        }

        if (sharedItemTypes.contains(SharedItemType.POLL)) {
            val tabVoice: TabLayout.Tab = binding.sharedItemsTabs.newTab()
            tabVoice.tag = SharedItemType.POLL
            tabVoice.setText(R.string.shared_items_poll)
            binding.sharedItemsTabs.addTab(tabVoice)
        }

        if (sharedItemTypes.contains(SharedItemType.LOCATION)) {
            val tabLocation: TabLayout.Tab = binding.sharedItemsTabs.newTab()
            tabLocation.tag = SharedItemType.LOCATION
            tabLocation.setText(R.string.nc_shared_items_location)
            binding.sharedItemsTabs.addTab(tabLocation)
        }

        if (sharedItemTypes.contains(SharedItemType.DECKCARD)) {
            val tabDeckCard: TabLayout.Tab = binding.sharedItemsTabs.newTab()
            tabDeckCard.tag = SharedItemType.DECKCARD
            tabDeckCard.setText(R.string.nc_shared_items_deck_card)
            binding.sharedItemsTabs.addTab(tabDeckCard)
        }

        if (sharedItemTypes.contains(SharedItemType.OTHER)) {
            val tabOther: TabLayout.Tab = binding.sharedItemsTabs.newTab()
            tabOther.tag = SharedItemType.OTHER
            tabOther.setText(R.string.shared_items_other)
            binding.sharedItemsTabs.addTab(tabOther)
        }

        binding.sharedItemsTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.initialLoadItems(tab.tag as SharedItemType)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit

            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            true
        } else {
            super.onOptionsItemSelected(item)
        }

    companion object {
        private val TAG = SharedItemsActivity::class.simpleName
        const val SPAN_COUNT: Int = 4
        const val KEY_USER_IS_OWNER_OR_MODERATOR = "userIsOwnerOrModerator"
    }
}
