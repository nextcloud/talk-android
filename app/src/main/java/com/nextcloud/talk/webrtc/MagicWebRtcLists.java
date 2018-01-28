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

package com.nextcloud.talk.webrtc;

import java.util.HashSet;
import java.util.Set;

public class MagicWebRtcLists {
    /*
       AEC blacklist and SL_ES_WHITELIST are borrowed from Signal
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
    }};

    public static Set<String> OPEN_SL_ES_WHITELIST = new HashSet<String>() {{
        add("Pixel");
        add("Pixel XL");
    }};

    public static Set<String> HARDWARE_ACCELERATION_DEVICE_BLACKLIST = new HashSet<String>() {{
        add("GT-I9100"); // Samsung Galaxy S2
        add("GT-N8013"); // Samsung Galaxy Note 10.1
        add("SM-G930F"); // Samsung Galaxy S7
        add("AGS-W09"); // Huawei MediaPad T3 10
    }};

    public static Set<String> HARDWARE_ACCELERATION_VENDOR_BLACKLIST = new HashSet<String>() {{
        add("samsung");
    }};
}
