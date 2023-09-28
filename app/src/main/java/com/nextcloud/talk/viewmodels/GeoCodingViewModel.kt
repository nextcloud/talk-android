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
import fr.dudie.nominatim.client.TalkJsonNominatimClient
import fr.dudie.nominatim.model.Address
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.IOException

class GeoCodingViewModel : ViewModel() {
    private val geocodingResultsLiveData = MutableLiveData<List<Address>>()
    fun getGeocodingResultsLiveData(): LiveData<List<Address>> {
        return geocodingResultsLiveData
    }

    private val nominatimClient: TalkJsonNominatimClient
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder().build()

    init {
        nominatimClient = TalkJsonNominatimClient(
            "https://nominatim.openstreetmap.org/",
            okHttpClient,
            " android@nextcloud.com"
        )
    }

    fun searchLocation(query: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val results = nominatimClient.search(query) as ArrayList<Address>
                geocodingResultsLiveData.postValue(results)
            } catch (e: IOException) {
                Log.e(TAG, "Failed to get geocoded addresses", e)
            }
        }
    }
}
