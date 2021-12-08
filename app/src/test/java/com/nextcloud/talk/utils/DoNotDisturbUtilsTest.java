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

import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.os.Vibrator;

import com.nextcloud.talk.application.NextcloudTalkApplication;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@RunWith(PowerMockRunner.class)
@PrepareForTest(NextcloudTalkApplication.class)
public class DoNotDisturbUtilsTest {

    @Mock
    private Context context;

    @Mock
    private NextcloudTalkApplication application;

    @Mock
    private NotificationManager notificationManager;

    @Mock
    private AudioManager audioManager;

    @Mock
    private Vibrator vibrator;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        mockStatic(NextcloudTalkApplication.Companion.class);
        NextcloudTalkApplication.Companion companionMock = PowerMockito.mock(NextcloudTalkApplication.Companion.class);
        Whitebox.setInternalState(NextcloudTalkApplication.class,"Companion",companionMock);
        PowerMockito.when(NextcloudTalkApplication.Companion.getSharedApplication()).thenReturn(application);
        when(application.getApplicationContext()).thenReturn(context);
        when(context.getSystemService(Context.NOTIFICATION_SERVICE)).thenReturn(notificationManager);
        when(context.getSystemService(Context.AUDIO_SERVICE)).thenReturn(audioManager);
        when(context.getSystemService(Context.VIBRATOR_SERVICE)).thenReturn(vibrator);
    }

    private static void setFinalStatic(Field field, Object newValue) throws Exception {
        field.setAccessible(true);

        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);

        field.set(null, newValue);
    }


    @Test
    public void shouldPlaySound_givenAndroidMAndInterruptionFilterNone_assertReturnsFalse()
            throws Exception {
        setFinalStatic(Build.VERSION.class.getField("SDK_INT"), Build.VERSION_CODES.M);

        when(notificationManager.getCurrentInterruptionFilter()).thenReturn(NotificationManager.INTERRUPTION_FILTER_NONE);
        when(audioManager.getRingerMode()).thenReturn(AudioManager.RINGER_MODE_NORMAL);

        assertFalse("shouldPlaySound incorrectly returned true",
                DoNotDisturbUtils.INSTANCE.shouldPlaySound());
    }

    @Test
    public void shouldPlaySound_givenRingerModeNotNormal_assertReturnsFalse() throws Exception {
        setFinalStatic(Build.VERSION.class.getField("SDK_INT"), Build.VERSION_CODES.LOLLIPOP);
        when(audioManager.getRingerMode()).thenReturn(AudioManager.RINGER_MODE_VIBRATE);

        assertFalse("shouldPlaySound incorrectly returned true",
                DoNotDisturbUtils.INSTANCE.shouldPlaySound());
    }

    @Test
    public void shouldVibrate_givenNoVibrator_assertReturnsFalse() {
        when(vibrator.hasVibrator()).thenReturn(false);

        assertFalse("shouldVibrate returned true despite no vibrator",
                DoNotDisturbUtils.INSTANCE.shouldVibrate(true));
    }

    @Test
    public void shouldVibrate_givenVibratorAndRingerModeNormal_assertReturnsTrue() {
        when(vibrator.hasVibrator()).thenReturn(true);

        when(audioManager.getRingerMode()).thenReturn(AudioManager.RINGER_MODE_NORMAL);

        assertTrue("shouldVibrate incorrectly returned false",
                DoNotDisturbUtils.INSTANCE.shouldVibrate(true));
    }

    @Test
    public void shouldVibrate_givenVibratorAndRingerModeSilent_assertReturnsFalse() {
        when(vibrator.hasVibrator()).thenReturn(true);

        when(audioManager.getRingerMode()).thenReturn(AudioManager.RINGER_MODE_SILENT);

        assertFalse("shouldVibrate returned true despite ringer mode set to silent",
                DoNotDisturbUtils.INSTANCE.shouldVibrate(true));
    }
}
