/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2021-2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:Suppress("TooManyFunctions")

package com.nextcloud.talk.location.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.PermissionChecker
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nextcloud.talk.R
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.viewmodels.LocationPickerViewModel
import kotlinx.coroutines.launch
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController.Visibility
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

private const val PERSON_HOT_SPOT_X: Float = 0.5F
private const val PERSON_HOT_SPOT_Y: Float = 0.5F
private const val ZOOM_LEVEL_RECEIVED_RESULT: Double = 14.0
private const val ZOOM_LEVEL_DEFAULT: Double = 14.0
private const val COORDINATE_ZERO: Double = 0.0
private const val MIN_LOCATION_UPDATE_TIME: Long = 30 * 1000L
private const val MIN_LOCATION_UPDATE_DISTANCE: Float = 0f
private const val PIN_HEIGHT_DP = 50
private const val TAG = "LocationPickerScreen"

@Suppress("Detekt.LongMethod", "Detekt.LongParameterList")
@Composable
fun LocationPickerScreen(
    viewModel: LocationPickerViewModel,
    roomToken: String,
    chatApiVersion: Int,
    onSearchClick: () -> Unit,
    onBack: () -> Unit,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var myLocation by remember { mutableStateOf(GeoPoint(COORDINATE_ZERO, COORDINATE_ZERO)) }

    val mapView = remember { MapView(context) }

    @SuppressLint("LocalContextResourcesRead")
    val locationOverlay = remember {
        MyLocationNewOverlay(GpsMyLocationProvider(context), mapView).apply {
            enableMyLocation()
            setPersonIcon(
                DisplayUtils.getBitmap(
                    ResourcesCompat.getDrawable(context.resources, R.drawable.current_location_circle, null)!!
                )
            )
            setPersonAnchor(PERSON_HOT_SPOT_X, PERSON_HOT_SPOT_Y)
        }
    }

    val locationManager = remember { context.getSystemService(Context.LOCATION_SERVICE) as LocationManager }

    val locationListener = remember {
        LocationListener { location -> myLocation = GeoPoint(location) }
    }

    @SuppressLint("LocalContextGetResourceValueCall")
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.values.all { it }) {
                requestLocationUpdates(locationManager, locationListener) { msgRes ->
                    coroutineScope.launch { snackbarHostState.showSnackbar(context.getString(msgRes)) }
                }
            } else {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.nc_location_permission_required))
                }
            }
        }

    val isDarkMode = isSystemInDarkTheme()
    LaunchedEffect(isDarkMode) {
        val colorFilter = if (isDarkMode) TilesOverlay.INVERT_COLORS else null
        mapView.overlayManager.tilesOverlay.setColorFilter(colorFilter)
    }

    LaunchedEffect(uiState.geocodingResult) {
        val result = uiState.geocodingResult ?: return@LaunchedEffect
        if (result.lat != COORDINATE_ZERO && result.lon != COORDINATE_ZERO) {
            mapView.controller.animateTo(GeoPoint(result.lat, result.lon))
            viewModel.updateMapCenter(result.lat, result.lon)
        }
    }

    @SuppressLint("LocalContextGetResourceValueCall")
    LaunchedEffect(uiState.viewState) {
        when (val state = uiState.viewState) {
            is LocationPickerViewModel.ViewState.LocationShared -> {
                viewModel.consumeViewState()
                onFinish()
            }

            is LocationPickerViewModel.ViewState.Error -> {
                viewModel.consumeViewState()
                snackbarHostState.showSnackbar(context.getString(state.messageRes))
            }

            else -> {}
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    mapView.onResume()
                    viewModel.setReadyToShareLocation(false)
                    val state = viewModel.uiState.value
                    viewModel.setLocationDescription(
                        isGpsLocation = false,
                        isGeocodedResult = state.geocodingResult != null
                    )
                }

                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> {
                    locationOverlay.disableMyLocation()
                    @Suppress("Detekt.TooGenericExceptionCaught")
                    try {
                        locationManager.removeUpdates(locationListener)
                    } catch (e: Exception) {
                        Log.e(TAG, "error when trying to remove updates for location Manager", e)
                    }
                }

                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    val sharedLocationFallbackName = stringResource(R.string.nc_shared_location)

    @SuppressLint("LocalContextGetResourceValueCall")
    LocationPickerScreenContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onSearchClick = onSearchClick,
        onBack = onBack,
        onShareClick = {
            viewModel.shareLocation(
                selectedLat = mapView.mapCenter?.latitude,
                selectedLon = mapView.mapCenter?.longitude,
                locationName = uiState.placeName.ifEmpty { null },
                sharedLocationFallbackName = sharedLocationFallbackName,
                roomToken = roomToken,
                chatApiVersion = chatApiVersion
            )
        },
        onCenterClick = {
            if (myLocation.latitude == COORDINATE_ZERO && myLocation.longitude == COORDINATE_ZERO) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.nc_location_unknown))
                }
            } else {
                mapView.controller.animateTo(myLocation)
                viewModel.setMoveToCurrentLocation(true)
            }
        },
        onZoomIn = { mapView.controller.zoomIn() },
        onZoomOut = { mapView.controller.zoomOut() },
        mapContent = {
            AndroidView(
                factory = { ctx ->
                    setupMapView(
                        mapView,
                        locationOverlay,
                        locationManager,
                        locationListener,
                        viewModel,
                        permissionLauncher,
                        ctx
                    )
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    )
}

@Suppress("Detekt.LongParameterList")
@Composable
private fun LocationPickerScreenContent(
    uiState: LocationPickerViewModel.UiState,
    snackbarHostState: SnackbarHostState,
    onSearchClick: () -> Unit,
    onBack: () -> Unit,
    onShareClick: () -> Unit,
    onCenterClick: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    mapContent: @Composable BoxScope.() -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            mapContent()
            LocationPickerMapOverlays()
            LocationPickerSearchCard(onBack = onBack, onSearchClick = onSearchClick)
            LocationPickerBottomControls(
                uiState = uiState,
                snackbarHostState = snackbarHostState,
                onShareClick = onShareClick,
                onCenterClick = onCenterClick,
                onZoomIn = onZoomIn,
                onZoomOut = onZoomOut
            )
        }
    }
}

