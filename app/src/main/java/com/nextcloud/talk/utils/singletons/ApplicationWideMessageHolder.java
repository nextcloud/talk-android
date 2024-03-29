/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils.singletons;

import androidx.annotation.Nullable;

public class ApplicationWideMessageHolder {
    private static final ApplicationWideMessageHolder holder = new ApplicationWideMessageHolder();
    private MessageType messageType;

    public static ApplicationWideMessageHolder getInstance() {
        return holder;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public void setMessageType(@Nullable MessageType messageType) {
        this.messageType = messageType;
    }

    public enum MessageType {
        WRONG_ACCOUNT, ACCOUNT_UPDATED_NOT_ADDED, SERVER_WITHOUT_TALK,
        FAILED_TO_IMPORT_ACCOUNT, ACCOUNT_WAS_IMPORTED, CALL_PASSWORD_WRONG
    }


}
