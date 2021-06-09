/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
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
import android.content.Intent
import android.graphics.PorterDuff
import android.net.Uri
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.ViewCompat
import androidx.emoji.widget.EmojiTextView
import autodagger.AutoInjector
import butterknife.BindView
import butterknife.ButterKnife
import coil.load
import com.google.android.flexbox.FlexboxLayout
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.models.json.chat.ReadStatus
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.stfalcon.chatkit.messages.MessageHolders
import java.net.URLEncoder
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class OutcomingLocationMessageViewHolder(incomingView: View) : MessageHolders
.OutcomingTextMessageViewHolder<ChatMessage>(incomingView) {

    private val TAG = "LocMessageView"

    var locationLon: String? = ""
    var locationLat: String? = ""
    var locationName: String? = ""
    var locationGeoLink: String? = ""

    @JvmField
    @BindView(R.id.messageText)
    var messageText: EmojiTextView? = null

    @JvmField
    @BindView(R.id.messageTime)
    var messageTimeView: TextView? = null

    @JvmField
    @BindView(R.id.quotedChatMessageView)
    var quotedChatMessageView: RelativeLayout? = null

    @JvmField
    @BindView(R.id.quotedMessageAuthor)
    var quotedUserName: EmojiTextView? = null

    @JvmField
    @BindView(R.id.quotedMessageImage)
    var quotedMessagePreview: ImageView? = null

    @JvmField
    @BindView(R.id.quotedMessage)
    var quotedMessage: EmojiTextView? = null

    @JvmField
    @BindView(R.id.quoteColoredView)
    var quoteColoredView: View? = null

    @JvmField
    @BindView(R.id.checkMark)
    var checkMark: ImageView? = null

    @JvmField
    @Inject
    var context: Context? = null

    @JvmField
    @BindView(R.id.webview)
    var webview: WebView? = null

    private val realView: View

    @SuppressLint("SetTextI18n", "SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onBind(message: ChatMessage) {
        super.onBind(message)
        sharedApplication!!.componentApplication.inject(this)

        realView.isSelected = false
        messageTimeView!!.setTextColor(context!!.resources.getColor(R.color.white60))
        val layoutParams = messageTimeView!!.layoutParams as FlexboxLayout.LayoutParams
        layoutParams.isWrapBefore = false

        val textSize = context!!.resources.getDimension(R.dimen.chat_text_size)

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
        messageText!!.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
        messageTimeView!!.layoutParams = layoutParams
        messageText!!.text = message.text
        messageText!!.isEnabled = false

        // parent message handling

        if (!message.isDeleted && message.parentMessage != null) {
            val parentChatMessage = message.parentMessage
            parentChatMessage.activeUser = message.activeUser
            parentChatMessage.imageUrl?.let {
                quotedMessagePreview?.visibility = View.VISIBLE
                quotedMessagePreview?.load(it) {
                    addHeader(
                        "Authorization",
                        ApiUtils.getCredentials(message.activeUser.username, message.activeUser.token)
                    )
                }
            } ?: run {
                quotedMessagePreview?.visibility = View.GONE
            }
            quotedUserName?.text = parentChatMessage.actorDisplayName
                ?: context!!.getText(R.string.nc_nick_guest)
            quotedMessage?.text = parentChatMessage.text
            quotedMessage?.setTextColor(context!!.resources.getColor(R.color.nc_outcoming_text_default))
            quotedUserName?.setTextColor(context!!.resources.getColor(R.color.nc_grey))

            quoteColoredView?.setBackgroundResource(R.color.white)

            quotedChatMessageView?.visibility = View.VISIBLE
        } else {
            quotedChatMessageView?.visibility = View.GONE
        }

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
                checkMark?.setImageDrawable(it)
            }
        }

        checkMark?.setContentDescription(readStatusContentDescriptionString)

        // geo-location

        if (message.messageParameters != null && message.messageParameters.size > 0) {
            for (key in message.messageParameters.keys) {
                val individualHashMap: Map<String, String> = message.messageParameters[key]!!
                if (individualHashMap["type"] == "geo-location") {
                    locationLon = individualHashMap["longitude"]
                    locationLat = individualHashMap["latitude"]
                    locationName = individualHashMap["name"]
                    locationGeoLink = individualHashMap["id"]
                }
            }
        }

        webview?.settings?.javaScriptEnabled = true

        webview?.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return if (url != null && (url.startsWith("http://") || url.startsWith("https://"))
                ) {
                    view?.context?.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    true
                } else {
                    false
                }
            }
        }

        val urlStringBuffer = StringBuffer("file:///android_asset/leafletMapMessagePreview.html")
        urlStringBuffer.append("?mapProviderUrl=" + URLEncoder.encode(context!!.getString(R.string.osm_tile_server_url)))
        urlStringBuffer.append(
            "&mapProviderAttribution=" + URLEncoder.encode(
                context!!.getString(
                    R.string
                        .osm_tile_server_attributation
                )
            )
        )
        urlStringBuffer.append("&locationLat=" + URLEncoder.encode(locationLat))
        urlStringBuffer.append("&locationLon=" + URLEncoder.encode(locationLon))
        urlStringBuffer.append("&locationName=" + URLEncoder.encode(locationName))
        urlStringBuffer.append("&locationGeoLink=" + URLEncoder.encode(locationGeoLink))

        webview?.loadUrl(urlStringBuffer.toString())

        webview?.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_UP -> openGeoLink()
                }

                return v?.onTouchEvent(event) ?: true
            }
        })
    }

    private fun openGeoLink() {
        if (!locationGeoLink.isNullOrEmpty()) {
            val geoLinkWithMarker = addMarkerToGeoLink(locationGeoLink!!)
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(geoLinkWithMarker))
            browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context!!.startActivity(browserIntent)
        } else {
            Toast.makeText(context, R.string.nc_common_error_sorry, Toast.LENGTH_LONG).show()
            Log.e(TAG, "locationGeoLink was null or empty")
        }
    }

    private fun addMarkerToGeoLink(locationGeoLink: String): String {
        return locationGeoLink.replace("geo:", "geo:0,0?q=")
    }

    init {
        ButterKnife.bind(this, itemView)
        this.realView = itemView
    }
}
