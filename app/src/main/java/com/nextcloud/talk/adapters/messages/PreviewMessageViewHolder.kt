/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2021-2022 Tim Krüger <t@timkrueger.me>
 * SPDX-FileCopyrightText: 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2021 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.adapters.messages

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Handler
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.emoji2.widget.EmojiTextView
import autodagger.AutoInjector
import com.google.android.material.card.MaterialCardView
import com.nextcloud.android.common.ui.theme.utils.ColorRole
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.ReactionsInsideMessageBinding
import com.nextcloud.talk.extensions.loadChangelogBotAvatar
import com.nextcloud.talk.extensions.loadFederatedUserAvatar
import com.nextcloud.talk.filebrowser.models.BrowserFile
import com.nextcloud.talk.filebrowser.webdav.ReadFilesystemOperation
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.DateUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.DrawableUtils.getDrawableResourceIdForMimeType
import com.nextcloud.talk.utils.FileViewerUtils
import com.nextcloud.talk.utils.FileViewerUtils.ProgressUi
import com.nextcloud.talk.utils.message.MessageUtils
import com.stfalcon.chatkit.messages.MessageHolders.IncomingImageMessageViewHolder
import io.reactivex.Single
import io.reactivex.SingleObserver
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import okhttp3.OkHttpClient
import java.io.ByteArrayInputStream
import java.io.IOException
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
abstract class PreviewMessageViewHolder(itemView: View?, payload: Any?) :
    IncomingImageMessageViewHolder<ChatMessage>(itemView, payload) {
    @JvmField
    @Inject
    var context: Context? = null

    @JvmField
    @Inject
    var viewThemeUtils: ViewThemeUtils? = null

    @Inject
    lateinit var dateUtils: DateUtils

    @Inject
    lateinit var messageUtils: MessageUtils

    @Inject
    lateinit var userManager: UserManager

    @JvmField
    @Inject
    var okHttpClient: OkHttpClient? = null
    open var progressBar: ProgressBar? = null
    open var reactionsBinding: ReactionsInsideMessageBinding? = null
    var fileViewerUtils: FileViewerUtils? = null
    var clickView: View? = null

    lateinit var commonMessageInterface: CommonMessageInterface
    var previewMessageInterface: PreviewMessageInterface? = null

    private var placeholder: Drawable? = null

    init {
        sharedApplication!!.componentApplication.inject(this)
    }

    @SuppressLint("SetTextI18n")
    @Suppress("NestedBlockDepth", "ComplexMethod", "LongMethod")
    override fun onBind(message: ChatMessage) {
        super.onBind(message)
        image.minimumHeight = DisplayUtils.convertDpToPixel(MIN_IMAGE_HEIGHT, context!!).toInt()

        time.text = dateUtils.getLocalTimeStringFromTimestamp(message.timestamp)

        viewThemeUtils!!.platform.colorCircularProgressBar(progressBar!!, ColorRole.PRIMARY)
        clickView = image
        messageText.visibility = View.VISIBLE
        if (message.getCalculateMessageType() === ChatMessage.MessageType.SINGLE_NC_ATTACHMENT_MESSAGE) {
            fileViewerUtils = FileViewerUtils(context!!, message.activeUser!!)
            val fileName = message.selectedIndividualHashMap!![KEY_NAME]

            messageText.text = fileName

            if (message.activeUser != null &&
                message.activeUser!!.username != null &&
                message.activeUser!!.baseUrl != null
            ) {
                clickView!!.setOnClickListener { v: View? ->
                    fileViewerUtils!!.openFile(
                        message,
                        ProgressUi(progressBar, messageText, image)
                    )
                }
                clickView!!.setOnLongClickListener {
                    previewMessageInterface!!.onPreviewMessageLongClick(message)
                    true
                }
            } else {
                Log.e(TAG, "failed to set click listener because activeUser, username or baseUrl were null")
            }
            fileViewerUtils!!.resumeToUpdateViewsByProgress(
                message.selectedIndividualHashMap!![KEY_NAME]!!,
                message.selectedIndividualHashMap!![KEY_ID]!!,
                message.selectedIndividualHashMap!![KEY_MIMETYPE],
                message.openWhenDownloaded,
                ProgressUi(progressBar, messageText, image)
            )
        } else if (message.getCalculateMessageType() === ChatMessage.MessageType.SINGLE_LINK_GIPHY_MESSAGE) {
            messageText.text = "GIPHY"
            DisplayUtils.setClickableString("GIPHY", "https://giphy.com", messageText)
        } else if (message.getCalculateMessageType() === ChatMessage.MessageType.SINGLE_LINK_TENOR_MESSAGE) {
            messageText.text = "Tenor"
            DisplayUtils.setClickableString("Tenor", "https://tenor.com", messageText)
        } else {
            if (message.messageType == ChatMessage.MessageType.SINGLE_LINK_IMAGE_MESSAGE.name) {
                clickView!!.setOnClickListener {
                    val browserIntent = Intent(Intent.ACTION_VIEW, message.imageUrl!!.toUri())
                    browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context!!.startActivity(browserIntent)
                }
            } else {
                clickView!!.setOnClickListener(null)
            }
            messageText.text = ""
        }
        itemView.setTag(R.string.replyable_message_view_tag, message.replyable)
        val paddingSide = DisplayUtils.convertDpToPixel(HORIZONTAL_REACTION_PADDING, context!!).toInt()
        Reaction().showReactions(
            message,
            ::clickOnReaction,
            ::longClickOnReaction,
            reactionsBinding!!,
            messageText.context,
            true,
            viewThemeUtils!!,
            hasBubbleBackground(message)
        )
        reactionsBinding!!.reactionsEmojiWrapper.setPadding(paddingSide, 0, paddingSide, 0)

        if (userAvatar != null) {
            if (message.isGrouped || message.isOneToOneConversation) {
                if (message.isOneToOneConversation) {
                    userAvatar.visibility = View.GONE
                } else {
                    userAvatar.visibility = View.INVISIBLE
                }
            } else {
                userAvatar.visibility = View.VISIBLE
                userAvatar.setOnClickListener { v: View ->
                    if (payload is MessagePayload) {
                        (payload as MessagePayload).profileBottomSheet.showFor(
                            message,
                            v.context
                        )
                    }
                }
                if (ACTOR_TYPE_BOTS == message.actorType && ACTOR_ID_CHANGELOG == message.actorId) {
                    userAvatar.loadChangelogBotAvatar()
                } else if (message.actorType == "federated_users") {
                    userAvatar.loadFederatedUserAvatar(message)
                }
            }
        }

        messageCaption.setOnClickListener(null)
        messageCaption.setOnLongClickListener {
            previewMessageInterface!!.onPreviewMessageLongClick(message)
            true
        }
    }

    private fun longClickOnReaction(chatMessage: ChatMessage) {
        commonMessageInterface.onLongClickReactions(chatMessage)
    }

    private fun clickOnReaction(chatMessage: ChatMessage, emoji: String) {
        commonMessageInterface.onClickReaction(chatMessage, emoji)
    }

    override fun getPayloadForImageLoader(message: ChatMessage?): Any? {
        if (message!!.selectedIndividualHashMap!!.containsKey(KEY_CONTACT_NAME)) {
            previewContainer.visibility = View.GONE
            previewContactContainer.visibility = View.VISIBLE
            previewContactName.text = message.selectedIndividualHashMap!![KEY_CONTACT_NAME]
            progressBar = previewContactProgressBar
            messageText.visibility = View.INVISIBLE
            clickView = previewContactContainer
            viewThemeUtils!!.talk.colorContactChatItemBackground(previewContactContainer)
            viewThemeUtils!!.talk.colorContactChatItemName(previewContactName)
            viewThemeUtils!!.platform.colorCircularProgressBar(
                previewContactProgressBar!!,
                ColorRole.ON_PRIMARY_CONTAINER
            )

            if (message.selectedIndividualHashMap!!.containsKey(KEY_CONTACT_PHOTO)) {
                image = previewContactPhoto
                placeholder = getDrawableFromContactDetails(
                    context,
                    message.selectedIndividualHashMap!![KEY_CONTACT_PHOTO]
                )
            } else {
                image = previewContactPhoto
                image.setImageDrawable(ContextCompat.getDrawable(context!!, R.drawable.ic_mimetype_text_vcard))
            }
        } else {
            previewContainer.visibility = View.VISIBLE
            previewContactContainer.visibility = View.GONE
        }

        if (message.selectedIndividualHashMap!!.containsKey(KEY_MIMETYPE)) {
            val mimetype = message.selectedIndividualHashMap!![KEY_MIMETYPE]
            val drawableResourceId = getDrawableResourceIdForMimeType(mimetype)
            var drawable = ContextCompat.getDrawable(context!!, drawableResourceId)
            if (drawable != null &&
                (
                    drawableResourceId == R.drawable.ic_mimetype_folder ||
                        drawableResourceId == R.drawable.ic_mimetype_package_x_generic
                    )
            ) {
                drawable = viewThemeUtils?.platform?.tintDrawable(context!!, drawable)
            }
            placeholder = drawable
        } else {
            fetchFileInformation(
                "/" + message.selectedIndividualHashMap!![KEY_PATH],
                message.activeUser
            )
        }

        return placeholder
    }

    private fun getDrawableFromContactDetails(context: Context?, base64: String?): Drawable? {
        var drawable: Drawable? = null
        if (base64 != "") {
            val inputStream = ByteArrayInputStream(
                Base64.decode(base64!!.toByteArray(), Base64.DEFAULT)
            )
            drawable = Drawable.createFromResourceStream(
                context!!.resources,
                null,
                inputStream,
                null,
                null
            )
            try {
                inputStream.close()
            } catch (e: IOException) {
                Log.e(TAG, "failed to close stream in getDrawableFromContactDetails", e)
            }
        }
        if (drawable == null) {
            drawable = ContextCompat.getDrawable(context!!, R.drawable.ic_mimetype_text_vcard)
        }
        return drawable
    }

    private fun fetchFileInformation(url: String, activeUser: User?) {
        Single.fromCallable { ReadFilesystemOperation(okHttpClient, activeUser, url, 0) }
            .observeOn(Schedulers.io())
            .subscribe(object : SingleObserver<ReadFilesystemOperation> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                override fun onSuccess(readFilesystemOperation: ReadFilesystemOperation) {
                    val davResponse = readFilesystemOperation.readRemotePath()
                    if (davResponse.data != null) {
                        val browserFileList = davResponse.data as List<BrowserFile>
                        if (browserFileList.isNotEmpty()) {
                            Handler(context!!.mainLooper).post {
                                val resourceId = getDrawableResourceIdForMimeType(browserFileList[0].mimeType)
                                placeholder = ContextCompat.getDrawable(context!!, resourceId)
                            }
                        }
                    }
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, "Error reading file information", e)
                }
            })
    }

    fun assignCommonMessageInterface(commonMessageInterface: CommonMessageInterface) {
        this.commonMessageInterface = commonMessageInterface
    }

    fun assignPreviewMessageInterface(previewMessageInterface: PreviewMessageInterface?) {
        this.previewMessageInterface = previewMessageInterface
    }

    fun hasBubbleBackground(message: ChatMessage): Boolean = !message.isVoiceMessage && message.message != "{file}"

    abstract val messageText: EmojiTextView
    abstract val messageCaption: EmojiTextView
    abstract val previewContainer: View
    abstract val previewContactContainer: MaterialCardView
    abstract val previewContactPhoto: ImageView
    abstract val previewContactName: EmojiTextView
    abstract val previewContactProgressBar: ProgressBar?

    companion object {
        private const val TAG = "PreviewMsgViewHolder"
        const val KEY_CONTACT_NAME = "contact-name"
        const val KEY_CONTACT_PHOTO = "contact-photo"
        const val KEY_MIMETYPE = "mimetype"
        const val KEY_ID = "id"
        const val KEY_PATH = "path"
        const val ACTOR_TYPE_BOTS = "bots"
        const val ACTOR_ID_CHANGELOG = "changelog"
        const val KEY_NAME = "name"
        const val MIN_IMAGE_HEIGHT = 100F
        const val HORIZONTAL_REACTION_PADDING = 8.0F
    }
}