@Composable
private fun BoxScope.LocationPickerSearchCard(onBack: () -> Unit, onSearchClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = Modifier
            .widthIn(min = 360.dp, max = 720.dp)
            .fillMaxWidth()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .align(Alignment.TopCenter),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.back_button)
                )
            }
            Text(
                text = stringResource(R.string.nc_search_location),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .wrapContentHeight(Alignment.CenterVertically)
                    .clickable(onClick = onSearchClick),
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = onSearchClick) {
                Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.nc_search))
            }
        }
    }
}

@Composable
private fun LocationPickerZoomControls(onZoomIn: () -> Unit, onZoomOut: () -> Unit, onCenterClick: () -> Unit) {
    val zoomColors = IconButtonDefaults.filledIconButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Column(
            modifier = Modifier.padding(end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledIconButton(onClick = onZoomIn, colors = zoomColors) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.nc_zoom_in))
            }
            FilledIconButton(onClick = onZoomOut, colors = zoomColors) {
                Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.nc_zoom_out))
            }
            FloatingActionButton(onClick = onCenterClick) {
                Icon(painter = painterResource(R.drawable.ic_baseline_gps_fixed_24), contentDescription = null)
            }
        }
    }
}

@Suppress("Detekt.LongParameterList")
@Composable
private fun BoxScope.LocationPickerBottomControls(
    uiState: LocationPickerViewModel.UiState,
    snackbarHostState: SnackbarHostState,
    onShareClick: () -> Unit,
    onCenterClick: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val zoomColors = IconButtonDefaults.filledIconButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Column(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LocationPickerSharePanel(
                    uiState = uiState,
                    onShareClick = onShareClick,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    modifier = Modifier.padding(start = 8.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledIconButton(onClick = onZoomIn, colors = zoomColors) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.nc_zoom_in))
                    }
                    FilledIconButton(onClick = onZoomOut, colors = zoomColors) {
                        Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.nc_zoom_out))
                    }
                    FloatingActionButton(onClick = onCenterClick) {
                        Icon(painter = painterResource(R.drawable.ic_baseline_gps_fixed_24), contentDescription = null)
                    }
                }
            }
        } else {
            LocationPickerZoomControls(onZoomIn = onZoomIn, onZoomOut = onZoomOut, onCenterClick = onCenterClick)
            LocationPickerSharePanel(uiState = uiState, onShareClick = onShareClick)
        }
        SnackbarHost(hostState = snackbarHostState)
    }
}

