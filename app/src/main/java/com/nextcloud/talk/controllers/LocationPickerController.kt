package com.nextcloud.talk.controllers

import android.Manifest
import android.app.SearchManager
import android.content.Context
import android.content.pm.PackageManager
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
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.cardview.widget.CardView
import androidx.core.content.PermissionChecker
import androidx.core.view.MenuItemCompat
import androidx.preference.PreferenceManager
import autodagger.AutoInjector
import butterknife.BindView
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.nextcloud.talk.R
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.controllers.base.BaseController
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import com.nextcloud.talk.utils.database.user.UserUtils
import com.nextcloud.talk.utils.preferences.AppPreferences
import fr.dudie.nominatim.client.JsonNominatimClient
import fr.dudie.nominatim.model.Address
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.http.client.HttpClient
import org.apache.http.conn.ClientConnectionManager
import org.apache.http.conn.scheme.Scheme
import org.apache.http.conn.scheme.SchemeRegistry
import org.apache.http.conn.ssl.SSLSocketFactory
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.impl.conn.SingleClientConnManager
import org.osmdroid.config.Configuration.getInstance
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class LocationPickerController(args: Bundle) :
    BaseController(args),
    SearchView.OnQueryTextListener,
    GeocodingController.GeocodingResultListener {

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var userUtils: UserUtils

    @Inject
    @JvmField
    var appPreferences: AppPreferences? = null

    @Inject
    @JvmField
    var context: Context? = null

    @BindView(R.id.map)
    @JvmField
    var map: MapView? = null

    @BindView(R.id.ic_center_map)
    @JvmField
    var btCenterMap: CardView? = null

    @BindView(R.id.share_location)
    @JvmField
    var shareLocation: LinearLayout? = null

    @BindView(R.id.place_name)
    @JvmField
    var placeName: TextView? = null

    @BindView(R.id.share_location_description)
    @JvmField
    var shareLocationDescription: TextView? = null

    var nominatimClient: JsonNominatimClient? = null

    var roomToken: String?

    var moveToCurrentLocationWasClicked: Boolean = true
    var readyToShareLocation: Boolean = false
    var searchItem: MenuItem? = null
    var searchView: SearchView? = null

    var receivedChosenGeocodingResult: Boolean = false
    var geocodedLat: Double = 0.0
    var geocodedLon: Double = 0.0
    var geocodedName: String = ""

    init {
        setHasOptionsMenu(true)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))

        roomToken = args.getString(KEY_ROOM_TOKEN)
    }

    override fun inflateView(inflater: LayoutInflater, container: ViewGroup): View {
        return inflater.inflate(R.layout.controller_location, container, false)
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        initMap()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_locationpicker, menu)
        searchItem = menu.findItem(R.id.location_action_search)
        initSearchView()
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        hideSearchBar()
        actionBar.setIcon(ColorDrawable(resources!!.getColor(android.R.color.transparent)))
        actionBar.title = context!!.getString(R.string.nc_share_location)
    }

    override fun onViewBound(view: View) {
        setLocationDescription(false, receivedChosenGeocodingResult)
        shareLocation?.isClickable = false
        shareLocation?.setOnClickListener {
            if (readyToShareLocation) {
                shareLocation(
                    map?.mapCenter?.latitude,
                    map?.mapCenter?.longitude,
                    placeName?.text.toString()
                )
            } else {
                Log.w(TAG, "readyToShareLocation was false while user tried to share location.")
            }
        }
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
            }
        }
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        if (!query.isNullOrEmpty()) {
            val bundle = Bundle()
            bundle.putString(BundleKeys.KEY_GEOCODING_QUERY, query)
            bundle.putString(BundleKeys.KEY_ROOM_TOKEN, roomToken)
            router.pushController(
                RouterTransaction.with(GeocodingController(bundle, this))
                    .pushChangeHandler(HorizontalChangeHandler())
                    .popChangeHandler(HorizontalChangeHandler())
            )
        }
        return true
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        return true
    }

    private fun initMap() {
        if (!isFineLocationPermissionGranted()) {
            requestFineLocationPermission()
        }

        map?.setTileSource(TileSourceFactory.MAPNIK)

        map?.onResume()

        val copyrightOverlay = CopyrightOverlay(context)
        map?.overlays?.add(copyrightOverlay)

        map?.setMultiTouchControls(true)
        map?.isTilesScaledToDpi = true

        val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), map)
        // locationOverlay.enableFollowLocation()
        locationOverlay.enableMyLocation()
        // locationOverlay.setPersonIcon(
        //     DisplayUtils.getBitmap(ResourcesCompat.getDrawable(resources!!, R.drawable.current_location_circle, null)))
        map?.overlays?.add(locationOverlay)

        val mapController = map?.controller

        if (receivedChosenGeocodingResult) {
            mapController?.setZoom(14.0)
        } else {
            mapController?.setZoom(12.0)
        }

        var myLocation: GeoPoint
        myLocation = GeoPoint(13.0, 52.0)

        var zoomToCurrentPositionAllowed = !receivedChosenGeocodingResult
        locationOverlay.runOnFirstFix {
            myLocation = locationOverlay.myLocation
            if (zoomToCurrentPositionAllowed) {
                activity!!.runOnUiThread {
                    mapController?.setZoom(12.0)
                    mapController?.setCenter(myLocation)
                }
            }
        }

        if (receivedChosenGeocodingResult && geocodedLat != 0.0 && geocodedLon != 0.0) {
            mapController?.setCenter(GeoPoint(geocodedLat, geocodedLon))
        }

        btCenterMap?.setOnClickListener {
            mapController?.animateTo(myLocation)
            moveToCurrentLocationWasClicked = true
        }

        map?.addMapListener(
            DelayedMapListener(
                object : MapListener {
                    override fun onScroll(paramScrollEvent: ScrollEvent): Boolean {
                        if (moveToCurrentLocationWasClicked) {
                            setLocationDescription(true, false)
                            moveToCurrentLocationWasClicked = false
                        } else if (receivedChosenGeocodingResult) {
                            shareLocation?.isClickable = true
                            setLocationDescription(false, true)
                            receivedChosenGeocodingResult = false
                        } else {
                            shareLocation?.isClickable = true
                            setLocationDescription(false, false)
                        }
                        readyToShareLocation = true
                        return true
                    }

                    override fun onZoom(event: ZoomEvent): Boolean {
                        return false
                    }
                })
        )
    }

    private fun setLocationDescription(isGpsLocation: Boolean, isGeocodedResult: Boolean) {
        when {
            isGpsLocation -> {
                shareLocationDescription?.text = context!!.getText(R.string.nc_share_current_location)
                placeName?.text = ""
            }
            isGeocodedResult -> {
                shareLocationDescription?.text = context!!.getText(R.string.nc_share_this_location)
                placeName?.text = geocodedName
            }
            else -> {
                shareLocationDescription?.text = context!!.getText(R.string.nc_share_this_location)
                placeName?.text = ""
            }
        }
    }

    private fun shareLocation(selectedLat: Double?, selectedLon: Double?, locationName: String?) {
        if (selectedLat != null || selectedLon != null) {

            var name = locationName
            if (name.isNullOrEmpty()) {
                initGeocoder()
                searchPlaceNameForCoordinates(selectedLat!!, selectedLon!!)
            } else {
                executeShareLocation(selectedLat, selectedLon, locationName)
            }
        }
    }

    private fun executeShareLocation(selectedLat: Double?, selectedLon: Double?, locationName: String?) {
        val objectId = "geo:$selectedLat,$selectedLon"
        val metaData: String =
            "{\"type\":\"geo-location\",\"id\":\"geo:$selectedLat,$selectedLon\",\"latitude\":\"$selectedLat\"," +
                "\"longitude\":\"$selectedLon\",\"name\":\"$locationName\"}"

        ncApi.sendLocation(
            ApiUtils.getCredentials(userUtils.currentUser?.username, userUtils.currentUser?.token),
            ApiUtils.getUrlToSendLocation(userUtils.currentUser?.baseUrl, roomToken),
            "geo-location",
            objectId,
            metaData
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<GenericOverall> {
                override fun onSubscribe(d: Disposable) {
                }

                override fun onNext(t: GenericOverall) {
                    router.popCurrentController()
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, "error when trying to share location", e)
                    Toast.makeText(context, R.string.nc_common_error_sorry, Toast.LENGTH_LONG).show()
                    router.popCurrentController()
                }

                override fun onComplete() {
                }
            })
    }

    private fun isFineLocationPermissionGranted(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (PermissionChecker.checkSelfPermission(
                    context!!,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PermissionChecker.PERMISSION_GRANTED
            ) {
                Log.d(TAG, "Permission is granted")
                return true
            } else {
                Log.d(TAG, "Permission is revoked")
                return false
            }
        } else {
            Log.d(TAG, "Permission is granted")
            return true
        }
    }

    private fun requestFineLocationPermission() {
        requestPermissions(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            ),
            REQUEST_PERMISSIONS_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE && grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initMap()
        } else {
            Toast.makeText(context, context!!.getString(R.string.nc_location_permission_required), Toast.LENGTH_LONG)
                .show()
        }
    }

    override fun receiveChosenGeocodingResult(lat: Double, lon: Double, name: String) {
        receivedChosenGeocodingResult = true
        geocodedLat = lat
        geocodedLon = lon
        geocodedName = name
    }

    private fun initGeocoder() {
        val registry = SchemeRegistry()
        registry.register(Scheme("https", SSLSocketFactory.getSocketFactory(), 443))
        val connexionManager: ClientConnectionManager = SingleClientConnManager(null, registry)
        val httpClient: HttpClient = DefaultHttpClient(connexionManager, null)
        val baseUrl = context!!.getString(R.string.osm_geocoder_url)
        val email = context!!.getString(R.string.osm_geocoder_contact)
        nominatimClient = JsonNominatimClient(baseUrl, httpClient, email)
    }

    private fun searchPlaceNameForCoordinates(lat: Double, lon: Double): Boolean {
        CoroutineScope(Dispatchers.IO).launch {
            executeGeocodingRequest(lat, lon)
        }
        return true
    }

    private suspend fun executeGeocodingRequest(lat: Double, lon: Double) {
        var address: Address? = null
        try {
            address = nominatimClient!!.getAddress(lon, lat)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get geocoded addresses", e)
            Toast.makeText(context, R.string.nc_common_error_sorry, Toast.LENGTH_LONG).show()
        }
        updateResultOnMainThread(lat, lon, address?.displayName)
    }

    private suspend fun updateResultOnMainThread(lat: Double, lon: Double, addressName: String?) {
        withContext(Dispatchers.Main) {
            executeShareLocation(lat, lon, addressName)
        }
    }

    companion object {
        private const val TAG = "LocationPicker"
        private const val REQUEST_PERMISSIONS_REQUEST_CODE = 1
    }
}
