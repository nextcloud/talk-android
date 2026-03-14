/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.ui.model.ChatMessageUi
import com.nextcloud.talk.chat.ui.model.MessageTypeContent
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
    conversationThreadId: Long? = null
) {
    MessageScaffold(
        uiMessage = message,
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

            OpenStreetMap(
                latitude,
                longitude,
                { openGeoLink(context,typeContent.id) }
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
    longitude: Double,
    onMapClick: () -> Unit
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
        onMapClick = { _, _ ->
            onMapClick()
            ClickResult.Consume
        },
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

