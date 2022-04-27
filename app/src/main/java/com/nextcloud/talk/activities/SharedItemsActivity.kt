package com.nextcloud.talk.activities

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.nextcloud.talk.adapters.SharedItemsAdapter
import com.nextcloud.talk.databinding.ActivitySharedItemsBinding
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_CONVERSATION_NAME
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_USER_ENTITY
import com.nextcloud.talk.viewmodels.SharedItemsViewModel

class SharedItemsActivity : AppCompatActivity() {
    companion object {
        private val TAG = SharedItemsActivity::class.simpleName
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val roomToken = intent.getStringExtra(KEY_ROOM_TOKEN)!!
        val conversationName = intent.getStringExtra(KEY_CONVERSATION_NAME)
        val userEntity = intent.getParcelableExtra<UserEntity>(KEY_USER_ENTITY)!!

        binding = ActivitySharedItemsBinding.inflate(layoutInflater)
        setSupportActionBar(binding.sharedItemsToolbar)
        setContentView(binding.root)

        supportActionBar?.title = conversationName
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        viewModel = ViewModelProvider(
            this,
            SharedItemsViewModel.Factory(userEntity, roomToken)
        ).get(SharedItemsViewModel::class.java)

        viewModel.media.observe(this) {
            Log.d(TAG, "Items received: $it")
            val adapter = SharedItemsAdapter()
            adapter.items = it.items
            adapter.authHeader = it.authHeader
            binding.imageRecycler.adapter = adapter
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            onBackPressed()
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    private lateinit var binding: ActivitySharedItemsBinding

    private lateinit var viewModel: SharedItemsViewModel
}
