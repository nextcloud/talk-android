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
import com.nextcloud.talk.adapters.items.ConversationItem
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.conversationlist.ConversationsListActivity
import com.nextcloud.talk.databinding.DialogFilterConversationBinding
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.users.UserManager
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class FilterConversationFragment(
    adapter: FlexibleAdapter<AbstractFlexibleItem<*>>,
    currentConversations: MutableList<AbstractFlexibleItem<*>>,
    savedFilterState: MutableMap<String, Boolean>,
    conversationsListActivity: ConversationsListActivity
) : DialogFragment() {
    lateinit var binding: DialogFilterConversationBinding
    private var dialogView: View? = null
    private var currentAdapter: FlexibleAdapter<AbstractFlexibleItem<*>> = adapter
    private var currentItems = currentConversations
    private var filterState = savedFilterState
    private var conversationsList = conversationsListActivity

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogFilterConversationBinding.inflate(LayoutInflater.from(context))
        dialogView = binding.root

        return MaterialAlertDialogBuilder(requireContext()).setView(dialogView).create()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        setUpColors()
        setUpListeners()
        return inflater.inflate(R.layout.dialog_filter_conversation, container, false)
    }

    private fun setUpColors() {
        binding.run {
            listOf(
                binding.root,
            )
        }.forEach(viewThemeUtils.platform::colorViewBackground)

        binding.run {
            listOf(
                unreadFilterChip,
                mentionedFilterChip
            )
        }.forEach(viewThemeUtils.material::colorChipBackground)

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
    }

    private fun setUpChips() {
        binding.unreadFilterChip.isChecked = filterState[UNREAD]!!
        binding.mentionedFilterChip.isChecked = filterState[MENTION]!!
    }

    private fun processSubmit() {
        val newItems: MutableList<AbstractFlexibleItem<*>> = ArrayList()
        if (!filterState.containsValue(true)) {
            currentAdapter.updateDataSet(currentItems, true)
        } else {
            val items = currentItems
            for (i in items) {
                val conversation = (i as ConversationItem).model
                if (filter(conversation)) {
                    newItems.add(i)
                }
            }
            currentAdapter.updateDataSet(newItems, true)
            conversationsList.setFilterableItems(newItems)
        }
        conversationsList.updateFilterState(
            filterState[MENTION]!!,
            filterState[UNREAD]!!
        )

        conversationsList.updateFilterConversationButtonColor()
    }
    private fun filter(conversation: Conversation): Boolean {
        var result = true
        for ((k, v) in filterState) {
            if (v) {
                when (k) {
                    MENTION -> result = result && conversation.unreadMention
                    UNREAD -> result = result && (conversation.unreadMessages > 0)
                }
            }
        }

        return result
    }

    companion object {
        @JvmStatic
        fun newInstance(
            adapter: FlexibleAdapter<AbstractFlexibleItem<*>>,
            currentConversations: MutableList<AbstractFlexibleItem<*>>,
            savedFilterState: MutableMap<String, Boolean>,
            conversationsListActivity: ConversationsListActivity
        ) = FilterConversationFragment(adapter, currentConversations, savedFilterState, conversationsListActivity)
        val TAG: String = FilterConversationFragment::class.java.simpleName
        const val MENTION: String = "mention"
        const val UNREAD: String = "unread"
    }
}
