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

package com.nextcloud.talk.events;

import androidx.annotation.Nullable;
import com.nextcloud.talk.models.json.signaling.NCIceCandidate;
import lombok.Data;
import org.webrtc.SessionDescription;

@Data
public class SessionDescriptionSendEvent {
    @Nullable
    private final SessionDescription sessionDescription;
    private final String peerId;
    private final String type;
    @Nullable
    private final NCIceCandidate ncIceCandidate;
    private final String videoStreamType;

    public SessionDescriptionSendEvent(@Nullable SessionDescription sessionDescription, String peerId, String type,
                                       @Nullable NCIceCandidate ncIceCandidate, @Nullable String videoStreamType) {
        this.sessionDescription = sessionDescription;
        this.peerId = peerId;
        this.type = type;
        this.ncIceCandidate = ncIceCandidate;
        this.videoStreamType = videoStreamType;
    }
}
