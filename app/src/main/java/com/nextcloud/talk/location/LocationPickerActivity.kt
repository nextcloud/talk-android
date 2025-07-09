/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Ezhil Shanmugham <ezhil56x.contact@gmail.com>
 * SPDX-FileCopyrightText: 2021 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.location

import android.Manifest
import android.app.Activity
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.SearchView
import androidx.core.content.PermissionChecker
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.MenuItemCompat
import androidx.preference.PreferenceManager
import autodagger.AutoInjector
import com.google.android.material.snackbar.Snackbar
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.databinding.ActivityLocationBinding
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_CHAT_API_VERSION
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_GEOCODING_RESULT
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import fr.dudie.nominatim.client.TalkJsonNominatimClient
import fr.dudie.nominatim.model.Address
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.osmdroid.config.Configuration.getInstance
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class LocationPickerActivity :
    BaseActivity(),
    SearchView.OnQueryTextListener,
    LocationListener {

    private lateinit var binding: ActivityLocationBinding

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var okHttpClient: OkHttpClient

    var nominatimClient: TalkJsonNominatimClient? = null

    lateinit var roomToken: String
    private var chatApiVersion: Int = 1
    var geocodingResult: GeocodingResult? = null

    var myLocation: GeoPoint = GeoPoint(COORDINATE_ZERO, COORDINATE_ZERO)
    private var locationManager: LocationManager? = null
    private lateinit var locationOverlay: MyLocationNewOverlay

    var moveToCurrentLocation: Boolean = true
    var readyToShareLocation: Boolean = false

    private var mapCenterLat: Double = 0.0
    private var mapCenterLon: Double = 0.0

    var searchItem: MenuItem? = null
    var searchView: SearchView? = null

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        roomToken = intent.getStringExtra(KEY_ROOM_TOKEN)!!
        chatApiVersion = intent.getIntExtra(KEY_CHAT_API_VERSION, 1)
        geocodingResult = intent.getParcelableExtra(KEY_GEOCODING_RESULT)

        if (savedInstanceState != null) {
            moveToCurrentLocation = savedInstanceState.getBoolean("moveToCurrentLocation") == true
            mapCenterLat = savedInstanceState.getDouble("mapCenterLat")
            mapCenterLon = savedInstanceState.getDouble("mapCenterLon")
            geocodingResult = savedInstanceState.getParcelable("geocodingResult")
        }

        binding = ActivityLocationBinding.inflate(layoutInflater)
        setupActionBar()
        setContentView(binding.root)
        initSystemBars()

        getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    override fun onStart() {
        super.onStart()
        initMap()
    }

    override fun onResume() {
        super.onResume()

        if (geocodingResult != null) {
            moveToCurrentLocation = false
        }

        setLocationDescription(false, geocodingResult != null)
        binding.shareLocation.isClickable = false
        binding.shareLocation.setOnClickListener {
            if (readyToShareLocation) {
                shareLocation(
                    binding.map.mapCenter?.latitude,
                    binding.map.mapCenter?.longitude,
                    binding.placeName.text.toString()
                )
            } else {
                Log.w(TAG, "readyToShareLocation was false while user tried to share location.")
            }
        }
    }

    override fun onSaveInstanceState(bundle: Bundle) {
        super.onSaveInstanceState(bundle)
        bundle.putBoolean("moveToCurrentLocation", moveToCurrentLocation)
        bundle.putDouble("mapCenterLat", binding.map.mapCenter.latitude)
        bundle.putDouble("mapCenterLon", binding.map.mapCenter.longitude)
        bundle.putParcelable("geocodingResult", geocodingResult)
    }

    private fun setupActionBar() {
        setSupportActionBar(binding.locationPickerToolbar)
        binding.locationPickerToolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setIcon(resources!!.getColor(android.R.color.transparent, null).toDrawable())
        supportActionBar?.title = context.getString(R.string.nc_share_location)
        viewThemeUtils.material.themeToolbar(binding.locationPickerToolbar)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.menu_locationpicker, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)
        searchItem = menu.findItem(R.id.location_action_search)
        initSearchView()
        return true
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    override fun onStop() {
        super.onStop()

        try {
            locationManager!!.removeUpdates(this)
        } catch (e: Exception) {
            Log.e(TAG, "error when trying to remove updates for location Manager", e)
        }

        locationOverlay.disableMyLocation()
    }

    private fun initSearchView() {
        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        if (searchItem != null) {
            searchView = MenuItemCompat.getActionView(searchItem) as SearchView
            searchView?.maxWidth = Int.MAX_VALUE
            searchView?.inputType = InputType.TYPE_TEXT_VARIATION_FILTER
            var imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_FULLSCREEN
            if (appPreferences!!.isKeyboardIncognito) {
                imeOptions = imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            }
            searchView?.imeOptions = imeOptions
            searchView?.queryHint = resources!!.getString(R.string.nc_search)
            searchView?.setSearchableInfo(searchManager.getSearchableInfo(componentName))
            searchView?.setOnQueryTextListener(this)
        }
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        if (!query.isNullOrEmpty()) {
            val intent = Intent(this, GeocodingActivity::class.java)
            intent.putExtra(BundleKeys.KEY_GEOCODING_QUERY, query)
            intent.putExtra(KEY_ROOM_TOKEN, roomToken)
            intent.putExtra(KEY_CHAT_API_VERSION, chatApiVersion)
            startActivity(intent)
        }
        return true
    }

    override fun onQueryTextChange(newText: String?): Boolean = true

    @Suppress("Detekt.TooGenericExceptionCaught", "Detekt.ComplexMethod", "Detekt.LongMethod")
    private fun initMap() {
        binding.map.setTileSource(TileSourceFactory.MAPNIK)
        binding.map.onResume()

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        if (!isLocationPermissionsGranted()) {
            requestLocationPermissions()
        } else {
            requestLocationUpdates()
        }

        val copyrightOverlay = CopyrightOverlay(context)
        binding.map.overlays.add(copyrightOverlay)

        binding.map.setMultiTouchControls(true)
        binding.map.isTilesScaledToDpi = true

        locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), binding.map)
        locationOverlay.enableMyLocation()
        locationOverlay.setPersonHotspot(PERSON_HOT_SPOT_X, PERSON_HOT_SPOT_Y)
        locationOverlay.setPersonIcon(
            DisplayUtils.getBitmap(
                ResourcesCompat.getDrawable(resources!!, R.drawable.current_location_circle, null)!!
            )
        )
        binding.map.overlays.add(locationOverlay)

        val mapController = binding.map.controller

        if (geocodingResult != null) {
            mapController.setZoom(ZOOM_LEVEL_RECEIVED_RESULT)
        } else {
            mapController.setZoom(ZOOM_LEVEL_DEFAULT)
        }

        if (mapCenterLat != 0.0 && mapCenterLon != 0.0) {
            mapController.setCenter(GeoPoint(mapCenterLat, mapCenterLon))
        }

        val zoomToCurrentPositionOnFirstFix = geocodingResult == null && moveToCurrentLocation
        locationOverlay.runOnFirstFix {
            if (locationOverlay.myLocation != null) {
                myLocation = locationOverlay.myLocation
                if (zoomToCurrentPositionOnFirstFix) {
                    runOnUiThread {
                        mapController.setZoom(ZOOM_LEVEL_DEFAULT)
                        mapController.setCenter(myLocation)
                    }
                }
            } else {
                // locationOverlay.myLocation was null. might be an osmdroid bug?
                // However that seems to be okay because runOnFirstFix is called twice somehow and the second time
                // locationOverlay.myLocation is not null.
            }
        }

        geocodingResult?.let {
            if (it.lat != COORDINATE_ZERO && it.lon != COORDINATE_ZERO) {
                mapController.setCenter(GeoPoint(it.lat, it.lon))
            }
        }

        binding.centerMapButton.setOnClickListener {
            if (myLocation.latitude == COORDINATE_ZERO && myLocation.longitude == COORDINATE_ZERO) {
                Snackbar.make(
                    binding.root,
                    context.getString(R.string.nc_location_unknown),
                    Snackbar.LENGTH_LONG
                ).show()
            } else {
                mapController.animateTo(myLocation)
                moveToCurrentLocation = true
            }
        }

        binding.map.addMapListener(
            delayedMapListener()
        )
    }

    private fun delayedMapListener() =
        DelayedMapListener(
            object : MapListener {
                @Suppress("Detekt.TooGenericExceptionCaught")
                override fun onScroll(paramScrollEvent: ScrollEvent): Boolean {
                    try {
                        when {
                            moveToCurrentLocation -> {
                                setLocationDescription(isGpsLocation = true, isGeocodedResult = false)
                                moveToCurrentLocation = false
                            }

                            geocodingResult != null -> {
                                binding.shareLocation.isClickable = true
                                setLocationDescription(isGpsLocation = false, isGeocodedResult = true)
                                geocodingResult = null
                            }

                            else -> {
                                binding.shareLocation.isClickable = true
                                setLocationDescription(isGpsLocation = false, isGeocodedResult = false)
                            }
                        }
                    } catch (e: NullPointerException) {
                        Log.d(TAG, "UI already closed")
                    }

                    readyToShareLocation = true
                    return true
                }

                override fun onZoom(event: ZoomEvent): Boolean = false
            }
        )

    @Suppress("Detekt.TooGenericExceptionCaught")
    private fun requestLocationUpdates() {
        try {
            when {
                locationManager!!.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> {
                    locationManager!!.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        MIN_LOCATION_UPDATE_TIME,
                        MIN_LOCATION_UPDATE_DISTANCE,
                        this
                    )
                }

                locationManager!!.isProviderEnabled(LocationManager.GPS_PROVIDER) -> {
                    locationManager!!.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        MIN_LOCATION_UPDATE_TIME,
                        MIN_LOCATION_UPDATE_DISTANCE,
                        this
                    )
                    Log.d(TAG, "LocationManager.NETWORK_PROVIDER falling back to LocationManager.GPS_PROVIDER")
                }

                else -> {
                    Log.e(
                        TAG,
                        "Error requesting location updates. Probably this is a phone without google services" +
                            " and there is no alternative like UnifiedNlp installed. Furthermore no GPS is " +
                            "supported."
                    )
                    Snackbar.make(binding.root, context.getString(R.string.nc_location_unknown), Snackbar.LENGTH_LONG)
                        .show()
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Error when requesting location updates. Permissions may be missing.", e)
            Snackbar.make(binding.root, context.getString(R.string.nc_location_unknown), Snackbar.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error when requesting location updates.", e)
            Snackbar.make(binding.root, context.getString(R.string.nc_common_error_sorry), Snackbar.LENGTH_LONG).show()
        }
    }

    private fun setLocationDescription(isGpsLocation: Boolean, isGeocodedResult: Boolean) {
        when {
            isGpsLocation -> {
                binding.shareLocationDescription.text = context!!.getText(R.string.nc_share_current_location)
                binding.placeName.visibility = View.GONE
                binding.placeName.text = ""
            }

            isGeocodedResult -> {
                binding.shareLocationDescription.text = context!!.getText(R.string.nc_share_this_location)
                binding.placeName.visibility = View.VISIBLE
                binding.placeName.text = geocodingResult?.displayName
            }

            else -> {
                binding.shareLocationDescription.text = context!!.getText(R.string.nc_share_this_location)
                binding.placeName.visibility = View.GONE
                binding.placeName.text = ""
            }
        }
    }

    private fun shareLocation(selectedLat: Double?, selectedLon: Double?, locationName: String?) {
        if (selectedLat != null || selectedLon != null) {
            val name = locationName
            if (name.isNullOrEmpty()) {
                initGeocoder()
                searchPlaceNameForCoordinates(selectedLat!!, selectedLon!!)
            } else {
                executeShareLocation(selectedLat, selectedLon, locationName)
            }
        }
    }

    private fun executeShareLocation(selectedLat: Double?, selectedLon: Double?, locationName: String?) {
        binding.roundedImageView.visibility = View.GONE
        binding.sendingLocationProgressbar.visibility = View.VISIBLE

        val objectId = "geo:$selectedLat,$selectedLon"

        var locationNameToShare = locationName
        if (locationNameToShare.isNullOrBlank()) {
            locationNameToShare = resources.getString(R.string.nc_shared_location)
        }

        val metaData: String =
            "{\"type\":\"geo-location\",\"id\":\"geo:$selectedLat,$selectedLon\",\"latitude\":\"$selectedLat\"," +
                "\"longitude\":\"$selectedLon\",\"name\":\"$locationNameToShare\"}"

        val currentUser = currentUserProvider.currentUser.blockingGet()

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
                    finish()
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, "error when trying to share location", e)
                    Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
                    finish()
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    private fun isLocationPermissionsGranted(): Boolean {
        fun isCoarseLocationGranted(): Boolean =
            PermissionChecker.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PermissionChecker.PERMISSION_GRANTED

        fun isFineLocationGranted(): Boolean =
            PermissionChecker.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PermissionChecker.PERMISSION_GRANTED

        return isCoarseLocationGranted() && isFineLocationGranted()
    }

    private fun requestLocationPermissions() {
        requestPermissions(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            REQUEST_PERMISSIONS_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        fun areAllGranted(grantResults: IntArray): Boolean {
            grantResults.forEach {
                if (it == PackageManager.PERMISSION_DENIED) return false
            }
            return grantResults.isNotEmpty()
        }

        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE && areAllGranted(grantResults)) {
            initMap()
        } else {
            Snackbar.make(
                binding.root,
                context!!.getString(R.string.nc_location_permission_required),
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun initGeocoder() {
        val baseUrl = context!!.getString(R.string.osm_geocoder_url)
        val email = context!!.getString(R.string.osm_geocoder_contact)
        nominatimClient = TalkJsonNominatimClient(baseUrl, okHttpClient, email)
    }

    private fun searchPlaceNameForCoordinates(lat: Double, lon: Double): Boolean {
        CoroutineScope(Dispatchers.IO).launch {
            executeGeocodingRequest(lat, lon)
        }
        return true
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    private suspend fun executeGeocodingRequest(lat: Double, lon: Double) {
        var address: Address? = null
        try {
            address = nominatimClient!!.getAddress(lon, lat)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get geocoded addresses", e)
            Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
        }
        updateResultOnMainThread(lat, lon, address?.displayName)
    }

    private suspend fun updateResultOnMainThread(lat: Double, lon: Double, addressName: String?) {
        withContext(Dispatchers.Main) {
            executeShareLocation(lat, lon, addressName)
        }
    }

    override fun onLocationChanged(location: Location) {
        myLocation = GeoPoint(location)
    }

    @Deprecated("Deprecated. This callback will never be invoked on Android Q and above.")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        // empty
    }

    override fun onProviderEnabled(provider: String) {
        // empty
    }

    override fun onProviderDisabled(provider: String) {
        // empty
    }

    companion object {
        private val TAG = LocationPickerActivity::class.java.simpleName
        private const val REQUEST_PERMISSIONS_REQUEST_CODE = 1
        private const val PERSON_HOT_SPOT_X: Float = 20.0F
        private const val PERSON_HOT_SPOT_Y: Float = 20.0F
        private const val ZOOM_LEVEL_RECEIVED_RESULT: Double = 14.0
        private const val ZOOM_LEVEL_DEFAULT: Double = 14.0
        private const val COORDINATE_ZERO: Double = 0.0
        private const val MIN_LOCATION_UPDATE_TIME: Long = 30 * 1000L
        private const val MIN_LOCATION_UPDATE_DISTANCE: Float = 0f
    }
}
