/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class LocalCallParticipantModelTest {
    private var localCallParticipantModel: MutableLocalCallParticipantModel? = null
    private var mockedLocalCallParticipantModelObserver: LocalCallParticipantModel.Observer? = null

    @Before
    fun setUp() {
        localCallParticipantModel = MutableLocalCallParticipantModel()
        mockedLocalCallParticipantModelObserver = Mockito.mock(LocalCallParticipantModel.Observer::class.java)
    }

    @Test
    fun testSetAudioEnabled() {
        localCallParticipantModel!!.addObserver(mockedLocalCallParticipantModelObserver)

        localCallParticipantModel!!.isAudioEnabled = true

        assertTrue(localCallParticipantModel!!.isAudioEnabled)
        assertFalse(localCallParticipantModel!!.isSpeaking)
        assertFalse(localCallParticipantModel!!.isSpeakingWhileMuted)
        Mockito.verify(mockedLocalCallParticipantModelObserver, Mockito.only())?.onChange()
    }

    @Test
    fun testSetAudioEnabledWhileSpeakingWhileMuted() {
        localCallParticipantModel!!.isSpeaking = true

        localCallParticipantModel!!.addObserver(mockedLocalCallParticipantModelObserver)

        localCallParticipantModel!!.isAudioEnabled = true

        assertTrue(localCallParticipantModel!!.isAudioEnabled)
        assertTrue(localCallParticipantModel!!.isSpeaking)
        assertFalse(localCallParticipantModel!!.isSpeakingWhileMuted)
        Mockito.verify(mockedLocalCallParticipantModelObserver, Mockito.times(3))?.onChange()
    }

    @Test
    fun testSetAudioEnabledTwiceWhileSpeakingWhileMuted() {
        localCallParticipantModel!!.isSpeaking = true

        localCallParticipantModel!!.addObserver(mockedLocalCallParticipantModelObserver)

        localCallParticipantModel!!.isAudioEnabled = true
        localCallParticipantModel!!.isAudioEnabled = true

        assertTrue(localCallParticipantModel!!.isAudioEnabled)
        assertTrue(localCallParticipantModel!!.isSpeaking)
        assertFalse(localCallParticipantModel!!.isSpeakingWhileMuted)
        Mockito.verify(mockedLocalCallParticipantModelObserver, Mockito.times(3))?.onChange()
    }

    @Test
    fun testSetAudioDisabled() {
        localCallParticipantModel!!.isAudioEnabled = true

        localCallParticipantModel!!.addObserver(mockedLocalCallParticipantModelObserver)

        localCallParticipantModel!!.isAudioEnabled = false

        assertFalse(localCallParticipantModel!!.isAudioEnabled)
        assertFalse(localCallParticipantModel!!.isSpeaking)
        assertFalse(localCallParticipantModel!!.isSpeakingWhileMuted)
        Mockito.verify(mockedLocalCallParticipantModelObserver, Mockito.only())?.onChange()
    }

    @Test
    fun testSetAudioDisabledWhileSpeaking() {
        localCallParticipantModel!!.isAudioEnabled = true
        localCallParticipantModel!!.isSpeaking = true

        localCallParticipantModel!!.addObserver(mockedLocalCallParticipantModelObserver)

        localCallParticipantModel!!.isAudioEnabled = false

        assertFalse(localCallParticipantModel!!.isAudioEnabled)
        assertFalse(localCallParticipantModel!!.isSpeaking)
        assertTrue(localCallParticipantModel!!.isSpeakingWhileMuted)
        Mockito.verify(mockedLocalCallParticipantModelObserver, Mockito.times(3))?.onChange()
    }

    @Test
    fun testSetAudioDisabledTwiceWhileSpeaking() {
        localCallParticipantModel!!.isAudioEnabled = true
        localCallParticipantModel!!.isSpeaking = true

        localCallParticipantModel!!.addObserver(mockedLocalCallParticipantModelObserver)

        localCallParticipantModel!!.isAudioEnabled = false
        localCallParticipantModel!!.isAudioEnabled = false

        assertFalse(localCallParticipantModel!!.isAudioEnabled)
        assertFalse(localCallParticipantModel!!.isSpeaking)
        assertTrue(localCallParticipantModel!!.isSpeakingWhileMuted)
        Mockito.verify(mockedLocalCallParticipantModelObserver, Mockito.times(3))?.onChange()
    }

    @Test
    fun testSetSpeakingWhileAudioEnabled() {
        localCallParticipantModel!!.isAudioEnabled = true

        localCallParticipantModel!!.addObserver(mockedLocalCallParticipantModelObserver)

        localCallParticipantModel!!.isSpeaking = true

        assertTrue(localCallParticipantModel!!.isAudioEnabled)
        assertTrue(localCallParticipantModel!!.isSpeaking)
        assertFalse(localCallParticipantModel!!.isSpeakingWhileMuted)
        Mockito.verify(mockedLocalCallParticipantModelObserver, Mockito.only())?.onChange()
    }

    @Test
    fun testSetNotSpeakingWhileAudioEnabled() {
        localCallParticipantModel!!.isAudioEnabled = true
        localCallParticipantModel!!.isSpeaking = true

        localCallParticipantModel!!.addObserver(mockedLocalCallParticipantModelObserver)

        localCallParticipantModel!!.isSpeaking = false

        assertTrue(localCallParticipantModel!!.isAudioEnabled)
        assertFalse(localCallParticipantModel!!.isSpeaking)
        assertFalse(localCallParticipantModel!!.isSpeakingWhileMuted)
        Mockito.verify(mockedLocalCallParticipantModelObserver, Mockito.only())?.onChange()
    }

    @Test
    fun testSetSpeakingWhileAudioDisabled() {
        localCallParticipantModel!!.isAudioEnabled = false

        localCallParticipantModel!!.addObserver(mockedLocalCallParticipantModelObserver)

        localCallParticipantModel!!.isSpeaking = true

        assertFalse(localCallParticipantModel!!.isAudioEnabled)
        assertFalse(localCallParticipantModel!!.isSpeaking)
        assertTrue(localCallParticipantModel!!.isSpeakingWhileMuted)
        Mockito.verify(mockedLocalCallParticipantModelObserver, Mockito.only())?.onChange()
    }

    @Test
    fun testSetNotSpeakingWhileAudioDisabled() {
        localCallParticipantModel!!.isAudioEnabled = false
        localCallParticipantModel!!.isSpeaking = true

        localCallParticipantModel!!.addObserver(mockedLocalCallParticipantModelObserver)

        localCallParticipantModel!!.isSpeaking = false

        assertFalse(localCallParticipantModel!!.isAudioEnabled)
        assertFalse(localCallParticipantModel!!.isSpeaking)
        assertFalse(localCallParticipantModel!!.isSpeakingWhileMuted)
        Mockito.verify(mockedLocalCallParticipantModelObserver, Mockito.only())?.onChange()
    }
}
