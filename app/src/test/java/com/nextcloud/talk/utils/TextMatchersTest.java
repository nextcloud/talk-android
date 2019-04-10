package com.nextcloud.talk.utils;

import com.nextcloud.talk.models.json.chat.ChatMessage;

import org.junit.Test;

import static org.junit.Assert.*;

public class TextMatchersTest {

    @Test
    public void getMessageTypeFromString_assertRegularTextMessageParsing() {
        String simpleMessageText = "Hello world! Have a cookie!";
        String messageContainingLink = "Hello https://nextcloud.com! Have a good day";

        assertEquals(ChatMessage.MessageType.REGULAR_TEXT_MESSAGE,
                TextMatchers.getMessageTypeFromString(simpleMessageText));
        assertEquals(ChatMessage.MessageType.REGULAR_TEXT_MESSAGE,
                TextMatchers.getMessageTypeFromString(messageContainingLink));
    }

    @Test
    public void getMessageTypeFromString_assertSingleLinkMessageParsing() {
        String simpleUrlText = "https://nextcloud.com/";
        String complexUrlText = "https://docs.nextcloud.com/server/15/admin_manual/#target-audience";

        assertEquals(ChatMessage.MessageType.SINGLE_LINK_MESSAGE,
                TextMatchers.getMessageTypeFromString(simpleUrlText));
        assertEquals(ChatMessage.MessageType.SINGLE_LINK_MESSAGE,
                TextMatchers.getMessageTypeFromString(complexUrlText));
    }

    @Test
    public void getMessageTypeFromString_assertSingleLinkImageMessage() {
        String simpleImageText = "https://nextcloud.com/image.jpg";
        String complexImageUrlText = "https://nextcloud.com/wp-content/themes/next/assets/img/features/mobileDesktop.png?x22777";

        assertEquals(ChatMessage.MessageType.SINGLE_LINK_IMAGE_MESSAGE,
                TextMatchers.getMessageTypeFromString(simpleImageText));
        assertEquals(ChatMessage.MessageType.SINGLE_LINK_IMAGE_MESSAGE,
                TextMatchers.getMessageTypeFromString(complexImageUrlText));
    }

    @Test
    public void getMessageTypeFromString_assertSingleLinkGifMessage() {
        String gifImageText = "https://nextcloud.com/funny.gif";

        assertEquals(ChatMessage.MessageType.SINGLE_LINK_GIF_MESSAGE,
                TextMatchers.getMessageTypeFromString(gifImageText));
    }

    @Test
    public void getMessageTypeFromString_assertSingleLinkAudioMessage() {
        String wavLink = "https://nextcloud.com/message.wav";
        String mp3Link = "https://nextcloud.com/message.mp3";

        assertEquals(ChatMessage.MessageType.SINGLE_LINK_AUDIO_MESSAGE,
                TextMatchers.getMessageTypeFromString(wavLink));
        assertEquals(ChatMessage.MessageType.SINGLE_LINK_AUDIO_MESSAGE,
                TextMatchers.getMessageTypeFromString(mp3Link));
    }

    @Test
    public void getMessageTypeFromString_assertSingleLinkVideoMessage() {
        String mp4Link = "https://nextcloud.com/message.mp4";
        String flvLink = "https://nextcloud.com/message.flv";

        assertEquals(ChatMessage.MessageType.SINGLE_LINK_VIDEO_MESSAGE,
                TextMatchers.getMessageTypeFromString(mp4Link));
        assertEquals(ChatMessage.MessageType.SINGLE_LINK_VIDEO_MESSAGE,
                TextMatchers.getMessageTypeFromString(flvLink));
    }

    @Test
    public void getMessageTypeFromString_assertSingleGiphyMessage() {
        String giphyLink = "https://media.giphy.com/media/11fucLQCTOdvBS/giphy.gif";

        assertEquals(ChatMessage.MessageType.SINGLE_LINK_GIPHY_MESSAGE,
                TextMatchers.getMessageTypeFromString(giphyLink));
    }

    @Test
    public void getMessageTypeFromString_assertSingleTenorMessage() {
        String tenorLink = "https://media.tenor.com/images/d98e76e3930cf171cc39e301c9e974af/tenor.gif";

        assertEquals(ChatMessage.MessageType.SINGLE_LINK_TENOR_MESSAGE,
                TextMatchers.getMessageTypeFromString(tenorLink));
    }
}