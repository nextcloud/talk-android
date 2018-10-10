/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
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

package com.nextcloud.talk.webrtc;

import com.nextcloud.talk.api.ExternalSignaling;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.tinder.scarlet.Scarlet;
import com.tinder.scarlet.messageadapter.moshi.MoshiMessageAdapter;
import com.tinder.scarlet.retry.LinearBackoffStrategy;
import com.tinder.scarlet.streamadapter.rxjava2.RxJava2StreamAdapterFactory;
import com.tinder.scarlet.websocket.okhttp.OkHttpClientUtils;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import autodagger.AutoInjector;
import okhttp3.OkHttpClient;

@AutoInjector(NextcloudTalkApplication.class)
public class ScarletHelper {
    private Map<String, ExternalSignaling> externalSignalingMap = new HashMap<>();

    @Inject
    OkHttpClient okHttpClient;

    public ScarletHelper() {
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);
    }

    public ExternalSignaling getExternalSignalingInstanceForServer(String url) {
        if (externalSignalingMap.containsKey(url)) {
            return externalSignalingMap.get(url);
        } else {
            Scarlet scarlet = new Scarlet.Builder()
                    .backoffStrategy(new LinearBackoffStrategy(500))
                    .webSocketFactory(OkHttpClientUtils.newWebSocketFactory(okHttpClient, url))
                    .addMessageAdapterFactory(new MoshiMessageAdapter.Factory())
                    .addStreamAdapterFactory(new RxJava2StreamAdapterFactory())
                    .build();
            ExternalSignaling externalSignaling = scarlet.create(ExternalSignaling.class);
            externalSignalingMap.put(url, externalSignaling);
            return externalSignaling;
        }
    }
}
