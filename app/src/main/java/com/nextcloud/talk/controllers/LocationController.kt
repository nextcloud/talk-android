package com.nextcloud.talk.controllers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.content.PermissionChecker
import androidx.preference.PreferenceManager
import autodagger.AutoInjector
import butterknife.BindView
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.controllers.base.BaseController
import com.nextcloud.talk.utils.preferences.AppPreferences
import org.osmdroid.config.Configuration.getInstance
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
    var btCenterMap: ImageButton? = null

    init {
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context));
    }

    override fun inflateView(inflater: LayoutInflater, container: ViewGroup): View {
        return inflater.inflate(R.layout.controller_location, container, false)
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        drawMap()
    }

    fun drawMap(){
        if (!isFineLocationPermissionGranted()) {
            requestFineLocationPermission();
        }

        map?.setTileSource(TileSourceFactory.MAPNIK);

        map?.onResume();

        val copyrightOverlay = CopyrightOverlay(context);
        map?.overlays?.add(copyrightOverlay);

        map?.setMultiTouchControls(true);
        map?.isTilesScaledToDpi = true;

        val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), map);
        locationOverlay.enableFollowLocation();
        locationOverlay.enableMyLocation();
        map?.overlays?.add(locationOverlay)

        val mapController = map?.controller
        mapController?.setZoom(12.0)

        var myLocation: GeoPoint
        myLocation = GeoPoint(52.0 , 13.0)

        locationOverlay.runOnFirstFix(Runnable {
            activity!!.runOnUiThread {
                myLocation = locationOverlay.myLocation
                mapController?.setCenter(myLocation)
            }
        })

        btCenterMap?.setOnClickListener(View.OnClickListener {
            map?.controller?.animateTo(myLocation)
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
            drawMap()
        } else {
            Toast.makeText(context, "location permission required!", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private val TAG = "LocationController"
        private val REQUEST_PERMISSIONS_REQUEST_CODE = 1;
    }
}
