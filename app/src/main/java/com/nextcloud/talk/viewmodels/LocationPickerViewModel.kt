/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2021-2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.viewmodels

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

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    private var nominatimClient: TalkJsonNominatimClient? = null

    fun initGeocoder(baseUrl: String, email: String) {
        nominatimClient = TalkJsonNominatimClient(baseUrl, okHttpClient, email)
    }

    fun initState(
        geocodingResult: GeocodingResult?,
        moveToCurrentLocation: Boolean,
        mapCenterLat: Double,
        mapCenterLon: Double
    ) {
        _uiState.update {
            it.copy(
                geocodingResult = geocodingResult,
                moveToCurrentLocation = moveToCurrentLocation,
                mapCenterLat = mapCenterLat,
                mapCenterLon = mapCenterLon
            )
        }
        setLocationDescription(isGpsLocation = false, isGeocodedResult = geocodingResult != null)
    }

    fun setMoveToCurrentLocation(value: Boolean) {
        _uiState.update { it.copy(moveToCurrentLocation = value) }
    }

    fun setReadyToShareLocation(value: Boolean) {
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

    fun setGeocodingResultToNull() {
        _uiState.update { it.copy(geocodingResult = null) }
    }

    fun updateMapCenter(lat: Double, lon: Double) {
        _uiState.update { it.copy(mapCenterLat = lat, mapCenterLon = lon) }
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

    fun setLocationDescription(isGpsLocation: Boolean, isGeocodedResult: Boolean) {
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
                    placeName = _uiState.value.geocodingResult?.displayName ?: ""
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

    @Suppress("Detekt.LongParameterList")
    fun shareLocation(
        selectedLat: Double?,
        selectedLon: Double?,
        locationName: String?,
        sharedLocationFallbackName: String,
        roomToken: String,
        chatApiVersion: Int
    ) {
        if (selectedLat == null || selectedLon == null) return
        if (locationName.isNullOrEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                var address: Address? = null
                try {
                    address = nominatimClient?.getAddress(selectedLon, selectedLat)
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to get geocoded addresses", e)
                }
                withContext(Dispatchers.Main) {
                    executeShareLocation(
                        selectedLat,
                        selectedLon,
                        address?.displayName,
                        sharedLocationFallbackName,
                        roomToken,
                        chatApiVersion
                    )
                }
            }
        } else {
            executeShareLocation(
                selectedLat,
                selectedLon,
                locationName,
                sharedLocationFallbackName,
                roomToken,
                chatApiVersion
            )
        }
    }

    @Suppress("Detekt.LongParameterList")
    private fun executeShareLocation(
        selectedLat: Double,
        selectedLon: Double,
        locationName: String?,
        sharedLocationFallbackName: String,
        roomToken: String,
        chatApiVersion: Int
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
