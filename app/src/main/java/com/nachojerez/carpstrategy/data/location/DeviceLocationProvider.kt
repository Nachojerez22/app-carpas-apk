package com.nachojerez.carpstrategy.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

sealed interface LocationResult {
    data class Found(val point: GeoPoint) : LocationResult
    data object PermissionDenied : LocationResult
    data object Unavailable : LocationResult
}

/**
 * Ubicación puntual del móvil (FusedLocationProviderClient) cuando el usuario la pide.
 * Nunca se sigue en segundo plano y no sale del dispositivo (CONOCIMIENTO.md §0.7).
 */
@Singleton
class DeviceLocationProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    fun hasPermission(): Boolean = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    @SuppressLint("MissingPermission") // comprobado en hasPermission()
    suspend fun current(): LocationResult {
        if (!hasPermission()) return LocationResult.PermissionDenied
        return suspendCancellableCoroutine { cont ->
            val token = CancellationTokenSource()
            cont.invokeOnCancellation { token.cancel() }
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, token.token)
                .addOnSuccessListener { location ->
                    cont.resume(location?.let { LocationResult.Found(GeoPoint(it.latitude, it.longitude)) } ?: LocationResult.Unavailable)
                }
                .addOnFailureListener { cont.resume(LocationResult.Unavailable) }
        }
    }
}
