/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * Copyright (C) 2023 Marcel Hibbe <dev@mhibbe.de>
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

package com.nextcloud.talk.openconversations

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import autodagger.AutoInjector
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.ChatActivity
import com.nextcloud.talk.databinding.ActivityOpenConversationsBinding
import com.nextcloud.talk.openconversations.data.OpenConversation
import com.nextcloud.talk.openconversations.viewmodels.OpenConversationsViewModel
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class ListOpenConversationsActivity : BaseActivity() {

    private lateinit var binding: ActivityOpenConversationsBinding

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    @Inject
    lateinit var userProvider: CurrentUserProviderNew

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

        val user = userProvider.currentUser.blockingGet()

        adapter = OpenConversationsAdapter(user) { conversation -> adapterOnClick(conversation) }
        binding.openConversationsRecyclerView.adapter = adapter

        initObservers()
    }

    private fun adapterOnClick(conversation: OpenConversation) {
        val user = userProvider.currentUser.blockingGet()

        val bundle = Bundle()
        bundle.putParcelable(BundleKeys.KEY_USER_ENTITY, user)
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
                    Toast.makeText(context, R.string.nc_common_error_sorry, Toast.LENGTH_LONG).show()
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
