/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Samanwith KSN <samanwith21@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import fr.dudie.nominatim.client.TalkJsonNominatimClient
import fr.dudie.nominatim.model.Address
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.IOException

class GeoCodingViewModel : ViewModel() {
    private val _geocodingResults = MutableStateFlow<List<Address>>(emptyList())
    val geocodingResults: StateFlow<List<Address>> = _geocodingResults
    private val nominatimClient: TalkJsonNominatimClient
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder().build()
    private var query: String = ""

    fun getQuery(): String = query

    fun setQuery(query: String) {
        this.query = query
    }

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
                    _geocodingResults.value = results
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to get geocoded addresses", e)
                }
            }
        }
    }

    companion object {
        private val TAG = GeoCodingViewModel::class.java.simpleName
    }
}
