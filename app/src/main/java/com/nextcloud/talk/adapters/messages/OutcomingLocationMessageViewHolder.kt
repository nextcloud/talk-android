package com.nextcloud.talk.adapters.messages

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PorterDuff
import android.net.Uri
import android.text.Spannable
import android.text.SpannableString
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
import com.google.android.flexbox.FlexboxLayout
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.models.json.chat.ReadStatus
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.TextMatchers
import com.stfalcon.chatkit.messages.MessageHolders
import java.net.URLEncoder
import java.util.HashMap
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class OutcomingLocationMessageViewHolder(incomingView: View) : MessageHolders
.OutcomingTextMessageViewHolder<ChatMessage>(incomingView) {

    private val TAG = "LocationMessageViewHolder"

    var mapProviderUrl: String = "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
    var mapProviderAttribution: String = "OpenStreetMap contributors"

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
        val messageParameters: HashMap<String, HashMap<String, String>>? = message.messageParameters
        var messageString: Spannable = SpannableString(message.text)
        realView.isSelected = false
        messageTimeView!!.setTextColor(context!!.resources.getColor(R.color.white60))
        val layoutParams = messageTimeView!!.layoutParams as FlexboxLayout.LayoutParams
        layoutParams.isWrapBefore = false
        var textSize = context!!.resources.getDimension(R.dimen.chat_text_size)
        if (messageParameters != null && messageParameters.size > 0) {
            for (key in messageParameters.keys) {
                val individualHashMap: HashMap<String, String>? = message.messageParameters[key]
                if (individualHashMap != null) {
                    if (individualHashMap["type"] == "user" || (
                            individualHashMap["type"] == "guest"
                            ) || individualHashMap["type"] == "call"
                    ) {
                        messageString = DisplayUtils.searchAndReplaceWithMentionSpan(
                            messageText!!.context,
                            messageString,
                            individualHashMap["id"]!!,
                            individualHashMap["name"]!!,
                            individualHashMap["type"]!!,
                            message.activeUser,
                            R.xml.chip_others
                        )
                    } else if (individualHashMap["type"] == "file") {
                        realView.setOnClickListener(
                            View.OnClickListener { v: View? ->
                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(individualHashMap["link"]))
                                context!!.startActivity(browserIntent)
                            }
                        )
                    }
                }
            }
        } else if (TextMatchers.isMessageWithSingleEmoticonOnly(message.text)) {
            textSize = (textSize * 2.5).toFloat()
            layoutParams.isWrapBefore = true
            messageTimeView!!.setTextColor(context!!.resources.getColor(R.color.warm_grey_four))
            realView.isSelected = true
        }
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
            context?.resources?.getDrawable(drawableInt, null)?.let {
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

    init {
        ButterKnife.bind(this, itemView)
        this.realView = itemView
    }
}