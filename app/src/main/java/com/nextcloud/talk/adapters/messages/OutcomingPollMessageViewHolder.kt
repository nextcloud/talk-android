/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
 * Copyright (C) 2021 Marcel Hibbe <dev@mhibbe.de>
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

package com.nextcloud.talk.adapters.messages

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PorterDuff
import android.util.Log
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.ViewCompat
import autodagger.AutoInjector
import coil.load
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.MainActivity
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.databinding.ItemCustomOutcomingPollMessageBinding
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.models.json.chat.ReadStatus
import com.nextcloud.talk.polls.repositories.model.PollOverall
import com.nextcloud.talk.polls.ui.PollMainDialogFragment
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.preferences.AppPreferences
import com.stfalcon.chatkit.messages.MessageHolders
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class OutcomingPollMessageViewHolder(outcomingView: View, payload: Any) : MessageHolders
.OutcomingTextMessageViewHolder<ChatMessage>(outcomingView, payload) {

    private val binding: ItemCustomOutcomingPollMessageBinding =
        ItemCustomOutcomingPollMessageBinding.bind(itemView)

    @JvmField
    @Inject
    var context: Context? = null

    @JvmField
    @Inject
    var appPreferences: AppPreferences? = null

    @Inject
    @JvmField
    var ncApi: NcApi? = null

    lateinit var message: ChatMessage

    lateinit var reactionsInterface: ReactionsInterface

    @SuppressLint("SetTextI18n")
    override fun onBind(message: ChatMessage) {
        super.onBind(message)
        this.message = message
        sharedApplication!!.componentApplication.inject(this)

        colorizeMessageBubble(message)

        itemView.isSelected = false
        binding.messageTime.setTextColor(context!!.resources.getColor(R.color.white60))

        // parent message handling
        setParentMessageDataOnMessageItem(message)

        val readStatusDrawableInt = when (message.readStatus) {
            ReadStatus.READ -> R.drawable.ic_check_all
            ReadStatus.SENT -> R.drawable.ic_check
            else -> null
        }

        val readStatusContentDescriptionString = when (message.readStatus) {
            ReadStatus.READ -> context?.resources?.getString(R.string.nc_message_read)
            ReadStatus.SENT -> context?.resources?.getString(R.string.nc_message_sent)
            else -> null
        }

        readStatusDrawableInt?.let { drawableInt ->
            AppCompatResources.getDrawable(context!!, drawableInt)?.let {
                it.setColorFilter(context?.resources!!.getColor(R.color.white60), PorterDuff.Mode.SRC_ATOP)
                binding.checkMark.setImageDrawable(it)
            }
        }

        binding.checkMark.setContentDescription(readStatusContentDescriptionString)

        setPollPreview(message)

        Reaction().showReactions(message, binding.reactions, binding.messageTime.context, true)
        binding.reactions.reactionsEmojiWrapper.setOnClickListener {
            reactionsInterface.onClickReactions(message)
        }
        binding.reactions.reactionsEmojiWrapper.setOnLongClickListener { l: View? ->
            reactionsInterface.onLongClickReactions(message)
            true
        }
    }

    private fun setPollPreview(message: ChatMessage) {
        var pollId: String? = null
        var pollName: String? = null

        if (message.messageParameters != null && message.messageParameters!!.size > 0) {
            for (key in message.messageParameters!!.keys) {
                val individualHashMap: Map<String?, String?> = message.messageParameters!![key]!!
                if (individualHashMap["type"] == "talk-poll") {
                    pollId = individualHashMap["id"]
                    pollName = individualHashMap["name"].toString()
                }
            }
        }

        if (pollId != null && pollName != null) {
            binding.messagePollTitle.text = pollName

            val roomToken = (payload as? MessagePayload)!!.roomToken

            binding.bubble.setOnClickListener {
                val pollVoteDialog = PollMainDialogFragment.newInstance(
                    message.activeUser!!,
                    roomToken,
                    pollId,
                    pollName
                )
                pollVoteDialog.show(
                    (binding.messagePollIcon.context as MainActivity).supportFragmentManager,
                    TAG
                )
            }

            val credentials = ApiUtils.getCredentials(message.activeUser?.username, message.activeUser?.token)
            ncApi!!.getPoll(
                credentials,
                ApiUtils.getUrlForPoll(
                    message.activeUser?.baseUrl,
                    roomToken,
                    pollId
                )
            ).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object : Observer<PollOverall> {
                    override fun onSubscribe(d: Disposable) {
                        // unused atm
                    }

                    override fun onNext(pollOverall: PollOverall) {
                        if (pollOverall.ocs!!.data!!.status == 0) {
                            binding.messagePollSubtitle.text =
                                context?.resources?.getString(R.string.message_poll_tap_to_vote)
                        } else {
                            binding.messagePollSubtitle.text =
                                context?.resources?.getString(R.string.message_poll_tap_see_results)
                        }
                    }

                    override fun onError(e: Throwable) {
                        Log.e(TAG, "Error while fetching poll", e)
                    }

                    override fun onComplete() {
                        // unused atm
                    }
                })
        }
    }

    private fun setParentMessageDataOnMessageItem(message: ChatMessage) {
        if (!message.isDeleted && message.parentMessage != null) {
            val parentChatMessage = message.parentMessage
            parentChatMessage!!.activeUser = message.activeUser
            parentChatMessage.imageUrl?.let {
                binding.messageQuote.quotedMessageImage.visibility = View.VISIBLE
                binding.messageQuote.quotedMessageImage.load(it) {
                    addHeader(
                        "Authorization",
                        ApiUtils.getCredentials(message.activeUser!!.username, message.activeUser!!.token)
                    )
                }
            } ?: run {
                binding.messageQuote.quotedMessageImage.visibility = View.GONE
            }
            binding.messageQuote.quotedMessageAuthor.text = parentChatMessage.actorDisplayName
                ?: context!!.getText(R.string.nc_nick_guest)
            binding.messageQuote.quotedMessage.text = parentChatMessage.text
            binding.messageQuote.quotedMessage.setTextColor(
                context!!.resources.getColor(R.color.nc_outcoming_text_default)
            )
            binding.messageQuote.quotedMessageAuthor.setTextColor(context!!.resources.getColor(R.color.nc_grey))

            binding.messageQuote.quoteColoredView.setBackgroundResource(R.color.white)

            binding.messageQuote.quotedChatMessageView.visibility = View.VISIBLE
        } else {
            binding.messageQuote.quotedChatMessageView.visibility = View.GONE
        }
    }

    private fun colorizeMessageBubble(message: ChatMessage) {
        val resources = sharedApplication!!.resources
        val bgBubbleColor = if (message.isDeleted) {
            resources.getColor(R.color.bg_message_list_outcoming_bubble_deleted)
        } else {
            resources.getColor(R.color.bg_message_list_outcoming_bubble)
        }
        if (message.isGrouped) {
            val bubbleDrawable = DisplayUtils.getMessageSelector(
                bgBubbleColor,
                resources.getColor(R.color.transparent),
                bgBubbleColor,
                R.drawable.shape_grouped_outcoming_message
            )
            ViewCompat.setBackground(bubble, bubbleDrawable)
        } else {
            val bubbleDrawable = DisplayUtils.getMessageSelector(
                bgBubbleColor,
                resources.getColor(R.color.transparent),
                bgBubbleColor,
                R.drawable.shape_outcoming_message
            )
            ViewCompat.setBackground(bubble, bubbleDrawable)
        }
    }

    fun assignReactionInterface(reactionsInterface: ReactionsInterface) {
        this.reactionsInterface = reactionsInterface
    }

    companion object {
        private val TAG = NextcloudTalkApplication::class.java.simpleName
    }
}
