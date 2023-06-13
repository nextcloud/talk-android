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
import com.nextcloud.talk.api.NcApi
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
    lateinit var ncApi: NcApi

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
        updateFilters()
    }

    private fun setUpListeners() {
        binding.noFilterButton.setOnClickListener {
            filterState[NONE] = !filterState[NONE]!!
            filterState[UNREAD] = false
            filterState[MENTION] = false
            updateFilters()
        }

        binding.unreadFilterButton.setOnClickListener {
            filterState[UNREAD] = !filterState[UNREAD]!!
            changeUnreadFilter()
            filterState[NONE] = false
            changeNoneFilter()
        }

        binding.mentionedFilterButton.setOnClickListener {
            filterState[MENTION] = !filterState[MENTION]!!
            changeMentionFilter()
            filterState[NONE] = false
            changeNoneFilter()
        }

        binding.filterButton.setOnClickListener {
            processSubmit()
            dismiss()
        }
    }

    private fun updateFilters() {
        changeNoneFilter()
        changeUnreadFilter()
        changeMentionFilter()
    }

    private fun changeMentionFilter() {
        if (filterState[MENTION]!!) {
            binding.mentionedFilterButton.setBackgroundColor(resources.getColor(R.color.colorPrimary))
        } else
            binding.mentionedFilterButton.setBackgroundColor(resources.getColor(R.color.grey_200))
    }

    private fun changeUnreadFilter() {
        if (filterState[UNREAD]!!) {
            binding.unreadFilterButton.setBackgroundColor(
                resources.getColor(R.color.colorPrimary)
            )
        } else binding.unreadFilterButton.setBackgroundColor(resources.getColor(R.color.grey_200))
    }

    private fun changeNoneFilter() {
        if (filterState[NONE]!!) {
            binding.noFilterButton.setBackgroundColor(resources.getColor(R.color.colorPrimary))
        } else
            binding.noFilterButton.setBackgroundColor(resources.getColor(R.color.grey_200))
    }

    private fun processSubmit() {
        var newItems: MutableList<AbstractFlexibleItem<*>> = ArrayList()
        if (filterState[NONE]!!) {
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
        }
        conversationsList.updateFilterState(
            filterState[NONE]!!,
            filterState[MENTION]!!,
            filterState[UNREAD]!!
        )
    }
    private fun filter(conversation: Conversation): Boolean {
        var result = true
        for ((k, v) in filterState) {
            if (k != NONE && v) {
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
        const val NONE: String = "none"
        const val MENTION: String = "mention"
        const val UNREAD: String = "unread"
    }
}
