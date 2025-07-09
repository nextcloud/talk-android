/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Samanwith KSN <samanwith21@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.viewmodels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.nextcloud.talk.activities.CallActivity.Companion.TAG
import fr.dudie.nominatim.client.TalkJsonNominatimClient
import fr.dudie.nominatim.model.Address
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.IOException

class GeoCodingViewModel : ViewModel() {
    private val geocodingResultsLiveData = MutableLiveData<List<Address>>()
    private val nominatimClient: TalkJsonNominatimClient
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder().build()
    private var geocodingResults: List<Address> = ArrayList()
    private var query: String = ""
    fun getGeocodingResultsLiveData(): LiveData<List<Address>> = geocodingResultsLiveData

    fun getQuery(): String = query

    fun setQuery(query: String) {
        this.query = query
    }

    fun getGeocodingResults(): List<Address> = geocodingResults

    init {
        nominatimClient = TalkJsonNominatimClient(
            "https://nominatim.openstreetmap.org/",
            okHttpClient,
            " android@nextcloud.com"
        )
    }

    fun searchLocation() {
        if (query.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val results = nominatimClient.search(query) as ArrayList<Address>
                    for (address in results) {
                        Log.d(TAG, address.displayName)
                        Log.d(TAG, address.latitude.toString())
                        Log.d(TAG, address.longitude.toString())
                    }
                    geocodingResults = results
                    geocodingResultsLiveData.postValue(results)
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to get geocoded addresses", e)
                }
            }
        }
    }
}
