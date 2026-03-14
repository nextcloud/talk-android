/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2021-2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.location.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextcloud.talk.R
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.location.GeocodingResult
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.database.user.CurrentUserProviderOld
import fr.dudie.nominatim.client.TalkJsonNominatimClient
import fr.dudie.nominatim.model.Address
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.IOException
import javax.inject.Inject

@Suppress("TooManyFunctions")
class LocationPickerViewModel @Inject constructor(
    private val ncApi: NcApi,
    private val currentUserProviderOld: CurrentUserProviderOld,
    private val okHttpClient: OkHttpClient
) : ViewModel() {

    sealed class ViewState {
        data object Idle : ViewState()
        data object SendingLocation : ViewState()
        data object LocationShared : ViewState()
        data class Error(val messageRes: Int) : ViewState()
    }

    enum class LocationDescriptionType { GPS, GEOCODED, MANUAL }

    data class UiState(
        val geocodingResult: GeocodingResult? = null,
        val moveToCurrentLocation: Boolean = true,
        val readyToShareLocation: Boolean = false,
        val locationDescriptionType: LocationDescriptionType = LocationDescriptionType.MANUAL,
        val placeName: String = "",
        val mapCenterLat: Double = 0.0,
        val mapCenterLon: Double = 0.0,
        val viewState: ViewState = ViewState.Idle
    )

    data class LocationPickerInitParams(
        val roomToken: String,
        val chatApiVersion: Int,
        val geocodingResult: GeocodingResult?,
        val moveToCurrentLocation: Boolean,
        val mapCenterLat: Double,
        val mapCenterLon: Double,
        val geocoderBaseUrl: String,
        val geocoderEmail: String
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    private lateinit var nominatimClient: TalkJsonNominatimClient

    private var isStateInitialized = false

    private var roomToken: String = ""
    private var chatApiVersion: Int = 1

    /**
     * Initialises routing, map state, and the geocoder in one atomic call.
     * Safe to call on every [Activity.onCreate] — subsequent calls after the first are no-ops
     * because the ViewModel survives configuration changes.
     */
    fun initialize(params: LocationPickerInitParams) {
        if (isStateInitialized) return
        isStateInitialized = true

        roomToken = params.roomToken
        chatApiVersion = params.chatApiVersion
        nominatimClient = TalkJsonNominatimClient(params.geocoderBaseUrl, okHttpClient, params.geocoderEmail)

        _uiState.update {
            it.copy(
                geocodingResult = params.geocodingResult,
                moveToCurrentLocation = params.moveToCurrentLocation,
                mapCenterLat = params.mapCenterLat,
                mapCenterLon = params.mapCenterLon
            )
        }
        setLocationDescription(isGpsLocation = false, isGeocodedResult = params.geocodingResult != null)
    }

    private fun setMoveToCurrentLocation(value: Boolean) {
        _uiState.update { it.copy(moveToCurrentLocation = value) }
    }

    private fun setReadyToShareLocation(value: Boolean) {
        _uiState.update { it.copy(readyToShareLocation = value) }
    }

    fun onGeocodingResultReceived(geocodingResult: GeocodingResult) {
        _uiState.update {
            it.copy(
                geocodingResult = geocodingResult,
                placeName = geocodingResult.displayName,
                locationDescriptionType = LocationDescriptionType.GEOCODED,
                moveToCurrentLocation = false,
                readyToShareLocation = false
            )
        }
    }

    private fun setGeocodingResultToNull() {
        _uiState.update { it.copy(geocodingResult = null) }
    }

    fun updateMapCenter(lat: Double, lon: Double) {
        _uiState.update { it.copy(mapCenterLat = lat, mapCenterLon = lon) }
    }

    /**
     * Called when the screen becomes visible (ON_RESUME).
     * Resets the ready-to-share flag and location description for non-geocoded states so
     * the user must re-confirm the map position before sharing.
     * For an already-geocoded result the state is deliberately preserved.
     */
    fun onScreenResumed() {
        val state = _uiState.value
        if (state.locationDescriptionType != LocationDescriptionType.GEOCODED) {
            setReadyToShareLocation(false)
            setLocationDescription(isGpsLocation = false, isGeocodedResult = state.geocodingResult != null)
        }
    }

    /**
     * Called when the user taps the "center on my location" button.
     * The actual camera animation is driven by the View; the ViewModel records the intent
     * so that the next onMapScrolled() call is classified as a GPS-location event.
     */
    fun onCenterLocationRequested() {
        setMoveToCurrentLocation(true)
    }

    fun onMapScrolled() {
        val state = _uiState.value
        when {
            state.moveToCurrentLocation -> {
                setLocationDescription(isGpsLocation = true, isGeocodedResult = false)
                setMoveToCurrentLocation(false)
            }
            state.geocodingResult != null -> {
                setLocationDescription(isGpsLocation = false, isGeocodedResult = true)
                setGeocodingResultToNull()
            }
            else -> {
                setLocationDescription(isGpsLocation = false, isGeocodedResult = false)
            }
        }
        setReadyToShareLocation(true)
    }

    private fun setLocationDescription(isGpsLocation: Boolean, isGeocodedResult: Boolean) {
        when {
            isGpsLocation -> _uiState.update {
                it.copy(
                    locationDescriptionType = LocationDescriptionType.GPS,
                    placeName = ""
                )
            }
            isGeocodedResult -> _uiState.update {
                it.copy(
                    locationDescriptionType = LocationDescriptionType.GEOCODED,
                    // Fall back to the already-stored placeName if geocodingResult was
                    // cleared before this call (e.g. second call after setGeocodingResultToNull).
                    placeName = _uiState.value.geocodingResult?.displayName ?: it.placeName
                )
            }
            else -> _uiState.update {
                it.copy(
                    locationDescriptionType = LocationDescriptionType.MANUAL,
                    placeName = ""
                )
            }
        }
    }

    fun shareLocation(locationName: String?, sharedLocationFallbackName: String) {
        val selectedLat = _uiState.value.mapCenterLat.takeIf { it != 0.0 }
        val selectedLon = _uiState.value.mapCenterLon.takeIf { it != 0.0 }
        if (selectedLat == null || selectedLon == null) return
        if (locationName.isNullOrEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                var address: Address? = null
                try {
                    address = nominatimClient.getAddress(selectedLon, selectedLat)
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to get geocoded addresses", e)
                }
                withContext(Dispatchers.Main) {
                    executeShareLocation(
                        selectedLat,
                        selectedLon,
                        address?.displayName,
                        sharedLocationFallbackName
                    )
                }
            }
        } else {
            executeShareLocation(selectedLat, selectedLon, locationName, sharedLocationFallbackName)
        }
    }

    private fun executeShareLocation(
        selectedLat: Double,
        selectedLon: Double,
        locationName: String?,
        sharedLocationFallbackName: String
    ) {
        _uiState.update { it.copy(viewState = ViewState.SendingLocation) }

        val objectId = "geo:$selectedLat,$selectedLon"
        val locationNameToShare = if (locationName.isNullOrBlank()) sharedLocationFallbackName else locationName

        val metaData =
            "{\"type\":\"geo-location\",\"id\":\"geo:$selectedLat,$selectedLon\"," +
                "\"latitude\":\"$selectedLat\"," +
                "\"longitude\":\"$selectedLon\",\"name\":\"$locationNameToShare\"}"

        val currentUser = currentUserProviderOld.currentUser.blockingGet()

        ncApi.sendLocation(
            ApiUtils.getCredentials(currentUser.username, currentUser.token),
            ApiUtils.getUrlToSendLocation(chatApiVersion, currentUser.baseUrl!!, roomToken),
            "geo-location",
            objectId,
            metaData
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<GenericOverall> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                override fun onNext(t: GenericOverall) {
                    _uiState.update { it.copy(viewState = ViewState.LocationShared) }
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, "error when trying to share location", e)
                    _uiState.update { it.copy(viewState = ViewState.Error(R.string.nc_common_error_sorry)) }
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    fun consumeViewState() {
        _uiState.update { it.copy(viewState = ViewState.Idle) }
    }

    companion object {
        private val TAG = LocationPickerViewModel::class.java.simpleName
    }
}
