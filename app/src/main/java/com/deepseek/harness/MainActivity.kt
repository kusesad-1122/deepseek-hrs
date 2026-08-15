package com.deepseek.harness

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.WebSettings
import android.webkit.WebView
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var retryButton: Button
    private val handler = Handler(Looper.getMainLooper())
    private var pollAttempts = 0

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val storagePermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        progress = findViewById(R.id.progress)
        statusText = findViewById(R.id.status)
        retryButton = findViewById(R.id.retry)

        setupWebView()
        retryButton.setOnClickListener { boot() }
        requestNotifPermission()
        boot()
    }

    private fun requestNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestStoragePermission()
    }

    private fun requestStoragePermission() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            for (p in listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )) {
                if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                    perms.add(p)
                }
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                val env = android.os.Environment
                if (!env.isExternalStorageManager()) {
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = android.net.Uri.parse("package:$packageName")
                    startActivity(intent)
                }
            } catch (_: Throwable) {}
        }
        if (perms.isNotEmpty()) {
            storagePermission.launch(perms.toTypedArray())
        }
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            textZoom = 85
        }
        WebView.setWebContentsDebuggingEnabled(true)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(cm: ConsoleMessage): Boolean {
                Log.d("WebConsole", "${cm.messageLevel()} ${cm.message()} @${cm.sourceId()}:${cm.lineNumber()}")
                return true
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return false
            }
            override fun onReceivedError(
                view: WebView?,
                request: android.webkit.WebResourceRequest?,
                error: WebResourceError?
            ) {
                Log.d("WebErr", "onReceivedError ${error?.errorCode} ${error?.description} ${request?.url}")
            }
            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                response: android.webkit.WebResourceResponse?
            ) {
                Log.d("WebErr", "onReceivedHttpError ${response?.statusCode} ${request?.url}")
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                view?.evaluateJavascript(
                    "document.body.style.zoom = 0.85;", null
                )
            }
        }
    }

    private fun boot() {
        retryButton.isEnabled = false
        progress.visibility = android.view.View.VISIBLE

        val rootfsReady = File(filesDir, "rootfs/opt/dsh/entry.sh").canExecute()
        val prootReady = File(applicationInfo.nativeLibraryDir, "libproot.so").exists()
        if (!rootfsReady || !prootReady) {
            statusText.setText(R.string.preparing)
            thread {
                val ok = RootfsExtractor.extract(this)
                handler.post {
                    if (ok) startDshAndWait() else showFailure()
                }
            }
        } else {
            startDshAndWait()
        }
    }

    private fun startDshAndWait() {
        statusText.setText(R.string.starting_dsh)
        val alreadyUp = try {
            val c = URL(DshService.URL).openConnection() as HttpURLConnection
            c.connectTimeout = 1500
            c.requestMethod = "GET"
            c.responseCode == 200
        } catch (_: Throwable) { false }
        if (!alreadyUp && !DshService.isRunning()) {
            val intent = Intent(this, DshService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent)
            else startService(intent)
        }
        pollAttempts = 0
        pollServer()
    }

    private fun pollServer() {
        thread {
            val ok = try {
                val conn = URL(DshService.URL).openConnection() as HttpURLConnection
                conn.connectTimeout = 2000
                conn.requestMethod = "GET"
                conn.responseCode == 200
            } catch (_: Throwable) { false }

            handler.post {
                if (ok) {
                    findViewById<android.view.View>(R.id.overlay).visibility = android.view.View.GONE
                    webView.loadUrl(DshService.URL)
                } else if (pollAttempts < 150) {
                    pollAttempts++
                    handler.postDelayed({ pollServer() }, 1000)
                } else {
                    showFailure()
                }
            }
        }
    }

    private fun showFailure() {
        progress.visibility = android.view.View.GONE
        statusText.setText(R.string.dsh_failed)
        retryButton.isEnabled = true
        Toast.makeText(this, R.string.dsh_failed, Toast.LENGTH_LONG).show()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
