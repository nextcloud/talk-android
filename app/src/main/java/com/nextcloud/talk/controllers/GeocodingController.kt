/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * Copyright (C) 2021 Marcel Hibbe <dev@mhibbe.de>
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

package com.nextcloud.talk.controllers

import android.app.SearchManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuItemCompat
import androidx.preference.PreferenceManager
import autodagger.AutoInjector
import com.nextcloud.talk.R
import com.nextcloud.talk.adapters.GeocodingAdapter
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.controllers.base.NewBaseController
import com.nextcloud.talk.controllers.util.viewBinding
import com.nextcloud.talk.databinding.ControllerGeocodingBinding
import com.nextcloud.talk.utils.bundle.BundleKeys
import fr.dudie.nominatim.client.TalkJsonNominatimClient
import fr.dudie.nominatim.model.Address
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.osmdroid.config.Configuration
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class GeocodingController(args: Bundle) :
    NewBaseController(
        R.layout.controller_geocoding,
        args
    ),
    SearchView.OnQueryTextListener {
    private val binding: ControllerGeocodingBinding by viewBinding(ControllerGeocodingBinding::bind)

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var okHttpClient: OkHttpClient

    var roomToken: String?
    var nominatimClient: TalkJsonNominatimClient? = null

    var searchItem: MenuItem? = null
    var searchView: SearchView? = null
    var query: String? = null

    lateinit var adapter: GeocodingAdapter
    private var geocodingResults: List<Address> = ArrayList()

    constructor(args: Bundle, listener: LocationPickerController) : this(args) {
        targetController = listener
    }

    init {
        setHasOptionsMenu(true)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
        query = args.getString(BundleKeys.KEY_GEOCODING_QUERY)
        roomToken = args.getString(BundleKeys.KEY_ROOM_TOKEN)
    }

    private fun initAdapter(addresses: List<Address>) {
        adapter = GeocodingAdapter(binding.geocodingResults.context!!, addresses)
        binding.geocodingResults.adapter = adapter
    }

    override fun onAttach(view: View) {
        super.onAttach(view)

        initAdapter(geocodingResults)

        initGeocoder()
        if (!query.isNullOrEmpty()) {
            searchLocation()
        } else {
            Log.e(TAG, "search string that was passed to GeocodingController was null or empty")
        }

        binding.geocodingResults.onItemClickListener = AdapterView.OnItemClickListener { parent, view, position, id ->
            val address: Address = adapter.getItem(position) as Address
            val listener: GeocodingResultListener? = targetController as GeocodingResultListener?
            listener?.receiveChosenGeocodingResult(address.latitude, address.longitude, address.displayName)
            router.popCurrentController()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_geocoding, menu)
        searchItem = menu.findItem(R.id.geocoding_action_search)
        initSearchView()

        searchItem?.expandActionView()
        searchView?.setQuery(query, false)
        searchView?.clearFocus()
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        this.query = query
        searchLocation()
        searchView?.clearFocus()
        return true
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        return true
    }

    private fun initSearchView() {
        if (activity != null) {
            val searchManager = activity!!.getSystemService(Context.SEARCH_SERVICE) as SearchManager
            if (searchItem != null) {
                searchView = MenuItemCompat.getActionView(searchItem) as SearchView
                searchView?.maxWidth = Int.MAX_VALUE
                searchView?.inputType = InputType.TYPE_TEXT_VARIATION_FILTER
                var imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_FULLSCREEN
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appPreferences!!.isKeyboardIncognito) {
                    imeOptions = imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                }
                searchView?.imeOptions = imeOptions
                searchView?.queryHint = resources!!.getString(R.string.nc_search)
                searchView?.setSearchableInfo(searchManager.getSearchableInfo(activity!!.componentName))
                searchView?.setOnQueryTextListener(this)

                searchItem?.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
                    override fun onMenuItemActionExpand(menuItem: MenuItem): Boolean {
                        return true
                    }

                    override fun onMenuItemActionCollapse(menuItem: MenuItem): Boolean {
                        router.popCurrentController()
                        return true
                    }
                })
            }
        }
    }

    private fun initGeocoder() {
        val baseUrl = context!!.getString(R.string.osm_geocoder_url)
        val email = context!!.getString(R.string.osm_geocoder_contact)
        nominatimClient = TalkJsonNominatimClient(baseUrl, okHttpClient, email)
    }

    private fun searchLocation(): Boolean {
        CoroutineScope(IO).launch {
            executeGeocodingRequest()
        }
        return true
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    private suspend fun executeGeocodingRequest() {
        var results: ArrayList<Address> = ArrayList()
        try {
            results = nominatimClient!!.search(query) as ArrayList<Address>
            for (address in results) {
                Log.d(TAG, address.displayName)
                Log.d(TAG, address.latitude.toString())
                Log.d(TAG, address.longitude.toString())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get geocoded addresses", e)
            Toast.makeText(context, R.string.nc_common_error_sorry, Toast.LENGTH_LONG).show()
        }
        updateResultsOnMainThread(results)
    }

    private suspend fun updateResultsOnMainThread(results: ArrayList<Address>) {
        withContext(Main) {
            initAdapter(results)
        }
    }

    interface GeocodingResultListener {
        fun receiveChosenGeocodingResult(lat: Double, lon: Double, name: String)
    }

    companion object {
        private const val TAG = "GeocodingController"
    }
}
