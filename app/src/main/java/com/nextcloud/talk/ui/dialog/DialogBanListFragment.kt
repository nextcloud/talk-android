/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.ui.dialog

import android.annotation.SuppressLint
import android.graphics.drawable.Icon
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
    private lateinit var rightArrow: Icon
    private lateinit var downArrow: Icon

    private val adapter = object : BaseAdapter() {
        private var bans: List<TalkBan> = mutableListOf()
        private var banState: Array<Boolean> = emptyArray()

        fun setItems(items: List<TalkBan>) {
            bans = items
            banState = Array(items.size) { false }
        }

        override fun getCount(): Int {
            return bans.size
        }

        override fun getItem(position: Int): Any {
            return bans[position]
        }

        override fun getItemId(position: Int): Long {
            return bans[position].bannedTime!!.toLong()
        }

        @SuppressLint("ViewHolder")
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val binding = BanItemListBinding.inflate(LayoutInflater.from(context))
            binding.banActorName.text = bans[position].bannedId
            val time = bans[position].bannedTime!!.toLong()
            binding.banTime.text = DateUtils.formatDateTime(
                requireContext(),
                time,
                DateUtils.FORMAT_SHOW_DATE
            )
            binding.banReason.text = bans[position].internalNote
            binding.banItem.setOnClickListener {
                banState[position] = !banState[position]
                val icon = if (banState[position]) downArrow else rightArrow
                binding.banListItemDrop.setImageIcon(icon)

                binding.banReason.visibility = if (banState[position]) View.VISIBLE else View.GONE
            }
            binding.unbanBtn.setOnClickListener { unBanActor(bans[position].id!!.toInt()) }
            binding.banListItemDrop.setImageIcon(rightArrow)
            return binding.root
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        binding = FragmentDialogBanListBinding.inflate(LayoutInflater.from(context))
        viewModel =
            ViewModelProvider(this, viewModelFactory)[ConversationInfoViewModel::class.java]
        conversationUser = currentUserProvider.currentUser.blockingGet()
        initIcons()
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

                is ConversationInfoViewModel.ListBansErrorState -> {
                }

                else -> {}
            }
        }

        viewModel.getUnBanActorState.observe(viewLifecycleOwner) { state ->
            when (state) {
                ConversationInfoViewModel.UnBanActorSuccessState -> {
                    getBanList() // Refresh the ban list
                }

                ConversationInfoViewModel.UnBanActorErrorState -> {
                    Snackbar.make(binding.root, "Error unbanning actor", Snackbar.LENGTH_SHORT).show()
                }

                else -> {}
            }
        }
    }

    private fun initIcons() {
        rightArrow = Icon.createWithResource(requireContext(), R.drawable.ic_chevron_right)
        downArrow = Icon.createWithResource(requireContext(), R.drawable.ic_keyboard_arrow_down)
    }

    private fun themeView() {
        viewThemeUtils.platform.colorViewBackground(binding.root)
        downArrow.setTint(resources.getColor(R.color.nc_grey, null))
        rightArrow.setTint(resources.getColor(R.color.nc_grey, null))
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
    }
}
