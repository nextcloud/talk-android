/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Marcel Hibbe
 * @author Andy Scherzinger
 * Copyright (C) 2017 Mario Danic <mario@lovelyhq.com>
 * Copyright (C) 2022 Marcel Hibbe <dev@mhibbe.de>
 * Copyright (C) 2022 Andy Scherzinger <info@andy-scherzinger.de>
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
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Parcelable
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.content.res.ResourcesCompat
import autodagger.AutoInjector
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.google.android.material.textfield.TextInputLayout
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.controllers.base.NewBaseController
import com.nextcloud.talk.controllers.util.viewBinding
import com.nextcloud.talk.databinding.ControllerEntryMenuBinding
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.ui.theme.ServerTheme
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ShareUtils
import com.nextcloud.talk.utils.UriUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.singletons.ApplicationWideMessageHolder
import com.vanniktech.emoji.EmojiPopup
import okhttp3.internal.immutableListOf
import org.greenrobot.eventbus.EventBus
import org.parceler.Parcels
import org.parceler.Parcels.unwrap
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class EntryMenuController(args: Bundle) :
    NewBaseController(
        R.layout.controller_entry_menu,
        args
    ) {
    private val binding: ControllerEntryMenuBinding by viewBinding(ControllerEntryMenuBinding::bind)

    @Inject
    lateinit var eventBus: EventBus

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    @Inject
    lateinit var serverTheme: ServerTheme

    private val operation: ConversationOperationEnum
    private var conversation: Conversation? = null
    private var shareIntent: Intent? = null
    private val packageName: String
    private val name: String
    private val callUrl: String
    private var emojiPopup: EmojiPopup? = null
    private val originalBundle: Bundle

    override val appBarLayoutType: AppBarLayoutType
        get() = AppBarLayoutType.SEARCH_BAR

    override fun onAttach(view: View) {
        super.onAttach(view)
        if (ApplicationWideMessageHolder.MessageType.CALL_PASSWORD_WRONG ==
            ApplicationWideMessageHolder.getInstance().messageType
        ) {
            binding.textInputLayout.error = resources?.getString(R.string.nc_wrong_password)
            ApplicationWideMessageHolder.getInstance().setMessageType(null)
            if (binding.okButton.isEnabled) {
                binding.okButton.isEnabled = false
                binding.okButton.alpha = OPACITY_BUTTON_DISABLED
            }
        }
    }

    override fun onViewBound(view: View) {
        super.onViewBound(view)

        if (conversation != null && operation === ConversationOperationEnum.OPS_CODE_RENAME_ROOM) {
            binding.textEdit.setText(conversation!!.name)
        }

        binding.textEdit.setOnEditorActionListener { v, actionId, event ->
            @Suppress("IMPLICIT_BOXING_IN_IDENTITY_EQUALS")
            if (actionId === EditorInfo.IME_ACTION_DONE && binding.okButton.isEnabled) {
                binding.okButton.callOnClick()
                return@setOnEditorActionListener true
            }
            false
        }

        textEditAddChangedListener()

        var labelText = ""
        when (operation) {
            ConversationOperationEnum.OPS_CODE_INVITE_USERS, ConversationOperationEnum.OPS_CODE_RENAME_ROOM -> {
                labelText = resources!!.getString(R.string.nc_call_name)
                binding.textEdit.inputType = InputType.TYPE_CLASS_TEXT
                binding.smileyButton.visibility = View.VISIBLE
                emojiPopup = EmojiPopup(
                    rootView = view,
                    editText = binding.textEdit,
                    onEmojiPopupShownListener = {
                        viewThemeUtils.colorImageView(binding.smileyButton)
                    },
                    onEmojiPopupDismissListener = {
                        binding.smileyButton.imageTintList = ColorStateList.valueOf(
                            ResourcesCompat.getColor(resources!!, R.color.medium_emphasis_text, context.theme)
                        )
                    },
                    onEmojiClickListener = {
                        binding.textEdit.editableText.append(" ")
                    }
                )
            }

            ConversationOperationEnum.OPS_CODE_CHANGE_PASSWORD -> {
                labelText = resources!!.getString(R.string.nc_new_password)
                binding.textEdit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }

            ConversationOperationEnum.OPS_CODE_SET_PASSWORD,
            ConversationOperationEnum.OPS_CODE_SHARE_LINK,
            ConversationOperationEnum.OPS_CODE_JOIN_ROOM -> {
                // 99 is joining a conversation via password
                labelText = resources!!.getString(R.string.nc_password)
                binding.textEdit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }

            ConversationOperationEnum.OPS_CODE_GET_AND_JOIN_ROOM -> {
                labelText = resources!!.getString(R.string.nc_conversation_link)
                binding.textEdit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            }

            else -> {
            }
        }
        if (PASSWORD_ENTRY_OPERATIONS.contains(operation)) {
            binding.textInputLayout.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        } else {
            binding.textInputLayout.endIconMode = TextInputLayout.END_ICON_NONE
        }

        viewThemeUtils.colorTextInputLayout(binding.textInputLayout)
        viewThemeUtils.colorMaterialButtonText(binding.okButton)

        binding.textInputLayout.hint = labelText
        binding.textInputLayout.requestFocus()

        binding.smileyButton.setOnClickListener { onSmileyClick() }
        binding.okButton.setOnClickListener { onOkButtonClick() }
    }

    private fun textEditAddChangedListener() {
        binding.textEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                // unused atm
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                // unused atm
            }

            override fun afterTextChanged(s: Editable) {
                if (!TextUtils.isEmpty(s)) {
                    if (operation === ConversationOperationEnum.OPS_CODE_RENAME_ROOM) {
                        if (conversation!!.name == null || !conversation!!.name.equals(s.toString())) {
                            if (!binding.okButton.isEnabled) {
                                binding.okButton.isEnabled = true
                                binding.okButton.alpha = OPACITY_ENABLED
                            }
                            binding.textInputLayout.isErrorEnabled = false
                        } else {
                            if (binding.okButton.isEnabled) {
                                binding.okButton.isEnabled = false
                                binding.okButton.alpha = OPACITY_DISABLED
                            }
                            binding.textInputLayout.error = resources?.getString(R.string.nc_call_name_is_same)
                        }
                    } else if (operation !== ConversationOperationEnum.OPS_CODE_GET_AND_JOIN_ROOM) {
                        if (!binding.okButton.isEnabled) {
                            binding.okButton.isEnabled = true
                            binding.okButton.alpha = OPACITY_ENABLED
                        }
                        binding.textInputLayout.isErrorEnabled = false
                    } else if (
                        UriUtils.hasHttpProtocollPrefixed(binding.textEdit.text.toString()) &&
                        binding.textEdit.text.toString().contains("/call/")
                    ) {
                        if (!binding.okButton.isEnabled) {
                            binding.okButton.isEnabled = true
                            binding.okButton.alpha = OPACITY_ENABLED
                        }
                        binding.textInputLayout.isErrorEnabled = false
                    } else {
                        if (binding.okButton.isEnabled) {
                            binding.okButton.isEnabled = false
                            binding.okButton.alpha = OPACITY_DISABLED
                        }
                        binding.textInputLayout.error = resources?.getString(R.string.nc_wrong_link)
                    }
                } else {
                    if (binding.okButton.isEnabled) {
                        binding.okButton.isEnabled = false
                        binding.okButton.alpha = OPACITY_DISABLED
                    }
                    binding.textInputLayout.isErrorEnabled = false
                }
            }
        })
    }

    private fun onSmileyClick() {
        emojiPopup?.toggle()
    }

    private fun onOkButtonClick() {
        if (operation === ConversationOperationEnum.OPS_CODE_JOIN_ROOM) {
            joinRoom()
        } else if (operation !== ConversationOperationEnum.OPS_CODE_SHARE_LINK &&
            operation !== ConversationOperationEnum.OPS_CODE_GET_AND_JOIN_ROOM &&
            operation !== ConversationOperationEnum.OPS_CODE_INVITE_USERS
        ) {
            val bundle = Bundle()
            if (operation === ConversationOperationEnum.OPS_CODE_CHANGE_PASSWORD ||
                operation === ConversationOperationEnum.OPS_CODE_SET_PASSWORD
            ) {
                conversation!!.password = binding.textEdit.text.toString()
            } else {
                conversation!!.name = binding.textEdit.text.toString()
            }
            bundle.putParcelable(BundleKeys.KEY_ROOM, Parcels.wrap<Any>(conversation))
            bundle.putSerializable(BundleKeys.KEY_OPERATION_CODE, operation)
            router.pushController(
                RouterTransaction.with(OperationsMenuController(bundle))
                    .pushChangeHandler(HorizontalChangeHandler())
                    .popChangeHandler(HorizontalChangeHandler())
            )
        } else if (operation === ConversationOperationEnum.OPS_CODE_SHARE_LINK && activity != null) {
            shareIntent?.putExtra(
                Intent.EXTRA_TEXT,
                ShareUtils.getStringForIntent(
                    activity,
                    binding.textEdit.text.toString(),
                    userManager,
                    conversation
                )
            )
            val intent = Intent(shareIntent)
            intent.component = ComponentName(packageName, name)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            activity?.startActivity(intent)
        } else if (operation !== ConversationOperationEnum.OPS_CODE_INVITE_USERS) {
            val bundle = Bundle()
            bundle.putSerializable(BundleKeys.KEY_OPERATION_CODE, operation)
            bundle.putString(BundleKeys.KEY_CALL_URL, binding.textEdit.text.toString())
            router.pushController(
                RouterTransaction.with(OperationsMenuController(bundle))
                    .pushChangeHandler(HorizontalChangeHandler())
                    .popChangeHandler(HorizontalChangeHandler())
            )
        } else if (operation === ConversationOperationEnum.OPS_CODE_INVITE_USERS) {
            originalBundle.putString(BundleKeys.KEY_CONVERSATION_NAME, binding.textEdit.text.toString())
            router.pushController(
                RouterTransaction.with(
                    OperationsMenuController(
                        originalBundle
                    )
                )
                    .pushChangeHandler(HorizontalChangeHandler())
                    .popChangeHandler(HorizontalChangeHandler())
            )
        }
    }

    private fun joinRoom() {
        val bundle = Bundle()
        bundle.putParcelable(BundleKeys.KEY_ROOM, Parcels.wrap<Any>(conversation))
        bundle.putString(BundleKeys.KEY_CALL_URL, callUrl)
        bundle.putString(BundleKeys.KEY_CONVERSATION_PASSWORD, binding.textEdit.text.toString())
        bundle.putSerializable(BundleKeys.KEY_OPERATION_CODE, operation)
        if (originalBundle.containsKey(BundleKeys.KEY_SERVER_CAPABILITIES)) {
            bundle.putParcelable(
                BundleKeys.KEY_SERVER_CAPABILITIES,
                originalBundle.getParcelable<Parcelable>(BundleKeys.KEY_SERVER_CAPABILITIES)
            )
        }
        router.pushController(
            RouterTransaction.with(OperationsMenuController(bundle))
                .pushChangeHandler(HorizontalChangeHandler())
                .popChangeHandler(HorizontalChangeHandler())
        )
    }

    init {
        sharedApplication!!.componentApplication.inject(this)

        originalBundle = args
        operation = args.getSerializable(BundleKeys.KEY_OPERATION_CODE) as ConversationOperationEnum
        if (args.containsKey(BundleKeys.KEY_ROOM)) {
            conversation = unwrap<Conversation>(args.getParcelable<Parcelable>(BundleKeys.KEY_ROOM))
        }
        if (args.containsKey(BundleKeys.KEY_SHARE_INTENT)) {
            shareIntent = unwrap<Intent>(args.getParcelable<Parcelable>(BundleKeys.KEY_SHARE_INTENT))
        }
        name = args.getString(BundleKeys.KEY_APP_ITEM_NAME, "")
        packageName = args.getString(BundleKeys.KEY_APP_ITEM_PACKAGE_NAME, "")
        callUrl = args.getString(BundleKeys.KEY_CALL_URL, "")
    }

    companion object {
        private val PASSWORD_ENTRY_OPERATIONS: List<ConversationOperationEnum> =
            immutableListOf(
                ConversationOperationEnum.OPS_CODE_JOIN_ROOM,
                ConversationOperationEnum.OPS_CODE_CHANGE_PASSWORD,
                ConversationOperationEnum.OPS_CODE_SET_PASSWORD,
                ConversationOperationEnum.OPS_CODE_SHARE_LINK
            )
        const val OPACITY_DISABLED = 0.38f
        const val OPACITY_BUTTON_DISABLED = 0.7f
        const val OPACITY_ENABLED = 1.0f
    }
}
