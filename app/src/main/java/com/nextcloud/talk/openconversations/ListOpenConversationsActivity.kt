/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.openconversations

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import autodagger.AutoInjector
import com.google.android.material.snackbar.Snackbar
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.ChatActivity
import com.nextcloud.talk.databinding.ActivityOpenConversationsBinding
import com.nextcloud.talk.openconversations.adapters.OpenConversationsAdapter
import com.nextcloud.talk.openconversations.data.OpenConversation
import com.nextcloud.talk.openconversations.viewmodels.OpenConversationsViewModel
import com.nextcloud.talk.utils.bundle.BundleKeys
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class ListOpenConversationsActivity : BaseActivity() {

    private lateinit var binding: ActivityOpenConversationsBinding

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    lateinit var openConversationsViewModel: OpenConversationsViewModel

    lateinit var adapter: OpenConversationsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        openConversationsViewModel = ViewModelProvider(this, viewModelFactory)[OpenConversationsViewModel::class.java]

        openConversationsViewModel.fetchConversations()

        binding = ActivityOpenConversationsBinding.inflate(layoutInflater)
        setupActionBar()
        setContentView(binding.root)
        setupSystemColors()

        val user = currentUserProvider.currentUser.blockingGet()

        adapter = OpenConversationsAdapter(user) { conversation -> adapterOnClick(conversation) }
        binding.openConversationsRecyclerView.adapter = adapter

        initObservers()
    }

    private fun adapterOnClick(conversation: OpenConversation) {
        val bundle = Bundle()
        bundle.putString(BundleKeys.KEY_ROOM_TOKEN, conversation.roomToken)

        val chatIntent = Intent(context, ChatActivity::class.java)
        chatIntent.putExtras(bundle)
        chatIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(chatIntent)
    }

    private fun initObservers() {
        openConversationsViewModel.viewState.observe(this) { state ->
            when (state) {
                is OpenConversationsViewModel.FetchConversationsStartState -> {
                    binding.openConversationsRecyclerView.visibility = View.GONE
                    binding.progressBarWrapper.visibility = View.VISIBLE
                }
                is OpenConversationsViewModel.FetchConversationsSuccessState -> {
                    binding.openConversationsRecyclerView.visibility = View.VISIBLE
                    binding.progressBarWrapper.visibility = View.GONE
                    adapter.submitList(state.conversations)
                }
                is OpenConversationsViewModel.FetchConversationsEmptyState -> {
                    binding.openConversationsRecyclerView.visibility = View.GONE
                    binding.progressBarWrapper.visibility = View.GONE

                    binding.emptyList.emptyListView.visibility = View.VISIBLE
                    binding.emptyList.emptyListViewHeadline.text = getString(R.string.nc_no_open_conversations_headline)
                    binding.emptyList.emptyListViewText.text = getString(R.string.nc_no_open_conversations_text)
                    binding.emptyList.emptyListIcon.setImageResource(R.drawable.baseline_info_24)
                    binding.emptyList.emptyListIcon.visibility = View.VISIBLE
                    binding.emptyList.emptyListViewText.visibility = View.VISIBLE
                }
                is OpenConversationsViewModel.FetchConversationsErrorState -> {
                    Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
                }
                else -> {}
            }
        }
    }

    private fun setupActionBar() {
        setSupportActionBar(binding.openConversationsToolbar)
        binding.openConversationsToolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setIcon(ColorDrawable(resources!!.getColor(R.color.transparent, null)))
        viewThemeUtils.material.themeToolbar(binding.openConversationsToolbar)
    }
}
