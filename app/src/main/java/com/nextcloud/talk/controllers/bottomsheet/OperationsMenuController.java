/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017 Mario Danic <mario@lovelyhq.com>
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

package com.nextcloud.talk.controllers.bottomsheet;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bluelinelabs.conductor.RouterTransaction;
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler;
import com.bluelinelabs.conductor.internal.NoOpControllerChangeHandler;
import com.nextcloud.talk.R;
import com.nextcloud.talk.activities.CallActivity;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.controllers.base.BaseController;
import com.nextcloud.talk.events.BottomSheetLockEvent;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.call.CallOverall;
import com.nextcloud.talk.models.json.capabilities.CapabilitiesOverall;
import com.nextcloud.talk.models.json.rooms.Room;
import com.nextcloud.talk.models.json.rooms.RoomOverall;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.ApplicationWideMessageHolder;
import com.nextcloud.talk.utils.DisplayUtils;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.database.user.UserUtils;

import org.greenrobot.eventbus.EventBus;
import org.parceler.Parcels;

import javax.inject.Inject;

import autodagger.AutoInjector;
import butterknife.BindView;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import retrofit2.HttpException;

@AutoInjector(NextcloudTalkApplication.class)
public class OperationsMenuController extends BaseController {

    @BindView(R.id.progress_bar)
    ProgressBar progressBar;

    @BindView(R.id.result_image_view)
    ImageView resultImageView;

    @BindView(R.id.result_text_view)
    TextView resultsTextView;

    @BindView(R.id.ok_button)
    Button okButton;

    @BindView(R.id.web_button)
    Button webButton;

    @Inject
    NcApi ncApi;

    @Inject
    UserUtils userUtils;

    @Inject
    EventBus eventBus;

    private int operationCode;
    private Room room;

    private UserEntity userEntity;
    private String callPassword;
    private String callUrl;

    private String baseUrl;
    private String callSession;
    private String conversationToken;

    private Disposable disposable;

    public OperationsMenuController(Bundle args) {
        super(args);
        this.operationCode = args.getInt(BundleKeys.KEY_OPERATION_CODE);
        if (args.containsKey(BundleKeys.KEY_ROOM)) {
            this.room = Parcels.unwrap(args.getParcelable(BundleKeys.KEY_ROOM));
        }

        this.callPassword = args.getString(BundleKeys.KEY_CALL_PASSWORD, "");
        this.callUrl = args.getString(BundleKeys.KEY_CALL_URL, "");
    }

