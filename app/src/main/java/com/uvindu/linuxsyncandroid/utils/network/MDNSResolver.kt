package com.uvindu.linuxsyncandroid.utils.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress

class MDNSResolver {
    companion object {
        private const val TAG = "MDNSResolver"
        
        suspend fun resolveHostname(hostname: String): String? {
            return withContext(Dispatchers.IO) {
                try {
                    // Try to resolve mDNS hostname
                    // Format: hostname.local. (add .local. if not present)
                    val mdnsHostname = when {
                        hostname.endsWith(".local.") -> hostname
                        hostname.endsWith(".local") -> "$hostname."
                        hostname.contains(".") -> hostname // Already an IP
                        else -> "$hostname.local."
                    }
                    
                    Log.d(TAG, "Resolving hostname: $mdnsHostname")
                    
                    // Use InetAddress to resolve (supports mDNS on most Android devices)
                    val inetAddress = InetAddress.getByName(mdnsHostname)
                    val ip = inetAddress.hostAddress
                    
                    Log.d(TAG, "Resolved $mdnsHostname to $ip")
                    ip
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to resolve hostname $hostname: ${e.message}")
                    // Return null to fall back to original IP
                    null
                }
            }
        }
    }
}
