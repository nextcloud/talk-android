/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.location.viewmodels

import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.location.GeocodingResult
import com.nextcloud.talk.utils.database.user.CurrentUserProviderOld
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
class LocationPickerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: LocationPickerViewModel

    private val ncApi: NcApi = mock()
    private val userProvider: CurrentUserProviderOld = mock()
    private val okHttpClient: OkHttpClient = mock()

    private val initParams = LocationPickerViewModel.LocationPickerInitParams(
        roomToken = "testRoom",
        chatApiVersion = 1,
        geocodingResult = null,
        moveToCurrentLocation = false,
        mapCenterLat = 52.5163,
        mapCenterLon = 13.3777,
        geocoderBaseUrl = "https://nominatim.openstreetmap.org",
        geocoderEmail = "test@example.com"
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = LocationPickerViewModel(ncApi, userProvider, okHttpClient)
        viewModel.initialize(initParams)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onMapScrolled with same coordinates preserves placeName after geocoding result`() {
        val geocodingResult = GeocodingResult(lat = 52.5163, lon = 13.3777, displayName = "Brandenburger Tor")

        // Simulate geocoding result received by the screen
        viewModel.onGeocodingResultReceived(geocodingResult)
        // Screen calls updateMapCenter and then onMapScrolled with the result coordinates
        viewModel.updateMapCenter(geocodingResult.lat, geocodingResult.lon)
        viewModel.onMapScrolled(geocodingResult.lat, geocodingResult.lon)

        // placeName must still be "Brandenburger Tor" after this first scroll
        assertEquals("Brandenburger Tor", viewModel.uiState.value.placeName)

        // Simulate a zoom-in: same target coordinates, only zoom level changes.
        viewModel.onMapScrolled(geocodingResult.lat, geocodingResult.lon)

        assertEquals(
            "placeName must survive a zoom-only camera event",
            "Brandenburger Tor",
            viewModel.uiState.value.placeName
        )
        assertEquals(
            "LocationDescriptionType must remain GEOCODED after zoom",
            LocationPickerViewModel.LocationDescriptionType.GEOCODED,
            viewModel.uiState.value.locationDescriptionType
        )
    }

    @Test
    fun `repeated zoom events with same coordinates all preserve placeName`() {
        val geocodingResult = GeocodingResult(lat = 48.1374, lon = 11.5755, displayName = "Marienplatz, München")

        viewModel.onGeocodingResultReceived(geocodingResult)
        viewModel.updateMapCenter(geocodingResult.lat, geocodingResult.lon)
        viewModel.onMapScrolled(geocodingResult.lat, geocodingResult.lon)

        // Multiple zoom events
        repeat(5) {
            viewModel.onMapScrolled(geocodingResult.lat, geocodingResult.lon)
        }

        assertEquals("Marienplatz, München", viewModel.uiState.value.placeName)
    }

    @Test
    fun `onMapScrolled with different coordinates clears placeName`() {
        val geocodingResult = GeocodingResult(lat = 52.5163, lon = 13.3777, displayName = "Brandenburger Tor")

        viewModel.onGeocodingResultReceived(geocodingResult)
        viewModel.updateMapCenter(geocodingResult.lat, geocodingResult.lon)
        viewModel.onMapScrolled(geocodingResult.lat, geocodingResult.lon)

        assertEquals("Brandenburger Tor", viewModel.uiState.value.placeName)

        // User pans the map to a genuinely new location
        val newLat = 15.5200
        val newLon = 42.4050
        viewModel.onMapScrolled(newLat, newLon)

        assertEquals(
            "placeName must be cleared after the user pans to a new location",
            "",
            viewModel.uiState.value.placeName
        )
        assertEquals(
            "LocationDescriptionType must become MANUAL after panning",
            LocationPickerViewModel.LocationDescriptionType.MANUAL,
            viewModel.uiState.value.locationDescriptionType
        )
    }

    @Test
    fun `race condition simulation preserves placeName on second same-coords scroll`() {
        val geocodingResult = GeocodingResult(lat = 51.5074, lon = -0.1278, displayName = "London")

        // 1. Geocoding result received
        viewModel.onGeocodingResultReceived(geocodingResult)

        // 2. Screen updates map center and calls onMapScrolled (normal geocoding flow)
        viewModel.updateMapCenter(geocodingResult.lat, geocodingResult.lon)
        viewModel.onMapScrolled(geocodingResult.lat, geocodingResult.lon)

        // At this point geocodingResult is null in the state, but placeName is preserved
        assertEquals("London", viewModel.uiState.value.placeName)

        // 3. Race condition: final animation frame arrives after isProgrammaticCameraMove is reset
        //    → onMapScrolled is called a second time with the same coordinates
        viewModel.onMapScrolled(geocodingResult.lat, geocodingResult.lon)

        assertEquals(
            "placeName must survive the race-condition second call with the same coordinates",
            "London",
            viewModel.uiState.value.placeName
        )
    }
}
