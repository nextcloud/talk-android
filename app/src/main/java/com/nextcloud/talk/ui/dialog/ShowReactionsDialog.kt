/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.ui.dialog

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import autodagger.AutoInjector
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.nextcloud.talk.R
import com.nextcloud.talk.adapters.ReactionItem
import com.nextcloud.talk.adapters.ReactionItemClickListener
import com.nextcloud.talk.adapters.ReactionsAdapter
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.ChatActivity
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.DialogMessageReactionsBinding
import com.nextcloud.talk.databinding.ItemReactionsTabBinding
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.reactions.ReactionsOverall
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.ApiUtils
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.util.Collections
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class ShowReactionsDialog(
    val activity: Activity,
    private val roomToken: String,
    private val chatMessage: ChatMessage,
    private val user: User?,
    private val hasChatPermission: Boolean,
    private val ncApi: NcApi
) : BottomSheetDialog(activity),
    ReactionItemClickListener {

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    private lateinit var binding: DialogMessageReactionsBinding

    private var adapter: ReactionsAdapter? = null

    private val tagAll: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication?.componentApplication?.inject(this)

        binding = DialogMessageReactionsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        viewThemeUtils.material.colorBottomSheetBackground(binding.root)
        viewThemeUtils.material.colorBottomSheetDragHandle(binding.bottomSheetDragHandle)
        adapter = ReactionsAdapter(this, user)
        binding.reactionsList.adapter = adapter
        binding.reactionsList.layoutManager = LinearLayoutManager(context)
        initEmojiReactions()
    }

    override fun onStart() {
        super.onStart()
        val bottomSheet = findViewById<View>(R.id.design_bottom_sheet)
        val behavior = BottomSheetBehavior.from(bottomSheet as View)
        behavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    private fun initEmojiReactions() {
        adapter?.list?.clear()
        if (chatMessage.reactions != null && chatMessage.reactions!!.isNotEmpty()) {
            var reactionsTotal = 0
            for ((emoji, amount) in chatMessage.reactions!!) {
                reactionsTotal = reactionsTotal.plus(amount)
                val tab: TabLayout.Tab = binding.emojiReactionsTabs.newTab() // Create a new Tab names "First Tab"

                val itemBinding = ItemReactionsTabBinding.inflate(layoutInflater)
                itemBinding.reactionTab.tag = emoji
                itemBinding.reactionIcon.text = emoji
                itemBinding.reactionCount.text = amount.toString()
                tab.customView = itemBinding.root

                binding.emojiReactionsTabs.addTab(tab)
            }

            val tab: TabLayout.Tab = binding.emojiReactionsTabs.newTab() // Create a new Tab names "First Tab"

            val itemBinding = ItemReactionsTabBinding.inflate(layoutInflater)
            itemBinding.reactionTab.tag = tagAll
            itemBinding.reactionIcon.text = context.getString(R.string.reactions_tab_all)
            itemBinding.reactionCount.text = reactionsTotal.toString()
            tab.customView = itemBinding.root

            binding.emojiReactionsTabs.addTab(tab, 0)

            binding.emojiReactionsTabs.getTabAt(0)?.select()

            binding.emojiReactionsTabs.addOnTabSelectedListener(object : OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    // called when a tab is reselected
                    updateParticipantsForEmoji(chatMessage, tab.customView?.tag as String?)
                }

                override fun onTabUnselected(tab: TabLayout.Tab) {
                    // called when a tab is reselected
                }

                override fun onTabReselected(tab: TabLayout.Tab) {
                    // called when a tab is reselected
                }
            })

            viewThemeUtils.material.themeTabLayoutOnSurface(binding.emojiReactionsTabs)

            updateParticipantsForEmoji(chatMessage, tagAll)
        }
        adapter?.notifyDataSetChanged()
    }

    private fun updateParticipantsForEmoji(chatMessage: ChatMessage, emoji: String?) {
        adapter?.list?.clear()

        val credentials = ApiUtils.getCredentials(user?.username, user?.token)

        ncApi.getReactions(
            credentials,
            ApiUtils.getUrlForMessageReaction(
                user?.baseUrl!!,
                roomToken,
                chatMessage.id
            ),
            emoji
        )
            ?.subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<ReactionsOverall> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                override fun onNext(reactionsOverall: ReactionsOverall) {
                    val reactionVoters: ArrayList<ReactionItem> = ArrayList()
                    if (reactionsOverall.ocs?.data != null) {
                        val map = reactionsOverall.ocs?.data
                        for (key in map!!.keys) {
                            for (reactionVoter in reactionsOverall.ocs?.data!![key]!!) {
                                reactionVoters.add(ReactionItem(reactionVoter, key))
                            }
                        }

                        Collections.sort(reactionVoters, ReactionComparator(user.userId))

                        adapter?.list?.addAll(reactionVoters)
                        adapter?.notifyDataSetChanged()
                    } else {
                        Log.e(TAG, "no voters for this reaction")
                    }
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, "failed to retrieve list of reaction voters")
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    override fun onClick(reactionItem: ReactionItem) {
        if (hasChatPermission && reactionItem.reactionVoter.actorId?.equals(user?.userId) == true) {
            deleteReaction(chatMessage, reactionItem.reaction!!)
            adapter?.list?.remove(reactionItem)
            dismiss()
        }
    }

    private fun deleteReaction(message: ChatMessage, emoji: String) {
        val credentials = ApiUtils.getCredentials(user?.username, user?.token)
        ncApi.deleteReaction(
            credentials,
            ApiUtils.getUrlForMessageReaction(
                user?.baseUrl!!,
                roomToken,
                message.id
            ),
            emoji
        )
            ?.subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<GenericOverall> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                override fun onNext(genericOverall: GenericOverall) {
                    Log.d(TAG, "deleted reaction: $emoji")
                    (activity as ChatActivity).updateUiToDeleteReaction(message, emoji)
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, "error while deleting reaction: $emoji")
                }

                override fun onComplete() {
                    dismiss()
                }
            })
    }

    companion object {
        const val TAG = "ShowReactionsDialog"
    }

    class ReactionComparator(val activeUser: String?) : Comparator<ReactionItem> {
        @Suppress("ReturnCount")
        override fun compare(reactionItem1: ReactionItem?, reactionItem2: ReactionItem?): Int {
            // sort by emoji, own account, display-name, timestamp, actor-id

            if (reactionItem1 == null && reactionItem2 == null) {
                return 0
            }
            if (reactionItem1 == null) {
                return -1
            }
            if (reactionItem2 == null) {
                return 1
            }

            // emoji
            val reaction = StringComparator().compare(reactionItem1.reaction, reactionItem2.reaction)
            if (reaction != 0) {
                return reaction
            }

            // own account
            val ownAccount = compareOwnAccount(
                activeUser,
                reactionItem1.reactionVoter.actorId,
                reactionItem2.reactionVoter.actorId
            )

            if (ownAccount != 0) {
                return ownAccount
            }

            // display-name
            val displayName = StringComparator()
                .compare(
                    reactionItem1.reactionVoter.actorDisplayName,
                    reactionItem2.reactionVoter.actorDisplayName
                )

            if (displayName != 0) {
                return displayName
            }

            // timestamp
            val timestamp = LongComparator()
                .compare(
                    reactionItem1.reactionVoter.timestamp,
                    reactionItem2.reactionVoter.timestamp
                )

            if (timestamp != 0) {
                return timestamp
            }

            // actor-id
            val actorId = StringComparator()
                .compare(
                    reactionItem1.reactionVoter.actorId,
                    reactionItem2.reactionVoter.actorId
                )

            if (actorId != 0) {
                return actorId
            }

            return 0
        }

        @Suppress("ReturnCount")
        fun compareOwnAccount(activeUser: String?, actorId1: String?, actorId2: String?): Int {
            val reactionVote1Active = activeUser == actorId1
            val reactionVote2Active = activeUser == actorId2

            if (reactionVote1Active == reactionVote2Active) {
                return 0
            }

            if (activeUser == null) {
                return 0
            }

            if (reactionVote1Active) {
                return 1
            }
            if (reactionVote2Active) {
                return -1
            }

            return 0
        }

        internal class StringComparator : Comparator<String?> {
            @Suppress("ReturnCount")
            override fun compare(obj1: String?, obj2: String?): Int {
                if (obj1 === obj2) {
                    return 0
                }
                if (obj1 == null) {
                    return -1
                }
                return if (obj2 == null) {
                    1
                } else {
                    obj1.lowercase().compareTo(obj2.lowercase())
                }
            }
        }

        internal class LongComparator : Comparator<Long?> {
            @Suppress("ReturnCount")
            override fun compare(obj1: Long?, obj2: Long?): Int {
                if (obj1 === obj2) {
                    return 0
                }
                if (obj1 == null) {
                    return -1
                }
                return if (obj2 == null) {
                    1
                } else {
                    obj1.compareTo(obj2)
                }
            }
        }
    }
}
