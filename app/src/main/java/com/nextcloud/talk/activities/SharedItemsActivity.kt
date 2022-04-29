package com.nextcloud.talk.activities

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.nextcloud.talk.R
import com.nextcloud.talk.adapters.SharedItemsGridAdapter
import com.nextcloud.talk.adapters.SharedItemsListAdapter
import com.nextcloud.talk.databinding.ActivitySharedItemsBinding
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.repositories.SharedItemType
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_CONVERSATION_NAME
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_USER_ENTITY
import com.nextcloud.talk.viewmodels.SharedItemsViewModel

class SharedItemsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySharedItemsBinding
    private lateinit var viewModel: SharedItemsViewModel
    private lateinit var currentTab: SharedItemType

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        currentTab = SharedItemType.MEDIA

        val roomToken = intent.getStringExtra(KEY_ROOM_TOKEN)!!
        val conversationName = intent.getStringExtra(KEY_CONVERSATION_NAME)
        val userEntity = intent.getParcelableExtra<UserEntity>(KEY_USER_ENTITY)!!

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

        viewModel = ViewModelProvider(
            this,
            SharedItemsViewModel.Factory(userEntity, roomToken, currentTab)
        ).get(SharedItemsViewModel::class.java)

        viewModel.sharedItemType.observe(this) {
            initTabs(it)
        }

        viewModel.sharedItems.observe(this) {
            Log.d(TAG, "Items received: $it")

            if (currentTab == SharedItemType.MEDIA) {
                val adapter = SharedItemsGridAdapter()
                adapter.items = it.items
                adapter.authHeader = it.authHeader
                binding.imageRecycler.adapter = adapter

                val layoutManager = GridLayoutManager(this, SPAN_COUNT)
                binding.imageRecycler.layoutManager = layoutManager
            } else {
                val adapter = SharedItemsListAdapter()
                adapter.items = it.items
                adapter.authHeader = it.authHeader
                binding.imageRecycler.adapter = adapter

                val layoutManager = LinearLayoutManager(this)
                layoutManager.orientation = LinearLayoutManager.VERTICAL
                binding.imageRecycler.layoutManager = layoutManager
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
    }

    fun updateItems(type: SharedItemType) {
        currentTab = type
        viewModel.loadItems(type)
    }

    private fun initTabs(sharedItemTypes: Set<SharedItemType>) {

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
                updateItems(tab.tag as SharedItemType)
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
