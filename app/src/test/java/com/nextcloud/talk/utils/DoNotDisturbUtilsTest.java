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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.Assert.*;
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
        MockitoAnnotations.initMocks(this);
        mockStatic(NextcloudTalkApplication.class);
        PowerMockito.when(NextcloudTalkApplication.getSharedApplication()).thenReturn(application);
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
                DoNotDisturbUtils.shouldPlaySound());
    }

    @Test
    public void shouldPlaySound_givenRingerModeNotNormal_assertReturnsFalse() throws Exception {
        setFinalStatic(Build.VERSION.class.getField("SDK_INT"), Build.VERSION_CODES.LOLLIPOP);
        when(audioManager.getRingerMode()).thenReturn(AudioManager.RINGER_MODE_VIBRATE);

        assertFalse("shouldPlaySound incorrectly returned true",
                DoNotDisturbUtils.shouldPlaySound());
    }

    @Test
    public void shouldVibrate_givenNoVibrator_assertReturnsFalse() {
        when(vibrator.hasVibrator()).thenReturn(false);

        assertFalse("shouldVibrate returned true despite no vibrator",
                DoNotDisturbUtils.shouldVibrate(true));
    }

    @Test
    public void shouldVibrate_givenVibratorAndRingerModeNormal_assertReturnsTrue() {
        when(vibrator.hasVibrator()).thenReturn(true);

        when(audioManager.getRingerMode()).thenReturn(AudioManager.RINGER_MODE_NORMAL);

        assertTrue("shouldVibrate incorrectly returned false",
                DoNotDisturbUtils.shouldVibrate(true));
    }

    @Test
    public void shouldVibrate_givenVibratorAndRingerModeSilent_assertReturnsFalse() {
        when(vibrator.hasVibrator()).thenReturn(true);

        when(audioManager.getRingerMode()).thenReturn(AudioManager.RINGER_MODE_SILENT);

        assertFalse("shouldVibrate returned true despite ringer mode set to silent",
                DoNotDisturbUtils.shouldVibrate(true));
    }
}