    @Override
    protected View inflateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
        return inflater.inflate(R.layout.controller_operations_menu, container, false);
    }

    @Override
    protected void onViewBound(@NonNull View view) {
        super.onViewBound(view);
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);

        processOperation();
    }

    private void processOperation() {
        userEntity = userUtils.getCurrentUser();
        OperationsObserver operationsObserver = new OperationsObserver();

        if (!TextUtils.isEmpty(callUrl)) {
            conversationToken = callUrl.substring(callUrl.lastIndexOf("/") + 1, callUrl.length());
            if (callUrl.contains("/index.php")) {
                baseUrl = callUrl.substring(0, callUrl.indexOf("/index.php"));
            } else {
                baseUrl = callUrl.substring(0, callUrl.indexOf("/call"));
            }
        }

        if (userEntity != null) {
            String credentials = ApiUtils.getCredentials(userEntity.getUsername(), userEntity.getToken());

            if (!TextUtils.isEmpty(baseUrl) && !baseUrl.equals(userEntity.getBaseUrl())) {
                credentials = null;
            }

            switch (operationCode) {
                case 1:
                    ncApi.removeSelfFromRoom(credentials, ApiUtils.getUrlForRemoveSelfFromRoom(userEntity.getBaseUrl
                            (), room.getToken()))
                            .subscribeOn(Schedulers.newThread())
                            .observeOn(AndroidSchedulers.mainThread())
                            .retry(1)
                            .subscribe(operationsObserver);
                    break;
                case 2:
                    ncApi.renameRoom(credentials, ApiUtils.getRoom(userEntity.getBaseUrl(), room.getToken()),
                            room.getName())
                            .subscribeOn(Schedulers.newThread())
                            .observeOn(AndroidSchedulers.mainThread())
                            .retry(1)
                            .subscribe(operationsObserver);
                    break;
                case 3:
                    ncApi.makeRoomPublic(credentials, ApiUtils.getUrlForRoomVisibility(userEntity.getBaseUrl(), room
                            .getToken()))
                            .subscribeOn(Schedulers.newThread())
                            .observeOn(AndroidSchedulers.mainThread())
                            .retry(1)
                            .subscribe(operationsObserver);
                    break;
                case 4:
                case 5:
                case 6:
                    String pass = "";
                    if (room.getPassword() != null) {
                        pass = room.getPassword();
                    }
                    ncApi.setPassword(credentials, ApiUtils.getUrlForPassword(userEntity.getBaseUrl(),
                            room.getToken()), pass)
                            .subscribeOn(Schedulers.newThread())
                            .observeOn(AndroidSchedulers.mainThread())
                            .retry(1)
                            .subscribe(operationsObserver);
                    break;
                case 7:
                    // Operation 7 is sharing, so we handle this differently
                    break;
                case 8:
                    ncApi.makeRoomPrivate(credentials, ApiUtils.getUrlForRoomVisibility(userEntity.getBaseUrl(), room
                            .getToken()))
                            .subscribeOn(Schedulers.newThread())
                            .observeOn(AndroidSchedulers.mainThread())
                            .retry(1)
                            .subscribe(operationsObserver);
                    break;
                case 9:
                    ncApi.deleteRoom(credentials, ApiUtils.getUrlForRoomParticipants(userEntity.getBaseUrl(), room.getToken()))
                            .subscribeOn(Schedulers.newThread())
                            .observeOn(AndroidSchedulers.mainThread())
                            .retry(1)
                            .subscribe(operationsObserver);
                    break;
                case 10:
                    String finalCredentials = credentials;
                    ncApi.getRoom(null, ApiUtils.getRoom(baseUrl, conversationToken))
                            .subscribeOn(Schedulers.newThread())
                            .observeOn(AndroidSchedulers.mainThread())
                            .retry(1)
                            .subscribe(new Observer<RoomOverall>() {
                                @Override
                                public void onSubscribe(Disposable d) {
                                    disposable = d;
                                }

                                @Override
                                public void onNext(RoomOverall roomOverall) {
                                    room = roomOverall.getOcs().getData();
                                    ncApi.getCapabilities(finalCredentials, ApiUtils.getUrlForCapabilities(baseUrl))
                                            .subscribeOn(Schedulers.newThread())
                                            .observeOn(AndroidSchedulers.mainThread())
                                            .subscribe(new Observer<CapabilitiesOverall>() {
                                                @Override
                                                public void onSubscribe(Disposable d) {

                                                }

                                                @Override
                                                public void onNext(CapabilitiesOverall capabilitiesOverall) {
                                                    if (capabilitiesOverall.getOcs().getData()
                                                            .getCapabilities().getSpreedCapability() != null &&
                                                            capabilitiesOverall.getOcs().getData()
                                                                    .getCapabilities().getSpreedCapability()
                                                                    .getFeatures() != null && capabilitiesOverall.getOcs().getData()
                                                            .getCapabilities().getSpreedCapability()
                                                            .getFeatures().contains("guest-signaling")) {
                                                        if (room.isHasPassword() && room.isGuest()) {
                                                            eventBus.post(new BottomSheetLockEvent(true, 0,
                                                                    true, false));
                                                            Bundle bundle = new Bundle();
                                                            bundle.putParcelable(BundleKeys.KEY_ROOM, Parcels.wrap(room));
                                                            bundle.putString(BundleKeys.KEY_CALL_URL, callUrl);
                                                            bundle.putInt(BundleKeys.KEY_OPERATION_CODE, 99);
                                                            getRouter().pushController(RouterTransaction.with(new EntryMenuController(bundle))
                                                                    .pushChangeHandler(new HorizontalChangeHandler())
                                                                    .popChangeHandler(new HorizontalChangeHandler()));
                                                        } else {
                                                            initiateCall();
                                                        }
                                                    } else {
                                                        showResultImage(false, true);
                                                    }
                                                }

                                                @Override
                                                public void onError(Throwable e) {
                                                    showResultImage(false, false);
                                                }

                                                @Override
                                                public void onComplete() {

                                                }
                                            });
                                }

                                @Override
                                public void onError(Throwable e) {
                                    showResultImage(false, false);
                                    dispose();
                                }

                                @Override
                                public void onComplete() {
                                    dispose();
                                }
                            });
                    break;
                case 99:
                    ncApi.joinRoom(credentials, ApiUtils.getUrlForRoomParticipants(baseUrl, conversationToken),
                            callPassword)
                            .subscribeOn(Schedulers.newThread())
                            .observeOn(AndroidSchedulers.mainThread())
                            .retry(1)
                            .subscribe(operationsObserver);
                    break;
                default:
                    break;
            }
        }
    }

    private void showResultImage(boolean everythingOK, boolean isGuestSupportError) {
        progressBar.setVisibility(View.GONE);

        if (everythingOK) {
            resultImageView.setImageDrawable(DisplayUtils.getTintedDrawable(getResources(), R.drawable
                    .ic_check_circle_black_24dp, R.color.nc_darkGreen));
        } else {
            resultImageView.setImageDrawable(DisplayUtils.getTintedDrawable(getResources(), R.drawable
                    .ic_cancel_black_24dp, R.color.nc_darkRed));
        }

        resultImageView.setVisibility(View.VISIBLE);

        if (everythingOK) {
            resultsTextView.setText(R.string.nc_all_ok_operation);
        } else {
            resultsTextView.setTextColor(getResources().getColor(R.color.nc_darkRed));
            if (!isGuestSupportError) {
                resultsTextView.setText(R.string.nc_failed_to_perform_operation);
            } else {
                resultsTextView.setText(R.string.nc_failed_signaling_settings);
                webButton.setOnClickListener(v -> {
                    eventBus.post(new BottomSheetLockEvent(true, 0, false, true));
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(callUrl));
                    startActivity(browserIntent);
                    new BottomSheetLockEvent(true, 0, false, true);
                });
                webButton.setVisibility(View.VISIBLE);
            }
        }

        resultsTextView.setVisibility(View.VISIBLE);
        if (everythingOK) {
            eventBus.post(new BottomSheetLockEvent(true, 2500, true, true));
        } else {
            resultImageView.setImageDrawable(DisplayUtils.getTintedDrawable(getResources(), R.drawable
                    .ic_cancel_black_24dp, R.color.nc_darkRed));
            okButton.setOnClickListener(v -> eventBus.post(new BottomSheetLockEvent(true, 0, operationCode != 99
                    && operationCode != 10, true)));
            okButton.setVisibility(View.VISIBLE);
        }
    }

    private void dispose() {
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }

        disposable = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        dispose();
    }

    private void initiateCall() {
        eventBus.post(new BottomSheetLockEvent(true, 0, true, true));
        Bundle bundle = new Bundle();
        bundle.putString(BundleKeys.KEY_ROOM_TOKEN, room.getToken());
        bundle.putParcelable(BundleKeys.KEY_USER_ENTITY, Parcels.wrap(userEntity));
        if (!baseUrl.equals(userEntity.getBaseUrl())) {
            bundle.putString(BundleKeys.KEY_MODIFIED_BASE_URL, baseUrl);
        }
        bundle.putString(BundleKeys.KEY_CALL_SESSION, callSession);
        overridePushHandler(new NoOpControllerChangeHandler());
        overridePopHandler(new NoOpControllerChangeHandler());
        Intent callIntent = new Intent(getActivity(), CallActivity.class);
        callIntent.putExtras(bundle);
        startActivity(callIntent);
    }

    private class OperationsObserver implements Observer {

        @Override
        public void onSubscribe(Disposable d) {
            disposable = d;
        }

        @Override
        public void onNext(Object o) {
            if (operationCode != 99) {
                showResultImage(true, false);
            } else {
                CallOverall callOverall = (CallOverall) o;
                callSession = callOverall.getOcs().getData().getSessionId();
                initiateCall();
            }
        }

        @Override
        public void onError(Throwable e) {
            if (operationCode != 99 || !(e instanceof HttpException)) {
                showResultImage(false, false);
            } else {
                if (((HttpException) e).response().code() == 403) {
                    eventBus.post(new BottomSheetLockEvent(true, 0, false,
                            false));
                    ApplicationWideMessageHolder.getInstance().setMessageType(ApplicationWideMessageHolder.MessageType.CALL_PASSWORD_WRONG);
                    getRouter().popCurrentController();
                } else {
                    showResultImage(false, false);
                }
            }
            dispose();
        }

        @Override
        public void onComplete() {
            dispose();
        }
    }
}
