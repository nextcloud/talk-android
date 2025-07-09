/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.ui.dialog

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import autodagger.AutoInjector
import com.google.android.material.snackbar.Snackbar
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.conversationinfo.viewmodel.ConversationInfoViewModel
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.BanItemListBinding
import com.nextcloud.talk.databinding.FragmentDialogBanListBinding
import com.nextcloud.talk.models.json.participants.TalkBan
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class DialogBanListFragment(val roomToken: String) : DialogFragment() {

    lateinit var binding: FragmentDialogBanListBinding

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    @Inject
    lateinit var currentUserProvider: CurrentUserProviderNew

    lateinit var viewModel: ConversationInfoViewModel
    private lateinit var conversationUser: User

    private val adapter = object : BaseAdapter() {
        private var bans: List<TalkBan> = mutableListOf()

        fun setItems(items: List<TalkBan>) {
            bans = items
        }

        override fun getCount(): Int = bans.size

        override fun getItem(position: Int): Any = bans[position]

        override fun getItemId(position: Int): Long = bans[position].bannedTime!!.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val binding = BanItemListBinding.inflate(LayoutInflater.from(context))
            binding.banActorName.text = bans[position].bannedDisplayName
            val time = bans[position].bannedTime!!.toLong() * ONE_SEC
            binding.banTime.text = DateUtils.formatDateTime(
                requireContext(),
                time,
                (DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME)
            )
            binding.banReason.text = bans[position].internalNote
            binding.unbanBtn.setOnClickListener {
                unBanActor(bans[position].id!!.toInt())
            }
            return binding.root
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        binding = FragmentDialogBanListBinding.inflate(layoutInflater)
        viewModel =
            ViewModelProvider(this, viewModelFactory)[ConversationInfoViewModel::class.java]
        conversationUser = currentUserProvider.currentUser.blockingGet()

        themeView()
        initObservers()
        initListeners()
        getBanList()
        return binding.root
    }

    private fun initObservers() {
        viewModel.getTalkBanState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ConversationInfoViewModel.ListBansSuccessState -> {
                    adapter.setItems(state.talkBans)
                    binding.banListView.adapter = adapter
                }

                is ConversationInfoViewModel.ListBansErrorState -> {}
                else -> {}
            }
        }

        viewModel.getUnBanActorState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ConversationInfoViewModel.UnBanActorSuccessState -> {
                    getBanList()
                }

                is ConversationInfoViewModel.UnBanActorErrorState -> {
                    Snackbar.make(binding.root, getString(R.string.error_unbanning), Snackbar.LENGTH_SHORT).show()
                }

                else -> {}
            }
        }
    }

    private fun themeView() {
        viewThemeUtils.platform.colorViewBackground(binding.root)
    }

    private fun initListeners() {
        binding.closeBtn.setOnClickListener { dismiss() }
    }

    private fun getBanList() {
        viewModel.listBans(conversationUser, roomToken)
    }

    private fun unBanActor(banId: Int) {
        viewModel.unbanActor(conversationUser, roomToken, banId)
    }

    companion object {
        @JvmStatic
        fun newInstance(roomToken: String) = DialogBanListFragment(roomToken)
        const val ONE_SEC = 1000L
    }
}
