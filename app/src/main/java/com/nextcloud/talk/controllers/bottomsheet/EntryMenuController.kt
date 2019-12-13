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

package com.nextcloud.talk.controllers.bottomsheet

import android.content.ComponentName
import android.content.Intent
import android.graphics.PorterDuff
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.ImageView
import butterknife.BindView
import butterknife.OnClick
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.google.android.material.textfield.TextInputLayout
import com.nextcloud.talk.R
import com.nextcloud.talk.controllers.base.BaseController
import com.nextcloud.talk.events.BottomSheetLockEvent
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.utils.EmojiTextInputEditText
import com.nextcloud.talk.utils.ShareUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.singletons.ApplicationWideMessageHolder
import com.vanniktech.emoji.EmojiPopup
import org.parceler.Parcels

class EntryMenuController(private val originalBundle: Bundle) : BaseController() {

    @JvmField
    @BindView(R.id.ok_button)
    internal var proceedButton: Button? = null

    @JvmField
    @BindView(R.id.text_edit)
    internal var editText: EmojiTextInputEditText? = null

    @JvmField
    @BindView(R.id.text_input_layout)
    internal var textInputLayout: TextInputLayout? = null

    @JvmField
    @BindView(R.id.smileyButton)
    internal var smileyButton: ImageView? = null

    private val operationCode: Int
    private var conversation: Conversation? = null
    private var shareIntent: Intent? = null
    private val packageName: String
    private val name: String
    private val callUrl: String

    private var emojiPopup: EmojiPopup? = null

    init {

        this.operationCode = originalBundle.getInt(BundleKeys.KEY_OPERATION_CODE)
        if (originalBundle.containsKey(BundleKeys.KEY_ROOM)) {
            this.conversation = Parcels.unwrap(originalBundle.getParcelable(BundleKeys.KEY_ROOM))
        }

        if (originalBundle.containsKey(BundleKeys.KEY_SHARE_INTENT)) {
            this.shareIntent = Parcels.unwrap(originalBundle.getParcelable(BundleKeys.KEY_SHARE_INTENT))
        }

        this.name = originalBundle.getString(BundleKeys.KEY_APP_ITEM_NAME, "")
        this.packageName = originalBundle.getString(BundleKeys.KEY_APP_ITEM_PACKAGE_NAME, "")
        this.callUrl = originalBundle.getString(BundleKeys.KEY_CALL_URL, "")
    }

    override fun inflateView(inflater: LayoutInflater, container: ViewGroup): View {
        return inflater.inflate(R.layout.controller_entry_menu, container, false)
    }

    @OnClick(R.id.smileyButton)
    internal fun onSmileyClick() {
        emojiPopup!!.toggle()
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        if (ApplicationWideMessageHolder.getInstance().messageType != null && ApplicationWideMessageHolder.getInstance()
                        .messageType == ApplicationWideMessageHolder.MessageType.CALL_PASSWORD_WRONG) {
            textInputLayout!!.error = resources!!.getString(R.string.nc_wrong_password)
            ApplicationWideMessageHolder.getInstance().messageType = null
            if (proceedButton!!.isEnabled) {
                proceedButton!!.isEnabled = false
                proceedButton!!.alpha = 0.7f
            }
        }
    }

    @OnClick(R.id.ok_button)
    fun onProceedButtonClick() {
        val bundle: Bundle
        if (operationCode == 99) {
            eventBus.post(BottomSheetLockEvent(false, 0, false, false))
            bundle = Bundle()
            bundle.putParcelable(BundleKeys.KEY_ROOM, Parcels.wrap(conversation))
            bundle.putString(BundleKeys.KEY_CALL_URL, callUrl)
            bundle.putString(BundleKeys.KEY_CONVERSATION_PASSWORD,
                    editText!!.text!!.toString())
            bundle.putInt(BundleKeys.KEY_OPERATION_CODE, operationCode)
            if (originalBundle.containsKey(BundleKeys.KEY_SERVER_CAPABILITIES)) {
                bundle.putParcelable(BundleKeys.KEY_SERVER_CAPABILITIES,
                        originalBundle.getParcelable(BundleKeys.KEY_SERVER_CAPABILITIES))
            }

            router.pushController(RouterTransaction.with(OperationsMenuController(bundle))
                    .pushChangeHandler(HorizontalChangeHandler())
                    .popChangeHandler(HorizontalChangeHandler()))
        } else if (operationCode != 7 && operationCode != 10 && operationCode != 11) {
            eventBus.post(BottomSheetLockEvent(false, 0, false, false))
            bundle = Bundle()
            if (operationCode == 4 || operationCode == 6) {
                conversation!!.password = editText!!.text!!.toString()
            } else {
                conversation!!.name = editText!!.text!!.toString()
            }
            bundle.putParcelable(BundleKeys.KEY_ROOM, Parcels.wrap(conversation))
            bundle.putInt(BundleKeys.KEY_OPERATION_CODE, operationCode)
            router.pushController(RouterTransaction.with(OperationsMenuController(bundle))
                    .pushChangeHandler(HorizontalChangeHandler())
                    .popChangeHandler(HorizontalChangeHandler()))
        } else if (operationCode == 7) {
            if (activity != null) {
                shareIntent!!.putExtra(Intent.EXTRA_TEXT, ShareUtils.getStringForIntent(activity,
                        editText!!.text!!.toString(), conversation!!))
                val intent = Intent(shareIntent)
                intent.component = ComponentName(packageName, name)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                activity!!.startActivity(intent)
                eventBus.post(BottomSheetLockEvent(true, 0, false, true))
            }
        } else if (operationCode != 11) {
            eventBus.post(BottomSheetLockEvent(false, 0, false, false))
            bundle = Bundle()
            bundle.putInt(BundleKeys.KEY_OPERATION_CODE, operationCode)
            bundle.putString(BundleKeys.KEY_CALL_URL, editText!!.text!!.toString())
            router.pushController(RouterTransaction.with(OperationsMenuController(bundle))
                    .pushChangeHandler(HorizontalChangeHandler())
                    .popChangeHandler(HorizontalChangeHandler()))
        } else if (operationCode == 11) {
            eventBus.post(BottomSheetLockEvent(false, 0, false, false))
            originalBundle.putString(BundleKeys.KEY_CONVERSATION_NAME,
                    editText!!.text!!.toString())
            router.pushController(
                    RouterTransaction.with(OperationsMenuController(originalBundle))
                            .pushChangeHandler(HorizontalChangeHandler())
                            .popChangeHandler(HorizontalChangeHandler()))
        }
    }

