/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2019 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.converters;

import com.bluelinelabs.logansquare.typeconverters.StringBasedTypeConverter;
import com.nextcloud.talk.models.json.userprofile.Scope;

public class ScopeConverter extends StringBasedTypeConverter<Scope> {
    @Override
    public Scope getFromString(String string) {
        switch (string) {
            case "v2-private":
                return Scope.PRIVATE;
            case "v2-local":
                return Scope.LOCAL;
            case "v2-federated":
                return Scope.FEDERATED;
            case "v2-published":
                return Scope.PUBLISHED;
            default:
                return Scope.PRIVATE;
        }
    }

    @Override
    public String convertToString(Scope scope) {
        return scope.getId();
    }
}
