/*
 * Nextcloud Talk application
 *
 * @author Julius Linus
 * @author Andy Scherzinger
 * Copyright (C) 2023 Julius Linus <julius.linus@nextcloud.com>
 * Copyright (C) 2023 Andy Scherzinger <info@andy-scherzinger.de>
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
package com.nextcloud.talk.translate.ui

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import autodagger.AutoInjector
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.databinding.ActivityTranslateBinding
import com.nextcloud.talk.translate.viewmodels.TranslateViewModel
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.database.user.CapabilitiesUtilNew
import org.json.JSONArray
import java.util.Locale
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class TranslateActivity : BaseActivity() {

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    private lateinit var viewModel: TranslateViewModel
    private lateinit var binding: ActivityTranslateBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        binding = ActivityTranslateBinding.inflate(layoutInflater)
        viewModel  = ViewModelProvider(this, viewModelFactory)[TranslateViewModel::class.java] // fixme bug here


        setupActionBar()
        setContentView(binding.root)
        setupSystemColors()
        setupTextViews()
        getLanguageOptions()
        setupSpinners()
        setupCopyButton()

        val translateViewModelObserver = Observer<TranslateViewModel.TranslateUiState> { state ->
            enableSpinners(state.enableSpinners)

            binding.progressBar.visibility = if(state.showProgressBar) View.VISIBLE else View.GONE

            binding.translatedMessageContainer.visibility =
                if(state.showTranslatedMessageContainer) View.VISIBLE else View.GONE

            if(state.translatedMessageText != null)
                binding.translatedMessageTextview.text = state.translatedMessageText

            if(state.errorOccurred)
                showDialog()
        }

        viewModel.viewState.observe(this, translateViewModelObserver)

        if (savedInstanceState == null) {
            viewModel.translateMessage(Locale.getDefault().language, null)
        } else {
            binding.translatedMessageTextview.text = savedInstanceState.getString(BundleKeys.SAVED_TRANSLATED_MESSAGE)
        }
    }

    override fun onResume() {
        super.onResume()
        setItems()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.run {
            putString(BundleKeys.SAVED_TRANSLATED_MESSAGE, binding.translatedMessageTextview.text.toString())
        }
        super.onSaveInstanceState(outState)
    }

    private fun setupCopyButton() {
        viewThemeUtils.material.colorMaterialButtonPrimaryFilled(binding.copyTranslatedMessage)
        binding.copyTranslatedMessage.setOnClickListener {
            val clipboardManager =
                getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = ClipData.newPlainText(
                resources?.getString(R.string.nc_app_product_name),
                binding.translatedMessageTextview.text?.toString()
            )
            clipboardManager.setPrimaryClip(clipData)
        }
    }

    private fun setupActionBar() {
        setSupportActionBar(binding.translationToolbar)
        binding.translationToolbar.setNavigationOnClickListener {
            onBackPressed()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setIcon(ColorDrawable(resources!!.getColor(R.color.transparent)))
        supportActionBar?.title = resources!!.getString(R.string.translation)
        viewThemeUtils.material.themeToolbar(binding.translationToolbar)
    }

    private fun setupTextViews() {
        viewThemeUtils.talk.themeIncomingMessageBubble(
            binding.originalMessageTextview,
            grouped = true,
            deleted = false
        )
        viewThemeUtils.talk.themeIncomingMessageBubble(
            binding.translatedMessageTextview,
            grouped = true,
            deleted = false
        )

        binding.originalMessageTextview.movementMethod = ScrollingMovementMethod()
        binding.translatedMessageTextview.movementMethod = ScrollingMovementMethod()
        val bundle = intent.extras
        val text = bundle?.getString(BundleKeys.KEY_TRANSLATE_MESSAGE)
        binding.originalMessageTextview.text = text
        viewModel.viewState.value!!.originalMessageText = text
    }

    private fun getLanguageOptions() {
        val currentUser = userManager.currentUser.blockingGet()
        val json = JSONArray(CapabilitiesUtilNew.getLanguages(currentUser).toString())

        val fromLanguagesSet = mutableSetOf(resources.getString(R.string.translation_detect_language))
        val toLanguagesSet = mutableSetOf(resources.getString(R.string.translation_device_settings))

        for (i in 0 until json.length()) {
            val current = json.getJSONObject(i)
            if (current.getString(FROM_ID) != Locale.getDefault().language) {
                toLanguagesSet.add(current.getString(FROM_LABEL))
            }

            fromLanguagesSet.add(current.getString(TO_LABEL))
        }

        viewModel.viewState.value!!.toLanguages = toLanguagesSet.toTypedArray()
        viewModel.viewState.value!!.fromLanguages = fromLanguagesSet.toTypedArray()
    }

    private fun enableSpinners(value: Boolean) {
        binding.fromLanguageInputLayout.isEnabled = value
        binding.toLanguageInputLayout.isEnabled = value
    }

    private fun showDialog() {
        val dialogBuilder = MaterialAlertDialogBuilder(this@TranslateActivity)
            .setIcon(
                viewThemeUtils.dialog.colorMaterialAlertDialogIcon(
                    context,
                    R.drawable.ic_warning_white
                )
            )
            .setTitle(R.string.translation_error_title)
            .setMessage(R.string.translation_error_message)
            .setPositiveButton(R.string.nc_ok) { dialog, _ ->
                dialog.dismiss()
            }

        viewThemeUtils.dialog.colorMaterialAlertDialogBackground(context, dialogBuilder)

        val dialog = dialogBuilder.show()

        viewThemeUtils.platform.colorTextButtons(
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        )
    }



    private fun getISOFromLanguage(language: String): String {
        if (resources.getString(R.string.translation_device_settings) == language) {
            return Locale.getDefault().language
        }

        return getISOFromServer(language)
    }

    private fun getISOFromServer(language: String): String {
        val currentUser = userManager.currentUser.blockingGet()
        val json = JSONArray(CapabilitiesUtilNew.getLanguages(currentUser).toString())

        for (i in 0 until json.length()) {
            val current = json.getJSONObject(i)
            if (current.getString(FROM_LABEL) == language) {
                return current.getString(FROM_ID)
            }
        }

        return ""
    }

    private fun setupSpinners() {
        viewThemeUtils.material.colorTextInputLayout(binding.fromLanguageInputLayout)
        viewThemeUtils.material.colorTextInputLayout(binding.toLanguageInputLayout)
        fillSpinners()

        binding.fromLanguage.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
            val fromLabel: String = getISOFromLanguage(parent.getItemAtPosition(position).toString())
            val toLabel: String = getISOFromLanguage(binding.toLanguage.text.toString())
            viewModel.translateMessage(toLabel, fromLabel)
        }

        binding.toLanguage.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
            val toLabel: String = getISOFromLanguage(parent.getItemAtPosition(position).toString())
            val fromLabel: String = getISOFromLanguage(binding.fromLanguage.text.toString())
            viewModel.translateMessage(toLabel, fromLabel)
        }
    }

    private fun fillSpinners() {
        val fromLanguages = viewModel.viewState.value!!.fromLanguages!!
        val toLanguages = viewModel.viewState.value!!.toLanguages!!

        binding.fromLanguage.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, fromLanguages)
        )
        if (fromLanguages.isNotEmpty()) {
            binding.fromLanguage.setText(fromLanguages[0])
        }

        binding.toLanguage.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, toLanguages)
        )
        if (toLanguages.isNotEmpty()) {
            binding.toLanguage.setText(toLanguages[0])
        }
    }

    private fun setItems() {
        binding.fromLanguage.setSimpleItems(viewModel.viewState.value!!.fromLanguages!!)
        binding.toLanguage.setSimpleItems(viewModel.viewState.value!!.toLanguages!!)
    }

    companion object {
        private const val FROM_ID = "from"
        private const val FROM_LABEL = "fromLabel"
        private const val TO_LABEL = "toLabel"
    }

}
