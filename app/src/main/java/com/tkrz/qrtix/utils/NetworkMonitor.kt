package com.tkrz.qrtix.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _isConnected = MutableStateFlow(true)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { _isConnected.value = true }
        override fun onLost(network: Network) { _isConnected.value = false }
        override fun onUnavailable() { _isConnected.value = false }
    }
    
    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
        // Set initial state
        val activeNetwork = connectivityManager.activeNetworkInfo
        _isConnected.value = activeNetwork?.isConnectedOrConnecting == true
    }
    
    suspend fun checkConnectivity(): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (connectivityManager.activeNetworkInfo?.isConnectedOrConnecting != true) {
            _isConnected.value = false
            return@withContext false
        }
        
        try {
            val url = java.net.URL("https://www.google.com")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.connect()
            val isReachable = connection.responseCode == 200
            _isConnected.value = isReachable
            isReachable
        } catch (e: Exception) {
            _isConnected.value = false
            false
        }
    }
}
