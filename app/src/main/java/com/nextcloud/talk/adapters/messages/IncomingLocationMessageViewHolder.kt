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
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.ViewCompat
import autodagger.AutoInjector
import coil.load
import com.amulyakhare.textdrawable.TextDrawable
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.databinding.ItemCustomIncomingLocationMessageBinding
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.ui.bottom.sheet.ProfileBottomSheet
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.preferences.AppPreferences
import com.stfalcon.chatkit.messages.MessageHolders
import java.net.URLEncoder
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class IncomingLocationMessageViewHolder(incomingView: View, payload: Any) : MessageHolders
.IncomingTextMessageViewHolder<ChatMessage>(incomingView, payload) {
    private val binding: ItemCustomIncomingLocationMessageBinding =
        ItemCustomIncomingLocationMessageBinding.bind(itemView)

    var locationLon: String? = ""
    var locationLat: String? = ""
    var locationName: String? = ""
    var locationGeoLink: String? = ""

    @JvmField
    @Inject
    var context: Context? = null

    @JvmField
    @Inject
    var appPreferences: AppPreferences? = null

    @SuppressLint("SetTextI18n")
    override fun onBind(message: ChatMessage) {
        super.onBind(message)
        sharedApplication!!.componentApplication.inject(this)

        setAvatarAndAuthorOnMessageItem(message)

        colorizeMessageBubble(message)

        itemView.isSelected = false
        binding.messageTime.setTextColor(context?.resources!!.getColor(R.color.warm_grey_four))

        val textSize = context?.resources!!.getDimension(R.dimen.chat_text_size)
        binding.messageText.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
        binding.messageText.text = message.text
        binding.messageText.isEnabled = false

        // parent message handling
        setParentMessageDataOnMessageItem(message)

        // geo-location
        setLocationDataOnMessageItem(message)
    }

    private fun setAvatarAndAuthorOnMessageItem(message: ChatMessage) {
        val author: String = message.actorDisplayName
        if (!TextUtils.isEmpty(author)) {
            binding.messageAuthor.text = author
            binding.messageUserAvatar.setOnClickListener {
                (payload as? ProfileBottomSheet)?.showFor(message.actorId, itemView.context)
            }
        } else {
            binding.messageAuthor.setText(R.string.nc_nick_guest)
        }

        if (!message.isGrouped && !message.isOneToOneConversation) {
            binding.messageUserAvatar.visibility = View.VISIBLE
            if (message.actorType == "guests") {
                // do nothing, avatar is set
            } else if (message.actorType == "bots" && message.actorId == "changelog") {
                val layers = arrayOfNulls<Drawable>(2)
                layers[0] = AppCompatResources.getDrawable(context!!, R.drawable.ic_launcher_background)
                layers[1] = AppCompatResources.getDrawable(context!!, R.drawable.ic_launcher_foreground)
                val layerDrawable = LayerDrawable(layers)
                binding.messageUserAvatar.setImageDrawable(DisplayUtils.getRoundedDrawable(layerDrawable))
            } else if (message.actorType == "bots") {
                val drawable = TextDrawable.builder()
                    .beginConfig()
                    .bold()
                    .endConfig()
                    .buildRound(
                        ">",
                        context!!.resources.getColor(R.color.black)
                    )
                binding.messageUserAvatar.visibility = View.VISIBLE
                binding.messageUserAvatar.setImageDrawable(drawable)
            }
        } else {
            if (message.isOneToOneConversation) {
                binding.messageUserAvatar.visibility = View.GONE
            } else {
                binding.messageUserAvatar.visibility = View.INVISIBLE
            }
            binding.messageAuthor.visibility = View.GONE
        }
    }

    private fun colorizeMessageBubble(message: ChatMessage) {
        val resources = itemView.resources

        var bubbleResource = R.drawable.shape_incoming_message

        if (message.isGrouped) {
            bubbleResource = R.drawable.shape_grouped_incoming_message
        }

        val bgBubbleColor = if (message.isDeleted) {
            resources.getColor(R.color.bg_message_list_incoming_bubble_deleted)
        } else {
            resources.getColor(R.color.bg_message_list_incoming_bubble)
        }
        val bubbleDrawable = DisplayUtils.getMessageSelector(
            bgBubbleColor,
            resources.getColor(R.color.transparent),
            bgBubbleColor, bubbleResource
        )
        ViewCompat.setBackground(bubble, bubbleDrawable)
    }

    private fun setParentMessageDataOnMessageItem(message: ChatMessage) {
        if (!message.isDeleted && message.parentMessage != null) {
            val parentChatMessage = message.parentMessage
            parentChatMessage.activeUser = message.activeUser
            parentChatMessage.imageUrl?.let {
                binding.messageQuote.quotedMessageImage.visibility = View.VISIBLE
                binding.messageQuote.quotedMessageImage.load(it) {
                    addHeader(
                        "Authorization",
                        ApiUtils.getCredentials(message.activeUser.username, message.activeUser.token)
                    )
                }
            } ?: run {
                binding.messageQuote.quotedMessageImage.visibility = View.GONE
            }
            binding.messageQuote.quotedMessageAuthor.text = parentChatMessage.actorDisplayName
                ?: context!!.getText(R.string.nc_nick_guest)
            binding.messageQuote.quotedMessage.text = parentChatMessage.text

            binding.messageQuote.quotedMessageAuthor
                .setTextColor(context!!.resources.getColor(R.color.textColorMaxContrast))

            if (parentChatMessage.actorId?.equals(message.activeUser.userId) == true) {
                binding.messageQuote.quoteColoredView.setBackgroundResource(R.color.colorPrimary)
            } else {
                binding.messageQuote.quoteColoredView.setBackgroundResource(R.color.textColorMaxContrast)
            }

            binding.messageQuote.quotedChatMessageView.visibility = View.VISIBLE
        } else {
            binding.messageQuote.quotedChatMessageView.visibility = View.GONE
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun setLocationDataOnMessageItem(message: ChatMessage) {
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

        binding.webview.settings?.javaScriptEnabled = true

        binding.webview.webViewClient = object : WebViewClient() {
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
        urlStringBuffer.append(
            "?mapProviderUrl=" + URLEncoder.encode(context!!.getString(R.string.osm_tile_server_url))
        )
        urlStringBuffer.append(
            "&mapProviderAttribution=" + URLEncoder.encode(context!!.getString(R.string.osm_tile_server_attributation))
        )
        urlStringBuffer.append("&locationLat=" + URLEncoder.encode(locationLat))
        urlStringBuffer.append("&locationLon=" + URLEncoder.encode(locationLon))
        urlStringBuffer.append("&locationName=" + URLEncoder.encode(locationName))
        urlStringBuffer.append("&locationGeoLink=" + URLEncoder.encode(locationGeoLink))

        binding.webview.loadUrl(urlStringBuffer.toString())

        binding.webview.setOnTouchListener(object : View.OnTouchListener {
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

    companion object {
        private const val TAG = "LocInMessageView"
    }
}
