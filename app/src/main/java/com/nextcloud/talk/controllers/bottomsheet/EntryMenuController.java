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

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.bluelinelabs.conductor.RouterTransaction;
import com.nextcloud.talk.R;
import com.nextcloud.talk.api.models.json.rooms.Room;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.controllers.base.BaseController;
import com.nextcloud.talk.events.BottomSheetLockEvent;
import com.nextcloud.talk.utils.bundle.BundleKeys;

import org.greenrobot.eventbus.EventBus;
import org.parceler.Parcels;

import javax.inject.Inject;

import autodagger.AutoInjector;
import butterknife.BindView;
import butterknife.OnClick;
import studio.carbonylgroup.textfieldboxes.ExtendedEditText;
import studio.carbonylgroup.textfieldboxes.TextFieldBoxes;

@AutoInjector(NextcloudTalkApplication.class)
public class EntryMenuController extends BaseController {
    private static final String TAG = "EntryMenuController";

    @BindView(R.id.ok_button)
    Button proceedButton;

    @BindView(R.id.extended_edit_text)
    ExtendedEditText editText;

    @BindView(R.id.text_field_boxes)
    TextFieldBoxes textFieldBoxes;

    @Inject
    EventBus eventBus;

    private int operationCode;
    private Room room;

    public EntryMenuController(Bundle args) {
        super(args);
        this.operationCode = args.getInt(BundleKeys.KEY_OPERATION_CODE);
        this.room = Parcels.unwrap(args.getParcelable(BundleKeys.KEY_ROOM));
    }

    @Override
    protected View inflateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
        return inflater.inflate(R.layout.controller_entry_menu, container, false);
    }

    @OnClick(R.id.ok_button)
    public void onProceedButtonClick() {
        eventBus.post(new BottomSheetLockEvent(false, 0, false));

        Bundle bundle = new Bundle();
        if (operationCode == 4 || operationCode == 6) {
            room.setPassword(editText.getText().toString());
        } else {
            room.setName(editText.getText().toString());
        }
        bundle.putParcelable(BundleKeys.KEY_ROOM, Parcels.wrap(room));
        bundle.putInt(BundleKeys.KEY_OPERATION_CODE, operationCode);
        getRouter().pushController(RouterTransaction.with(new OperationsMenuController(bundle)));
    }

    @Override
    protected void onViewBound(@NonNull View view) {
        super.onViewBound(view);
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);

        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!TextUtils.isEmpty(s)) {
                    if (operationCode == 2) {
                        if (room.getName() == null || !room.getName().equals(s.toString())) {
                            if (proceedButton.isEnabled()) {
                                proceedButton.setEnabled(true);
                                proceedButton.setAlpha(1.0f);
                            }
                        } else {
                            if (!proceedButton.isEnabled()) {
                                proceedButton.setEnabled(false);
                                proceedButton.setAlpha(0.7f);
                            }
                            textFieldBoxes.setError(getResources().getString(R.string.nc_call_name_is_same),
                                    true);
                        }
                    } else {
                        if (!proceedButton.isEnabled()) {
                            proceedButton.setEnabled(true);
                            proceedButton.setAlpha(1.0f);
                        }
                    }
                } else {
                    if (proceedButton.isEnabled()) {
                        proceedButton.setEnabled(false);
                        proceedButton.setAlpha(0.7f);
                    }
                }
            }
        });

        String helperText = "";
        switch (operationCode) {
            case 2:
                helperText = getResources().getString(R.string.nc_call_name);
                break;
            case 4:
                helperText = getResources().getString(R.string.nc_new_password);
                break;
            case 6:
                helperText = getResources().getString(R.string.nc_password);
                break;
            default:
                break;
        }

        textFieldBoxes.setHelperText(helperText);
        editText.requestFocus();
    }

}
