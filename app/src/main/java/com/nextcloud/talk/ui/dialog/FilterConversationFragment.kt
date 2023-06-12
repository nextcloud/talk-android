package com.nextcloud.talk.ui.dialog

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import autodagger.AutoInjector
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nextcloud.talk.R
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.databinding.DialogFilterConversationBinding
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.users.UserManager
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class FilterConversationFragment(adapter: FlexibleAdapter<AbstractFlexibleItem<*>>) : DialogFragment() {
    lateinit var binding: DialogFilterConversationBinding
    private var dialogView: View? = null
    private var currentAdapter: FlexibleAdapter<AbstractFlexibleItem<*>> = adapter
    private var filterState = mutableMapOf(NONE to true, MENTION to false, UNREAD to false)

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
    }

    private fun setUpListeners() {
        binding.noFilterButton.setOnClickListener {
            Log.i(TAG, "no filter clicked")
            filterState[NONE] = !filterState[NONE]!!
            if (filterState[NONE]!!) {
                binding.noFilterButton.setBackgroundColor(resources.getColor(R.color.colorPrimary))
            } else
                binding.noFilterButton.setBackgroundColor(resources.getColor(R.color.grey_200))

            filterState[UNREAD] = false
            binding.unreadFilterButton.setBackgroundColor(resources.getColor(R.color.grey_200))
            filterState[MENTION] = false
            binding.mentionedFilterButton.setBackgroundColor(resources.getColor(R.color.grey_200))
        }

        binding.unreadFilterButton.setOnClickListener {
            Log.i(TAG, "unread filter clicked")
            filterState[UNREAD] = !filterState[UNREAD]!!
            if (filterState[UNREAD]!!) {
                binding.unreadFilterButton.setBackgroundColor(
                    resources.getColor(
                        R.color
                            .colorPrimary
                    )
                )
            } else binding.unreadFilterButton.setBackgroundColor(resources.getColor(R.color.grey_200))
            filterState[NONE] = false
            binding.noFilterButton.setBackgroundColor(resources.getColor(R.color.grey_200))
        }

        binding.mentionedFilterButton.setOnClickListener {
            Log.i(TAG, "mentioned filter clicked")
            filterState[MENTION] = !filterState[MENTION]!!
            if (filterState[MENTION]!!) {
                binding.mentionedFilterButton.setBackgroundColor(resources.getColor(R.color.colorPrimary))
            } else
                binding.mentionedFilterButton.setBackgroundColor(resources.getColor(R.color.grey_200))

            filterState[NONE] = false
            binding.noFilterButton.setBackgroundColor(resources.getColor(R.color.grey_200))
        }

        binding.filterButton.setOnClickListener {
            Log.i(TAG, "submit clicked")
            dismiss()
        }
    }

    companion object {
        @JvmStatic
        fun newInstance(adapter: FlexibleAdapter<AbstractFlexibleItem<*>>) = FilterConversationFragment(adapter)
        val TAG: String = FilterConversationFragment::class.java.simpleName
        const val NONE: String = "none"
        const val MENTION: String = "mention"
        const val UNREAD: String = "unread"
    }
}
