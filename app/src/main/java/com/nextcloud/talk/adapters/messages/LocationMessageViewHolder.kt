package com.nextcloud.talk.adapters.messages

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import autodagger.AutoInjector
import butterknife.BindView
import butterknife.ButterKnife
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.stfalcon.chatkit.messages.MessageHolders
import java.net.URLEncoder
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class LocationMessageViewHolder(incomingView: View) : MessageHolders
.IncomingTextMessageViewHolder<ChatMessage>(incomingView) {

    private val TAG = "LocationMessageViewHolder"

    var mapProviderUrl: String = "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
    var mapProviderAttribution: String = "OpenStreetMap contributors"

    var locationLon: String? = ""
    var locationLat: String? = ""
    var locationName: String? = ""
    var locationGeoLink: String? = ""

    @JvmField
    @BindView(R.id.locationText)
    var messageText: TextView? = null

    @JvmField
    @BindView(R.id.webview)
    var webview: WebView? = null

    @JvmField
    @Inject
    var context: Context? = null

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
        // if (message.messageType == ChatMessage.MessageType.SINGLE_NC_GEOLOCATION_MESSAGE) {
        //     Log.d(TAG, "handle geolocation here")
        //     messageText!!.text = "geolocation..."
        // }
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