package com.nextcloud.talk.controllers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.content.PermissionChecker
import androidx.preference.PreferenceManager
import autodagger.AutoInjector
import butterknife.BindView
import com.nextcloud.talk.R
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.controllers.base.BaseController
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import com.nextcloud.talk.utils.database.user.UserUtils
import com.nextcloud.talk.utils.preferences.AppPreferences
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
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
class LocationController(args: Bundle) : BaseController(args) {

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

    @BindView(R.id.gps_accuracy)
    @JvmField
    var gpsAccuracy: TextView? = null

    @BindView(R.id.share_location_description)
    @JvmField
    var shareLocationDescription: TextView? = null

    var roomToken: String?

    var moveToCurrentLocationWasClicked: Boolean = true
    var readyToShareLocation: Boolean = false

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
        inflater.inflate(R.menu.menu_conversation_plus_filter, menu)
        // searchItem = menu.findItem(R.id.action_search)
        // initSearchView()
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        Log.d(TAG, "onPrepareOptionsMenu")
        hideSearchBar()
        actionBar.setIcon(ColorDrawable(resources!!.getColor(android.R.color.transparent)));
        actionBar.title = "Share location"

        // searchView = MenuItemCompat.getActionView(searchItem) as SearchView
        // showShareToScreen = !shareToScreenWasShown && hasActivityActionSendIntent()
        // if (showShareToScreen) {
        //     hideSearchBar()
        //     actionBar.setTitle(R.string.send_to_three_dots)
        // }
    }

    override fun onViewBound(view: View) {
        setCurrentLocationDescription()
        shareLocation?.isClickable = false
        shareLocation?.setOnClickListener {
            if(readyToShareLocation){
                shareLocation()
            } else {
                Log.d(TAG, "readyToShareLocation was false while user tried to share location.")
            }
        }
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
        mapController?.setZoom(12.0)

        var myLocation: GeoPoint
        myLocation = GeoPoint(52.0, 13.0)

        locationOverlay.runOnFirstFix(Runnable {
            activity!!.runOnUiThread {
                myLocation = locationOverlay.myLocation
                mapController?.setCenter(myLocation)
            }
        })

        btCenterMap?.setOnClickListener(View.OnClickListener {
            map?.controller?.animateTo(myLocation)
            setCurrentLocationDescription()
            moveToCurrentLocationWasClicked = true
        })

        map?.addMapListener(DelayedMapListener(object : MapListener {
            override fun onScroll(paramScrollEvent: ScrollEvent): Boolean {
                if (moveToCurrentLocationWasClicked) {
                    moveToCurrentLocationWasClicked = false
                } else {
                    shareLocation?.isClickable = true
                    shareLocationDescription?.text = "Share this location"
                    gpsAccuracy?.text = ""
                }
                readyToShareLocation = true
                return true
            }

            override fun onZoom(event: ZoomEvent): Boolean {
                return false
            }
        }))
    }

    private fun setCurrentLocationDescription() {
        shareLocationDescription?.text = "Share current location"
        gpsAccuracy?.text = "Accuracy: xx m"
    }

    private fun shareLocation() {
        val selectedLat: Double? = map?.mapCenter?.latitude
        val selectedLon: Double? = map?.mapCenter?.longitude
        val name = ""
        val objectId = "geo:$selectedLat,$selectedLon"
        val metaData: String =
            "{\"type\":\"geo-location\",\"id\":\"geo:$selectedLat,$selectedLon\",\"latitude\":\"$selectedLat\"," +
                "\"longitude\":\"$selectedLon\",\"name\":\"$name\"}"

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
                    Log.d(TAG, "shared location")
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
        } else { //permission is automatically granted on sdk<23 upon installation
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
            Toast.makeText(context, "location permission required!", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private val TAG = "LocationController"
        private val REQUEST_PERMISSIONS_REQUEST_CODE = 1;
    }
}
