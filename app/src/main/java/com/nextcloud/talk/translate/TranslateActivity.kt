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
package com.nextcloud.talk.translate

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import autodagger.AutoInjector
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.ActivityTranslateBinding
import com.nextcloud.talk.models.json.translations.TranslationsOverall
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.database.user.CapabilitiesUtilNew
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.json.JSONArray
import java.util.Locale
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class TranslateActivity : BaseActivity() {
    private lateinit var binding: ActivityTranslateBinding

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var userManager: UserManager

    var fromLanguages = arrayOf<String>()

    var toLanguages = arrayOf<String>()

    var text: String? = null

    var check: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        binding = ActivityTranslateBinding.inflate(layoutInflater)

        setupActionBar()
        setContentView(binding.root)
        setupSystemColors()
        setupTextViews()
        setupSpinners()
        getLanguageOptions()
        translate(null, Locale.getDefault().language)
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
        val original = binding.originalMessageTextview
        val translation = binding.translatedMessageTextview

        viewThemeUtils.talk.themeIncomingMessageBubble(original, grouped = true, deleted = false)
        viewThemeUtils.talk.themeIncomingMessageBubble(translation, grouped = true, deleted = false)

        original.movementMethod = ScrollingMovementMethod()
        translation.movementMethod = ScrollingMovementMethod()

        val bundle = intent.extras
        binding.originalMessageTextview.text = bundle?.getString(BundleKeys.KEY_TRANSLATE_MESSAGE)
        text = bundle?.getString(BundleKeys.KEY_TRANSLATE_MESSAGE)
    }

    private fun getLanguageOptions() {
        val currentUser: User = userManager.currentUser.blockingGet()
        val json = JSONArray(CapabilitiesUtilNew.getLanguages(currentUser).toString())
        Log.i(TAG, "json is: $json")

        val fromLanguagesSet = mutableSetOf(resources.getString(R.string.translation_detect_language))
        val toLanguagesSet = mutableSetOf(resources.getString(R.string.translation_device_settings))

        for (i in 0..json.length() - 1) {
            val current = json.getJSONObject(i)
            if (current.getString(FROM_ID) != Locale.getDefault().language) {
                toLanguagesSet.add(current.getString(FROM_LABEL))
            }

            fromLanguagesSet.add(current.getString(TO_LABEL))
        }

        fromLanguages = fromLanguagesSet.toTypedArray()
        toLanguages = toLanguagesSet.toTypedArray()

        binding.fromLanguageSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            fromLanguages
        )

        binding.toLanguageSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            toLanguages
        )
    }

    private fun enableSpinners(value: Boolean) {
        binding.fromLanguageSpinner.isEnabled = value
        binding.toLanguageSpinner.isEnabled = value
    }

    private fun translate(fromLanguage: String?, toLanguage: String) {
        val currentUser: User = userManager.currentUser.blockingGet()
        val credentials: String = ApiUtils.getCredentials(currentUser.username, currentUser.token)
        val translateURL = currentUser.baseUrl +
            "/ocs/v2.php/translation/translate?text=$text&toLanguage=$toLanguage" +
            if (fromLanguage != null && fromLanguage != "") {
                "&fromLanguage=$fromLanguage"
            } else {
                ""
            }

        Log.i(TAG, "Url is: $translateURL")
        ncApi.translateMessage(credentials, translateURL)
            ?.subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<TranslationsOverall> {
                override fun onSubscribe(d: Disposable) {
                    enableSpinners(false)
                    binding.translatedMessageTextview.visibility = View.GONE
                    binding.progressBar.visibility = View.VISIBLE
                }

                override fun onNext(translationOverall: TranslationsOverall) {
                    binding.progressBar.visibility = View.GONE
                    binding.translatedMessageTextview.visibility = View.VISIBLE
                    binding.translatedMessageTextview.text = translationOverall.ocs?.data?.text
                }

                override fun onError(e: Throwable) {
                    binding.progressBar.visibility = View.GONE
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

                override fun onComplete() {
                    // nothing?
                }
            })

        enableSpinners(true)
    }

    private fun getISOFromLanguage(language: String): String {
        if (resources.getString(R.string.translation_device_settings).equals(language)) {
            return Locale.getDefault().language
        }

        val currentUser: User = userManager.currentUser.blockingGet()
        val json = JSONArray(CapabilitiesUtilNew.getLanguages(currentUser).toString())

        for (i in 0..json.length() - 1) {
            val current = json.getJSONObject(i)
            if (current.getString(FROM_LABEL) == language) {
                return current.getString(FROM_ID)
            }
        }

        return ""
    }

    private fun setupSpinners() {
        binding.fromLanguageSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            fromLanguages
        )
        binding.toLanguageSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            toLanguages
        )

        binding.fromLanguageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                if (++check > 1) {
                    val fromLabel: String = getISOFromLanguage(parent.getItemAtPosition(position).toString())
                    val toLabel: String = getISOFromLanguage(binding.toLanguageSpinner.selectedItem.toString())
                    Log.i(TAG, "fromLanguageSpinner :: $FROM_LABEL = $fromLabel, $TO_LABEL = $ count: $check")
                    translate(fromLabel, toLabel)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // write code to perform some action
            }
        }

        binding.toLanguageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                if (++check > 2) {
                    val toLabel: String = getISOFromLanguage(parent.getItemAtPosition(position).toString())
                    val fromLabel: String = getISOFromLanguage(binding.fromLanguageSpinner.selectedItem.toString())
                    Log.i(TAG, "toLanguageSpinner :: $FROM_LABEL = $fromLabel, $TO_LABEL = $toLabel count: $check")
                    translate(fromLabel, toLabel)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // write code to perform some action
            }
        }
    }

    companion object {
        private val TAG = TranslateActivity::class.simpleName
        private const val FROM_ID = "from"
        private const val FROM_LABEL = "fromLabel"
        private const val TO_LABEL = "toLabel"
    }
}