@Composable
private fun BoxScope.LocationPickerMapOverlays() {
    Image(
        painter = painterResource(R.drawable.ic_baseline_location_on_red_24),
        contentDescription = stringResource(R.string.nc_location_current_position_description),
        modifier = Modifier
            .size(width = 30.dp, height = PIN_HEIGHT_DP.dp)
            .align(Alignment.Center)
            .offset(y = (-(PIN_HEIGHT_DP / 2)).dp)
    )
}

@Suppress("LongMethod")
@Composable
private fun LocationPickerSharePanel(
    uiState: LocationPickerViewModel.UiState,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 0.dp)
                .clickable(enabled = uiState.readyToShareLocation, onClick = onShareClick),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (uiState.viewState) {
                    is LocationPickerViewModel.ViewState.SendingLocation -> {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(12.dp)
                                .size(48.dp)
                        )
                    }

                    else -> {
                        Image(
                            painter = painterResource(R.drawable.ic_circular_location),
                            contentDescription = null,
                            modifier = Modifier
                                .padding(12.dp)
                                .size(48.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 16.dp)
                ) {
                    val descriptionText = when (uiState.locationDescriptionType) {
                        LocationPickerViewModel.LocationDescriptionType.GPS ->
                            stringResource(R.string.nc_share_current_location)

                        else ->
                            stringResource(R.string.nc_share_this_location)
                    }
                    Text(
                        text = descriptionText,
                        fontSize = 16.sp,
                        color = colorResource(R.color.high_emphasis_text)
                    )
                    if (uiState.placeName.isNotEmpty()) {
                        Text(
                            text = uiState.placeName,
                            fontSize = 14.sp,
                            color = colorResource(R.color.medium_emphasis_text),
                            maxLines = 1
                        )
                    }
                }
            }
        }
        Text(
            text = stringResource(R.string.osm_map_view_attributation),
            fontSize = 12.sp,
            color = colorResource(R.color.medium_emphasis_text),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Suppress("Detekt.LongParameterList")
private fun setupMapView(
    mapView: MapView,
    locationOverlay: MyLocationNewOverlay,
    locationManager: LocationManager,
    locationListener: LocationListener,
    viewModel: LocationPickerViewModel,
    permissionLauncher: ActivityResultLauncher<Array<String>>,
    context: Context
): MapView {
    return mapView.apply {
        setTileSource(TileSourceFactory.MAPNIK)
        onResume()
        setMultiTouchControls(true)
        isTilesScaledToDpi = true
        overlays.add(locationOverlay)

        val mapController = controller
        val initialState = viewModel.uiState.value

        if (initialState.geocodingResult != null) {
            mapController.setZoom(ZOOM_LEVEL_RECEIVED_RESULT)
        } else {
            mapController.setZoom(ZOOM_LEVEL_DEFAULT)
        }

        if (initialState.mapCenterLat != COORDINATE_ZERO && initialState.mapCenterLon != COORDINATE_ZERO) {
            mapController.setCenter(GeoPoint(initialState.mapCenterLat, initialState.mapCenterLon))
            viewModel.updateMapCenter(initialState.mapCenterLat, initialState.mapCenterLon)
        }

        val zoomToCurrentPositionOnFirstFix =
            initialState.geocodingResult == null && initialState.moveToCurrentLocation

        locationOverlay.runOnFirstFix {
            val loc = locationOverlay.myLocation ?: return@runOnFirstFix
            if (zoomToCurrentPositionOnFirstFix) {
                Handler(Looper.getMainLooper()).post {
                    mapController.setZoom(ZOOM_LEVEL_DEFAULT)
                    mapController.setCenter(loc)
                    viewModel.updateMapCenter(loc.latitude, loc.longitude)
                }
            }
        }

        initialState.geocodingResult?.let {
            if (it.lat != COORDINATE_ZERO && it.lon != COORDINATE_ZERO) {
                mapController.setCenter(GeoPoint(it.lat, it.lon))
                viewModel.updateMapCenter(it.lat, it.lon)
            }
        }

        if (isLocationPermissionsGranted(context)) {
            requestLocationUpdates(locationManager, locationListener) { /* permissions already granted */ }
        } else {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }

        addMapListener(
            DelayedMapListener(
                object : MapListener {
                    @Suppress("Detekt.TooGenericExceptionCaught")
                    override fun onScroll(event: ScrollEvent): Boolean {
                        try {
                            viewModel.onMapScrolled()
                            viewModel.updateMapCenter(mapCenter.latitude, mapCenter.longitude)
                        } catch (_: NullPointerException) {
                            Log.d(TAG, "UI already closed")
                        }
                        return true
                    }

                    override fun onZoom(event: ZoomEvent): Boolean = false
                }
            )
        )

        zoomController.setVisibility(Visibility.NEVER)
    }
}

private fun isLocationPermissionsGranted(context: Context): Boolean =
    PermissionChecker.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PermissionChecker.PERMISSION_GRANTED &&
        PermissionChecker.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PermissionChecker.PERMISSION_GRANTED

@Suppress("Detekt.TooGenericExceptionCaught")
private fun requestLocationUpdates(
    locationManager: LocationManager,
    locationListener: LocationListener,
    onError: (Int) -> Unit
) {
    try {
        when {
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    MIN_LOCATION_UPDATE_TIME,
                    MIN_LOCATION_UPDATE_DISTANCE,
                    locationListener,
                    Looper.getMainLooper()
                )
            }

            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    MIN_LOCATION_UPDATE_TIME,
                    MIN_LOCATION_UPDATE_DISTANCE,
                    locationListener,
                    Looper.getMainLooper()
                )
                Log.d(TAG, "LocationManager.NETWORK_PROVIDER falling back to LocationManager.GPS_PROVIDER")
            }

            else -> {
                Log.e(
                    TAG,
                    "Error requesting location updates. Probably this is a phone without google services" +
                        " and there is no alternative like UnifiedNlp installed. Furthermore no GPS is supported."
                )
                onError(R.string.nc_location_unknown)
            }
        }
    } catch (e: SecurityException) {
        Log.e(TAG, "Error when requesting location updates. Permissions may be missing.", e)
        onError(R.string.nc_location_unknown)
    } catch (e: Exception) {
        Log.e(TAG, "Error when requesting location updates.", e)
        onError(R.string.nc_common_error_sorry)
    }
}

