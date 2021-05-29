package com.nextcloud.talk.adapters.messages

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
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
import androidx.core.view.ViewCompat
import androidx.emoji.widget.EmojiTextView
import autodagger.AutoInjector
import butterknife.BindView
import butterknife.ButterKnife
import coil.load
import com.amulyakhare.textdrawable.TextDrawable
import com.facebook.drawee.view.SimpleDraweeView
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.TextMatchers
import com.nextcloud.talk.utils.preferences.AppPreferences
import com.stfalcon.chatkit.messages.MessageHolders
import java.net.URLEncoder
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class IncomingLocationMessageViewHolder(incomingView: View) : MessageHolders
.IncomingTextMessageViewHolder<ChatMessage>(incomingView) {

    private val TAG = "LocationMessageViewHolder"

    var mapProviderUrl: String = "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
    var mapProviderAttribution: String = "OpenStreetMap contributors"

    var locationLon: String? = ""
    var locationLat: String? = ""
    var locationName: String? = ""
    var locationGeoLink: String? = ""

    @JvmField
    @BindView(R.id.messageAuthor)
    var messageAuthor: EmojiTextView? = null

    @JvmField
    @BindView(R.id.messageText)
    var messageText: EmojiTextView? = null

    @JvmField
    @BindView(R.id.messageUserAvatar)
    var messageUserAvatarView: SimpleDraweeView? = null

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
    @Inject
    var context: Context? = null

    @JvmField
    @Inject
    var appPreferences: AppPreferences? = null

    @JvmField
    @BindView(R.id.webview)
    var webview: WebView? = null

    init {
        ButterKnife.bind(
            this,
            itemView
        )
    }

    @SuppressLint("SetTextI18n", "SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onBind(message: ChatMessage) {
        super.onBind(message)
        sharedApplication!!.componentApplication.inject(this)
        val author: String = message.actorDisplayName
        if (!TextUtils.isEmpty(author)) {
            messageAuthor!!.text = author
        } else {
            messageAuthor!!.setText(R.string.nc_nick_guest)
        }

        if (!message.isGrouped && !message.isOneToOneConversation) {
            messageUserAvatarView!!.visibility = View.VISIBLE
            if (message.actorType == "guests") {
                // do nothing, avatar is set
            } else if (message.actorType == "bots" && message.actorId == "changelog") {
                val layers = arrayOfNulls<Drawable>(2)
                layers[0] = context?.getDrawable(R.drawable.ic_launcher_background)
                layers[1] = context?.getDrawable(R.drawable.ic_launcher_foreground)
                val layerDrawable = LayerDrawable(layers)
                messageUserAvatarView?.setImageDrawable(DisplayUtils.getRoundedDrawable(layerDrawable))
            } else if (message.actorType == "bots") {
                val drawable = TextDrawable.builder()
                    .beginConfig()
                    .bold()
                    .endConfig()
                    .buildRound(
                        ">",
                        context!!.resources.getColor(R.color.black)
                    )
                messageUserAvatarView!!.visibility = View.VISIBLE
                messageUserAvatarView?.setImageDrawable(drawable)
            }
        } else {
            if (message.isOneToOneConversation) {
                messageUserAvatarView!!.visibility = View.GONE
            } else {
                messageUserAvatarView!!.visibility = View.INVISIBLE
            }
            messageAuthor!!.visibility = View.GONE
        }

        val resources = itemView.resources

        val bgBubbleColor = if (message.isDeleted) {
            resources.getColor(R.color.bg_message_list_incoming_bubble_deleted)
        } else {
            resources.getColor(R.color.bg_message_list_incoming_bubble)
        }

        var bubbleResource = R.drawable.shape_incoming_message

        if (message.isGrouped) {
            bubbleResource = R.drawable.shape_grouped_incoming_message
        }

        val bubbleDrawable = DisplayUtils.getMessageSelector(
            bgBubbleColor,
            resources.getColor(R.color.transparent),
            bgBubbleColor, bubbleResource
        )
        ViewCompat.setBackground(bubble, bubbleDrawable)

        val messageParameters = message.messageParameters

        itemView.isSelected = false
        messageTimeView!!.setTextColor(context?.resources!!.getColor(R.color.warm_grey_four))

        var messageString: Spannable = SpannableString(message.text)

        var textSize = context?.resources!!.getDimension(R.dimen.chat_text_size)

        if (messageParameters != null && messageParameters.size > 0) {
            for (key in messageParameters.keys) {
                val individualHashMap = message.messageParameters[key]
                if (individualHashMap != null) {
                    if (individualHashMap["type"] == "user" || individualHashMap["type"] == "guest" || individualHashMap["type"] == "call") {
                        if (individualHashMap["id"] == message.activeUser!!.userId) {
                            messageString = DisplayUtils.searchAndReplaceWithMentionSpan(
                                messageText!!.context,
                                messageString,
                                individualHashMap["id"]!!,
                                individualHashMap["name"]!!,
                                individualHashMap["type"]!!,
                                message.activeUser!!,
                                R.xml.chip_you
                            )
                        } else {
                            messageString = DisplayUtils.searchAndReplaceWithMentionSpan(
                                messageText!!.context,
                                messageString,
                                individualHashMap["id"]!!,
                                individualHashMap["name"]!!,
                                individualHashMap["type"]!!,
                                message.activeUser!!,
                                R.xml.chip_others
                            )
                        }
                    } else if (individualHashMap["type"] == "file") {
                        itemView.setOnClickListener { v ->
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(individualHashMap["link"]))
                            context!!.startActivity(browserIntent)
                        }
                    }
                }
            }
        } else if (TextMatchers.isMessageWithSingleEmoticonOnly(message.text)) {
            textSize = (textSize * 2.5).toFloat()
            itemView.isSelected = true
            messageAuthor!!.visibility = View.GONE
        }

        messageText!!.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
        messageText!!.text = messageString

        // parent message handling

        if (!message.isDeleted && message.parentMessage != null) {
            var parentChatMessage = message.parentMessage
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

            quotedUserName?.setTextColor(context!!.resources.getColor(R.color.textColorMaxContrast))

            if (parentChatMessage.actorId?.equals(message.activeUser.userId) == true) {
                quoteColoredView?.setBackgroundResource(R.color.colorPrimary)
            } else {
                quoteColoredView?.setBackgroundResource(R.color.textColorMaxContrast)
            }

            quotedChatMessageView?.visibility = View.VISIBLE
        } else {
            quotedChatMessageView?.visibility = View.GONE
        }

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
        urlStringBuffer.append("?mapProviderUrl=" + URLEncoder.encode(mapProviderUrl))
        urlStringBuffer.append("&mapProviderAttribution=" + URLEncoder.encode(mapProviderAttribution))
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
            context!!.startActivity(browserIntent)
        } else {
            Toast.makeText(context, R.string.nc_common_error_sorry, Toast.LENGTH_LONG).show()
            Log.e(TAG, "locationGeoLink was null or empty")
        }
    }

    private fun addMarkerToGeoLink(locationGeoLink: String): String {
        return locationGeoLink.replace("geo:", "geo:0,0?q=")
    }
}