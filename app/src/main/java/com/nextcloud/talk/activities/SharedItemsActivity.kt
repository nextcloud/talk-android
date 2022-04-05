package com.nextcloud.talk.activities

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.nextcloud.talk.adapters.SharedItemsAdapter
import com.nextcloud.talk.databinding.ActivitySharedItemsBinding
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.viewmodels.SharedItemsViewModel

class SharedItemsActivity : AppCompatActivity() {
    companion object {
        private val TAG = SharedItemsActivity::class.simpleName
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val roomToken = intent.getStringExtra(BundleKeys.KEY_ROOM_TOKEN)!!
        val userEntity = intent.getParcelableExtra<UserEntity>(BundleKeys.KEY_USER_ENTITY)!!

        binding = ActivitySharedItemsBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

    private lateinit var binding: ActivitySharedItemsBinding

    private lateinit var viewModel: SharedItemsViewModel
}