private val previewMapPlaceholder: @Composable BoxScope.() -> Unit = {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Gray),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "Map Preview")
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewLocationPickerScreen() {
    MaterialTheme {
        LocationPickerScreenContent(
            uiState = LocationPickerViewModel.UiState(
                locationDescriptionType = LocationPickerViewModel.LocationDescriptionType.GPS,
                readyToShareLocation = true
            ),
            snackbarHostState = SnackbarHostState(),
            onSearchClick = {},
            onBack = {},
            onShareClick = {},
            onCenterClick = {},
            onZoomIn = {},
            onZoomOut = {},
            mapContent = previewMapPlaceholder
        )
    }
}

@Preview(
    name = "Landscape",
    showBackground = true,
    device = "spec:width=891dp,height=411dp,dpi=420,orientation=landscape"
)
@Composable
private fun PreviewLocationPickerScreenLandscape() {
    MaterialTheme {
        LocationPickerScreenContent(
            uiState = LocationPickerViewModel.UiState(
                locationDescriptionType = LocationPickerViewModel.LocationDescriptionType.GPS,
                readyToShareLocation = true
            ),
            snackbarHostState = SnackbarHostState(),
            onSearchClick = {},
            onBack = {},
            onShareClick = {},
            onCenterClick = {},
            onZoomIn = {},
            onZoomOut = {},
            mapContent = previewMapPlaceholder
        )
    }
}

