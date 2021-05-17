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

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.ImageView;

import com.bluelinelabs.conductor.RouterTransaction;
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler;
import com.google.android.material.textfield.TextInputLayout;
import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.controllers.base.BaseController;
import com.nextcloud.talk.events.BottomSheetLockEvent;
import com.nextcloud.talk.models.json.conversations.Conversation;
import com.nextcloud.talk.utils.EmojiTextInputEditText;
import com.nextcloud.talk.utils.ShareUtils;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.database.user.UserUtils;
import com.nextcloud.talk.utils.singletons.ApplicationWideMessageHolder;
import com.vanniktech.emoji.EmojiImageView;
import com.vanniktech.emoji.EmojiPopup;
import com.vanniktech.emoji.emoji.Emoji;
import com.vanniktech.emoji.listeners.OnEmojiClickListener;
import com.vanniktech.emoji.listeners.OnEmojiPopupDismissListener;
import com.vanniktech.emoji.listeners.OnEmojiPopupShownListener;

import org.greenrobot.eventbus.EventBus;
import org.parceler.Parcels;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import autodagger.AutoInjector;
import butterknife.BindView;
import butterknife.OnClick;

@AutoInjector(NextcloudTalkApplication.class)
public class EntryMenuController extends BaseController {

    @BindView(R.id.ok_button)
    Button proceedButton;

    @BindView(R.id.text_edit)
    EmojiTextInputEditText editText;

    @BindView(R.id.text_input_layout)
    TextInputLayout textInputLayout;

    @BindView(R.id.smileyButton)
    ImageView smileyButton;

    @Inject
    EventBus eventBus;

    @Inject
    UserUtils userUtils;

    private int operationCode;
    private Conversation conversation;
    private Intent shareIntent;
    private String packageName;
    private String name;
    private String callUrl;

    private EmojiPopup emojiPopup;

    private Bundle originalBundle;

    public EntryMenuController(Bundle args) {
        super(args);
        originalBundle = args;

        this.operationCode = args.getInt(BundleKeys.INSTANCE.getKEY_OPERATION_CODE());
        if (args.containsKey(BundleKeys.INSTANCE.getKEY_ROOM())) {
            this.conversation = Parcels.unwrap(args.getParcelable(BundleKeys.INSTANCE.getKEY_ROOM()));
        }

        if (args.containsKey(BundleKeys.INSTANCE.getKEY_SHARE_INTENT())) {
            this.shareIntent = Parcels.unwrap(args.getParcelable(BundleKeys.INSTANCE.getKEY_SHARE_INTENT()));
        }

        this.name = args.getString(BundleKeys.INSTANCE.getKEY_APP_ITEM_NAME(), "");
        this.packageName = args.getString(BundleKeys.INSTANCE.getKEY_APP_ITEM_PACKAGE_NAME(), "");
        this.callUrl = args.getString(BundleKeys.INSTANCE.getKEY_CALL_URL(), "");
    }

