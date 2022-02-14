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

    private ConversationOperationEnum operation;
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

        this.operation = (ConversationOperationEnum) args.getSerializable(BundleKeys.INSTANCE.getKEY_OPERATION_CODE());
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
        if (operation == ConversationOperationEnum.JOIN_ROOM) {
            bundle = new Bundle();
            bundle.putParcelable(BundleKeys.INSTANCE.getKEY_ROOM(), Parcels.wrap(conversation));
            bundle.putString(BundleKeys.INSTANCE.getKEY_CALL_URL(), callUrl);
            bundle.putString(BundleKeys.INSTANCE.getKEY_CONVERSATION_PASSWORD(), editText.getText().toString());
            bundle.putSerializable(BundleKeys.INSTANCE.getKEY_OPERATION_CODE(), operation);
            if (originalBundle.containsKey(BundleKeys.INSTANCE.getKEY_SERVER_CAPABILITIES())) {
                bundle.putParcelable(BundleKeys.INSTANCE.getKEY_SERVER_CAPABILITIES(), originalBundle.getParcelable(BundleKeys.INSTANCE.getKEY_SERVER_CAPABILITIES()));
            }

            getRouter().pushController(RouterTransaction.with(new OperationsMenuController(bundle))
                    .pushChangeHandler(new HorizontalChangeHandler())
                    .popChangeHandler(new HorizontalChangeHandler()));
        } else if (operation != ConversationOperationEnum.SHARE_LINK && operation != ConversationOperationEnum.GET_JOIN_ROOM && operation != ConversationOperationEnum.INVITE_USERS) {
            bundle = new Bundle();
            if (operation == ConversationOperationEnum.CHANGE_PASSWORD || operation == ConversationOperationEnum.SET_PASSWORD) {
                conversation.setPassword(editText.getText().toString());
            } else {
                conversation.setName(editText.getText().toString());
            }
            bundle.putParcelable(BundleKeys.INSTANCE.getKEY_ROOM(), Parcels.wrap(conversation));
            bundle.putSerializable(BundleKeys.INSTANCE.getKEY_OPERATION_CODE(), operation);
            getRouter().pushController(RouterTransaction.with(new OperationsMenuController(bundle))
                    .pushChangeHandler(new HorizontalChangeHandler())
                    .popChangeHandler(new HorizontalChangeHandler()));
        } else if (operation == ConversationOperationEnum.SHARE_LINK) {
            if (getActivity() != null) {
                shareIntent.putExtra(Intent.EXTRA_TEXT, ShareUtils.getStringForIntent(getActivity(),
                        editText.getText().toString(), userUtils, conversation));
                Intent intent = new Intent(shareIntent);
                intent.setComponent(new ComponentName(packageName, name));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getActivity().startActivity(intent);
            }
        } else if (operation != ConversationOperationEnum.INVITE_USERS) {
            bundle = new Bundle();
            bundle.putSerializable(BundleKeys.INSTANCE.getKEY_OPERATION_CODE(), operation);
            bundle.putString(BundleKeys.INSTANCE.getKEY_CALL_URL(), editText.getText().toString());
            getRouter().pushController(RouterTransaction.with(new OperationsMenuController(bundle))
                    .pushChangeHandler(new HorizontalChangeHandler())
                    .popChangeHandler(new HorizontalChangeHandler()));

        } else if (operation == ConversationOperationEnum.INVITE_USERS) {
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

        if (conversation != null && operation == ConversationOperationEnum.RENAME_ROOM) {
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
                    if (operation == ConversationOperationEnum.RENAME_ROOM) {
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
                    } else if (operation != ConversationOperationEnum.GET_JOIN_ROOM) {
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
        switch (operation) {
            case INVITE_USERS:
            case RENAME_ROOM:
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
            case CHANGE_PASSWORD:
                labelText = getResources().getString(R.string.nc_new_password);
                editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                break;
            case SET_PASSWORD:
            case SHARE_LINK:
            case JOIN_ROOM:
                // 99 is joining a conversation via password
                labelText = getResources().getString(R.string.nc_password);
                editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                break;
            case GET_JOIN_ROOM:
                labelText = getResources().getString(R.string.nc_conversation_link);
                editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
                break;
            default:
                break;
        }

        if (operation == ConversationOperationEnum.JOIN_ROOM
            || operation == ConversationOperationEnum.CHANGE_PASSWORD
            || operation == ConversationOperationEnum.SET_PASSWORD
            || operation == ConversationOperationEnum.SHARE_LINK) {
            textInputLayout.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        } else {
            textInputLayout.setEndIconMode(TextInputLayout.END_ICON_NONE);
        }

        textInputLayout.setHint(labelText);
        textInputLayout.requestFocus();
    }

    @Override
    public AppBarLayoutType getAppBarLayoutType() {
        return AppBarLayoutType.SEARCH_BAR;
    }
}