    override fun onViewBound(view: View) {
        super.onViewBound(view)
        if (conversation != null && operationCode == 2) {
            editText!!.setText(conversation!!.name)
        }

        editText!!.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE
                    && proceedButton != null
                    && proceedButton!!.isEnabled) {
                proceedButton!!.callOnClick()
                true
            }
            false
        }

        editText!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {

            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {

            }

            override fun afterTextChanged(s: Editable) {
                if (!TextUtils.isEmpty(s)) {
                    if (operationCode == 2) {
                        if (conversation!!.name == null || conversation!!.name != s.toString()) {
                            if (!proceedButton!!.isEnabled) {
                                proceedButton!!.isEnabled = true
                                proceedButton!!.alpha = 1.0f
                            }
                            textInputLayout!!.isErrorEnabled = false
                        } else {
                            if (proceedButton!!.isEnabled) {
                                proceedButton!!.isEnabled = false
                                proceedButton!!.alpha = 0.38f
                            }
                            textInputLayout!!.error = resources!!.getString(R.string.nc_call_name_is_same)
                        }
                    } else if (operationCode != 10) {
                        if (!proceedButton!!.isEnabled) {
                            proceedButton!!.isEnabled = true
                            proceedButton!!.alpha = 1.0f
                        }
                        textInputLayout!!.isErrorEnabled = false
                    } else if ((editText!!.text!!.toString().startsWith("http://") || editText!!.text!!.toString().startsWith("https://")) && editText!!.text!!.toString().contains("/call/")) {
                        // operation code 10
                        if (!proceedButton!!.isEnabled) {
                            proceedButton!!.isEnabled = true
                            proceedButton!!.alpha = 1.0f
                        }
                        textInputLayout!!.isErrorEnabled = false
                    } else {
                        if (proceedButton!!.isEnabled) {
                            proceedButton!!.isEnabled = false
                            proceedButton!!.alpha = 0.38f
                        }
                        textInputLayout!!.error = resources!!.getString(R.string.nc_wrong_link)
                    }
                } else {
                    if (proceedButton!!.isEnabled) {
                        proceedButton!!.isEnabled = false
                        proceedButton!!.alpha = 0.38f
                    }
                    textInputLayout!!.isErrorEnabled = false
                }
            }
        })

        var labelText = ""
        when (operationCode) {
            11, 2 -> {
                labelText = resources!!.getString(R.string.nc_call_name)
                editText!!.inputType = InputType.TYPE_CLASS_TEXT
                smileyButton!!.visibility = View.VISIBLE
                emojiPopup = EmojiPopup.Builder.fromRootView(view)
                        .setOnEmojiPopupShownListener {
                            if (resources != null) {
                                smileyButton!!.setColorFilter(resources!!.getColor(R.color.colorPrimary),
                                        PorterDuff.Mode.SRC_IN)
                            }
                        }
                        .setOnEmojiPopupDismissListener {
                            if (smileyButton != null) {
                                smileyButton!!.setColorFilter(resources!!.getColor(R.color.emoji_icons),
                                        PorterDuff.Mode.SRC_IN)
                            }
                        }
                        .setOnEmojiClickListener { emoji, imageView -> editText!!.editableText.append(" ") }
                        .build(editText!!)
            }
            4 -> {
                labelText = resources!!.getString(R.string.nc_new_password)
                editText!!.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            6, 7, 99 -> {
                // 99 is joining a conversation via password
                labelText = resources!!.getString(R.string.nc_password)
                editText!!.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            10 -> {
                labelText = resources!!.getString(R.string.nc_conversation_link)
                editText!!.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            }
            else -> {
            }
        }

        textInputLayout!!.isPasswordVisibilityToggleEnabled = operationCode == 99 || operationCode == 4 || operationCode == 6 || operationCode == 7
        textInputLayout!!.hint = labelText
        textInputLayout!!.requestFocus()
    }
}
