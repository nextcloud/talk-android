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

package com.nextcloud.talk.utils.singletons;

import android.content.Context;
import autodagger.AutoInjector;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.events.NetworkEvent;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.utils.database.user.UserUtils;
import com.novoda.merlin.*;
import org.greenrobot.eventbus.EventBus;

import javax.inject.Inject;

@AutoInjector(NextcloudTalkApplication.class)
public class MerlinTheWizard {
    private static Merlin merlin;
    private static MerlinsBeard merlinsBeard;

    private UserEntity currentUserEntity;

    @Inject
    EventBus eventBus;

    @Inject
    Context context;

    @Inject
    UserUtils userUtils;

    public MerlinTheWizard() {
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);
    }

    public void initMerlin() {
        if (userUtils.anyUserExists() && (currentUserEntity == null ||
                (userUtils.getCurrentUser().getId() != currentUserEntity.getId()))) {
            currentUserEntity = userUtils.getCurrentUser();
            setupMerlinForCurrentUserEntity();
        }
    }

    public Merlin getMerlin() {
        return merlin;
    }

    public MerlinsBeard getMerlinsBeard() {
        return merlinsBeard;
    }


    private void setupMerlinForCurrentUserEntity() {
        Endpoint endpoint = Endpoint.from(currentUserEntity.getBaseUrl() + "/index.php/204");
        ResponseCodeValidator responseCodeValidator =
                new ResponseCodeValidator.CaptivePortalResponseCodeValidator();

        if (merlin != null) {
            merlin.unbind();
        }

        merlin = new Merlin.Builder().withAllCallbacks().withEndpoint(endpoint).withResponseCodeValidator(responseCodeValidator).build(context);

        merlin.bind();

        merlinsBeard = new MerlinsBeard.Builder().withEndpoint(Endpoint.from(currentUserEntity.getBaseUrl() +
                "/index.php/204")).withResponseCodeValidator(new ResponseCodeValidator.CaptivePortalResponseCodeValidator()).build(context);

        merlin.registerConnectable(new Connectable() {
            @Override
            public void onConnect() {
                eventBus.post(new NetworkEvent(NetworkEvent.NetworkConnectionEvent.NETWORK_CONNECTED));
            }
        });

        merlin.registerDisconnectable(new Disconnectable() {
            @Override
            public void onDisconnect() {
                eventBus.post(new NetworkEvent(NetworkEvent.NetworkConnectionEvent.NETWORK_DISCONNECTED));
            }
        });
    }

}
