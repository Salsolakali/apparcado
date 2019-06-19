package com.example.apparcado

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.Location
import android.os.Bundle
import android.os.PersistableBundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import com.example.youtube.R
import com.mapbox.android.core.location.LocationEngine
import com.mapbox.android.core.location.LocationEngineListener
import com.mapbox.android.core.location.LocationEnginePriority
import com.mapbox.android.core.location.LocationEngineProvider
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.annotations.IconFactory
import com.mapbox.mapboxsdk.annotations.Marker
import com.mapbox.mapboxsdk.annotations.MarkerOptions
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.plugins.locationlayer.LocationLayerPlugin
import com.mapbox.mapboxsdk.plugins.locationlayer.modes.CameraMode
import com.mapbox.mapboxsdk.plugins.locationlayer.modes.RenderMode
import com.mapbox.services.android.navigation.ui.v5.route.NavigationMapRoute
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute
import retrofit2.Call
import retrofit2.Response

class MainActivity : AppCompatActivity(), PermissionsListener, LocationEngineListener {

    private lateinit var mapView: MapView
    private lateinit var map: MapboxMap
    private lateinit var permissionsManager: PermissionsManager
    private lateinit var originLocation: Location

    private lateinit var originPoint: Point
    private lateinit var clickedPoint: Point

    private var locationEngine: LocationEngine? = null
    private var locationLayerPlugin: LocationLayerPlugin? = null
    private var clickedPointMarker: Marker? = null
    private var navigationMapRoute: NavigationMapRoute? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Mapbox.getInstance(applicationContext, getString(R.string.access_token))
        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync{mapboxMap ->
            map = mapboxMap
            enableLocation()
            map.addOnMapClickListener {
                clickedPoint = Point.fromLngLat(it.longitude, it.latitude)
                displayIcon(clickedPoint, "green")
                getRoute(originPoint,clickedPoint)

            }
            originPoint = Point.fromLngLat(originLocation.longitude, originLocation.latitude)
        }
    }

    private fun getRoute(origin: Point, destination: Point) {
        NavigationRoute.builder()
            .accessToken(Mapbox.getAccessToken())
            .origin(origin)
            .destination(destination)
            .build()
            .getRoute(object : retrofit2.Callback<DirectionsResponse>{

                override fun onResponse(call: Call<DirectionsResponse>, response: Response<DirectionsResponse>) {
                    val routeResponse = response
                    val body = routeResponse.body()?: return
                    if(body.routes().count() == 0){
                        Toast.makeText(this@MainActivity, "No hay rutas disponibles hasta el coche", Toast.LENGTH_SHORT).show()
                    }

                    if(navigationMapRoute != null){
                        navigationMapRoute?.removeRoute()
                    }
                    else{
                        navigationMapRoute = NavigationMapRoute(null, mapView, map)
                    }

                    navigationMapRoute?.addRoute(body.routes().first())
                }

                override fun onFailure(call: Call<DirectionsResponse>, t: Throwable) {
                    Toast.makeText(this@MainActivity, "No hay rutas disponibles hasta el coche", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun displayIcon(it: Point?, color: String) {
        val itLatLng : LatLng? = it?.longitude()?.let { it1 -> LatLng(it?.latitude(), it1) }
        val iconFactory = IconFactory.getInstance(applicationContext)
        val icon = getIcon(iconFactory, color)

        if(color.equals("green")) {
            clickedPointMarker = map.addMarker(MarkerOptions().position(itLatLng).icon(icon))
        }
        else{
            val let = clickedPointMarker?.let {
                map.removeMarker(it)
            }
            clickedPointMarker = map.addMarker(MarkerOptions().position(itLatLng).icon(icon))
        }
    }

    fun getIcon(iconFactory: IconFactory, color:String): com.mapbox.mapboxsdk.annotations.Icon{
        var bitmap: Bitmap? = null
        when(color){
            "red" -> bitmap = getBitmap(R.drawable.ic_car_red)
            "green" -> bitmap = getBitmap(R.drawable.ic_car_green)
        }
        return iconFactory.fromBitmap(bitmap!!)
    }

    private fun getBitmap(drawableId: Int, desireWidth: Int? = null, desireHeight: Int? = null): Bitmap? {
        val drawable = AppCompatResources.getDrawable(applicationContext, drawableId) ?: return null
        val bitmap = Bitmap.createBitmap(
            desireWidth ?: drawable.intrinsicWidth,
            desireHeight ?: drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    override fun onExplanationNeeded(permissionsToExplain: MutableList<String>?) {
    }

    override fun onPermissionResult(granted: Boolean) {
        if(granted){
            enableLocation()
        }
    }

    private fun enableLocation() {
        if(PermissionsManager.areLocationPermissionsGranted(this)){
            initializeLocationEngine()
            initializeLocationLayer()
        }
        else{
            permissionsManager = PermissionsManager(this)
            permissionsManager.requestLocationPermissions(this)
        }
    }

    @SuppressLint("MissingPermission")
    private fun initializeLocationEngine() {
        locationEngine = LocationEngineProvider(this).obtainBestLocationEngineAvailable()
        locationEngine?.priority = LocationEnginePriority.HIGH_ACCURACY
        locationEngine?.activate()
        val lastLocation = locationEngine?.lastLocation
        if(lastLocation != null){
            originLocation= lastLocation
            setCameraPosition(lastLocation)
        }
        else{
            locationEngine?.addLocationEngineListener(this)
        }
    }

    private fun setCameraPosition(location: Location) {
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 40.0))
    }


    @SuppressLint("MissingPermission")
    private fun initializeLocationLayer() {
        locationLayerPlugin = LocationLayerPlugin(mapView, map,locationEngine)
        locationLayerPlugin?.setLocationLayerEnabled(true)
        locationLayerPlugin?.cameraMode = CameraMode.TRACKING
        locationLayerPlugin?.renderMode = RenderMode.COMPASS
    }

    override fun onLocationChanged(location: Location?) {
        location?.let {
            originLocation = location
            setCameraPosition(location)
        }
    }

    override fun onConnected() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    @SuppressLint("MissingPermission")
    override fun onStart() {
        super.onStart()
        if(PermissionsManager.areLocationPermissionsGranted(this)){
            locationEngine?.requestLocationUpdates()
            locationLayerPlugin?.onStart()
        }
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause(){
        super.onPause()
        mapView.onPause()
    }

    override fun onStop(){
        super.onStop()
        locationEngine?.removeLocationUpdates()
        locationLayerPlugin?.onStop()
        mapView.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
        locationEngine?.deactivate()
    }

    override fun onSaveInstanceState(outState: Bundle?, outPersistentState: PersistableBundle?) {
        super.onSaveInstanceState(outState, outPersistentState)
        if(outState != null){
            mapView.onSaveInstanceState(outState)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }


}
