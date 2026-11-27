package com.theonlyvip.promax

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.VpnService
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {

    private var webView: WebView? = null
    private var pendingConfigUri: String = ""

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startVpnService(pendingConfigUri)
        } else {
            notifyJsVpnStatus("disconnected", "មិនបានអនុញ្ញាត VpnService")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            val trace = throwable.stackTrace
                .take(6)
                .joinToString("\n") { "  at ${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
            val text = "App Crash:\n${throwable.javaClass.simpleName}: ${throwable.message}\n$trace"
            runOnUiThread {
                showStatusScreen("TheOnlyVIP Pro Max — បញ្ហាដំណើរការ", text)
            }
        }

        try {
            val container = FrameLayout(this).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.parseColor("#060608"))
            }

            val hasIndex = try {
                assets.list("")?.contains("index.html") == true
            } catch (_: Throwable) {
                false
            }

            if (!hasIndex) {
                showStatusScreen(
                    "TheOnlyVIP Pro Max",
                    "មិនទាន់មានไฟล์ app/src/main/assets/index.html នៅក្នុងគម្រោងទេ។\n" +
                        "សូមបង្កើតไฟล์ app/src/main/assets/index.html នៅលើ GitHub រួច Build APK ថ្មី។"
                )
                return
            }

            val wv = WebView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.parseColor("#060608"))
            }

            wv.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }

            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    notifyJsDeviceReady()
                }
            }

            wv.addJavascriptInterface(VpnBridge(), "AndroidVPN")
            webView = wv
            container.addView(wv)
            setContentView(container)

            wv.loadUrl("file:///android_asset/index.html")
        } catch (ex: Throwable) {
            Log.e("TheOnlyVIPMain", "onCreate error", ex)
            showStatusScreen(
                "TheOnlyVIP Pro Max",
                "បញ្ហាក្នុងពេលបើកកម្មវិធី:\n${ex.javaClass.simpleName}: ${ex.message}"
            )
        }
    }

    private fun showStatusScreen(heading: String, detail: String) {
        try {
            val root = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(Color.parseColor("#060608"))
                setPadding(48, 48, 48, 48)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            val titleView = TextView(this).apply {
                text = heading
                setTextColor(Color.parseColor("#F59E0B"))
                textSize = 18f
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 24)
            }

            val bodyView = TextView(this).apply {
                text = detail
                setTextColor(Color.parseColor("#E4E4E7"))
                textSize = 12f
                gravity = Gravity.START
            }

            root.addView(titleView)
            root.addView(bodyView)
            setContentView(root)
        } catch (_: Throwable) {
        }
    }

    private fun requestOrStartVpn(configUri: String) {
        pendingConfigUri = configUri
        try {
            val prepareIntent = VpnService.prepare(this)
            if (prepareIntent != null) {
                notifyJsVpnStatus("connecting", "កំពុងស្នើសុំសិទ្ធិ VPN...")
                vpnPermissionLauncher.launch(prepareIntent)
            } else {
                startVpnService(configUri)
            }
        } catch (ex: Throwable) {
            Log.e("TheOnlyVIPMain", "prepare VPN failed", ex)
            notifyJsVpnStatus("disconnected", "មិនអាចស្នើសុំសិទ្ធិ VPN បាន")
        }
    }

    private fun startVpnService(configUri: String) {
        try {
            val intent = Intent(this, TheOnlyVipVpnService::class.java).apply {
                action = TheOnlyVipVpnService.ACTION_CONNECT
                putExtra(TheOnlyVipVpnService.EXTRA_CONFIG_URI, configUri)
            }
            startService(intent)
            notifyJsVpnStatus("connected", "បានភ្ជាប់ VPN Tunnel")
        } catch (ex: Throwable) {
            Log.e("TheOnlyVIPMain", "startVpnService failed", ex)
            notifyJsVpnStatus("disconnected", "បរាជ័យក្នុងការចាប់ផ្តើម Service")
        }
    }

    private fun stopVpnService() {
        try {
            val intent = Intent(this, TheOnlyVipVpnService::class.java).apply {
                action = TheOnlyVipVpnService.ACTION_DISCONNECT
            }
            startService(intent)
            notifyJsVpnStatus("disconnected", "បានផ្តាច់ VPN Tunnel")
        } catch (ex: Throwable) {
            Log.e("TheOnlyVIPMain", "stopVpnService failed", ex)
        }
    }

    private fun notifyJsVpnStatus(state: String, message: String) {
        val safeMsg = message.replace("\\", "\\\\").replace("'", "\\'")
        runOnUiThread {
            webView?.evaluateJavascript(
                "window.onNativeVpnStateChange && window.onNativeVpnStateChange('$state', '$safeMsg');",
                null
            )
        }
    }

    private fun notifyJsDeviceReady() {
        runOnUiThread {
            webView?.evaluateJavascript(
                "window.onNativeAppReady && window.onNativeAppReady('android');",
                null
            )
        }
    }

    override fun onDestroy() {
        try {
            webView?.stopLoading()
            webView?.destroy()
        } catch (_: Throwable) {
        }
        webView = null
        super.onDestroy()
    }

    inner class VpnBridge {
        @JavascriptInterface
        fun connectVpn(configUri: String) {
            runOnUiThread {
                requestOrStartVpn(configUri)
            }
        }

        @JavascriptInterface
        fun disconnectVpn() {
            runOnUiThread {
                stopVpnService()
            }
        }

        @JavascriptInterface
        fun isAndroidApp(): Boolean {
            return true
        }
    }
}
