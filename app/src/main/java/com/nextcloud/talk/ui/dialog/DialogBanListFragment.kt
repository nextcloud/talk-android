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
import androidx.core.os.BundleCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import com.nextcloud.talk.models.json.participants.TalkBanDto
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.ui.theme.hostViewThemeUtils
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class DialogBanListFragment : DialogFragment() {

    // Read from the arguments, so the fragment can be recreated by the system after rotation or process death.
    private val roomToken: String by lazy { requireArguments().getString(ROOM_TOKEN_ARG)!! }
    private val conversationUser: User by lazy {
        BundleCompat.getParcelable(requireArguments(), USER_ARG, User::class.java)!!
    }

    lateinit var binding: FragmentDialogBanListBinding

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    lateinit var viewModel: ConversationInfoViewModel

    private val adapter = object : BaseAdapter() {
        private var bans: List<TalkBanDto> = mutableListOf()

        fun setItems(items: List<TalkBanDto>) {
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPadding(v.paddingLeft, statusBarInsets.top, v.paddingRight, v.paddingBottom)
            insets
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        viewThemeUtils = hostViewThemeUtils(activity, viewThemeUtils)
        binding = FragmentDialogBanListBinding.inflate(layoutInflater)
        viewModel =
            ViewModelProvider(this, viewModelFactory)[ConversationInfoViewModel::class.java]

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
        binding.closeBtn.setOnClickListener { parentFragmentManager.popBackStack() }
    }

    private fun getBanList() {
        viewModel.listBans(conversationUser, roomToken)
    }

    private fun unBanActor(banId: Int) {
        viewModel.unbanActor(conversationUser, roomToken, banId)
    }

    companion object {
        private const val ROOM_TOKEN_ARG = "ROOM_TOKEN_ARG"
        private const val USER_ARG = "USER_ARG"

        @JvmStatic
        fun newInstance(roomToken: String, user: User) =
            DialogBanListFragment().apply {
                arguments = Bundle().apply {
                    putString(ROOM_TOKEN_ARG, roomToken)
                    putParcelable(USER_ARG, user)
                }
            }
        const val ONE_SEC = 1000L
    }
}
