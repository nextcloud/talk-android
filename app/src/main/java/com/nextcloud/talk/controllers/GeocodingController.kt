package com.nextcloud.talk.controllers

import android.app.SearchManager
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ListView
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuItemCompat
import androidx.preference.PreferenceManager
import autodagger.AutoInjector
import butterknife.BindView
import com.nextcloud.talk.R
import com.nextcloud.talk.adapters.GeocodingAdapter
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.controllers.base.BaseController
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.preferences.AppPreferences
import fr.dudie.nominatim.client.JsonNominatimClient
import fr.dudie.nominatim.model.Address
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.http.client.HttpClient
import org.apache.http.conn.ClientConnectionManager
import org.apache.http.conn.scheme.Scheme
import org.apache.http.conn.scheme.SchemeRegistry
import org.apache.http.conn.ssl.SSLSocketFactory
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.impl.conn.SingleClientConnManager
import org.osmdroid.config.Configuration
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class GeocodingController(args: Bundle) : BaseController(args), SearchView.OnQueryTextListener {

    @Inject
    @JvmField
    var context: Context? = null

    @Inject
    @JvmField
    var appPreferences: AppPreferences? = null

    @BindView(R.id.geocoding_results)
    @JvmField
    var geocodingResultListView: ListView? = null

    var nominatimClient: JsonNominatimClient? = null

    var searchItem: MenuItem? = null
    var searchView: SearchView? = null
    var query: String? = null

    lateinit var adapter: GeocodingAdapter
    private var geocodingResults: List<Address> = ArrayList()

    init {
        setHasOptionsMenu(true)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
        query = args.getString(BundleKeys.KEY_GEOCODING_QUERY)

        initAdapter(geocodingResults)
    }

    private fun initAdapter(addresses: List<Address>) {
        adapter = GeocodingAdapter(context!!, addresses)
        geocodingResultListView?.adapter = adapter
    }

    override fun inflateView(inflater: LayoutInflater, container: ViewGroup): View {
        return inflater.inflate(R.layout.controller_geocoding, container, false)
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        initGeocoder()
        if (!query.isNullOrEmpty()) {
            searchLocation()
        } else {
            Log.e(TAG, "search string that was passed to GeocodingController was null or empty")
        }

        geocodingResultListView?.onItemClickListener = AdapterView.OnItemClickListener { parent, view, position, id ->
            val address: Address = adapter.getItem(position) as Address
            // TODO: directly share location? or post loaction to LocationPickerConttroller?
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

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        hideSearchBar()
        actionBar.setIcon(ColorDrawable(resources!!.getColor(android.R.color.transparent)))
        actionBar.title = "Share location"
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
                searchView?.setMaxWidth(Int.MAX_VALUE)
                searchView?.setInputType(InputType.TYPE_TEXT_VARIATION_FILTER)
                var imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_FULLSCREEN
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appPreferences!!.isKeyboardIncognito) {
                    imeOptions = imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                }
                searchView?.setImeOptions(imeOptions)
                searchView?.setQueryHint(resources!!.getString(R.string.nc_search))
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
        val registry = SchemeRegistry()
        registry.register(Scheme("https", SSLSocketFactory.getSocketFactory(), 443))
        val connexionManager: ClientConnectionManager = SingleClientConnManager(null, registry)
        val httpClient: HttpClient = DefaultHttpClient(connexionManager, null)
        val baseUrl = "https://nominatim.openstreetmap.org/"
        val email = "android@nextcloud.com"
        nominatimClient = JsonNominatimClient(baseUrl, httpClient, email)
    }

    private fun searchLocation(): Boolean {
        CoroutineScope(IO).launch {
            executeGeocodingRequest()
        }
        return true
    }

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
        }
        updateResultsOnMainThread(results)
    }

    private suspend fun updateResultsOnMainThread(results: ArrayList<Address>) {
        withContext(Main) {
            initAdapter(results)
        }
    }

    companion object {
        private val TAG = "GeocodingController"
    }
}
