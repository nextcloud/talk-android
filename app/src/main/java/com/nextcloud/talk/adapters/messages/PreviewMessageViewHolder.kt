/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Marcel Hibbe
 * @author Andy Scherzinger
 * @author Tim Krüger
 * Copyright (C) 2021 Tim Krüger <t@timkrueger.me>
 * Copyright (C) 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * Copyright (C) 2021 Marcel Hibbe <dev@mhibbe.de>
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
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
import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Handler
import android.util.Base64
import android.util.Log
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import android.widget.ProgressBar
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.emoji.widget.EmojiTextView
import autodagger.AutoInjector
import com.facebook.drawee.view.SimpleDraweeView
import com.google.android.material.card.MaterialCardView
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.components.filebrowser.models.BrowserFile
import com.nextcloud.talk.components.filebrowser.webdav.ReadFilesystemOperation
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.ReactionsInsideMessageBinding
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.ui.recyclerview.MessageSwipeCallback
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.DrawableUtils.getDrawableResourceIdForMimeType
import com.nextcloud.talk.utils.FileViewerUtils
import com.nextcloud.talk.utils.FileViewerUtils.ProgressUi
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

    @JvmField
    @Inject
    var okHttpClient: OkHttpClient? = null
    open var progressBar: ProgressBar? = null
    open var reactionsBinding: ReactionsInsideMessageBinding? = null
    var fileViewerUtils: FileViewerUtils? = null
    var clickView: View? = null

    lateinit var commonMessageInterface: CommonMessageInterface
    var previewMessageInterface: PreviewMessageInterface? = null

    init {
        sharedApplication!!.componentApplication.inject(this)
    }

    @SuppressLint("SetTextI18n")
    override fun onBind(message: ChatMessage) {
        super.onBind(message)
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
                            message.actorId!!,
                            v.context
                        )
                    }
                }
                if (ACTOR_TYPE_BOTS == message.actorType && ACTOR_ID_CHANGELOG == message.actorId) {
                    if (context != null) {
                        val layers = arrayOfNulls<Drawable>(2)
                        layers[0] = ContextCompat.getDrawable(context!!, R.drawable.ic_launcher_background)
                        layers[1] = ContextCompat.getDrawable(context!!, R.drawable.ic_launcher_foreground)
                        val layerDrawable = LayerDrawable(layers)
                        userAvatar.hierarchy.setPlaceholderImage(DisplayUtils.getRoundedDrawable(layerDrawable))
                    }
                }
            }
        }
        viewThemeUtils!!.platform.colorCircularProgressBar(progressBar!!)
        clickView = image
        messageText.visibility = View.VISIBLE
        if (message.getCalculateMessageType() === ChatMessage.MessageType.SINGLE_NC_ATTACHMENT_MESSAGE) {
            fileViewerUtils = FileViewerUtils(context!!, message.activeUser!!)
            val fileName = message.selectedIndividualHashMap!![KEY_NAME]
            messageText.text = fileName
            if (message.selectedIndividualHashMap!!.containsKey(KEY_CONTACT_NAME)) {
                previewContainer.visibility = View.GONE
                previewContactName.text = message.selectedIndividualHashMap!![KEY_CONTACT_NAME]
                progressBar = previewContactProgressBar
                messageText.visibility = View.INVISIBLE
                clickView = previewContactContainer
                viewThemeUtils!!.talk.colorContactChatItemBackground(previewContactContainer)
                viewThemeUtils!!.talk.colorContactChatItemName(previewContactName)
                viewThemeUtils!!.platform.colorCircularProgressBarOnPrimaryContainer(previewContactProgressBar!!)
            } else {
                previewContainer.visibility = View.VISIBLE
                previewContactContainer.visibility = View.GONE
            }
            if (message.selectedIndividualHashMap!!.containsKey(KEY_CONTACT_PHOTO)) {
                image = previewContactPhoto
                val drawable = getDrawableFromContactDetails(
                    context,
                    message.selectedIndividualHashMap!![KEY_CONTACT_PHOTO]
                )
                image.hierarchy.setPlaceholderImage(drawable)
            } else if (message.selectedIndividualHashMap!!.containsKey(KEY_MIMETYPE)) {
                val mimetype = message.selectedIndividualHashMap!![KEY_MIMETYPE]
                val drawableResourceId = getDrawableResourceIdForMimeType(mimetype)
                val drawable = ContextCompat.getDrawable(context!!, drawableResourceId)
                if (drawable != null &&
                    (
                        drawableResourceId == R.drawable.ic_mimetype_folder ||
                            drawableResourceId == R.drawable.ic_mimetype_package_x_generic
                        )
                ) {
                    drawable.setColorFilter(
                        viewThemeUtils!!.getScheme(image.context).primary,
                        PorterDuff.Mode.SRC_ATOP
                    )
                }
                image.hierarchy.setPlaceholderImage(drawable)
            } else {
                fetchFileInformation(
                    "/" + message.selectedIndividualHashMap!![KEY_PATH],
                    message.activeUser
                )
            }
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
                clickView!!.setOnLongClickListener { l: View? ->
                    onMessageViewLongClick(message)
                    true
                }
            } else {
                Log.e(TAG, "failed to set click listener because activeUser, username or baseUrl were null")
            }
            fileViewerUtils!!.resumeToUpdateViewsByProgress(
                message.selectedIndividualHashMap!![KEY_NAME]!!,
                message.selectedIndividualHashMap!![KEY_ID]!!,
                message.selectedIndividualHashMap!![KEY_MIMETYPE],
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
                (clickView as SimpleDraweeView?)?.setOnClickListener {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(message.imageUrl))
                    browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context!!.startActivity(browserIntent)
                }
            } else {
                (clickView as SimpleDraweeView?)?.setOnClickListener(null)
            }
            messageText.text = ""
        }
        itemView.setTag(MessageSwipeCallback.REPLYABLE_VIEW_TAG, message.replyable)
        Reaction().showReactions(
            message,
            ::clickOnReaction,
            ::longClickOnReaction,
            reactionsBinding!!,
            messageText.context,
            true,
            viewThemeUtils!!
        )
    }

    private fun longClickOnReaction(chatMessage: ChatMessage) {
        commonMessageInterface.onLongClickReactions(chatMessage)
    }

    private fun clickOnReaction(chatMessage: ChatMessage, emoji: String) {
        commonMessageInterface.onClickReaction(chatMessage, emoji)
    }

    private fun getDrawableFromContactDetails(context: Context?, base64: String?): Drawable? {
        var drawable: Drawable? = null
        if (base64 != "") {
            val inputStream = ByteArrayInputStream(
                Base64.decode(base64!!.toByteArray(), Base64.DEFAULT)
            )
            drawable = Drawable.createFromResourceStream(
                context!!.resources,
                null, inputStream, null, null
            )
            try {
                inputStream.close()
            } catch (e: IOException) {
                val drawableResourceId = getDrawableResourceIdForMimeType("text/vcard")
                drawable = ContextCompat.getDrawable(context, drawableResourceId)
            }
        }
        return drawable
    }

    private fun onMessageViewLongClick(message: ChatMessage) {
        if (fileViewerUtils!!.isSupportedForInternalViewer(message.selectedIndividualHashMap!![KEY_MIMETYPE])) {
            previewMessageInterface!!.onPreviewMessageLongClick(message)
            return
        }
        val viewContext: Context? = if (itemView.context != null) {
            itemView.context
        } else {
            context
        }
        val popupMenu = PopupMenu(
            ContextThemeWrapper(viewContext, R.style.appActionBarPopupMenu),
            itemView,
            Gravity.START
        )
        popupMenu.inflate(R.menu.chat_preview_message_menu)
        popupMenu.setOnMenuItemClickListener { item: MenuItem ->
            if (item.itemId == R.id.openInFiles) {
                val keyID = message.selectedIndividualHashMap!![KEY_ID]
                val link = message.selectedIndividualHashMap!!["link"]
                fileViewerUtils!!.openFileInFilesApp(link!!, keyID!!)
            }
            true
        }
        popupMenu.show()
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
                                val drawable = ContextCompat.getDrawable(context!!, resourceId)
                                image.hierarchy.setPlaceholderImage(drawable)
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

    abstract val messageText: EmojiTextView
    abstract val previewContainer: View
    abstract val previewContactContainer: MaterialCardView
    abstract val previewContactPhoto: SimpleDraweeView
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
    }
}
