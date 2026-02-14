/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2021-2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.location

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import autodagger.AutoInjector
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.components.ColoredStatusBar
import com.nextcloud.talk.location.components.GeocodingScreen
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.viewmodels.GeoCodingViewModel
import fr.dudie.nominatim.client.TalkJsonNominatimClient
import fr.dudie.nominatim.model.Address
import okhttp3.OkHttpClient
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class GeocodingActivity : BaseActivity() {

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var okHttpClient: OkHttpClient

    private lateinit var roomToken: String
    private var chatApiVersion: Int = 1
    private lateinit var viewModel: GeoCodingViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        org.osmdroid.config.Configuration.getInstance()
            .load(context, PreferenceManager.getDefaultSharedPreferences(context))

        roomToken = intent.getStringExtra(BundleKeys.KEY_ROOM_TOKEN)!!
        chatApiVersion = intent.getIntExtra(BundleKeys.KEY_CHAT_API_VERSION, 1)

        viewModel = ViewModelProvider(this)[GeoCodingViewModel::class.java]

        var query = viewModel.getQuery()
        if (query.isEmpty() && intent.hasExtra(BundleKeys.KEY_GEOCODING_QUERY)) {
            query = intent.getStringExtra(BundleKeys.KEY_GEOCODING_QUERY).orEmpty()
            viewModel.setQuery(query)
        }

        val baseUrl = getString(R.string.osm_geocoder_url)
        val email = context.getString(R.string.osm_geocoder_contact)
        TalkJsonNominatimClient(baseUrl, okHttpClient, email)

        setContent {
            val colorScheme = viewThemeUtils.getColorScheme(this)
            MaterialTheme(colorScheme = colorScheme) {
                ColoredStatusBar()
                GeocodingScreen(
                    viewModel = viewModel,
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    onAddressSelected = { address -> navigateToLocationPicker(address) }
                )
            }
        }
    }

    private fun navigateToLocationPicker(address: Address) {
        val geocodingResult = GeocodingResult(address.latitude, address.longitude, address.displayName)
        val intent = Intent(this, LocationPickerActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.putExtra(BundleKeys.KEY_ROOM_TOKEN, roomToken)
        intent.putExtra(BundleKeys.KEY_CHAT_API_VERSION, chatApiVersion)
        intent.putExtra(BundleKeys.KEY_GEOCODING_RESULT, geocodingResult)
        startActivity(intent)
        finish()
    }

    companion object {
        val TAG: String = GeocodingActivity::class.java.simpleName
    }
}
