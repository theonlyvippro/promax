package com.theonlyvip.promax

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor

class TheOnlyVipVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.theonlyvip.promax.CONNECT"
        const val ACTION_DISCONNECT = "com.theonlyvip.promax.DISCONNECT"
        const val EXTRA_CONFIG_URI = "extra_config_uri"
    }

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val configUri = intent.getStringExtra(EXTRA_CONFIG_URI).orEmpty()
                establishTunnel(configUri)
            }
            ACTION_DISCONNECT -> {
                closeTunnel()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun establishTunnel(configUri: String) {
        closeTunnel()
        try {
            val builder = Builder()
                .setSession("TheOnlyVIP Pro Max")
                .addAddress("10.66.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                .setMtu(1400)

            vpnInterface = builder.establish()
        } catch (_: Exception) {
        }
    }

    private fun closeTunnel() {
        try {
            vpnInterface?.close()
        } catch (_: Exception) {
        } finally {
            vpnInterface = null
        }
    }

    override fun onDestroy() {
        closeTunnel()
        super.onDestroy()
    }
}
