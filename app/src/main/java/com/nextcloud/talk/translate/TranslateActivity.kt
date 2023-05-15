package com.nextcloud.talk.translate
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import autodagger.AutoInjector
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.databinding.ActivityTranslateBinding
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.bundle.BundleKeys
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

    var text : String? = null

    var fromLanguage : String = "en"

    val toLanguage : String = "de"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTranslateBinding.inflate(layoutInflater)

        setupTextViews()
        setupActionBar()
        setupSpinners()
        setContentView(binding.root)
        translate()
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
        // this uses another call to their /languages endpoint which requires another separate function in ncAPI and
        // seprarate models for the JSON :|
    }

    // TODO get this function working
    private fun translate() {
        // var currentUser = userManager.currentUser.blockingGet()
        // Log.d("TranslateActivity Current User", currentUser.toString())
        // val credentials = ApiUtils.getCredentials(currentUser.username, currentUser.token)
        // val translateURL = currentUser.baseUrl + "/translation" + "/translate"



        // ncApi.translateMessage(credentials, translateURL, text, fromLanguage, toLanguage)
        //     ?.subscribeOn(Schedulers.io())
        //     ?.observeOn(AndroidSchedulers.mainThread())
        //     ?.subscribe(object : Observer<TranslationsOverall> {
        //         override fun onSubscribe(d: Disposable) {
        //             // TODO set progress bar to show
        //             binding.translatedMessageTextview.visibility = View.GONE
        //             binding.progressBar.visibility = View.VISIBLE
        //         }
        //
        //         override fun onNext(translationOverall: TranslationsOverall) {
        //             // TODO hide progress bar
        //             binding.progressBar.visibility = View.GONE
        //             binding.translatedMessageTextview.visibility = View.VISIBLE
        //             binding.translatedMessageTextview.text = translationOverall.ocs?.data?.text
        //         }
        //
        //         override fun onError(e: Throwable) {
        //             Log.e("TranslateActivity", "Error")
        //         }
        //
        //         override fun onComplete() {
        //             // not needed?
        //         }
        //     })

    }

    private fun setupSpinners() {
        // TODO set spinner options to use array from getLanguageOptions()

        // TODO set onClickListener to call server using translate()
        // binding.toLanguageSpinner.setOnClickListener(View.OnClickListener {
        //     // translate()
        // })
        //
        // binding.fromLanguageSpinner.setOnClickListener(View.OnClickListener {
        //     // translate()
        // })
    }


}