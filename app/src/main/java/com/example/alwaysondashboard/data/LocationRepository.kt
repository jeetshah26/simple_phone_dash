package com.example.alwaysondashboard.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class LocationRepository(
    private val fusedLocationClient: FusedLocationProviderClient
) {

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Result<Location> = suspendCancellableCoroutine { cont ->
        val tokenSource = CancellationTokenSource()
        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            tokenSource.token
        ).addOnSuccessListener { location ->
            if (cont.isActive) {
                if (location != null) {
                    cont.resume(Result.success(location))
                } else {
                    // Fallback to last known location if current is unavailable
                    fusedLocationClient.lastLocation
                        .addOnSuccessListener { last ->
                            if (cont.isActive) {
                                if (last != null) {
                                    cont.resume(Result.success(last))
                                } else {
                                    cont.resume(Result.failure(IllegalStateException("Location unavailable")))
                                }
                            }
                        }
                        .addOnFailureListener { error ->
                            if (cont.isActive) cont.resume(Result.failure(error))
                        }
                }
            }
        }.addOnFailureListener { error ->
            if (cont.isActive) {
                cont.resume(Result.failure(error))
            }
        }

        cont.invokeOnCancellation { tokenSource.cancel() }
    }

    companion object {
        fun create(context: Context): LocationRepository {
            return LocationRepository(LocationServices.getFusedLocationProviderClient(context))
        }
    }
}
