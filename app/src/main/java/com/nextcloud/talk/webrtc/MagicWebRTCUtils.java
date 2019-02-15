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
 *
 * Part of the code related to codec handling is originally:
 *
 *
 * Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license
 * that can be found in the LICENSE file in the root of the source
 * tree. An additional intellectual property rights grant can be found
 * in the file PATENTS.  All contributing project authors may
 * be found in the AUTHORS file in the root of the source tree.
 */

package com.nextcloud.talk.webrtc;

import android.os.Build;
import android.util.Log;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MagicWebRTCUtils {
    private static final String TAG = "MagicWebRTCUtils";

    /* AEC blacklist and SL_ES_WHITELIST are borrowed from Signal
       https://github.com/WhisperSystems/Signal-Android/blob/551470123d006b76a68d705d131bb12513a5e683/src/org/thoughtcrime/securesms/ApplicationContext.java
    */
    public static Set<String> HARDWARE_AEC_BLACKLIST = new HashSet<String>() {{
        add("D6503"); // Sony Xperia Z2 D6503
        add("ONE A2005"); // OnePlus 2
        add("MotoG3"); // Moto G (3rd Generation)
        add("Nexus 6P"); // Nexus 6p
        add("Pixel"); // Pixel
        add("Pixel XL"); // Pixel XL
        add("MI 4LTE"); // Xiami Mi4
        add("Redmi Note 3"); // Redmi Note 3
        add("Redmi Note 4"); // Redmi Note 4
        add("SM-G900F"); // Samsung Galaxy S5
        add("g3_kt_kr"); // LG G3
        add("SM-G930F"); // Samsung Galaxy S7
        add("Xperia SP"); // Sony Xperia SP
        add("Nexus 6"); // Nexus 6
        add("ONE E1003"); // OnePlus X
        add("One"); // OnePlus One
        add("Moto G5");
        add("Moto G (5S) Plus");
        add("Moto G4");
        add("TA-1053");
        add("E5823"); // Sony Z5 Compact
    }};

    public static Set<String> OPEN_SL_ES_WHITELIST = new HashSet<String>() {{
        add("Pixel");
        add("Pixel XL");
    }};

    private static Set<String> HARDWARE_ACCELERATION_DEVICE_BLACKLIST = new HashSet<String>() {{
        add("GT-I9100"); // Samsung Galaxy S2
        add("GT-N8013"); // Samsung Galaxy Note 10.1
        add("SM-G930F"); // Samsung Galaxy S7
        add("AGS-W09"); // Huawei MediaPad T3 10
        add("MIX 2"); // Xiaomi Mi Mix 2
        add("HUAWEI VNS-L31"); // Huawei P9 Lite
        add("ALE-L21"); // Huawei P8 Lite
        add("Z380M"); // Asus ZenPad 8.0
        add("XT1097"); // Motorola Moto X (2nd Gen)
    }};

    private static Set<String> HARDWARE_ACCELERATION_VENDOR_BLACKLIST = new HashSet<String>() {{
        add("samsung");
    }};


    public static boolean shouldEnableVideoHardwareAcceleration() {
        return (!HARDWARE_ACCELERATION_VENDOR_BLACKLIST.contains(Build.MANUFACTURER.toLowerCase())
                && !HARDWARE_ACCELERATION_DEVICE_BLACKLIST.contains(Build.MODEL.toUpperCase()));
    }

    public static String preferCodec(String sdpDescription, String codec, boolean isAudio) {
        final String[] lines = sdpDescription.split("\r\n");
        final int mLineIndex = findMediaDescriptionLine(isAudio, lines);
        if (mLineIndex == -1) {
            Log.w(TAG, "No mediaDescription line, so can't prefer " + codec);
            return sdpDescription;
        }
        // A list with all the payload types with name |codec|. The payload types are integers in the
        // range 96-127, but they are stored as strings here.
        final List<String> codecPayloadTypes = new ArrayList<String>();
        // a=rtpmap:<payload type> <encoding name>/<clock rate> [/<encoding parameters>]
        final Pattern codecPattern = Pattern.compile("^a=rtpmap:(\\d+) " + codec + "(/\\d+)+[\r]?$");
        for (int i = 0; i < lines.length; ++i) {
            Matcher codecMatcher = codecPattern.matcher(lines[i]);
            if (codecMatcher.matches()) {
                codecPayloadTypes.add(codecMatcher.group(1));
            }
        }
        if (codecPayloadTypes.isEmpty()) {
            Log.w(TAG, "No payload types with name " + codec);
            return sdpDescription;
        }

        final String newMLine = movePayloadTypesToFront(codecPayloadTypes, lines[mLineIndex]);
        if (newMLine == null) {
            return sdpDescription;
        }
        Log.d(TAG, "Change media description from: " + lines[mLineIndex] + " to " + newMLine);
        lines[mLineIndex] = newMLine;
        return joinString(Arrays.asList(lines), "\r\n", true);
    }

    /**
     * Returns the line number containing "m=audio|video", or -1 if no such line exists.
     */
    private static int findMediaDescriptionLine(boolean isAudio, String[] sdpLines) {
        final String mediaDescription = isAudio ? "m=audio " : "m=video ";
        for (int i = 0; i < sdpLines.length; ++i) {
            if (sdpLines[i].startsWith(mediaDescription)) {
                return i;
            }
        }
        return -1;
    }

    private static String movePayloadTypesToFront(List<String> preferredPayloadTypes, String mLine) {
        // The format of the media description line should be: m=<media> <port> <proto> <fmt> ...
        final List<String> origLineParts = Arrays.asList(mLine.split(" "));
        if (origLineParts.size() <= 3) {
            Log.e(TAG, "Wrong SDP media description format: " + mLine);
            return null;
        }
        final List<String> header = origLineParts.subList(0, 3);
        final List<String> unpreferredPayloadTypes =
                new ArrayList<String>(origLineParts.subList(3, origLineParts.size()));
        unpreferredPayloadTypes.removeAll(preferredPayloadTypes);
        // Reconstruct the line with |preferredPayloadTypes| moved to the beginning of the payload
        // types.
        final List<String> newLineParts = new ArrayList<String>();
        newLineParts.addAll(header);
        newLineParts.addAll(preferredPayloadTypes);
        newLineParts.addAll(unpreferredPayloadTypes);
        return joinString(newLineParts, " ", false /* delimiterAtEnd */);
    }

    private static String joinString(
            Iterable<? extends CharSequence> s, String delimiter, boolean delimiterAtEnd) {
        Iterator<? extends CharSequence> iter = s.iterator();
        if (!iter.hasNext()) {
            return "";
        }
        StringBuilder buffer = new StringBuilder(iter.next());
        while (iter.hasNext()) {
            buffer.append(delimiter).append(iter.next());
        }
        if (delimiterAtEnd) {
            buffer.append(delimiter);
        }
        return buffer.toString();
    }

}
