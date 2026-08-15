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
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return false
            }
        }
    }

    private fun boot() {
        retryButton.isEnabled = false
        progress.visibility = android.view.View.VISIBLE

        val rootfsReady = File(filesDir, "rootfs/opt/dsh/entry.sh").exists()
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
        if (!DshService.isRunning()) {
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
                    progress.visibility = android.view.View.GONE
                    statusText.text = ""
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
