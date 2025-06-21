/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Julius Linus <julius.linus@nextcloud.com>
 * SPDX-FileCopyrightText: 2023 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.translate.ui

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.ViewModelProvider
import autodagger.AutoInjector
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.databinding.ActivityTranslateBinding
import com.nextcloud.talk.translate.repositories.model.Language
import com.nextcloud.talk.translate.viewmodels.TranslateViewModel
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.bundle.BundleKeys
import java.util.Locale
import javax.inject.Inject

@Suppress("TooManyFunctions")
@AutoInjector(NextcloudTalkApplication::class)
class TranslateActivity : BaseActivity() {

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    lateinit var viewModel: TranslateViewModel
    lateinit var binding: ActivityTranslateBinding

    private var toLanguages: Array<String>? = null
    private var fromLanguages: Array<String>? = null
    private var languages: List<Language>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        binding = ActivityTranslateBinding.inflate(layoutInflater)
        viewModel = ViewModelProvider(this, viewModelFactory)[TranslateViewModel::class.java]

        viewModel.viewState.observe(this) { state ->
            when (state) {
                is TranslateViewModel.StartState -> {
                    onStartState()
                }

                is TranslateViewModel.TranslatedState -> {
                    onTranslatedState(state.msg)
                }

                is TranslateViewModel.TranslationErrorState -> {
                    onTranslationErrorState()
                }

                is TranslateViewModel.LanguagesErrorState -> {
                    onLanguagesErrorState()
                }

                is TranslateViewModel.LanguagesRetrievedState -> {
                    Log.d(TAG, "Languages are: ${state.list}")
                    languages = state.list
                    getLanguageOptions()
                    setupSpinners()
                    setItems()
                }
            }
        }
        setupActionBar()
        setContentView(binding.root)
        initSystemBars()
        setupTextViews()
        viewModel.getLanguages()
        setupCopyButton()

        if (savedInstanceState == null) {
            val text = intent.extras!!.getString(BundleKeys.KEY_TRANSLATE_MESSAGE)
            viewModel.translateMessage(Locale.getDefault().language, null, text!!)
        } else {
            binding.translatedMessageTextview.text = savedInstanceState.getString(BundleKeys.SAVED_TRANSLATED_MESSAGE)
        }
    }

    override fun onResume() {
        super.onResume()
        languages?.let { setItems() }
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
            onBackPressedDispatcher.onBackPressed()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setIcon(resources!!.getColor(R.color.transparent, null).toDrawable())
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
        val text = intent.extras!!.getString(BundleKeys.KEY_TRANSLATE_MESSAGE)
        binding.originalMessageTextview.text = text
    }

    private fun getLanguageOptions() {
        val fromLanguagesSet = mutableSetOf(resources.getString(R.string.translation_detect_language))
        val toLanguagesSet = mutableSetOf(resources.getString(R.string.translation_device_settings))

        for (language in languages!!) {
            val locale = Locale.getDefault().language
            if (language.from != locale) {
                toLanguagesSet.add(language.fromLabel!!)
            }

            fromLanguagesSet.add(language.toLabel!!)
        }

        toLanguages = toLanguagesSet.toTypedArray()
        fromLanguages = fromLanguagesSet.toTypedArray()
    }

    private fun enableSpinners(value: Boolean) {
        binding.fromLanguageInputLayout.isEnabled = value
        binding.toLanguageInputLayout.isEnabled = value
    }

    private fun showDialog(titleInt: Int, messageInt: Int) {
        val dialogBuilder = MaterialAlertDialogBuilder(this@TranslateActivity)
            .setIcon(
                viewThemeUtils.dialog.colorMaterialAlertDialogIcon(
                    context,
                    R.drawable.ic_warning_white
                )
            )
            .setTitle(titleInt)
            .setMessage(messageInt)
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

    private fun getISOFromServer(label: String): String {
        var result = ""
        for (language in languages!!) {
            if (language.fromLabel == label) {
                result = language.from!!
            }
        }

        return result
    }

    private fun setupSpinners() {
        viewThemeUtils.material.colorTextInputLayout(binding.fromLanguageInputLayout)
        viewThemeUtils.material.colorTextInputLayout(binding.toLanguageInputLayout)
        fillSpinners()
        val text = intent.extras!!.getString(BundleKeys.KEY_TRANSLATE_MESSAGE)

        binding.fromLanguage.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
            val fromLabel: String = getISOFromLanguage(parent.getItemAtPosition(position).toString())
            val toLabel: String = getISOFromLanguage(binding.toLanguage.text.toString())
            viewModel.translateMessage(toLabel, fromLabel, text!!)
        }

        binding.toLanguage.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
            val toLabel: String = getISOFromLanguage(parent.getItemAtPosition(position).toString())
            val fromLabel: String = getISOFromLanguage(binding.fromLanguage.text.toString())
            viewModel.translateMessage(toLabel, fromLabel, text!!)
        }
    }

    private fun fillSpinners() {
        binding.fromLanguage.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, fromLanguages!!)
        )
        if (fromLanguages!!.isNotEmpty()) {
            binding.fromLanguage.setText(fromLanguages!![0])
        }

        binding.toLanguage.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, toLanguages!!)
        )
        if (toLanguages!!.isNotEmpty()) {
            binding.toLanguage.setText(toLanguages!![0])
        }
    }

    private fun setItems() {
        binding.fromLanguage.setSimpleItems(fromLanguages!!)
        binding.toLanguage.setSimpleItems(toLanguages!!)
    }

    private fun onStartState() {
        enableSpinners(false)
        binding.translatedMessageContainer.visibility = View.GONE
        binding.progressBar.visibility = View.VISIBLE
        binding.copyTranslatedMessage.visibility = View.GONE
    }

    private fun onTranslatedState(msg: String) {
        binding.progressBar.visibility = View.GONE
        binding.translatedMessageContainer.visibility = View.VISIBLE
        binding.translatedMessageTextview.text = msg
        binding.copyTranslatedMessage.visibility = View.VISIBLE
        enableSpinners(true)
    }

    private fun onTranslationErrorState() {
        binding.progressBar.visibility = View.GONE
        enableSpinners(true)
        showDialog(R.string.translation_error_title, R.string.translation_error_message)
    }

    private fun onLanguagesErrorState() {
        binding.progressBar.visibility = View.GONE
        enableSpinners(true)
        showDialog(R.string.languages_error_title, R.string.languages_error_message)
    }

    companion object {
        val TAG = TranslateActivity::class.simpleName
    }
}
