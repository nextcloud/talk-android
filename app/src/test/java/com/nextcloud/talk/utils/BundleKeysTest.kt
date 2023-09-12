/*
 * Nextcloud Talk application
 *
 * @author Samanwith KSN
 * Copyright (C) 2023 Samanwith KSN <samanwith21@gmail.com>
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
package com.nextcloud.talk.utils

import com.nextcloud.talk.utils.bundle.BundleKeys
import junit.framework.TestCase.assertEquals
import org.junit.Test
class BundleKeysTest {

    @Test
    fun testBundleKeysValues() {
        assertEquals("KEY_SELECTED_USERS", BundleKeys.KEY_SELECTED_USERS)
        assertEquals("KEY_SELECTED_GROUPS", BundleKeys.KEY_SELECTED_GROUPS)
        assertEquals("KEY_SELECTED_CIRCLES", BundleKeys.KEY_SELECTED_CIRCLES)
        assertEquals("KEY_SELECTED_EMAILS", BundleKeys.KEY_SELECTED_EMAILS)
        assertEquals("KEY_USERNAME", BundleKeys.KEY_USERNAME)
        assertEquals("KEY_TOKEN", BundleKeys.KEY_TOKEN)
        assertEquals("KEY_TRANSLATE_MESSAGE", BundleKeys.KEY_TRANSLATE_MESSAGE)
        assertEquals("KEY_BASE_URL", BundleKeys.KEY_BASE_URL)
        assertEquals("KEY_IS_ACCOUNT_IMPORT", BundleKeys.KEY_IS_ACCOUNT_IMPORT)
        assertEquals("KEY_ORIGINAL_PROTOCOL", BundleKeys.KEY_ORIGINAL_PROTOCOL)
        assertEquals("KEY_OPERATION_CODE", BundleKeys.KEY_OPERATION_CODE)
        assertEquals("KEY_APP_ITEM_PACKAGE_NAME", BundleKeys.KEY_APP_ITEM_PACKAGE_NAME)
        assertEquals("KEY_APP_ITEM_NAME", BundleKeys.KEY_APP_ITEM_NAME)
        assertEquals("KEY_CONVERSATION_PASSWORD", BundleKeys.KEY_CONVERSATION_PASSWORD)
        assertEquals("KEY_ROOM_TOKEN", BundleKeys.KEY_ROOM_TOKEN)
        assertEquals("KEY_ROOM_ONE_TO_ONE", BundleKeys.KEY_ROOM_ONE_TO_ONE)
        assertEquals("KEY_NEW_CONVERSATION", BundleKeys.KEY_NEW_CONVERSATION)
        assertEquals("KEY_ADD_PARTICIPANTS", BundleKeys.KEY_ADD_PARTICIPANTS)
        assertEquals("KEY_EXISTING_PARTICIPANTS", BundleKeys.KEY_EXISTING_PARTICIPANTS)
        assertEquals("KEY_CALL_URL", BundleKeys.KEY_CALL_URL)
        assertEquals("KEY_NEW_ROOM_NAME", BundleKeys.KEY_NEW_ROOM_NAME)
        assertEquals("KEY_MODIFIED_BASE_URL", BundleKeys.KEY_MODIFIED_BASE_URL)
        assertEquals("KEY_NOTIFICATION_SUBJECT", BundleKeys.KEY_NOTIFICATION_SUBJECT)
        assertEquals("KEY_NOTIFICATION_SIGNATURE", BundleKeys.KEY_NOTIFICATION_SIGNATURE)
        assertEquals("KEY_INTERNAL_USER_ID", BundleKeys.KEY_INTERNAL_USER_ID)
        assertEquals("KEY_CONVERSATION_TYPE", BundleKeys.KEY_CONVERSATION_TYPE)
        assertEquals("KEY_INVITED_PARTICIPANTS", BundleKeys.KEY_INVITED_PARTICIPANTS)
        assertEquals("KEY_INVITED_CIRCLE", BundleKeys.KEY_INVITED_CIRCLE)
        assertEquals("KEY_INVITED_GROUP", BundleKeys.KEY_INVITED_GROUP)
        assertEquals("KEY_INVITED_EMAIL", BundleKeys.KEY_INVITED_EMAIL)
    }
    @Test
    fun testBundleKeysValues2() {
        assertEquals("KEY_CONVERSATION_NAME", BundleKeys.KEY_CONVERSATION_NAME)
        assertEquals("KEY_RECORDING_STATE", BundleKeys.KEY_RECORDING_STATE)
        assertEquals("KEY_CALL_VOICE_ONLY", BundleKeys.KEY_CALL_VOICE_ONLY)
        assertEquals("KEY_CALL_WITHOUT_NOTIFICATION", BundleKeys.KEY_CALL_WITHOUT_NOTIFICATION)
        assertEquals("KEY_FROM_NOTIFICATION_START_CALL", BundleKeys.KEY_FROM_NOTIFICATION_START_CALL)
        assertEquals("KEY_ROOM_ID", BundleKeys.KEY_ROOM_ID)
        assertEquals("KEY_ARE_CALL_SOUNDS", BundleKeys.KEY_ARE_CALL_SOUNDS)
        assertEquals("KEY_FILE_PATHS", BundleKeys.KEY_FILE_PATHS)
        assertEquals("KEY_ACCOUNT", BundleKeys.KEY_ACCOUNT)
        assertEquals("KEY_FILE_ID", BundleKeys.KEY_FILE_ID)
        assertEquals("KEY_NOTIFICATION_ID", BundleKeys.KEY_NOTIFICATION_ID)
        assertEquals("KEY_NOTIFICATION_TIMESTAMP", BundleKeys.KEY_NOTIFICATION_TIMESTAMP)
        assertEquals("KEY_SHARED_TEXT", BundleKeys.KEY_SHARED_TEXT)
        assertEquals("KEY_GEOCODING_QUERY", BundleKeys.KEY_GEOCODING_QUERY)
        assertEquals("KEY_META_DATA", BundleKeys.KEY_META_DATA)
        assertEquals("KEY_FORWARD_MSG_FLAG", BundleKeys.KEY_FORWARD_MSG_FLAG)
        assertEquals("KEY_FORWARD_MSG_TEXT", BundleKeys.KEY_FORWARD_MSG_TEXT)
        assertEquals("KEY_FORWARD_HIDE_SOURCE_ROOM", BundleKeys.KEY_FORWARD_HIDE_SOURCE_ROOM)
        assertEquals("KEY_SYSTEM_NOTIFICATION_ID", BundleKeys.KEY_SYSTEM_NOTIFICATION_ID)
        assertEquals("KEY_MESSAGE_ID", BundleKeys.KEY_MESSAGE_ID)
        assertEquals("KEY_MIME_TYPE_FILTER", BundleKeys.KEY_MIME_TYPE_FILTER)
        assertEquals("KEY_PARTICIPANT_PERMISSION_CAN_PUBLISH_AUDIO",
            BundleKeys.KEY_PARTICIPANT_PERMISSION_CAN_PUBLISH_AUDIO)
        assertEquals("KEY_PARTICIPANT_PERMISSION_CAN_PUBLISH_VIDEO",
            BundleKeys.KEY_PARTICIPANT_PERMISSION_CAN_PUBLISH_VIDEO)
        assertEquals("KEY_IS_MODERATOR", BundleKeys.KEY_IS_MODERATOR)
        assertEquals("KEY_SWITCH_TO_ROOM", BundleKeys.KEY_SWITCH_TO_ROOM)
        assertEquals("KEY_START_CALL_AFTER_ROOM_SWITCH", BundleKeys.KEY_START_CALL_AFTER_ROOM_SWITCH)
        assertEquals("KEY_IS_BREAKOUT_ROOM", BundleKeys.KEY_IS_BREAKOUT_ROOM)
        assertEquals("KEY_NOTIFICATION_RESTRICT_DELETION", BundleKeys.KEY_NOTIFICATION_RESTRICT_DELETION)
        assertEquals("KEY_DISMISS_RECORDING_URL", BundleKeys.KEY_DISMISS_RECORDING_URL)
        assertEquals("KEY_SHARE_RECORDING_TO_CHAT_URL", BundleKeys.KEY_SHARE_RECORDING_TO_CHAT_URL)
        assertEquals("KEY_GEOCODING_RESULT", BundleKeys.KEY_GEOCODING_RESULT)
        assertEquals("ADD_ACCOUNT", BundleKeys.ADD_ACCOUNT)
        assertEquals("SAVED_TRANSLATED_MESSAGE", BundleKeys.SAVED_TRANSLATED_MESSAGE)
    }
}
