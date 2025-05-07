/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2021-2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2022 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.adapters;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.nextcloud.talk.R;
import com.nextcloud.talk.activities.CallActivity;
import com.nextcloud.talk.extensions.ImageViewExtensionsKt;
import com.nextcloud.talk.models.json.participants.Participant;

import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.RendererCommon;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.Map;

public class ParticipantsAdapter extends BaseAdapter {

    private static final String TAG = "ParticipantsAdapter";

    private final ParticipantDisplayItem.Observer participantDisplayItemObserver = this::notifyDataSetChanged;

    private final Context mContext;
    private final ArrayList<ParticipantDisplayItem> participantDisplayItems;
    private final RelativeLayout gridViewWrapper;
    private final LinearLayout callInfosLinearLayout;
    private final int columns;
    private final boolean isVoiceOnlyCall;

    public ParticipantsAdapter(Context mContext,
                               Map<String, ParticipantDisplayItem> participantDisplayItems,
                               RelativeLayout gridViewWrapper,
                               LinearLayout callInfosLinearLayout,
                               int columns,
                               boolean isVoiceOnlyCall) {
        this.mContext = mContext;
        this.gridViewWrapper = gridViewWrapper;
        this.callInfosLinearLayout = callInfosLinearLayout;
        this.columns = columns;
        this.isVoiceOnlyCall = isVoiceOnlyCall;

        this.participantDisplayItems = new ArrayList<>();
        this.participantDisplayItems.addAll(participantDisplayItems.values());

        for (ParticipantDisplayItem participantDisplayItem : this.participantDisplayItems) {
            participantDisplayItem.addObserver(participantDisplayItemObserver);
        }
    }

    public void destroy() {
        for (ParticipantDisplayItem participantDisplayItem : participantDisplayItems) {
            participantDisplayItem.removeObserver(participantDisplayItemObserver);
        }
    }

    @Override
    public int getCount() {
        return participantDisplayItems.size();
    }

    @Override
    public ParticipantDisplayItem getItem(int position) {
        return participantDisplayItems.get(position);
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ParticipantDisplayItem participantDisplayItem = getItem(position);

        SurfaceViewRenderer surfaceViewRenderer;
        if (convertView == null) {
            convertView = LayoutInflater.from(mContext).inflate(R.layout.call_item, parent, false);
            convertView.setVisibility(View.VISIBLE);

            surfaceViewRenderer = convertView.findViewById(R.id.surface_view);
            try {
                Log.d(TAG, "hasSurface: " + participantDisplayItem.getRootEglBase().hasSurface());

                surfaceViewRenderer.setMirror(false);
                surfaceViewRenderer.init(participantDisplayItem.getRootEglBase().getEglBaseContext(), null);
                surfaceViewRenderer.setZOrderMediaOverlay(false);
                // disabled because it causes some devices to crash
                surfaceViewRenderer.setEnableHardwareScaler(false);
                surfaceViewRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
            } catch (Exception e) {
                Log.e(TAG, "error while initializing surfaceViewRenderer", e);
            }
        } else {
            surfaceViewRenderer = convertView.findViewById(R.id.surface_view);
        }

        ProgressBar progressBar = convertView.findViewById(R.id.participant_progress_bar);
        if (!participantDisplayItem.isConnected()) {
            progressBar.setVisibility(View.VISIBLE);
        } else {
            progressBar.setVisibility(View.GONE);
        }

        ViewGroup.LayoutParams layoutParams = convertView.getLayoutParams();
        layoutParams.height = scaleGridViewItemHeight();
        convertView.setLayoutParams(layoutParams);

        TextView nickTextView = convertView.findViewById(R.id.peer_nick_text_view);
        ImageView imageView = convertView.findViewById(R.id.avatarImageView);

        MediaStream mediaStream = participantDisplayItem.getMediaStream();
        if (hasVideoStream(participantDisplayItem, mediaStream)) {
            VideoTrack videoTrack = mediaStream.videoTracks.get(0);
            videoTrack.addSink(surfaceViewRenderer);
            imageView.setVisibility(View.INVISIBLE);
            surfaceViewRenderer.setVisibility(View.VISIBLE);
            nickTextView.setVisibility(View.GONE);
        } else {
            imageView.setVisibility(View.VISIBLE);
            surfaceViewRenderer.setVisibility(View.INVISIBLE);

            if (((CallActivity) mContext).isInPipMode) {
                nickTextView.setVisibility(View.GONE);
            } else {
                nickTextView.setVisibility(View.VISIBLE);
                nickTextView.setText(participantDisplayItem.getNick());
            }
            if (participantDisplayItem.getActorType() == Participant.ActorType.GUESTS ||
                participantDisplayItem.getActorType() == Participant.ActorType.EMAILS) {
                ImageViewExtensionsKt.loadFirstLetterAvatar(
                    imageView,
                    String.valueOf(participantDisplayItem.getNick())
                );
            } else {
                ImageViewExtensionsKt.loadAvatarWithUrl(imageView,null, participantDisplayItem.getUrlForAvatar());
            }
        }

        ImageView audioOffView = convertView.findViewById(R.id.remote_audio_off);
        if (!participantDisplayItem.isAudioEnabled()) {
            audioOffView.setVisibility(View.VISIBLE);
        } else {
            audioOffView.setVisibility(View.GONE);
        }

        ImageView raisedHandView = convertView.findViewById(R.id.raised_hand);
        if (participantDisplayItem.getRaisedHand() != null && participantDisplayItem.getRaisedHand().getState()) {
            raisedHandView.setVisibility(View.VISIBLE);
        } else {
            raisedHandView.setVisibility(View.GONE);
        }

        return convertView;
    }

    private boolean hasVideoStream(ParticipantDisplayItem participantDisplayItem, MediaStream mediaStream) {
        if (!participantDisplayItem.isStreamEnabled()) {
            return false;
        }

        if (mediaStream == null || mediaStream.videoTracks == null) {
            return false;
        }

        for (VideoTrack t : mediaStream.videoTracks) {
            if (MediaStreamTrack.State.LIVE == t.state()) {
                return true;
            }
        }

        return false;
    }

    private int scaleGridViewItemHeight() {
        int headerHeight = 0;
        int callControlsHeight = 0;
        if (callInfosLinearLayout.getVisibility() == View.VISIBLE && isVoiceOnlyCall) {
            headerHeight = callInfosLinearLayout.getHeight();
        }
        if (isVoiceOnlyCall) {
            callControlsHeight = Math.round(mContext.getResources().getDimension(R.dimen.call_controls_height));
        }
        int itemHeight = (gridViewWrapper.getHeight() - headerHeight - callControlsHeight) / getRowsCount(getCount());
        int itemMinHeight = Math.round(mContext.getResources().getDimension(R.dimen.call_grid_item_min_height));
        if (itemHeight < itemMinHeight) {
            itemHeight = itemMinHeight;
        }
        return itemHeight;
    }

    private int getRowsCount(int items) {
        int rows = (int) Math.ceil((double) items / (double) columns);
        if (rows == 0) {
            rows = 1;
        }
        return rows;
    }
}
