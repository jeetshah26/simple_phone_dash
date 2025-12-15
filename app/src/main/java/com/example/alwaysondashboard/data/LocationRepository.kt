package com.example.alwaysondashboard.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class LocationRepository(
    private val fusedLocationClient: FusedLocationProviderClient,
    private val locationManager: LocationManager
) {

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(forceFresh: Boolean = false): Result<Location> = suspendCancellableCoroutine { cont ->
        val tokenSource = CancellationTokenSource()
        fusedLocationClient.getCurrentLocation(
            if (forceFresh) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            tokenSource.token
        ).addOnSuccessListener { location ->
            if (cont.isActive) {
                if (location != null) {
                    cont.resume(Result.success(location))
                } else {
                    if (!forceFresh) {
                        // Fallback to last known location if current is unavailable
                        fusedLocationClient.lastLocation
                            .addOnSuccessListener { last ->
                                if (cont.isActive) {
                                    if (last != null) {
                                        cont.resume(Result.success(last))
                                    } else {
                                        tryLegacyLocation(cont, IllegalStateException("Location unavailable"), forceFresh)
                                    }
                                }
                            }
                            .addOnFailureListener { error ->
                                if (cont.isActive) tryLegacyLocation(cont, error, forceFresh)
                            }
                    } else {
                        tryLegacyLocation(cont, IllegalStateException("Location unavailable"), forceFresh)
                    }
                }
            }
        }.addOnFailureListener { error ->
            if (cont.isActive) {
                tryLegacyLocation(cont, error, forceFresh)
            }
        }

        cont.invokeOnCancellation { tokenSource.cancel() }
    }

    @SuppressLint("MissingPermission")
    private fun tryLegacyLocation(
        cont: kotlinx.coroutines.CancellableContinuation<Result<Location>>,
        originalError: Throwable? = null,
        forceFresh: Boolean = false
    ) {
        // On older devices (e.g., Android 6), fused can fail; fall back to LocationManager.
        val handler = Handler(Looper.getMainLooper())

        // Try last known first for a quick win.
        if (!forceFresh) {
            val lastKnown = sequenceOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .mapNotNull { provider ->
                    runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
                }
                .maxByOrNull { it.time }

            if (lastKnown != null && cont.isActive) {
                cont.resume(Result.success(lastKnown))
                return
            }
        }

        val criteria = Criteria().apply {
            accuracy = Criteria.ACCURACY_COARSE
            powerRequirement = Criteria.POWER_LOW
            isAltitudeRequired = false
            isBearingRequired = false
            isSpeedRequired = false
            isCostAllowed = false
        }
        val provider = locationManager.getBestProvider(criteria, true) ?: LocationManager.NETWORK_PROVIDER

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (cont.isActive) {
                    cont.resume(Result.success(location))
                }
                cleanup()
            }

            @Deprecated("Deprecated in API 29")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}

            private fun cleanup() {
                handler.removeCallbacksAndMessages(null)
                locationManager.removeUpdates(this)
            }
        }

        val timeout = Runnable {
            if (cont.isActive) {
                cont.resume(Result.failure(originalError ?: IllegalStateException("Location unavailable")))
            }
            locationManager.removeUpdates(listener)
        }

        handler.postDelayed(timeout, 12000L)
        try {
            locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
        } catch (t: Throwable) {
            handler.removeCallbacks(timeout)
            if (cont.isActive) {
                Log.w("LocationRepository", "Legacy provider failed: ${t.localizedMessage}")
                cont.resume(Result.failure(originalError ?: t))
            }
        }
    }

    companion object {
        fun create(context: Context): LocationRepository {
            return LocationRepository(
                LocationServices.getFusedLocationProviderClient(context),
                context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            )
        }
    }
}
