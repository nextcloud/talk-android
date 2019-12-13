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
package com.nextcloud.talk.utils.preferences

import android.app.Dialog
import android.content.Context
import android.os.Build
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.nextcloud.talk.R
import com.yarolegovich.mp.io.StandardUserInputModule
import com.yarolegovich.mp.io.UserInputModule
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.util.*

class MagicUserInputModule : StandardUserInputModule, KoinComponent {
    val appPreferences: AppPreferences by inject()
    private var keysWithIntegerInput: List<String> = ArrayList()

    constructor(context: Context?, keysWithIntegerInput: List<String>) : super(context) {
        this.keysWithIntegerInput = keysWithIntegerInput
    }

    override fun showEditTextInput(
            key: String,
            title: CharSequence,
            defaultValue: CharSequence,
            listener: UserInputModule.Listener<String>) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_edittext, null)
        val inputField = view.findViewById<EditText>(R.id.mp_text_input)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appPreferences.isKeyboardIncognito) {
            inputField.imeOptions = inputField.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        }
        if (defaultValue != null) {
            inputField.setText(defaultValue)
            inputField.setSelection(defaultValue.length)
        }
        if (keysWithIntegerInput.contains(key)) {
            inputField.inputType = InputType.TYPE_CLASS_NUMBER
        }
        val dialog: Dialog = AlertDialog.Builder(context)
                .setTitle(title)
                .setView(view)
                .show()
        view.findViewById<View>(R.id.mp_btn_confirm).setOnClickListener {
            listener.onInput(inputField.text.toString())
            dialog.dismiss()
        }
    }
}