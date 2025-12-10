/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.nextcloud.talk.chat.data.model.ChatMessage
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

private const val MAP_ZOOM = 15.0

@Composable
fun GeolocationMessage(message: ChatMessage, conversationThreadId: Long? = null, state: MutableState<Boolean>) {
    CommonMessageBody(
        message = message,
        conversationThreadId = conversationThreadId,
        playAnimation = state.value,
        content = {
            Column {
                if (message.messageParameters != null && message.messageParameters!!.isNotEmpty()) {
                    for (key in message.messageParameters!!.keys) {
                        val individualHashMap: Map<String?, String?> = message.messageParameters!![key]!!
                        if (individualHashMap["type"] == "geo-location") {
                            val lat = individualHashMap["latitude"]
                            val lng = individualHashMap["longitude"]

                            if (lat != null && lng != null) {
                                val latitude = lat.toDouble()
                                val longitude = lng.toDouble()
                                OpenStreetMap(latitude, longitude)
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun OpenStreetMap(latitude: Double, longitude: Double) {
    AndroidView(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(),
        factory = { context ->
            Configuration.getInstance().userAgentValue = context.packageName
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)

                val geoPoint = GeoPoint(latitude, longitude)
                controller.setCenter(geoPoint)
                controller.setZoom(MAP_ZOOM)

                val marker = Marker(this)
                marker.position = geoPoint
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                marker.title = "Location"
                overlays.add(marker)

                invalidate()
            }
        },
        update = { mapView ->
            val geoPoint = GeoPoint(latitude, longitude)
            mapView.controller.setCenter(geoPoint)

            val marker = mapView.overlays.find { it is Marker } as? Marker
            marker?.position = geoPoint
            mapView.invalidate()
        }
    )
}
