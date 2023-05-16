package com.nextcloud.talk.translate
import android.app.AlertDialog
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import autodagger.AutoInjector
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



// TODO include license at top of the file


@AutoInjector(NextcloudTalkApplication::class)
class TranslateActivity : BaseActivity()
{
    private lateinit var binding: ActivityTranslateBinding

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var userManager: UserManager




    var fromLanguages = arrayOf<String>()

    var toLanguages = arrayOf<String>()

    var text : String? = null



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        binding = ActivityTranslateBinding.inflate(layoutInflater)

        setupActionBar()
        setContentView(binding.root)
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
        supportActionBar?.title = "Translation"
        viewThemeUtils.material.themeToolbar(binding.translationToolbar)
    }

    private fun setupTextViews() {
        val original = binding.originalMessageTextview
        val translation = binding.translatedMessageTextview

        original.movementMethod = ScrollingMovementMethod()
        translation.movementMethod = ScrollingMovementMethod()

        val bundle = intent.extras
        binding.originalMessageTextview.text = bundle?.getString(BundleKeys.KEY_TRANSLATE_MESSAGE)
        text = bundle?.getString(BundleKeys.KEY_TRANSLATE_MESSAGE)
    }


    private fun getLanguageOptions() {
        val currentUser : User = userManager.currentUser.blockingGet()
        val json  = JSONArray(CapabilitiesUtilNew.getLanguages(currentUser).toString())
        Log.i("TranslateActivity", "json is: ${json.toString()}")

        var fromLanguagesSet = mutableSetOf<String>("Detect Language")
        var toLanguagesSet = mutableSetOf<String>("Device Settings")

        for( i in 0..json.length()-1) {
            val current = json.getJSONObject(i)
            if(current.getString("from") != Locale.getDefault().language)
            {
                toLanguagesSet.add(current.getString("fromLabel"))
            }

            fromLanguagesSet.add(current.getString("toLabel"))
        }

        fromLanguages = fromLanguagesSet.toTypedArray()
        toLanguages = toLanguagesSet.toTypedArray()

        binding.fromLanguageSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            fromLanguages)

        binding.toLanguageSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            toLanguages)

    }

    private fun enableSpinners(value : Boolean) {
        binding.fromLanguageSpinner.isEnabled = value
        binding.toLanguageSpinner.isEnabled = value
    }

    private fun translate(fromLanguage: String?, toLanguage : String) {
        val currentUser : User = userManager.currentUser.blockingGet()
        val credentials : String = ApiUtils.getCredentials(currentUser.username, currentUser.token)
        val translateURL = currentUser.baseUrl +
            "/ocs/v2.php/translation/translate?text=$text&toLanguage=$toLanguage" +
            if(fromLanguage != "") { "&fromLanguage=$fromLanguage" } else {""}


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
                    val builder = AlertDialog.Builder(this@TranslateActivity)
                    builder.setTitle("Translation Failed")
                    builder.setMessage("Could not detect language")
                    val dialog = builder.create()
                    dialog.show()
                }

                override fun onComplete() {
                    // nothing?
                }
            })

        enableSpinners(true)
    }


    private fun getISOFromLanguage(language: String) : String {
        if(language == "Device Settings") {
            return Locale.getDefault().language
        }


        val currentUser : User = userManager.currentUser.blockingGet()
        val json  = JSONArray(CapabilitiesUtilNew.getLanguages(currentUser).toString())

        for( i in 0..json.length()-1) {
            val current = json.getJSONObject(i)
            if (current.getString("fromLabel") == language) {
                return current.getString("from")
            }
        }

        return ""
    }


    private fun setupSpinners() {
        binding.fromLanguageSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            fromLanguages)
        binding.toLanguageSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            toLanguages)

        // TODO set up onclickers make sure to deal with options becoming unavaliable in the spinner onClicker
        binding.fromLanguageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                var fromLabel : String = getISOFromLanguage(parent.getItemAtPosition(position).toString())
                var toLabel : String = getISOFromLanguage(binding.toLanguageSpinner.selectedItem.toString())
                Log.i("TranslateActivity", "fromLanguageSpinner :: fromLabel = $fromLabel, toLabel = $toLabel")
                translate(fromLabel, toLabel)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // write code to perform some action
            }
        }

        binding.toLanguageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                var toLabel : String = getISOFromLanguage(parent.getItemAtPosition(position).toString())
                var fromLabel : String = getISOFromLanguage(binding.fromLanguageSpinner.selectedItem.toString())
                Log.i("TranslateActivity", "toLanguageSpinner :: fromLabel = $fromLabel, toLabel = $toLabel")
                translate(fromLabel, toLabel)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // write code to perform some action
            }
        }



    }


}