package com.example.lumatutorium2

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.example.lumatutorium2.ui.theme.LumaTutorium2Theme
import java.util.Locale
import kotlin.math.pow
import kotlin.math.sqrt


class MainActivity : ComponentActivity() {
    private var str_accelData = mutableStateOf("\nNO DATA YET\n")
    private var str_gyrosData = mutableStateOf("\nNO DATA YET\n")
    private var str_lightData = mutableStateOf("\nNO DATA YET\n")
    private var str_magnetData = mutableStateOf("\nNO DATA YET\n")

    private val SENSOR_TYPES = listOf(
        Sensor.TYPE_ACCELEROMETER,
        Sensor.TYPE_GYROSCOPE,
        Sensor.TYPE_LIGHT,
        Sensor.TYPE_MAGNETIC_FIELD
    )
    private lateinit var sensorManager : SensorManager
    private var sensorListeners = mutableListOf<SensorEventListener>()

    private lateinit var locationManager : LocationManager
    private val locationProviders = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
    private var locationListenersAndData = mutableMapOf<String, Pair<LocationListener, MutableState<String>>>()

    private val locationPermissions = arrayOf(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private lateinit var locationPermissionRequest : ActivityResultLauncher<Array<String>>
    private var locationPermissionsGranted : Boolean = true

    // delayed loop stuff (nur für den "1 per sec" knopf aus der übung):
    private var handler = Handler() // deprecated af
    private var runDelayedSensorLoop : Boolean = true
    private var runnable : Runnable? = null

    @Composable
    private fun PrintSensorData(label: String, dataString: MutableState<String>?, modifier: Modifier = Modifier) {
        Text(
            text = buildAnnotatedString {
                withStyle(
                    style = SpanStyle(
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    append("$label:\n")
                }
                append("${dataString?.value}")
            },
            textAlign = TextAlign.Center,
            modifier = modifier.padding(horizontal = 20.dp, vertical = 10.dp)
        )
    }

    private fun getMagnitude(values : FloatArray): Float {
        var result : Float = 0.0f
        values.forEach {
            result += it.pow(2)
        }
        return sqrt(result)
    }

    private fun radToDeg(rad : Float): Double {
        return rad * 180 / Math.PI
    }

    private fun createListeners() {
        // Sensor Listeners anlegen (wichtig: 1 für jeden sensor_type, sonst unregelmäßige readouts)
        SENSOR_TYPES.forEach { _ ->
            sensorListeners.add(
                object : SensorEventListener {
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                        // kann leer sein, muss aber implementiert werden
                    }

                    override fun onSensorChanged(event: SensorEvent?) {
                        when (event?.sensor?.type) {
                            Sensor.TYPE_GYROSCOPE -> {
                                str_gyrosData.value =
                                    "X: %.2f deg/s\nY: %.2f deg/s\nZ: %.2f deg/s\nMag: %.2f deg/s".format(
                                        radToDeg(event.values[0]),
                                        radToDeg(event.values[1]),
                                        radToDeg(event.values[2]),
                                        radToDeg(getMagnitude(event.values))
                                    )
                            }
                            Sensor.TYPE_ACCELEROMETER -> {
                                str_accelData.value =
                                    "X: %.2f m/s²\nY: %.2f m/s²\nZ: %.2f m/s²\nMag: %.2f m/s²".format(
                                        event.values[0],
                                        event.values[1],
                                        event.values[2],
                                        getMagnitude(event.values)
                                    )
                            }
                            Sensor.TYPE_LIGHT -> {
                                str_lightData.value = "\n${event.values[0].toInt()} lx"
                            }
                            Sensor.TYPE_MAGNETIC_FIELD -> {
                                str_magnetData.value =
                                    "X: %.2f uT\nY: %.2f uT\nZ: %.2f uT".format(
                                        event.values[0],
                                        event.values[1],
                                        event.values[2]
                                    )
                            }
                        }

                        if(runDelayedSensorLoop) { // unregister nach 1 readout:
                            sensorManager.unregisterListener(this)
                        }
                    }
                }
            )
        }

        // Location Listeners & Data als dictionary mit ["provider"] = Pair<Listener, str_Data>
        locationProviders.forEach {
            val str_locData = mutableStateOf("NO DATA YET\n")

            locationListenersAndData[it] = Pair<LocationListener, MutableState<String>>(
                object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        //Log.d("Location", "Position:\nLatitude: ${location.latitude}\nLongitude: ${location.longitude}")
                        str_locData.value = "Lat: ${location.latitude}\nLong: ${location.longitude}"
                    }
                },
                str_locData)
        }

    }

    private fun registerSensorListeners(samplingPeriodUs : Int = SensorManager.SENSOR_DELAY_UI) {
        SENSOR_TYPES.forEachIndexed { idx, type ->
            sensorManager.registerListener(
                sensorListeners[idx],
                sensorManager.getDefaultSensor(type),
                samplingPeriodUs
            )
        }
    }

    private fun unregisterSensorListeners() {
        sensorListeners.forEach {
            sensorManager.unregisterListener(it)
        }
    }

    // Nur für Tutorium Aufgabe, in jeder echten App überflüssig:
    // .registerListener(.., samplingPeriodUs) wird für Werte >200ms scheinbar ignoriert
    // und das selbe postDelayed skript im Listener.onSensorChanged läuft auch nicht :(
    // Also workaround mit delayed register & unregister (im Listener) nach dem ersten readout:
    private fun startDelayedSensorLoop(delayMillis : Long = 1000L) {
        runnable = object : Runnable {
            override fun run() {
                registerSensorListeners()
                handler.postDelayed(this, delayMillis) // registriere 1 mal pro sekunde neu
            }
        }
        handler.post(runnable as Runnable)
    }

    private fun stopDelayedSensorLoop() {
        runnable?.let { handler.removeCallbacks(it) }
    }

    private fun checkLocationPermissions(): Int {
        locationPermissions.forEach {
            if (ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_DENIED) {
                return PackageManager.PERMISSION_DENIED
            }
        }
        return PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")  // wird geregelt, Android studio kackt unnötig rum
    private fun registerLocationListeners(minTimeMs : Long = 1000L) {
        if(locationPermissionsGranted) {
            // registriere location listener für GPS & NETWORK:
            locationListenersAndData.forEach { (provider, listenerAndData) ->
                listenerAndData.second.value = "Waiting 4 signal ...\n"
                locationManager.requestLocationUpdates(
                    provider,
                    minTimeMs,
                    0f,
                    listenerAndData.first
                )
            }
        }
    }

    private fun unregisterLocationListeners() {
        locationListenersAndData.forEach { (_, listenerAndData) ->
            locationManager.removeUpdates(listenerAndData.first)
        }
    }

    private fun stopAllReadouts() {
        stopDelayedSensorLoop()
        unregisterSensorListeners()
        unregisterLocationListeners()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request Launcher definieren:
        locationPermissionRequest =
            registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { isGranted: Map<String, @JvmSuppressWildcards Boolean> ->
                if (isGranted.containsValue(true)) {
                    // Mindestens eine location permission erteilt (coarse oder fine)
                    locationPermissionsGranted = true
                    Log.i("LocPermissions", "Request: Location Permissions granted: $isGranted")
                } else {
                    // Keine permission erteilt
                    locationPermissionsGranted = false
                    locationListenersAndData.forEach { (_, listenerAndData) ->
                        listenerAndData.second.value =
                            "Standortzugriff verweigert\n(Änderbar in Einstellungen)"
                    }
                    Log.i("LocPermissions", "Request: Location Permissions denied.")
                }
            }

        // TODO shouldShowRequestPermissionRationale(permission) abfragen
        // s. https://developer.android.com/training/permissions/requesting#allow-system-manage-request-code
        if(checkLocationPermissions() == PackageManager.PERMISSION_DENIED) {
            Log.i("LocPermissions", "Check: Location Permissions denied.")
            locationPermissionRequest.launch(locationPermissions)
        } else {
            Log.i("LocPermissions", "Check: Location Permissions granted.")
        }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        this.createListeners()

        enableEdgeToEdge()
        setContent {
            LumaTutorium2Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier.padding(innerPadding).fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // SENSOREN
                        Row() {
                            PrintSensorData("Accelerometer", str_accelData)
                            PrintSensorData("Gyroskop", str_gyrosData)
                        }
                        Row() {
                            PrintSensorData("Beleuchtung", str_lightData)
                            PrintSensorData("Magnetfeld", str_magnetData)
                        }

                        // LOCATIONS
                        locationListenersAndData.forEach { (provider, listenerAndData) ->
                            Row() {
                                PrintSensorData(
                                    "Position (${provider.uppercase(Locale.ROOT)})",
                                    listenerAndData.second
                                )
                            }
                        }

                        // BUTTONS
                        Row(Modifier.padding(top = 30.dp)) {
                            Button(
                                content = { Text("Fast AF") },
                                modifier = Modifier.padding(horizontal = 20.dp),
                                onClick = {
                                    stopAllReadouts()
                                    runDelayedSensorLoop = false
                                    registerSensorListeners()
                                    registerLocationListeners(0L)
                                }
                            )
                            Button(
                                content = { Text("1 per Sec") },
                                modifier = Modifier.padding(horizontal = 20.dp),
                                onClick = {
                                    stopAllReadouts()
                                    runDelayedSensorLoop = true
                                    startDelayedSensorLoop(1000L)
                                    registerLocationListeners(1000L)
                                }
                            )
                        }
                        Row() {
                            Button(
                                content = { Text("HALT STOP") },
                                modifier = Modifier.padding(vertical = 20.dp),
                                onClick = {
                                    stopAllReadouts()
                                    str_accelData.value = "\nSTOPPED\n"
                                    str_gyrosData.value = "\nSTOPPED\n"
                                    str_lightData.value = "\nSTOPPED\n"
                                    str_magnetData.value = "\nSTOPPED\n"

                                    if(locationPermissionsGranted) {
                                        locationListenersAndData.forEach { (_, listenerAndData) ->
                                            listenerAndData.second.value = "STOPPED\n"
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}