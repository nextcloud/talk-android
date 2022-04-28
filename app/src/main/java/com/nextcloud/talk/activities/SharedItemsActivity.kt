package com.nextcloud.talk.activities

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.nextcloud.talk.R
import com.nextcloud.talk.adapters.SharedItemsAdapter
import com.nextcloud.talk.databinding.ActivitySharedItemsBinding
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_CONVERSATION_NAME
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_USER_ENTITY
import com.nextcloud.talk.viewmodels.SharedItemsViewModel

class SharedItemsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySharedItemsBinding
    private lateinit var viewModel: SharedItemsViewModel
    private lateinit var currentTab: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        currentTab = TAB_AUDIO

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

        initTabs()

        viewModel = ViewModelProvider(
            this,
            SharedItemsViewModel.Factory(userEntity, roomToken, currentTab)
        ).get(SharedItemsViewModel::class.java)

        viewModel.media.observe(this) {
            Log.d(TAG, "Items received: $it")
            val adapter = SharedItemsAdapter()
            adapter.items = it.items
            adapter.authHeader = it.authHeader
            binding.imageRecycler.adapter = adapter

            if (currentTab == "media") {
                val layoutManager = GridLayoutManager(this, 4)
                binding.imageRecycler.layoutManager = layoutManager
            } else {
                val layoutManager = LinearLayoutManager(this)
                layoutManager.orientation = LinearLayoutManager.VERTICAL
                binding.imageRecycler.layoutManager = layoutManager
            }

            adapter.notifyDataSetChanged()
        }
    }

    fun updateItems(type: String) {
        currentTab = type
        viewModel.loadMediaItems(type)
    }

    private fun initTabs() {
        val tabAudio: TabLayout.Tab = binding.sharedItemsTabs.newTab()
        tabAudio.tag = TAB_AUDIO
        tabAudio.text = "audio"
        binding.sharedItemsTabs.addTab(tabAudio)

        // val tabDeckCard: TabLayout.Tab = binding.sharedItemsTabs.newTab()
        // tabDeckCard.tag = TAB_DECKCARD
        // tabDeckCard.text = "deckcard"
        // binding.sharedItemsTabs.addTab(tabDeckCard)

        val tabFile: TabLayout.Tab = binding.sharedItemsTabs.newTab()
        tabFile.tag = TAB_FILE
        tabFile.text = "file"
        binding.sharedItemsTabs.addTab(tabFile)

        // val tabLocation: TabLayout.Tab = binding.sharedItemsTabs.newTab()
        // tabLocation.tag = TAB_LOCATION
        // tabLocation.text = "location"
        // binding.sharedItemsTabs.addTab(tabLocation)

        val tabMedia: TabLayout.Tab = binding.sharedItemsTabs.newTab()
        tabMedia.tag = TAB_MEDIA
        tabMedia.text = "media"
        binding.sharedItemsTabs.addTab(tabMedia)

        val tabVoice: TabLayout.Tab = binding.sharedItemsTabs.newTab()
        tabVoice.tag = TAB_VOICE
        tabVoice.text = "voice"
        binding.sharedItemsTabs.addTab(tabVoice)

        val tabOther: TabLayout.Tab = binding.sharedItemsTabs.newTab()
        tabOther.tag = TAB_OTHER
        tabOther.text = "other"
        binding.sharedItemsTabs.addTab(tabOther)

        binding.sharedItemsTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                updateItems(tab.tag as String)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
            }

            override fun onTabReselected(tab: TabLayout.Tab) {
            }
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
        const val TAB_AUDIO = "audio"
        const val TAB_FILE = "file"
        const val TAB_MEDIA = "media"
        const val TAB_VOICE = "voice"
        const val TAB_LOCATION = "location"
        const val TAB_DECKCARD = "deckcard"
        const val TAB_OTHER = "other"
    }
}
