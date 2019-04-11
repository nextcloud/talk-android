package com.nextcloud.talk.utils;

import android.content.Context;
import android.content.res.Resources;
import android.text.TextUtils;

import com.nextcloud.talk.R;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.rooms.Conversation;
import com.nextcloud.talk.utils.database.user.UserUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@RunWith(PowerMockRunner.class)
@PrepareForTest(TextUtils.class)
public class ShareUtilsTest {

    @Mock
    private Context context;

    @Mock
    private Resources resources;

    @Mock
    private UserUtils userUtils;

    @Mock
    private Conversation conversation;

    @Mock
    private UserEntity userEntity;

    private final String baseUrl = "https://my.nextcloud.com";

    private final String token = "2aotbrjr";


    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mockStatic(TextUtils.class);
        when(userUtils.getCurrentUser()).thenReturn(userEntity);
        when(userEntity.getBaseUrl()).thenReturn(baseUrl);
        when(conversation.getToken()).thenReturn(token);
        when(context.getResources()).thenReturn(resources);
        when(resources.getString(R.string.nc_share_text)).thenReturn("Join the conversation at %1$s/index.php/call/%2$s");
        when(resources.getString(R.string.nc_share_text_pass)).thenReturn("\nPassword: %1$s");

    }

    @Test
    public void getStringForIntent_noPasswordGiven_correctStringWithoutPasswordReturned() {
        PowerMockito.when(TextUtils.isEmpty(anyString())).thenReturn(true);

        String expectedResult = String.format("Join the conversation at %s/index.php/call/%s",
                baseUrl, token);
        assertEquals("Intent string was not as expected",
                expectedResult, ShareUtils.getStringForIntent(context, "", userUtils, conversation));
    }

    @Test
    public void getStringForIntent_passwordGiven_correctStringWithPasswordReturned() {
        PowerMockito.when(TextUtils.isEmpty(anyString())).thenReturn(false);

        String password = "superSecret";
        String expectedResult = String.format("Join the conversation at %s/index.php/call/%s\nPassword: %s",
                baseUrl, token, password);
        assertEquals("Intent string was not as expected",
                expectedResult, ShareUtils.getStringForIntent(context, password, userUtils, conversation));
    }

}