    @NonNull
    @Override
    protected View inflateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
        return inflater.inflate(R.layout.controller_entry_menu, container, false);
    }

    @OnClick(R.id.smileyButton)
    void onSmileyClick() {
        emojiPopup.toggle();
    }

    @Override
    protected void onAttach(@NonNull View view) {
        super.onAttach(view);
        if (ApplicationWideMessageHolder.getInstance().getMessageType() != null &&
                ApplicationWideMessageHolder.getInstance().getMessageType().equals(ApplicationWideMessageHolder.MessageType.CALL_PASSWORD_WRONG)) {
            textInputLayout.setError(getResources().getString(R.string.nc_wrong_password));
            ApplicationWideMessageHolder.getInstance().setMessageType(null);
            if (proceedButton.isEnabled()) {
                proceedButton.setEnabled(false);
                proceedButton.setAlpha(0.7f);
            }
        }
    }

    @OnClick(R.id.ok_button)
    public void onProceedButtonClick() {
        Bundle bundle;
        if (operationCode == 99) {
            eventBus.post(new BottomSheetLockEvent(false, 0, false, false));
            bundle = new Bundle();
            bundle.putParcelable(BundleKeys.INSTANCE.getKEY_ROOM(), Parcels.wrap(conversation));
            bundle.putString(BundleKeys.INSTANCE.getKEY_CALL_URL(), callUrl);
            bundle.putString(BundleKeys.INSTANCE.getKEY_CONVERSATION_PASSWORD(), editText.getText().toString());
            bundle.putInt(BundleKeys.INSTANCE.getKEY_OPERATION_CODE(), operationCode);
            if (originalBundle.containsKey(BundleKeys.INSTANCE.getKEY_SERVER_CAPABILITIES())) {
                bundle.putParcelable(BundleKeys.INSTANCE.getKEY_SERVER_CAPABILITIES(), originalBundle.getParcelable(BundleKeys.INSTANCE.getKEY_SERVER_CAPABILITIES()));
            }

            getRouter().pushController(RouterTransaction.with(new OperationsMenuController(bundle))
                    .pushChangeHandler(new HorizontalChangeHandler())
                    .popChangeHandler(new HorizontalChangeHandler()));
        } else if (operationCode != 7 && operationCode != 10 && operationCode != 11) {
            eventBus.post(new BottomSheetLockEvent(false, 0, false, false));
            bundle = new Bundle();
            if (operationCode == 4 || operationCode == 6) {
                conversation.setPassword(editText.getText().toString());
            } else {
                conversation.setName(editText.getText().toString());
            }
            bundle.putParcelable(BundleKeys.INSTANCE.getKEY_ROOM(), Parcels.wrap(conversation));
            bundle.putInt(BundleKeys.INSTANCE.getKEY_OPERATION_CODE(), operationCode);
            getRouter().pushController(RouterTransaction.with(new OperationsMenuController(bundle))
                    .pushChangeHandler(new HorizontalChangeHandler())
                    .popChangeHandler(new HorizontalChangeHandler()));
        } else if (operationCode == 7) {
            if (getActivity() != null) {
                shareIntent.putExtra(Intent.EXTRA_TEXT, ShareUtils.getStringForIntent(getActivity(),
                        editText.getText().toString(), userUtils, conversation));
                Intent intent = new Intent(shareIntent);
                intent.setComponent(new ComponentName(packageName, name));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getActivity().startActivity(intent);
                eventBus.post(new BottomSheetLockEvent(true, 0, false, true));
            }
        } else if (operationCode != 11) {
            eventBus.post(new BottomSheetLockEvent(false, 0, false, false));
            bundle = new Bundle();
            bundle.putInt(BundleKeys.INSTANCE.getKEY_OPERATION_CODE(), operationCode);
            bundle.putString(BundleKeys.INSTANCE.getKEY_CALL_URL(), editText.getText().toString());
            getRouter().pushController(RouterTransaction.with(new OperationsMenuController(bundle))
                    .pushChangeHandler(new HorizontalChangeHandler())
                    .popChangeHandler(new HorizontalChangeHandler()));

        } else if (operationCode == 11) {
            eventBus.post(new BottomSheetLockEvent(false, 0, false, false));
            originalBundle.putString(BundleKeys.INSTANCE.getKEY_CONVERSATION_NAME(), editText.getText().toString());
            getRouter().pushController(RouterTransaction.with(new OperationsMenuController(originalBundle))
                    .pushChangeHandler(new HorizontalChangeHandler())
                    .popChangeHandler(new HorizontalChangeHandler()));

        }
    }

    @Override
    protected void onViewBound(@NonNull View view) {
        super.onViewBound(view);
        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);

        if (conversation != null && operationCode == 2) {
            editText.setText(conversation.getName());
        }

        editText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE && proceedButton != null && proceedButton.isEnabled()) {
                proceedButton.callOnClick();
                return true;
            }
            return false;
        });

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
                        if (conversation.getName() == null || !conversation.getName().equals(s.toString())) {
                            if (!proceedButton.isEnabled()) {
                                proceedButton.setEnabled(true);
                                proceedButton.setAlpha(1.0f);
                            }
                            textInputLayout.setErrorEnabled(false);
                        } else {
                            if (proceedButton.isEnabled()) {
                                proceedButton.setEnabled(false);
                                proceedButton.setAlpha(0.38f);
                            }
                            textInputLayout.setError(getResources().getString(R.string.nc_call_name_is_same));
                        }
                    } else if (operationCode != 10) {
                        if (!proceedButton.isEnabled()) {
                            proceedButton.setEnabled(true);
                            proceedButton.setAlpha(1.0f);
                        }
                        textInputLayout.setErrorEnabled(false);
                    } else if ((editText.getText().toString().startsWith("http://") ||
                            editText.getText().toString().startsWith("https://")) &&
                                    editText.getText().toString().contains("/call/")) {
                        // operation code 10
                        if (!proceedButton.isEnabled()) {
                            proceedButton.setEnabled(true);
                            proceedButton.setAlpha(1.0f);
                        }
                        textInputLayout.setErrorEnabled(false);
                    } else {
                        if (proceedButton.isEnabled()) {
                            proceedButton.setEnabled(false);
                            proceedButton.setAlpha(0.38f);
                        }
                        textInputLayout.setError(getResources().getString(R.string.nc_wrong_link));
                    }
                } else {
                    if (proceedButton.isEnabled()) {
                        proceedButton.setEnabled(false);
                        proceedButton.setAlpha(0.38f);
                    }
                    textInputLayout.setErrorEnabled(false);
                }
            }
        });

        String labelText = "";
        switch (operationCode) {
            case 11:
            case 2:
                labelText = getResources().getString(R.string.nc_call_name);
                editText.setInputType(InputType.TYPE_CLASS_TEXT);
                smileyButton.setVisibility(View.VISIBLE);
                emojiPopup = EmojiPopup.Builder.fromRootView(view).setOnEmojiPopupShownListener(new OnEmojiPopupShownListener() {
                    @Override
                    public void onEmojiPopupShown() {
                        if (getResources() != null) {
                            smileyButton.setColorFilter(getResources().getColor(R.color.colorPrimary),
                                    PorterDuff.Mode.SRC_IN);
                        }
                    }
                }).setOnEmojiPopupDismissListener(new OnEmojiPopupDismissListener() {
                    @Override
                    public void onEmojiPopupDismiss() {
                        if (smileyButton != null) {
                            smileyButton.setColorFilter(getResources().getColor(R.color.emoji_icons),
                                    PorterDuff.Mode.SRC_IN);
                        }
                    }
                }).setOnEmojiClickListener(new OnEmojiClickListener() {
                    @Override
                    public void onEmojiClick(@NonNull EmojiImageView emoji, @NonNull Emoji imageView) {
                        editText.getEditableText().append(" ");
                    }
                }).build(editText);

                break;
            case 4:
                labelText = getResources().getString(R.string.nc_new_password);
                editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                break;
            case 6:
            case 7:
            case 99:
                // 99 is joining a conversation via password
                labelText = getResources().getString(R.string.nc_password);
                editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                break;
            case 10:
                labelText = getResources().getString(R.string.nc_conversation_link);
                editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
                break;
            default:
                break;
        }

        if (operationCode == 99 || operationCode == 4 || operationCode == 6 || operationCode == 7) {
            textInputLayout.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        } else {
            textInputLayout.setEndIconMode(TextInputLayout.END_ICON_NONE);
        }

        textInputLayout.setHint(labelText);
        textInputLayout.requestFocus();
    }
}
