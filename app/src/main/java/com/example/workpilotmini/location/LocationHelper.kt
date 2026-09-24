package com.example.workpilotmini.location

import android.Manifest
import android.content.Context
import android.location.Geocoder
import android.location.Location
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

class LocationHelper(private val context: Context) {

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED

    /** Fetches a single fresh GPS fix. Returns null if permission missing or location unavailable. */
    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    suspend fun getCurrentLocation(): Location? {
        if (!hasLocationPermission()) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()
        return runCatching { client.getCurrentLocation(request, null).await() }.getOrNull()
    }

    /** Reverse-geocodes [lat]/[lng] into a short human-readable line, e.g. "Gulshan, Dhaka".
     *  Prefers subLocality + locality/subAdminArea + adminArea so it reads like a short
     *  area name rather than a full postal address; falls back to the geocoder's own
     *  full address line if those specific fields aren't available. Returns null on any
     *  failure (no network, geocoder backend unavailable, no result, etc.) — callers must
     *  treat this as best-effort and fall back to showing raw coordinates.
     *
     *  Runs on Dispatchers.IO: the legacy synchronous Geocoder.getFromLocation call is a
     *  blocking network call and must never run on the main thread. */
    suspend fun getAddressFromLocation(lat: Double, lng: Double): String? = withContext(Dispatchers.IO) {
        runCatching {
            if (!Geocoder.isPresent()) return@runCatching null
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            val addr = addresses?.firstOrNull() ?: return@runCatching null
            val parts = listOfNotNull(
                addr.subLocality?.takeIf { it.isNotBlank() },
                (addr.locality ?: addr.subAdminArea)?.takeIf { it.isNotBlank() },
                addr.adminArea?.takeIf { it.isNotBlank() }
            )
            if (parts.isNotEmpty()) parts.joinToString(", ") else addr.getAddressLine(0)
        }.getOrNull()
    }
}