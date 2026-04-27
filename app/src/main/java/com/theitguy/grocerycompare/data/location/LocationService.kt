package com.theitguy.grocerycompare.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.theitguy.grocerycompare.data.models.UserLocation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Handles GPS location retrieval and reverse geocoding.
 */
object LocationService {

    /**
     * Get the user's current location.
     * Returns null if permissions aren't granted or location unavailable.
     */
    suspend fun getCurrentLocation(context: Context): UserLocation? {
        if (!hasLocationPermission(context)) return null

        return try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)

            // Try last known location first (fast)
            val lastLocation = getLastKnownLocation(fusedClient, context)
            if (lastLocation != null) return lastLocation

            // Request a fresh location
            getFreshLocation(fusedClient, context)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressWarnings("MissingPermission")
    private suspend fun getLastKnownLocation(
        client: FusedLocationProviderClient,
        context: Context
    ): UserLocation? = suspendCancellableCoroutine { cont ->
        try {
            client.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        val userLoc = reverseGeocode(context, location.latitude, location.longitude)
                        cont.resume(userLoc)
                    } else {
                        cont.resume(null)
                    }
                }
                .addOnFailureListener {
                    cont.resume(null)
                }
        } catch (e: SecurityException) {
            cont.resume(null)
        }
    }

    @SuppressWarnings("MissingPermission")
    private suspend fun getFreshLocation(
        client: FusedLocationProviderClient,
        context: Context
    ): UserLocation? = suspendCancellableCoroutine { cont ->
        try {
            val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 5000)
                .setWaitForAccurateLocation(false)
                .setMaxUpdates(1)
                .setMaxUpdateDelayMillis(8000)
                .build()

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    client.removeLocationUpdates(this)
                    val location = result.lastLocation
                    if (location != null) {
                        val userLoc = reverseGeocode(context, location.latitude, location.longitude)
                        cont.resume(userLoc)
                    } else {
                        cont.resume(null)
                    }
                }
            }

            client.requestLocationUpdates(request, callback, Looper.getMainLooper())

            cont.invokeOnCancellation {
                client.removeLocationUpdates(callback)
            }
        } catch (e: SecurityException) {
            cont.resume(null)
        }
    }

    /**
     * Convert lat/lng to city, state, zip.
     */
    private fun reverseGeocode(context: Context, lat: Double, lng: Double): UserLocation {
        return try {
            @Suppress("DEPRECATION")
            val geocoder = Geocoder(context, Locale.US)
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (addresses != null && addresses.isNotEmpty()) {
                val addr = addresses[0]
                UserLocation(
                    latitude = lat,
                    longitude = lng,
                    city = addr.locality ?: addr.subAdminArea ?: "",
                    state = addr.adminArea ?: "",
                    zip = addr.postalCode ?: ""
                )
            } else {
                UserLocation(latitude = lat, longitude = lng)
            }
        } catch (e: Exception) {
            UserLocation(latitude = lat, longitude = lng)
        }
    }
}
