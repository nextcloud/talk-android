/*
 * Nextcloud Talk application
 *
 * @author Tim Krüger
 * @author Álvaro Brey
 * Copyright (C) 2022 Álvaro Brey
 * Copyright (C) 2022 Tim Krüger <t@timkrueger.me>
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
 */

package com.nextcloud.talk.shareditems.activities

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import autodagger.AutoInjector
import com.google.android.material.tabs.TabLayout
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.ActivitySharedItemsBinding
import com.nextcloud.talk.shareditems.adapters.SharedItemsAdapter
import com.nextcloud.talk.shareditems.model.SharedItemType
import com.nextcloud.talk.shareditems.viewmodels.SharedItemsViewModel
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_CONVERSATION_NAME
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_USER_ENTITY
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class SharedItemsActivity : AppCompatActivity() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    private lateinit var binding: ActivitySharedItemsBinding
    private lateinit var viewModel: SharedItemsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        val roomToken = intent.getStringExtra(KEY_ROOM_TOKEN)!!
        val conversationName = intent.getStringExtra(KEY_CONVERSATION_NAME)
        val user = intent.getParcelableExtra<User>(KEY_USER_ENTITY)!!

        binding = ActivitySharedItemsBinding.inflate(layoutInflater)
        setSupportActionBar(binding.sharedItemsToolbar)
        setContentView(binding.root)

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

        supportActionBar?.title = conversationName
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        viewModel = ViewModelProvider(this, viewModelFactory)[SharedItemsViewModel::class.java]

        viewModel.viewState.observe(this) { state ->
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

                    val adapter = SharedItemsAdapter(showGrid, user).apply {
                        items = sharedMediaItems.items
                    }
                    binding.imageRecycler.adapter = adapter
                    binding.imageRecycler.layoutManager = layoutManager
                }
                is SharedItemsViewModel.TypesLoadedState -> {
                    initTabs(state.types)
                }
            }
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

        if (sharedItemTypes.contains(SharedItemType.VOICE)) {
            val tabVoice: TabLayout.Tab = binding.sharedItemsTabs.newTab()
            tabVoice.tag = SharedItemType.VOICE
            tabVoice.setText(R.string.shared_items_voice)
            binding.sharedItemsTabs.addTab(tabVoice)
        }

        // if(sharedItemTypes.contains(SharedItemType.LOCATION)) {
        // val tabLocation: TabLayout.Tab = binding.sharedItemsTabs.newTab()
        // tabLocation.tag = SharedItemType.LOCATION
        // tabLocation.text = "location"
        // binding.sharedItemsTabs.addTab(tabLocation)
        // }

        // if(sharedItemTypes.contains(SharedItemType.DECKCARD)) {
        // val tabDeckCard: TabLayout.Tab = binding.sharedItemsTabs.newTab()
        // tabDeckCard.tag = SharedItemType.DECKCARD
        // tabDeckCard.text = "deckcard"
        // binding.sharedItemsTabs.addTab(tabDeckCard)
        // }

        // if(sharedItemTypes.contains(SharedItemType.OTHER)) {
        // val tabOther: TabLayout.Tab = binding.sharedItemsTabs.newTab()
        // tabOther.tag = SharedItemType.OTHER
        // tabOther.setText(R.string.shared_items_other)
        // binding.sharedItemsTabs.addTab(tabOther)
        // }

        binding.sharedItemsTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.initialLoadItems(tab.tag as SharedItemType)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit

            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            onBackPressed()
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    companion object {
        private val TAG = SharedItemsActivity::class.simpleName
        const val SPAN_COUNT: Int = 4
    }
}
