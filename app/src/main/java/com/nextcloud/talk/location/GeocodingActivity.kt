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

package com.nextcloud.talk.location

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuItemCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import autodagger.AutoInjector
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.adapters.GeocodingAdapter
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.databinding.ActivityGeocodingBinding
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.viewmodels.GeoCodingViewModel
import fr.dudie.nominatim.client.TalkJsonNominatimClient
import fr.dudie.nominatim.model.Address
import okhttp3.OkHttpClient
import org.osmdroid.config.Configuration
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class GeocodingActivity :
    BaseActivity(),
    SearchView.OnQueryTextListener {

    private lateinit var binding: ActivityGeocodingBinding

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var okHttpClient: OkHttpClient

    lateinit var roomToken: String
    var nominatimClient: TalkJsonNominatimClient? = null

    var searchItem: MenuItem? = null
    var searchView: SearchView? = null
    var query: String? = null

    lateinit var adapter: GeocodingAdapter
    private var geocodingResults: List<Address> = ArrayList()
    private lateinit var recyclerView: RecyclerView
    private lateinit var viewModel: GeoCodingViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        binding = ActivityGeocodingBinding.inflate(layoutInflater)
        setupActionBar()
        setContentView(binding.root)
        setupSystemColors()

        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))

        roomToken = intent.getStringExtra(BundleKeys.KEY_ROOM_TOKEN)!!
        query = intent.getStringExtra(BundleKeys.KEY_GEOCODING_QUERY)
        recyclerView = findViewById(R.id.geocoding_results)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = GeocodingAdapter(this, geocodingResults)
        recyclerView.adapter = adapter
        viewModel = ViewModelProvider(this).get(GeoCodingViewModel::class.java)

        query = viewModel.getQuery()
        if (query.isNullOrEmpty()) {
            query = intent.getStringExtra(BundleKeys.KEY_GEOCODING_QUERY)
            viewModel.setQuery(query ?: "")
        }
        val savedResults = viewModel.getGeocodingResults()
        initAdapter(savedResults)
        viewModel.getGeocodingResultsLiveData().observe(
            this,
            Observer { results ->
                geocodingResults = results
                adapter.updateData(results)
            }
        )
        viewModel.getQueryLiveData().observe(
            this,
            Observer { newQuery ->
                query = newQuery
                searchView?.setQuery(query, false)
            }
        )
        val baseUrl = getString(R.string.osm_geocoder_url)
        val email = context.getString(R.string.osm_geocoder_contact)
        nominatimClient = TalkJsonNominatimClient(baseUrl, okHttpClient, email)
    }

    override fun onStart() {
        super.onStart()
        initAdapter(geocodingResults)
        initGeocoder()
    }

    override fun onResume() {
        super.onResume()

        if (!query.isNullOrEmpty()) {
            viewModel.searchLocation(query!!)
        } else {
            Log.e(TAG, "search string that was passed to GeocodingController was null or empty")
        }
        adapter.setOnItemClickListener(object : GeocodingAdapter.OnItemClickListener {
            override fun onItemClick(position: Int) {
                val address: Address = adapter.getItem(position) as Address
                val geocodingResult = GeocodingResult(address.latitude, address.longitude, address.displayName)
                val intent = Intent(this@GeocodingActivity, LocationPickerActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                intent.putExtra(BundleKeys.KEY_ROOM_TOKEN, roomToken)
                intent.putExtra(BundleKeys.KEY_GEOCODING_RESULT, geocodingResult)
                startActivity(intent)
            }
        })
        searchView?.setQuery(query, false)
    }

    private fun setupActionBar() {
        setSupportActionBar(binding.geocodingToolbar)
        binding.geocodingToolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setIcon(ColorDrawable(resources!!.getColor(R.color.transparent, null)))
        supportActionBar?.title = ""
        viewThemeUtils.material.themeToolbar(binding.geocodingToolbar)
    }

    private fun initAdapter(addresses: List<Address>) {
        adapter = GeocodingAdapter(binding.geocodingResults.context!!, addresses)
        adapter.setOnItemClickListener(object : GeocodingAdapter.OnItemClickListener {
            override fun onItemClick(position: Int) {
                val address: Address = adapter.getItem(position) as Address
                val geocodingResult = GeocodingResult(address.latitude, address.longitude, address.displayName)
                val intent = Intent(this@GeocodingActivity, LocationPickerActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                intent.putExtra(BundleKeys.KEY_ROOM_TOKEN, roomToken)
                intent.putExtra(BundleKeys.KEY_GEOCODING_RESULT, geocodingResult)
                startActivity(intent)
            }
        })
        binding.geocodingResults.adapter = adapter
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.menu_geocoding, menu)
        searchItem = menu.findItem(R.id.geocoding_action_search)
        initSearchView()

        searchItem?.expandActionView()
        searchView?.setQuery(query, false)
        searchView?.clearFocus()
        return true
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        this.query = query ?: ""
        viewModel.setQuery(this.query!!)
        if (query != null) {
            viewModel.searchLocation(query)
        }
        searchView?.clearFocus()
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        query?.let { viewModel.setQuery(it) }
        outState.putString(KEY_SEARCH_QUERY, query)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        query = viewModel.getQuery()
        query = savedInstanceState.getString(KEY_SEARCH_QUERY)
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        return true
    }

    private fun initSearchView() {
        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        if (searchItem != null) {
            searchView = MenuItemCompat.getActionView(searchItem) as SearchView
            searchView?.maxWidth = Int.MAX_VALUE
            searchView?.inputType = InputType.TYPE_TEXT_VARIATION_FILTER
            var imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_FULLSCREEN
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appPreferences.isKeyboardIncognito) {
                imeOptions = imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            }
            searchView?.imeOptions = imeOptions
            searchView?.queryHint = resources!!.getString(R.string.nc_search)
            searchView?.setSearchableInfo(searchManager.getSearchableInfo(componentName))
            searchView?.setOnQueryTextListener(this)

            searchItem?.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionExpand(menuItem: MenuItem): Boolean {
                    return true
                }

                override fun onMenuItemActionCollapse(menuItem: MenuItem): Boolean {
                    val intent = Intent(context, LocationPickerActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    intent.putExtra(BundleKeys.KEY_ROOM_TOKEN, roomToken)
                    startActivity(intent)
                    return true
                }
            })
        }
    }

    private fun initGeocoder() {
        val baseUrl = getString(R.string.osm_geocoder_url)
        val email = context.getString(R.string.osm_geocoder_contact)
        nominatimClient = TalkJsonNominatimClient(baseUrl, okHttpClient, email)
    }

    companion object {
        val TAG = GeocodingActivity::class.java.simpleName
        const val KEY_SEARCH_QUERY = "search_query"
    }
}
