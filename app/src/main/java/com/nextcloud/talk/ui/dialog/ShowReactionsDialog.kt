package com.nextcloud.talk.ui.dialog

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.NonNull
import autodagger.AutoInjector
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.databinding.DialogMessageReactionsBinding
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.reactions.ReactionVoter
import com.nextcloud.talk.models.json.reactions.ReactionsOverall
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.vanniktech.emoji.EmojiTextView
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.apache.commons.lang3.StringEscapeUtils
import java.net.URLDecoder
import java.net.URLEncoder
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class ShowReactionsDialog(
    val activity: Activity,
    val currentConversation: Conversation?,
    val chatMessage: ChatMessage,
    val userEntity: UserEntity?,
    val ncApi: NcApi
) : BottomSheetDialog(activity) {

    private lateinit var binding: DialogMessageReactionsBinding

    // @Inject
    // lateinit var ncApi: NcApi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogMessageReactionsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        initEmojiReactions()
    }

    private fun initEmojiReactions() {
        if (chatMessage.reactions != null && chatMessage.reactions.isNotEmpty()) {
            for ((emoji, amount) in chatMessage.reactions) {
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
                }
            }
        }
    }

    private fun updateParticipantsForEmoji(chatMessage: ChatMessage, emoji: String?) {

        val credentials = ApiUtils.getCredentials(userEntity?.username, userEntity?.token)

        Log.d(TAG, "emoji= " + emoji)

        // TODO: fix encoding for emoji. set this in NcApi or here...

        // var emojiForServer = StringEscapeUtils.escapeJava(emoji)
        // Log.d(TAG, "emojiForServer= " + emojiForServer)            //     ?reaction=%5Cu2764%5CuFE0F

        ncApi.getParticipantsForEmojiReaction(
            credentials,
            ApiUtils.getUrlForParticipantsForEmojiReaction(
                userEntity?.baseUrl,
                currentConversation!!.token,
                chatMessage.id),
            emoji
        )
            ?.subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<ReactionsOverall> {
                override fun onSubscribe(d: Disposable) {
                    Log.d(TAG, "onSubscribe")
                }

                override fun onNext(@NonNull reactionsOverall: ReactionsOverall) {
                    Log.d(TAG, "onNext")

                    val reactionVoters: ArrayList<ReactionVoter> = ArrayList()
                    if (reactionsOverall.ocs?.data != null){
                        for (reactionVoter in reactionsOverall.ocs?.data!!) {
                            reactionVoters.add(reactionVoter)
                        }
                    } else {
                        Log.e(TAG, "no voters for this reaction")
                    }
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, "failed to retrieve list of reaction voters")
                }

                override fun onComplete() {
                    Log.d(TAG, "onComplete")
                }
            })




        // binding.emojiReactionsContactList
    }

    companion object {
        const val TAG = "ShowReactionsDialog"
        const val EMOJI_RIGHT_MARGIN: Int = 12
        const val EMOJI_SIZE: Float = 30F
    }
}
