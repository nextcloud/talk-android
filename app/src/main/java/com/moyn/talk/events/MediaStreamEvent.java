/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017 Mario Danic <mario@lovelyhq.com>
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

package com.moyn.talk.events;

import org.webrtc.MediaStream;

import androidx.annotation.Nullable;

public class MediaStreamEvent {
    private final MediaStream mediaStream;
    private final String session;
    private final String videoStreamType;

    public MediaStreamEvent(@Nullable MediaStream mediaStream, String session, String videoStreamType) {
        this.mediaStream = mediaStream;
        this.session = session;
        this.videoStreamType = videoStreamType;
    }

    public MediaStream getMediaStream() {
        return this.mediaStream;
    }

    public String getSession() {
        return this.session;
    }

    public String getVideoStreamType() {
        return this.videoStreamType;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof MediaStreamEvent)) {
            return false;
        }
        final MediaStreamEvent other = (MediaStreamEvent) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$mediaStream = this.getMediaStream();
        final Object other$mediaStream = other.getMediaStream();
        if (this$mediaStream == null ? other$mediaStream != null : !this$mediaStream.equals(other$mediaStream)) {
            return false;
        }
        final Object this$session = this.getSession();
        final Object other$session = other.getSession();
        if (this$session == null ? other$session != null : !this$session.equals(other$session)) {
            return false;
        }
        final Object this$videoStreamType = this.getVideoStreamType();
        final Object other$videoStreamType = other.getVideoStreamType();

        return this$videoStreamType == null ? other$videoStreamType == null : this$videoStreamType.equals(other$videoStreamType);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof MediaStreamEvent;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $mediaStream = this.getMediaStream();
        result = result * PRIME + ($mediaStream == null ? 43 : $mediaStream.hashCode());
        final Object $session = this.getSession();
        result = result * PRIME + ($session == null ? 43 : $session.hashCode());
        final Object $videoStreamType = this.getVideoStreamType();
        result = result * PRIME + ($videoStreamType == null ? 43 : $videoStreamType.hashCode());
        return result;
    }

    public String toString() {
        return "MediaStreamEvent(mediaStream=" + this.getMediaStream() + ", session=" + this.getSession() + ", videoStreamType=" + this.getVideoStreamType() + ")";
    }
}
