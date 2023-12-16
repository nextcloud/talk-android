/*
 * Nextcloud Talk application
 *
 * @author Julius Linus
 * Copyright (C) 2023 Julius Linus <julius.linus@nextcloud.com>
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
package com.nextcloud.talk.ui.dialog

import android.app.Dialog
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
class FilterConversationFragment(
    savedFilterState: MutableMap<String, Boolean>,
    conversationsListActivity: ConversationsListActivity
) : DialogFragment() {
    lateinit var binding: DialogFilterConversationBinding
    private var dialogView: View? = null
    private var filterState = savedFilterState
    private var conversationsList = conversationsListActivity

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    @Inject
    lateinit var arbitraryStorageManager: ArbitraryStorageManager
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogFilterConversationBinding.inflate(LayoutInflater.from(context))
        dialogView = binding.root

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
                mentionedFilterChip
            )
        }.forEach(viewThemeUtils.material::themeChipFilter)

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

        binding.buttonClose.setOnClickListener {
            dismiss()
        }
    }

    private fun setUpChips() {
        binding.unreadFilterChip.isChecked = filterState[UNREAD]!!
        binding.mentionedFilterChip.isChecked = filterState[MENTION]!!
    }

    private fun processSubmit() {
        // store
        val accountId = UserIdUtils.getIdForUser(userManager.currentUser.blockingGet())
        val mentionValue = filterState[MENTION] == true
        val unreadValue = filterState[UNREAD] == true

        arbitraryStorageManager.storeStorageSetting(accountId, MENTION, mentionValue.toString(), "")
        arbitraryStorageManager.storeStorageSetting(accountId, UNREAD, unreadValue.toString(), "")

        conversationsList.filterConversation()
    }

    companion object {
        @JvmStatic
        fun newInstance(
            savedFilterState: MutableMap<String, Boolean>,
            conversationsListActivity: ConversationsListActivity
        ) = FilterConversationFragment(savedFilterState, conversationsListActivity)
        val TAG: String = FilterConversationFragment::class.java.simpleName
        const val MENTION: String = "mention"
        const val UNREAD: String = "unread"
    }
}
