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
import lombok.Data;
import org.webrtc.MediaStream;

@Data
public class MediaStreamEvent {
    private final MediaStream mediaStream;
    private final String session;
    private final String videoStreamType;

    public MediaStreamEvent(@Nullable MediaStream mediaStream, String session, String videoStreamType) {
        this.mediaStream = mediaStream;
        this.session = session;
        this.videoStreamType = videoStreamType;
    }
}
