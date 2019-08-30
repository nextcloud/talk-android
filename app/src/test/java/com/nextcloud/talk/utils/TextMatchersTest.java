/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2019 Mario Danic <mario@lovelyhq.com>
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

package com.nextcloud.talk.utils;

import com.nextcloud.talk.models.json.chat.ChatMessage;

import org.junit.Test;

import static org.junit.Assert.*;

public class TextMatchersTest {

    @Test
    public void getMessageTypeFromString_regularTextGiven_regularTextMessageTypeReturned() {
        String simpleMessageText = "Hello world! Have a cookie!";
        String messageContainingLink = "Hello https://nextcloud.com! Have a good day";

        assertEquals(ChatMessage.MessageType.REGULAR_TEXT_MESSAGE,
                TextMatchers.getMessageTypeFromString(simpleMessageText));
        assertEquals(ChatMessage.MessageType.REGULAR_TEXT_MESSAGE,
                TextMatchers.getMessageTypeFromString(messageContainingLink));
    }

    @Test
    public void getMessageTypeFromString_singleUrlTextGiven_singleLinkMessageTypeReturned() {
        String simpleUrlText = "https://nextcloud.com/";
        String complexUrlText = "https://docs.nextcloud.com/server/15/admin_manual/#target-audience";

        assertEquals(ChatMessage.MessageType.SINGLE_LINK_MESSAGE,
                TextMatchers.getMessageTypeFromString(simpleUrlText));
        assertEquals(ChatMessage.MessageType.SINGLE_LINK_MESSAGE,
                TextMatchers.getMessageTypeFromString(complexUrlText));
    }

    @Test
    public void getMessageTypeFromString_imageLinkGiven_singleLinkImageMessageReturned() {
        String simpleImageText = "https://nextcloud.com/image.jpg";
        String complexImageUrlText = "https://nextcloud.com/wp-content/themes/next/assets/img/features/mobileDesktop.png?x22777";

        assertEquals(ChatMessage.MessageType.SINGLE_LINK_IMAGE_MESSAGE,
                TextMatchers.getMessageTypeFromString(simpleImageText));
        assertEquals(ChatMessage.MessageType.SINGLE_LINK_IMAGE_MESSAGE,
                TextMatchers.getMessageTypeFromString(complexImageUrlText));
    }

    @Test
    public void getMessageTypeFromString_gifLinkGiven_gifMessageTypeReturned() {
        String gifImageText = "https://nextcloud.com/funny.gif";

        assertEquals(ChatMessage.MessageType.SINGLE_LINK_GIF_MESSAGE,
                TextMatchers.getMessageTypeFromString(gifImageText));
    }

    @Test
    public void getMessageTypeFromString_audioLinkGiven_audioMessageTypeReturned() {
        String wavLink = "https://nextcloud.com/message.wav";
        String mp3Link = "https://nextcloud.com/message.mp3";

        assertEquals(ChatMessage.MessageType.SINGLE_LINK_AUDIO_MESSAGE,
                TextMatchers.getMessageTypeFromString(wavLink));
        assertEquals(ChatMessage.MessageType.SINGLE_LINK_AUDIO_MESSAGE,
                TextMatchers.getMessageTypeFromString(mp3Link));
    }

    @Test
    public void getMessageTypeFromString_videoLinkGiven_videoMessageTypeReturned() {
        String mp4Link = "https://nextcloud.com/message.mp4";
        String flvLink = "https://nextcloud.com/message.flv";

        assertEquals(ChatMessage.MessageType.SINGLE_LINK_VIDEO_MESSAGE,
                TextMatchers.getMessageTypeFromString(mp4Link));
        assertEquals(ChatMessage.MessageType.SINGLE_LINK_VIDEO_MESSAGE,
                TextMatchers.getMessageTypeFromString(flvLink));
    }

    @Test
    public void getMessageTypeFromString_giphyLinkGiven_giphyMessageTypeReturned() {
        String giphyLink = "https://media.giphy.com/media/11fucLQCTOdvBS/giphy.gif";

        assertEquals(ChatMessage.MessageType.SINGLE_LINK_GIPHY_MESSAGE,
                TextMatchers.getMessageTypeFromString(giphyLink));
    }

    @Test
    public void getMessageTypeFromString_tenorLinkGiven_tenorMessageTypeReturned() {
        String tenorLink = "https://media.tenor.com/images/d98e76e3930cf171cc39e301c9e974af/tenor.gif";

        assertEquals(ChatMessage.MessageType.SINGLE_LINK_TENOR_MESSAGE,
                TextMatchers.getMessageTypeFromString(tenorLink));
    }
}
