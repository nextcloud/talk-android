/*
 * Nextcloud Talk application
 *
 * @author Andy Scherzinger
 * @author Mario Danic
 * @author Marcel Hibbe
 * Copyright (C) 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * Copyright (C) 2017 Mario Danic <mario@lovelyhq.com>
 * Copyright (C) 2022 Marcel Hibbe (dev@mhibbe.de)
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
 *
 * Parts related to account import were either copied from or inspired by the great work done by David Luhmer at:
 * https://github.com/nextcloud/ownCloud-Account-Importer
 */
package com.nextcloud.talk.ui.dialog

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.NonNull
import androidx.recyclerview.widget.LinearLayoutManager
import autodagger.AutoInjector
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.nextcloud.talk.R
import com.nextcloud.talk.adapters.ReactionItem
import com.nextcloud.talk.adapters.ReactionItemClickListener
import com.nextcloud.talk.adapters.ReactionsAdapter
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.databinding.DialogMessageReactionsBinding
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.reactions.ReactionsOverall
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.vanniktech.emoji.EmojiTextView
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers

@AutoInjector(NextcloudTalkApplication::class)
class ShowReactionsDialog(
    val activity: Activity,
    val currentConversation: Conversation?,
    val chatMessage: ChatMessage,
    val userEntity: UserEntity?,
    val ncApi: NcApi
) : BottomSheetDialog(activity), ReactionItemClickListener {

    private lateinit var binding: DialogMessageReactionsBinding

    private var adapter: ReactionsAdapter? = null

    // @Inject
    // lateinit var ncApi: NcApi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogMessageReactionsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        adapter = ReactionsAdapter(this, userEntity)
        binding.reactionsList.adapter = adapter
        binding.reactionsList.layoutManager = LinearLayoutManager(context)
        initEmojiReactions()
    }

    private fun initEmojiReactions() {
        adapter?.list?.clear()
        if (chatMessage.reactions != null && chatMessage.reactions.isNotEmpty()) {

            var firstEmoji = ""
            for ((emoji, amount) in chatMessage.reactions) {
                if(firstEmoji.isEmpty()){
                    firstEmoji = emoji
                }

                var emojiView = EmojiTextView(activity)
                emojiView.setEmojiSize(DisplayUtils.convertDpToPixel(EMOJI_SIZE, context).toInt())
                emojiView.text = emoji

                val params = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 0, EMOJI_RIGHT_MARGIN, 0)
                params.gravity = Gravity.CENTER
                emojiView.layoutParams = params

                binding.emojiReactionsWrapper.addView(emojiView)

                emojiView.setOnClickListener {
                    updateParticipantsForEmoji(chatMessage, emoji)
                    emojiView.setBackgroundColor(context.resources.getColor(R.color.colorPrimary))
                }

                // TODO: Add proper implementation
                adapter?.list?.add(
                    ReactionItem(
                        ReactionVoter(
                            ReactionVoter.ReactionActorType.USERS,
                            "marcel",
                            "Marcel",
                            0
                        ),
                        emoji
                    )
                )
            }
            updateParticipantsForEmoji(chatMessage, firstEmoji)
        }
        adapter?.notifyDataSetChanged()
    }

    private fun updateParticipantsForEmoji(chatMessage: ChatMessage, emoji: String?) {
        adapter?.list?.clear()

        val credentials = ApiUtils.getCredentials(userEntity?.username, userEntity?.token)

        ncApi.getParticipantsForEmojiReaction(
            credentials,
            ApiUtils.getUrlForParticipantsForEmojiReaction(
                userEntity?.baseUrl,
                currentConversation!!.token,
                chatMessage.id
            ),
            emoji
        )
            ?.subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<ReactionsOverall> {
                override fun onSubscribe(d: Disposable) {
                }

                override fun onNext(@NonNull reactionsOverall: ReactionsOverall) {
                    val reactionVoters: ArrayList<ReactionItem> = ArrayList()
                    if (reactionsOverall.ocs?.data != null) {
                        for (reactionVoter in reactionsOverall.ocs?.data!![emoji]!!) {
                            reactionVoters.add(ReactionItem(reactionVoter, emoji))

                        }
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
                }
            })
    }

    override fun onClick(reactionItem: ReactionItem) {
        Log.d(TAG, "onClick(reactionItem: ReactionItem): " + reactionItem.reaction)
        // TODO: implement removal of users reaction,
        //  ownership needs to be checked, so only owned
        //  reactions can be removed upon click
        dismiss()
    }

    companion object {
        const val TAG = "ShowReactionsDialog"
        const val EMOJI_RIGHT_MARGIN: Int = 12
        const val EMOJI_SIZE: Float = 30F
    }
}
