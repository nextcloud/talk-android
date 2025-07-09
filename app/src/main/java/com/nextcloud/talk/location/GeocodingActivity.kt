/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2021 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.location

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import androidx.appcompat.widget.SearchView
import androidx.core.graphics.drawable.toDrawable
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
class GeocodingActivity : BaseActivity() {

    private lateinit var binding: ActivityGeocodingBinding

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var okHttpClient: OkHttpClient

    lateinit var roomToken: String
    private var chatApiVersion: Int = 1
    private var nominatimClient: TalkJsonNominatimClient? = null

    private var searchItem: MenuItem? = null
    var searchView: SearchView? = null

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
        initSystemBars()

        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))

        roomToken = intent.getStringExtra(BundleKeys.KEY_ROOM_TOKEN)!!
        chatApiVersion = intent.getIntExtra(BundleKeys.KEY_CHAT_API_VERSION, 1)

        recyclerView = findViewById(R.id.geocoding_results)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = GeocodingAdapter(this, geocodingResults)
        recyclerView.adapter = adapter
        viewModel = ViewModelProvider(this)[GeoCodingViewModel::class.java]

        var query = viewModel.getQuery()
        if (query.isEmpty() && intent.hasExtra(BundleKeys.KEY_GEOCODING_QUERY)) {
            query = intent.getStringExtra(BundleKeys.KEY_GEOCODING_QUERY).orEmpty()
            viewModel.setQuery(query)
        }
        val savedResults = viewModel.getGeocodingResults()
        initAdapter(savedResults)
        viewModel.getGeocodingResultsLiveData().observe(this) { results ->
            geocodingResults = results
            adapter.updateData(results)
        }
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

        if (viewModel.getQuery().isNotEmpty() && adapter.itemCount == 0) {
            viewModel.searchLocation()
        } else {
            Log.e(TAG, "search string that was passed to GeocodingActivity was null or empty")
        }
        adapter.setOnItemClickListener(object : GeocodingAdapter.OnItemClickListener {
            override fun onItemClick(position: Int) {
                val address: Address = adapter.getItem(position) as Address
                val geocodingResult = GeocodingResult(address.latitude, address.longitude, address.displayName)
                val intent = Intent(this@GeocodingActivity, LocationPickerActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                intent.putExtra(BundleKeys.KEY_ROOM_TOKEN, roomToken)
                intent.putExtra(BundleKeys.KEY_CHAT_API_VERSION, chatApiVersion)
                intent.putExtra(BundleKeys.KEY_GEOCODING_RESULT, geocodingResult)
                startActivity(intent)
            }
        })
        searchView?.setQuery(viewModel.getQuery(), false)
    }

    private fun setupActionBar() {
        setSupportActionBar(binding.geocodingToolbar)
        binding.geocodingToolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setIcon(resources!!.getColor(R.color.transparent, null).toDrawable())
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
                intent.putExtra(BundleKeys.KEY_CHAT_API_VERSION, chatApiVersion)
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
        searchView?.setQuery(viewModel.getQuery(), false)
        searchView?.clearFocus()
        return true
    }

    private fun initSearchView() {
        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        if (searchItem != null) {
            searchView = searchItem!!.actionView as SearchView?

            searchView?.maxWidth = Int.MAX_VALUE
            searchView?.inputType = InputType.TYPE_TEXT_VARIATION_FILTER
            var imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_FULLSCREEN
            if (appPreferences.isKeyboardIncognito) {
                imeOptions = imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            }
            searchView?.imeOptions = imeOptions
            searchView?.queryHint = resources!!.getString(R.string.nc_search)
            searchView?.setSearchableInfo(searchManager.getSearchableInfo(componentName))
            searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean {
                    viewModel.setQuery(query)
                    viewModel.searchLocation()
                    searchView?.clearFocus()
                    return true
                }

                override fun onQueryTextChange(query: String): Boolean {
                    // This is a workaround to not set viewModel data when onQueryTextChange is triggered on startup
                    // Otherwise it would be set to an empty string.
                    if (searchView?.width!! > 0) {
                        viewModel.setQuery(query)
                    }
                    return true
                }
            })

            searchItem?.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionExpand(menuItem: MenuItem): Boolean = true

                override fun onMenuItemActionCollapse(menuItem: MenuItem): Boolean {
                    val intent = Intent(context, LocationPickerActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    intent.putExtra(BundleKeys.KEY_ROOM_TOKEN, roomToken)
                    intent.putExtra(BundleKeys.KEY_CHAT_API_VERSION, chatApiVersion)
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
    }
}
