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

package com.nextcloud.talk.utils.preferences;

import android.app.Dialog;
import android.content.Context;
import android.os.Build;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import androidx.appcompat.app.AlertDialog;
import autodagger.AutoInjector;
import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.yarolegovich.mp.io.StandardUserInputModule;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

@AutoInjector(NextcloudTalkApplication.class)
public class MagicUserInputModule extends StandardUserInputModule {

    @Inject
    AppPreferences appPreferences;

    private List<String> keysWithIntegerInput = new ArrayList<>();

    public MagicUserInputModule(Context context) {
        super(context);
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);
    }

    public MagicUserInputModule(Context context, List<String> keysWithIntegerInput) {
        super(context);
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);
        this.keysWithIntegerInput = keysWithIntegerInput;
    }

    @Override
    public void showEditTextInput(
            String key,
            CharSequence title,
            CharSequence defaultValue,
            final Listener<String> listener) {
        final View view = LayoutInflater.from(context).inflate(R.layout.dialog_edittext, null);
        final EditText inputField = view.findViewById(R.id.mp_text_input);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appPreferences.getIsKeyboardIncognito()) {
            inputField.setImeOptions(inputField.getImeOptions() | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        }

        if (defaultValue != null) {
            inputField.setText(defaultValue);
            inputField.setSelection(defaultValue.length());
        }

        if (keysWithIntegerInput.contains(key)) {
            inputField.setInputType(InputType.TYPE_CLASS_NUMBER);
        }

        final Dialog dialog = new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(view)
                .show();
        view.findViewById(R.id.mp_btn_confirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onInput(inputField.getText().toString());
                dialog.dismiss();
            }
        });
    }

}
