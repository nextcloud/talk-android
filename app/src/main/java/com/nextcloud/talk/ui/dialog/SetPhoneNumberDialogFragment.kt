/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Parneet Singh <gurayaparneet@gmail.com>
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.ui.dialog

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import autodagger.AutoInjector
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.databinding.DialogSetPhoneNumberBinding
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class SetPhoneNumberDialogFragment : DialogFragment() {

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    private lateinit var clickListener: SetPhoneNumberDialogClickListener

    private lateinit var binding: DialogSetPhoneNumberBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication?.componentApplication?.inject(this)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogSetPhoneNumberBinding.inflate(requireActivity().layoutInflater)

        val dialogBuilder =
            MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.nc_settings_phone_book_integration_phone_number_dialog_title)
                .setView(binding.root)
                .setMessage(R.string.nc_settings_phone_book_integration_phone_number_dialog_description)
                .setPositiveButton(requireContext().resources.getString(R.string.nc_common_set)) { dialog, _ ->
                    clickListener
                        .onSubmitClick(binding.phoneInputLayout, dialog)
                }
                .setNegativeButton(requireContext().resources.getString(R.string.nc_common_skip), null)

        viewThemeUtils.dialog.colorMaterialAlertDialogBackground(requireActivity(), dialogBuilder)

        binding.phoneInputLayout.setHelperTextColor(
            ColorStateList.valueOf(resources.getColor(R.color.nc_darkRed, null))
        )

        binding.phoneEditTextField.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.phoneInputLayout.helperText = ""
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // unused atm
            }
            override fun afterTextChanged(s: Editable?) {
                // unused atm
            }
        })

        return dialogBuilder.create()
    }

    override fun onStart() {
        super.onStart()
        val alertDialog = dialog as AlertDialog?
        alertDialog?.let {
            viewThemeUtils.platform.colorTextButtons(it.getButton(AlertDialog.BUTTON_POSITIVE))
            viewThemeUtils.platform.colorTextButtons(it.getButton(AlertDialog.BUTTON_NEGATIVE))
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        clickListener = context as SetPhoneNumberDialogClickListener
    }

    interface SetPhoneNumberDialogClickListener {
        fun onSubmitClick(textInputLayout: TextInputLayout, dialog: DialogInterface)
    }

    companion object {
        val TAG: String = SetPhoneNumberDialogFragment::class.java.simpleName

        fun newInstance(): DialogFragment = SetPhoneNumberDialogFragment()
    }
}
