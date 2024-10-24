/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Julius Linus <julius.linus@nextcloud.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.ui.dialog

import android.app.Dialog
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import autodagger.AutoInjector
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.arbitrarystorage.ArbitraryStorageManager
import com.nextcloud.talk.conversationlist.ConversationsListActivity
import com.nextcloud.talk.databinding.DialogFilterConversationBinding
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.UserIdUtils
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class FilterConversationFragment : DialogFragment() {
    lateinit var binding: DialogFilterConversationBinding
    private var dialogView: View? = null
    private lateinit var filterState: HashMap<String, Boolean>

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    @Inject
    lateinit var arbitraryStorageManager: ArbitraryStorageManager
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogFilterConversationBinding.inflate(layoutInflater)
        dialogView = binding.root
        filterState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arguments?.getSerializable(FILTER_STATE_ARG, HashMap::class.java) as HashMap<String, Boolean>
        } else {
            arguments?.getSerializable(FILTER_STATE_ARG) as HashMap<String, Boolean>
        }
        return MaterialAlertDialogBuilder(requireContext()).setView(dialogView).create()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        setUpColors()
        setUpListeners()
        return inflater.inflate(R.layout.dialog_filter_conversation, container, false)
    }

    private fun setUpColors() {
        viewThemeUtils.material.colorMaterialButtonPrimaryBorderless(binding.buttonClose)

        binding.run {
            listOf(
                binding.root
            )
        }.forEach(viewThemeUtils.platform::colorViewBackground)

        binding.run {
            listOf(
                unreadFilterChip,
                mentionedFilterChip,
                archivedFilterChip
            )
        }.forEach(viewThemeUtils.talk::themeChipFilter)

        setUpChips()
    }

    private fun setUpListeners() {
        binding.unreadFilterChip.setOnCheckedChangeListener { _, isChecked ->
            filterState[UNREAD] = isChecked
            binding.unreadFilterChip.isChecked = isChecked
            processSubmit()
        }

        binding.mentionedFilterChip.setOnCheckedChangeListener { _, isChecked ->
            filterState[MENTION] = isChecked
            binding.mentionedFilterChip.isChecked = isChecked
            processSubmit()
        }

        binding.archivedFilterChip.setOnCheckedChangeListener { _, isChecked ->
            filterState[ARCHIVE] = isChecked
            binding.archivedFilterChip.isChecked = isChecked
            processSubmit()
        }

        binding.buttonClose.setOnClickListener {
            dismiss()
        }
    }

    private fun setUpChips() {
        binding.unreadFilterChip.isChecked = filterState[UNREAD]!!
        binding.mentionedFilterChip.isChecked = filterState[MENTION]!!
        binding.archivedFilterChip.isChecked = filterState[ARCHIVE]!!
    }

    private fun processSubmit() {
        // store
        val accountId = UserIdUtils.getIdForUser(userManager.currentUser.blockingGet())
        val mentionValue = filterState[MENTION] == true
        val unreadValue = filterState[UNREAD] == true
        val archivedValue = filterState[ARCHIVE] == true

        arbitraryStorageManager.storeStorageSetting(accountId, MENTION, mentionValue.toString(), "")
        arbitraryStorageManager.storeStorageSetting(accountId, UNREAD, unreadValue.toString(), "")
        arbitraryStorageManager.storeStorageSetting(accountId, ARCHIVE, archivedValue.toString(), "")

        (requireActivity() as ConversationsListActivity).filterConversation()
    }

    companion object {
        private const val FILTER_STATE_ARG = "FILTER_STATE_ARG"

        @JvmStatic
        fun newInstance(savedFilterState: MutableMap<String, Boolean>): FilterConversationFragment {
            val filterConversationFragment = FilterConversationFragment()
            val args = Bundle()
            args.putSerializable(FILTER_STATE_ARG, HashMap(savedFilterState))
            filterConversationFragment.arguments = args
            return filterConversationFragment
        }

        val TAG: String = FilterConversationFragment::class.java.simpleName
        const val MENTION: String = "mention"
        const val UNREAD: String = "unread"
        const val ARCHIVE: String = "archive"
        const val DEFAULT: String = "default"
    }
}
