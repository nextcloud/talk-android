/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2021-2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.location

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import autodagger.AutoInjector
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.components.ColoredStatusBar
import com.nextcloud.talk.extensions.getParcelableExtraProvider
import com.nextcloud.talk.location.components.LocationPickerScreen
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_CHAT_API_VERSION
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_GEOCODING_RESULT
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import com.nextcloud.talk.viewmodels.LocationPickerViewModel
import org.osmdroid.config.Configuration.getInstance
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class LocationPickerActivity : BaseActivity() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    private lateinit var viewModel: LocationPickerViewModel
    private lateinit var roomToken: String
    private var chatApiVersion: Int = 1

    private val geocodingLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val geocodingResult: GeocodingResult? = result.data?.getParcelableExtraProvider(KEY_GEOCODING_RESULT)
            if (geocodingResult != null) {
                viewModel.onGeocodingResultReceived(geocodingResult)
            }
        }
    }

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        roomToken = intent.getStringExtra(KEY_ROOM_TOKEN)!!
        chatApiVersion = intent.getIntExtra(KEY_CHAT_API_VERSION, 1)

        viewModel = ViewModelProvider(this, viewModelFactory)[LocationPickerViewModel::class.java]

        val geocodingResult: GeocodingResult? = if (savedInstanceState != null) {
            savedInstanceState.getParcelable("geocodingResult")
        } else {
            intent.getParcelableExtraProvider(KEY_GEOCODING_RESULT)
        }

        val moveToCurrentLocation = savedInstanceState?.getBoolean("moveToCurrentLocation") ?: true
        val mapCenterLat = savedInstanceState?.getDouble("mapCenterLat") ?: 0.0
        val mapCenterLon = savedInstanceState?.getDouble("mapCenterLon") ?: 0.0

        viewModel.initState(geocodingResult, moveToCurrentLocation, mapCenterLat, mapCenterLon)

        val baseUrl = getString(R.string.osm_geocoder_url)
        val email = getString(R.string.osm_geocoder_contact)
        viewModel.initGeocoder(baseUrl, email)

        getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        val colorScheme = viewThemeUtils.getColorScheme(this)
        setContent {
            MaterialTheme(colorScheme = colorScheme) {
                ColoredStatusBar()
                LocationPickerScreen(
                    viewModel = viewModel,
                    roomToken = roomToken,
                    chatApiVersion = chatApiVersion,
                    onSearchClick = { navigateToGeocoding() },
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    onFinish = { finish() }
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val state = viewModel.uiState.value
        outState.putBoolean("moveToCurrentLocation", state.moveToCurrentLocation)
        outState.putDouble("mapCenterLat", state.mapCenterLat)
        outState.putDouble("mapCenterLon", state.mapCenterLon)
        outState.putParcelable("geocodingResult", state.geocodingResult)
    }

    private fun navigateToGeocoding() {
        val intent = Intent(this, GeocodingActivity::class.java)
        intent.putExtra(KEY_ROOM_TOKEN, roomToken)
        intent.putExtra(KEY_CHAT_API_VERSION, chatApiVersion)
        geocodingLauncher.launch(intent)
    }
}
