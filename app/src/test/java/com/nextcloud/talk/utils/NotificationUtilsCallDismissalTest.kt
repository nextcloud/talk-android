/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.jobs.CallPushPayload
import com.nextcloud.talk.models.json.participants.ParticipantDto
import com.nextcloud.talk.utils.bundle.BundleKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A delete push for the server notification of a call has to take the ringing notification down. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class NotificationUtilsCallDismissalTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val user = User().apply { id = USER_ID }

    @Before
    fun setUp() {
        notificationManager.cancelAll()
    }

    @Test
    fun deletePushTakesDownTheCallWithTheSameServerNotificationId() {
        notifyCall(SYSTEM_ID, USER_ID, SERVER_ID)

        NotificationUtils.cancelNotification(context, user, SERVER_ID)

        assertTrue(notificationManager.activeNotifications.isEmpty())
    }

    @Test
    fun deletePushLeavesTheCallWithAnotherServerNotificationId() {
        notifyCall(SYSTEM_ID, USER_ID, SERVER_ID)

        NotificationUtils.cancelNotification(context, user, SERVER_ID + 1)

        assertEquals(listOf(SYSTEM_ID), notificationManager.activeNotifications.map { it.id })
    }

    @Test
    fun deletePushLeavesTheCallOfAnotherAccount() {
        notifyCall(SYSTEM_ID, USER_ID + 1, SERVER_ID)

        NotificationUtils.cancelNotification(context, user, SERVER_ID)

        assertEquals(listOf(SYSTEM_ID), notificationManager.activeNotifications.map { it.id })
    }

    @Test
    fun callWithoutServerNotificationIdIsNotTakenDownByAnyDeletePush() {
        notifyCall(SYSTEM_ID, USER_ID, null)

        NotificationUtils.cancelNotification(context, user, SERVER_ID)

        assertEquals(listOf(SYSTEM_ID), notificationManager.activeNotifications.map { it.id })
    }

    @Test
    fun callStopsRingingByItselfAfterTheTimeout() {
        val notification = callNotification(USER_ID, SERVER_ID)

        assertEquals(NotificationUtils.CALL_NOTIFICATION_TIMEOUT_MS, notification.timeoutAfter)
    }

    @Test
    fun activeNotificationExtrasCarryTheCallDataForTheOpenCallScreen() {
        notifyCall(SYSTEM_ID, USER_ID, SERVER_ID)

        val extras = NotificationUtils.getActiveNotificationExtras(context, SYSTEM_ID)!!

        assertEquals(CALL_FLAG, extras.getInt(BundleKeys.KEY_CALL_FLAG))
        assertEquals(DISPLAY_NAME, extras.getString(BundleKeys.KEY_CONVERSATION_DISPLAY_NAME))
        assertTrue(NotificationUtils.isNotificationVisible(context, SYSTEM_ID))
    }

    @Test
    fun noActiveNotificationMeansNoExtras() {
        assertNull(NotificationUtils.getActiveNotificationExtras(context, SYSTEM_ID))
        assertFalse(NotificationUtils.isNotificationVisible(context, SYSTEM_ID))
    }

    @Test
    fun callDataInTheExtrasDoesNotLetRoomScansFindTheCall() {
        notifyCall(SYSTEM_ID, USER_ID, SERVER_ID)

        assertNull(NotificationUtils.findNotificationForRoom(context, user, ROOM_TOKEN))
        NotificationUtils.cancelExistingNotificationsForRoom(context, user, ROOM_TOKEN)
        NotificationUtils.cancelNotification(context, user, SERVER_ID + 1)

        assertEquals(listOf(SYSTEM_ID), notificationManager.activeNotifications.map { it.id })
    }

    private fun callNotification(userId: Long, serverId: Long?): Notification {
        val call = CallPushPayload.IncomingCall.fromPush(ROOM_TOKEN, DISPLAY_NAME).copy(callFlag = CALL_FLAG)
        return NotificationUtils.applyCallDismissal(
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.sym_call_incoming)
                .addExtras(call.toNotificationExtras(userId, SYSTEM_ID)),
            userId,
            serverId
        ).build()
    }

    private fun notifyCall(systemId: Int, userId: Long, serverId: Long?) {
        notificationManager.notify(systemId, callNotification(userId, serverId))
    }

    private companion object {
        const val CHANNEL_ID = "calls"
        const val SYSTEM_ID = 1_234_567
        const val SERVER_ID = 619L
        const val USER_ID = 7L
        const val ROOM_TOKEN = "abc123"
        const val DISPLAY_NAME = "Alice"
        const val CALL_FLAG = ParticipantDto.InCallFlags.IN_CALL or ParticipantDto.InCallFlags.WITH_VIDEO
    }
}
