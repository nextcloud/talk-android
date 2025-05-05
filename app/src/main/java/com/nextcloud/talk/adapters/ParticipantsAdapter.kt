/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2021-2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2022 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.adapters

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.CallActivity
import com.nextcloud.talk.extensions.loadAvatarWithUrl
import com.nextcloud.talk.extensions.loadFirstLetterAvatar
import com.nextcloud.talk.models.json.participants.Participant
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import kotlin.math.ceil

class ParticipantsAdapter(
    private val mContext: Context,
    participantDisplayItems: Map<String?, ParticipantDisplayItem>,
    private val gridViewWrapper: RelativeLayout,
    private val callInfosLinearLayout: LinearLayout,
    private val columns: Int,
    private val isVoiceOnlyCall: Boolean
) : BaseAdapter() {
    private val participantDisplayItemObserver = ParticipantDisplayItem.Observer { this.notifyDataSetChanged() }

    private val participantDisplayItems = ArrayList<ParticipantDisplayItem>()

    init {
        this.participantDisplayItems.addAll(participantDisplayItems.values)

        for (participantDisplayItem in this.participantDisplayItems) {
            participantDisplayItem.addObserver(participantDisplayItemObserver)
        }
    }

    fun destroy() {
        for (participantDisplayItem in participantDisplayItems) {
            participantDisplayItem.removeObserver(participantDisplayItemObserver)
        }
    }

    override fun getCount(): Int {
        return participantDisplayItems.size
    }

    override fun getItem(position: Int): ParticipantDisplayItem {
        return participantDisplayItems[position]
    }

    override fun getItemId(position: Int): Long {
        return 0
    }

    @Suppress("Detekt.LongMethod", "Detekt.TooGenericExceptionCaught")
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var convertView = convertView
        val participantDisplayItem = getItem(position)

        val surfaceViewRenderer: SurfaceViewRenderer
        if (convertView == null) {
            convertView = LayoutInflater.from(mContext).inflate(R.layout.call_item, parent, false)
            convertView.visibility = View.VISIBLE

            surfaceViewRenderer = convertView.findViewById(R.id.surface_view)
            try {
                Log.d(TAG, "hasSurface: " + participantDisplayItem.rootEglBase.hasSurface())

                surfaceViewRenderer.setMirror(false)
                surfaceViewRenderer.init(participantDisplayItem.rootEglBase.eglBaseContext, null)
                surfaceViewRenderer.setZOrderMediaOverlay(false)
                // disabled because it causes some devices to crash
                surfaceViewRenderer.setEnableHardwareScaler(false)
                surfaceViewRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
            } catch (e: Exception) {
                Log.e(TAG, "error while initializing surfaceViewRenderer", e)
            }
        } else {
            surfaceViewRenderer = convertView.findViewById(R.id.surface_view)
        }

        val progressBar = convertView!!.findViewById<ProgressBar>(R.id.participant_progress_bar)
        if (!participantDisplayItem.isConnected) {
            progressBar.visibility = View.VISIBLE
        } else {
            progressBar.visibility = View.GONE
        }

        val layoutParams = convertView.layoutParams
        layoutParams.height = scaleGridViewItemHeight()
        convertView.layoutParams = layoutParams

        val nickTextView = convertView.findViewById<TextView>(R.id.peer_nick_text_view)
        val imageView = convertView.findViewById<ImageView>(R.id.avatarImageView)

        val mediaStream = participantDisplayItem.mediaStream
        if (hasVideoStream(participantDisplayItem, mediaStream)) {
            val videoTrack = mediaStream.videoTracks[0]
            videoTrack.addSink(surfaceViewRenderer)
            imageView.visibility = View.INVISIBLE
            surfaceViewRenderer.visibility = View.VISIBLE
            nickTextView.visibility = View.GONE
        } else {
            imageView.visibility = View.VISIBLE
            surfaceViewRenderer.visibility = View.INVISIBLE

            if ((mContext as CallActivity).isInPipMode) {
                nickTextView.visibility = View.GONE
            } else {
                nickTextView.visibility = View.VISIBLE
                nickTextView.text = participantDisplayItem.nick
            }
            if (participantDisplayItem.actorType == Participant.ActorType.GUESTS ||
                participantDisplayItem.actorType == Participant.ActorType.EMAILS
            ) {
                imageView
                    .loadFirstLetterAvatar(
                        participantDisplayItem.nick.toString()
                    )
            } else {
                imageView.loadAvatarWithUrl(null, participantDisplayItem.urlForAvatar)
            }
        }

        val audioOffView = convertView.findViewById<ImageView>(R.id.remote_audio_off)
        if (!participantDisplayItem.isAudioEnabled) {
            audioOffView.visibility = View.VISIBLE
        } else {
            audioOffView.visibility = View.GONE
        }

        val raisedHandView = convertView.findViewById<ImageView>(R.id.raised_hand)
        if (participantDisplayItem.raisedHand != null && participantDisplayItem.raisedHand.state) {
            raisedHandView.visibility = View.VISIBLE
        } else {
            raisedHandView.visibility = View.GONE
        }

        return convertView
    }

    @Suppress("ReturnCount")
    private fun hasVideoStream(participantDisplayItem: ParticipantDisplayItem, mediaStream: MediaStream?): Boolean {
        if (!participantDisplayItem.isStreamEnabled) {
            return false
        }

        if (mediaStream?.videoTracks == null) {
            return false
        }

        for (t in mediaStream.videoTracks) {
            if (MediaStreamTrack.State.LIVE == t.state()) {
                return true
            }
        }

        return false
    }

    private fun scaleGridViewItemHeight(): Int {
        var headerHeight = 0
        var callControlsHeight = 0
        if (callInfosLinearLayout.visibility == View.VISIBLE && isVoiceOnlyCall) {
            headerHeight = callInfosLinearLayout.height
        }
        if (isVoiceOnlyCall) {
            callControlsHeight = Math.round(mContext.resources.getDimension(R.dimen.call_controls_height))
        }
        var itemHeight = (gridViewWrapper.height - headerHeight - callControlsHeight) / getRowsCount(count)
        val itemMinHeight = Math.round(mContext.resources.getDimension(R.dimen.call_grid_item_min_height))
        if (itemHeight < itemMinHeight) {
            itemHeight = itemMinHeight
        }
        return itemHeight
    }

    private fun getRowsCount(items: Int): Int {
        var rows = ceil(items.toDouble() / columns.toDouble()).toInt()
        if (rows == 0) {
            rows = 1
        }
        return rows
    }

    companion object {
        private const val TAG = "ParticipantsAdapter"
    }
}