@Preview(name = "Geocoded", showBackground = true)
@Composable
private fun PreviewLocationPickerScreenGeocoded() {
    MaterialTheme {
        LocationPickerScreenContent(
            uiState = LocationPickerViewModel.UiState(
                locationDescriptionType = LocationPickerViewModel.LocationDescriptionType.GEOCODED,
                placeName = "Brandenburger Tor, Mitte, Berlin",
                readyToShareLocation = true
            ),
            snackbarHostState = SnackbarHostState(),
            onSearchClick = {},
            onBack = {},
            onShareClick = {},
            onCenterClick = {},
            onZoomIn = {},
            onZoomOut = {},
            mapContent = previewMapPlaceholder
        )
    }
}

@Preview(name = "Sending", showBackground = true)
@Composable
private fun PreviewLocationPickerScreenSending() {
    MaterialTheme {
        LocationPickerScreenContent(
            uiState = LocationPickerViewModel.UiState(
                locationDescriptionType = LocationPickerViewModel.LocationDescriptionType.GPS,
                readyToShareLocation = true,
                viewState = LocationPickerViewModel.ViewState.SendingLocation
            ),
            snackbarHostState = SnackbarHostState(),
            onSearchClick = {},
            onBack = {},
            onShareClick = {},
            onCenterClick = {},
            onZoomIn = {},
            onZoomOut = {},
            mapContent = previewMapPlaceholder
        )
    }
}

@Preview(name = "RTL - Arabic", showBackground = true, locale = "ar")
@Composable
private fun PreviewLocationPickerScreenRtl() {
    MaterialTheme {
        LocationPickerScreenContent(
            uiState = LocationPickerViewModel.UiState(
                locationDescriptionType = LocationPickerViewModel.LocationDescriptionType.GEOCODED,
                placeName = "برج خليفة، دبي، الإمارات العربية المتحدة",
                readyToShareLocation = true
            ),
            snackbarHostState = SnackbarHostState(),
            onSearchClick = {},
            onBack = {},
            onShareClick = {},
            onCenterClick = {},
            onZoomIn = {},
            onZoomOut = {},
            mapContent = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Gray),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "معاينة الخريطة")
                }
            }
        )
    }
}

@Preview(name = "Map Overlays - Light", showBackground = true)
@Preview(name = "Map Overlays - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewLocationPickerMapOverlays() {
    MaterialTheme {
        Box(
            modifier = Modifier
                .size(300.dp)
                .background(Color.Gray)
        ) {
            LocationPickerMapOverlays()
        }
    }
}

@Preview(name = "Share Panel - GPS", showBackground = true)
@Preview(name = "Share Panel - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewLocationPickerSharePanelGps() {
    MaterialTheme {
        LocationPickerSharePanel(
            uiState = LocationPickerViewModel.UiState(
                locationDescriptionType = LocationPickerViewModel.LocationDescriptionType.GPS,
                readyToShareLocation = true
            ),
            onShareClick = {}
        )
    }
}

@Preview(name = "Share Panel - Geocoded", showBackground = true)
@Composable
private fun PreviewLocationPickerSharePanelGeocoded() {
    MaterialTheme {
        LocationPickerSharePanel(
            uiState = LocationPickerViewModel.UiState(
                locationDescriptionType = LocationPickerViewModel.LocationDescriptionType.GEOCODED,
                placeName = "Brandenburger Tor, Mitte, Berlin",
                readyToShareLocation = true
            ),
            onShareClick = {}
        )
    }
}

@Preview(name = "Share Panel - Sending", showBackground = true)
@Composable
private fun PreviewLocationPickerSharePanelSending() {
    MaterialTheme {
        LocationPickerSharePanel(
            uiState = LocationPickerViewModel.UiState(
                locationDescriptionType = LocationPickerViewModel.LocationDescriptionType.GPS,
                readyToShareLocation = true,
                viewState = LocationPickerViewModel.ViewState.SendingLocation
            ),
            onShareClick = {}
        )
    }
}

@Preview(name = "Share Panel - RTL Arabic", showBackground = true, locale = "ar")
@Composable
private fun PreviewLocationPickerSharePanelRtl() {
    MaterialTheme {
        LocationPickerSharePanel(
            uiState = LocationPickerViewModel.UiState(
                locationDescriptionType = LocationPickerViewModel.LocationDescriptionType.GEOCODED,
                placeName = "برج خليفة، دبي، الإمارات العربية المتحدة",
                readyToShareLocation = true
            ),
            onShareClick = {}
        )
    }
}
