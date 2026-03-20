/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.ui.model.ChatMessageUi
import com.nextcloud.talk.chat.ui.model.MessageStatusIcon
import com.nextcloud.talk.chat.ui.model.MessageTypeContent
import java.time.LocalDate
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.map.GestureOptions
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.style.rememberStyleState
import org.maplibre.compose.util.ClickResult
import org.maplibre.spatialk.geojson.Position

@Composable
fun GeolocationMessage(
    typeContent: MessageTypeContent.Geolocation,
    message: ChatMessageUi,
    isOneToOneConversation: Boolean = false,
    conversationThreadId: Long? = null
) {
    MessageScaffold(
        uiMessage = message,
        isOneToOneConversation = isOneToOneConversation,
        conversationThreadId = conversationThreadId,
        forceTimeBelow = true,
        includePadding = false,
        content = {
            GeolocationContent(
                typeContent = typeContent,
                message = message,
                conversationThreadId = conversationThreadId
            )
        }
    )
}

@Composable
fun GeolocationContent(
    typeContent: MessageTypeContent.Geolocation,
    message: ChatMessageUi,
    conversationThreadId: Long? = null
){
    val context = LocalContext.current

    Column {
        val latitude = typeContent.lat
        val longitude = typeContent.lon
        Box {
            OpenStreetMap(latitude, longitude)
            // Transparent overlay on top of the map. By sitting above the MapView in Compose's
            // z-order it receives touches first, preventing the AndroidView from calling
            // requestDisallowInterceptTouchEvent(true) and blocking parent list scrolling.
            // detectTapGestures does not consume drag events, so swipe-to-scroll propagates
            // to the parent list naturally.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(typeContent.id) {
                        detectTapGestures { openGeoLink(context, typeContent.id) }
                    }
            )
            Text(
                text = stringResource(R.string.osm_map_view_attributation),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = Color.Black.copy(alpha = 0.7f)
            )
        }
        typeContent.name.let { name ->
            if (name.isNotEmpty()) {
                Text(
                    text = name,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun OpenStreetMap(
    latitude: Double,
    longitude: Double
) {
    val cameraState =
        rememberCameraState(CameraPosition(target = Position(longitude, latitude), zoom = 12.0))

    val markerJson = """
        {
          "type": "FeatureCollection",
          "features": [
            {
              "type": "Feature",
              "geometry": {
                "type": "Point",
                "coordinates": [$longitude, $latitude]
              },
              "properties": {}
            }
          ]
        }
    """.trimIndent()

    MaplibreMap(
        modifier = Modifier.height(200.dp),
        // TODO: load style from file incl. other tiles url
        baseStyle = BaseStyle.Uri("https://tiles.openfreemap.org/styles/liberty"),
        cameraState = cameraState,
        styleState = rememberStyleState(),
        onMapClick = { _, _ -> ClickResult.Pass },
        options = MapOptions(
            ornamentOptions = OrnamentOptions.AllDisabled,
            gestureOptions = GestureOptions(
                isScrollEnabled = false,
                isZoomEnabled = false,
                isRotateEnabled = false,
                isTiltEnabled = false,
            )
        ),
    ) {
        val markerSource = rememberGeoJsonSource(
            GeoJsonData.JsonString(markerJson)
        )

        SymbolLayer(
            id = "marker-layer",
            source = markerSource,
            iconImage = image(painterResource(R.drawable.ic_baseline_location_on_red_24), drawAsSdf = true),
            iconColor = const(Color.Red),
            iconSize = const(1f),
            iconAllowOverlap = const(true),
        )
    }
}

private fun openGeoLink(context: Context, geoLink: String) {
    if (geoLink.isNotEmpty()) {
        val geoLinkWithMarker = geoLink.replace("geo:", "geo:0,0?q=")
        val browserIntent = Intent(Intent.ACTION_VIEW, geoLinkWithMarker.toUri())
        browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(browserIntent)
    }
}

@Preview(showBackground = true, name = "Geolocation Message Incoming")
@Preview(showBackground = true, name = "Geolocation Message Incoming Dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GeolocationMessagePreview() {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        val typeContent = MessageTypeContent.Geolocation(
            id = "geo:52.5200,13.4050",
            name = "Berlin, Germany",
            lat = 52.5200,
            lon = 13.4050
        )
        val uiMessage = ChatMessageUi(
            id = 1,
            text = "Check out this location!",
            message = "Check out this location!",
            renderMarkdown = false,
            actorDisplayName = "John Doe",
            isThread = false,
            threadTitle = "",
            incoming = true,
            isDeleted = false,
            avatarUrl = null,
            statusIcon = MessageStatusIcon.SENT,
            timestamp = System.currentTimeMillis() / 1000,
            date = LocalDate.now(),
            content = typeContent
        )
        GeolocationMessage(typeContent = typeContent, message = uiMessage)
    }
}

@Preview(showBackground = true, name = "Geolocation Message Outgoing")
@Composable
private fun GeolocationMessageOutgoingPreview() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        val typeContent = MessageTypeContent.Geolocation(
            id = "geo:48.8566,2.3522",
            name = "Paris, France",
            lat = 48.8566,
            lon = 2.3522
        )
        val uiMessage = ChatMessageUi(
            id = 2,
            text = "I am here!",
            message = "I am here!",
            renderMarkdown = false,
            actorDisplayName = "Me",
            isThread = false,
            threadTitle = "",
            incoming = false,
            isDeleted = false,
            avatarUrl = null,
            statusIcon = MessageStatusIcon.READ,
            timestamp = System.currentTimeMillis() / 1000,
            date = LocalDate.now(),
            content = typeContent
        )
        GeolocationMessage(typeContent = typeContent, message = uiMessage)
    }
}

@Preview(showBackground = true, name = "Geolocation Message No Name")
@Composable
private fun GeolocationMessageNoNamePreview() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        val typeContent = MessageTypeContent.Geolocation(
            id = "geo:52.5200,13.4050",
            name = "",
            lat = 52.5200,
            lon = 13.4050
        )
        val uiMessage = ChatMessageUi(
            id = 3,
            text = "Check this out",
            message = "Check this out",
            renderMarkdown = false,
            actorDisplayName = "John Doe",
            isThread = false,
            threadTitle = "",
            incoming = true,
            isDeleted = false,
            avatarUrl = null,
            statusIcon = MessageStatusIcon.SENT,
            timestamp = System.currentTimeMillis() / 1000,
            date = LocalDate.now(),
            content = typeContent
        )
        GeolocationMessage(typeContent = typeContent, message = uiMessage)
    }
}
