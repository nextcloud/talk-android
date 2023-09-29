/*
 * Nextcloud Talk application
 *
 * @author Samanwith KSN
 * Copyright (C) 2023 Samanwith KSN <samanwith21@gmail.com>
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

package com.nextcloud.talk.viewmodels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.nextcloud.talk.activities.CallActivity.Companion.TAG
import com.nextcloud.talk.location.GeocodingActivity
import fr.dudie.nominatim.client.TalkJsonNominatimClient
import fr.dudie.nominatim.model.Address
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.IOException

class GeoCodingViewModel : ViewModel() {
    private val geocodingResultsLiveData = MutableLiveData<List<Address>>()
    private val queryLiveData = MutableLiveData<String>()
    private val nominatimClient: TalkJsonNominatimClient
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder().build()
    private var geocodingResults: List<Address> = ArrayList()
    private var query: String = ""
    fun getGeocodingResultsLiveData(): LiveData<List<Address>> {
        return geocodingResultsLiveData
    }

    fun getQueryLiveData(): LiveData<String> {
        return queryLiveData
    }

    fun getQuery(): String {
        return query
    }

    fun setQuery(query: String) {
        if (queryLiveData.value.isNullOrEmpty()) {
            queryLiveData.value = query
        }
    }

    fun getGeocodingResults(): List<Address> {
        return geocodingResults
    }

    init {
        nominatimClient = TalkJsonNominatimClient(
            "https://nominatim.openstreetmap.org/",
            okHttpClient,
            " android@nextcloud.com"
        )
    }

    fun searchLocation(query: String) {
        if (query.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val results = nominatimClient.search(query) as ArrayList<Address>
                    for (address in results) {
                        Log.d(GeocodingActivity.TAG, address.displayName)
                        Log.d(GeocodingActivity.TAG, address.latitude.toString())
                        Log.d(GeocodingActivity.TAG, address.longitude.toString())
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
