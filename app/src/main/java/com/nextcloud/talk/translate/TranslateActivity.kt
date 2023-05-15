package com.nextcloud.talk.translate
import android.app.AlertDialog
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.View
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
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
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

    var fromLanguages = arrayOf("Detect Language")

    var toLanguages = arrayOf("Device Default")

    var text : String? = null

    var fromLanguage : String = "de"

    val toLanguage : String = Locale.getDefault().language

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        binding = ActivityTranslateBinding.inflate(layoutInflater)

        setupActionBar()
        setContentView(binding.root)
        setupTextViews()
        setupSpinners()
        translate()
        getLanguageOptions()
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
        // TODO implement this function to retrieve an array of strings from the server for each language option
        // weird, for some reason I'm not getting a body from the server, but I'm getting a 200 OK, that's dumb


    }


    private fun translate() {
        val currentUser : User = userManager.currentUser.blockingGet()
        // Log.d("TranslateActivity Current User", currentUser.toString())
        val credentials : String = ApiUtils.getCredentials(currentUser.username, currentUser.token)
        val translateURL = currentUser.baseUrl + "/ocs/v2.php//translation/translate?text=" + text+ "&toLanguage=" + toLanguage


        ncApi.translateMessage(credentials, translateURL)
            ?.subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<TranslationsOverall> {
                override fun onSubscribe(d: Disposable) {
                    binding.translatedMessageTextview.visibility = View.GONE
                    binding.progressBar.visibility = View.VISIBLE
                }

                override fun onNext(translationOverall: TranslationsOverall) {
                    binding.progressBar.visibility = View.GONE
                    binding.translatedMessageTextview.visibility = View.VISIBLE
                    // binding.translatedMessageTextview.text = "Worked"
                    binding.translatedMessageTextview.text = translationOverall.ocs?.data?.text

                }

                override fun onError(e: Throwable) {
                    binding.progressBar.visibility = View.GONE
                    //
                    val builder = AlertDialog.Builder(this@TranslateActivity)
                    builder.setTitle("Translation Failed")
                    builder.setMessage("Could not detect language")
                    val dialog = builder.create()
                    dialog.show()
                }

                override fun onComplete() {
                    // not needed?
                }
            })
    }

    private fun setupSpinners() {

        //TODO create a way to show that items are disabled/enabled
        binding.fromLanguageSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            fromLanguages)
        // binding.fromLanguageSpinner.isEnabled = false




        binding.toLanguageSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            toLanguages)
        // binding.toLanguageSpinner.isEnabled = false
    }


}