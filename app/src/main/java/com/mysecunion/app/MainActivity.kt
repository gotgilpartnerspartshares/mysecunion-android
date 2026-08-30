package com.mysecunion.app

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.mysecunion.app.databinding.ActivityMainBinding
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import org.json.JSONArray
import java.io.File

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DEEP_LINK_URL = "deep_link_url" // FR-304
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var remoteConfig: FirebaseRemoteConfig

    private var allowedHosts: Set<String> = emptySet()
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraCaptureUri: Uri? = null
    private var retryAction: () -> Unit = {}

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchCameraCapture() else filePathCallback?.onReceiveValue(null)
        }

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = filePathCallback
            filePathCallback = null
            if (callback == null) return@registerForActivityResult

            val resultUris: Array<Uri>? = when {
                result.resultCode != RESULT_OK -> null
                result.data?.dataUris() != null -> result.data?.dataUris()
                cameraCaptureUri != null -> arrayOf(cameraCaptureUri!!)
                else -> null
            }
            callback.onReceiveValue(resultUris)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen() // FR-201
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        CookieManager.getInstance().setAcceptCookie(true) // FR-102: keep session cookies

        binding.btnRetry.setOnClickListener { retryAction() } // FR-109
        setupRemoteConfig()
        askNotificationPermission()
    }

    private fun setupRemoteConfig() {
        remoteConfig = Firebase.remoteConfig
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 3600
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.setDefaultsAsync(
            mutableMapOf<String, Any>(
                RemoteConfigKeys.BASE_URL to getString(R.string.default_base_url),
                RemoteConfigKeys.ALLOWED_HOSTS to """["${getString(R.string.default_allowed_host)}"]""",
                RemoteConfigKeys.MAINTENANCE_MODE to false,
                RemoteConfigKeys.MAINTENANCE_MESSAGE to "",
                RemoteConfigKeys.LATEST_VERSION to BuildConfig.VERSION_NAME,
                RemoteConfigKeys.MIN_SUPPORTED_VERSION to BuildConfig.VERSION_NAME,
            )
        )
        retryAction = { setupRemoteConfig() }
        remoteConfig.fetchAndActivate().addOnCompleteListener {
            // FR-401: fall back to last-known-good / built-in defaults on failure automatically
            // (Remote Config keeps last activated values; nothing extra needed here)
            onRemoteConfigReady()
        }
    }

    private fun onRemoteConfigReady() {
        allowedHosts = parseAllowedHosts(remoteConfig.getString(RemoteConfigKeys.ALLOWED_HOSTS))

        if (remoteConfig.getBoolean(RemoteConfigKeys.MAINTENANCE_MODE)) {
            showError(
                remoteConfig.getString(RemoteConfigKeys.MAINTENANCE_MESSAGE)
                    .ifBlank { getString(R.string.error_maintenance) } // FR-403
            )
            return
        }

        if (checkForcedUpdate()) return

        hideError()
        setupWebView()
        setupSwipeRefresh()
        binding.webView.loadUrl(resolveStartUrl())

        checkOptionalUpdate()
    }

    /** FR-304: open the pushed article URL if present and within the whitelist, else base_url. */
    private fun resolveStartUrl(): String {
        val deepLink = intent?.getStringExtra(EXTRA_DEEP_LINK_URL)
        if (deepLink != null && isAllowedHost(Uri.parse(deepLink))) return deepLink
        return remoteConfig.getString(RemoteConfigKeys.BASE_URL)
    }

    private fun parseAllowedHosts(json: String): Set<String> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it).lowercase() }.toSet()
        } catch (e: Exception) {
            setOf(getString(R.string.default_allowed_host))
        }
    }

    /** NFR-306: keep in-app navigation limited to the whitelist; everything else -> system apps. */
    private fun isAllowedHost(uri: Uri): Boolean {
        val host = uri.host?.lowercase() ?: return false
        return allowedHosts.any { host == it || host.endsWith(".$it") }
    }

    private fun setupWebView() {
        binding.webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.setSupportZoom(true) // FR-110
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.textZoom = 100 // FR-111: ignore system font scaling for layout stability

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val uri = request.url
                    return when (uri.scheme) {
                        "tel", "mailto" -> {
                            openExternally(uri); true // FR-106
                        }
                        "http", "https" -> {
                            if (isAllowedHost(uri)) {
                                false // let WebView load it
                            } else {
                                openExternally(uri); true // FR-106: outside whitelist -> browser
                            }
                        }
                        else -> {
                            openExternally(uri); true // FR-106: telegram etc. via system app
                        }
                    }
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    hideError()
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    binding.swipeRefreshLayout.isRefreshing = false
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        binding.swipeRefreshLayout.isRefreshing = false
                        retryAction = { binding.webView.reload() }
                        showError(getString(R.string.error_generic_message)) // FR-109
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                // FR-108: top loading progress
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    if (newProgress >= 100) {
                        binding.progressBar.visibility = android.view.View.GONE
                    } else {
                        binding.progressBar.visibility = android.view.View.VISIBLE
                        binding.progressBar.progress = newProgress
                    }
                }

                // FR-104: file upload (gallery + camera)
                override fun onShowFileChooser(
                    webView: WebView?,
                    callback: ValueCallback<Array<Uri>>,
                    params: FileChooserParams?
                ): Boolean {
                    filePathCallback = callback
                    val chooserIntent = buildChooserIntent()
                    fileChooserLauncher.launch(chooserIntent)
                    return true
                }
            }

            // FR-105: downloads with session cookie forwarded to DownloadManager
            setDownloadListener { url, _, contentDisposition, mimeType, _ ->
                downloadFile(url, contentDisposition, mimeType)
            }
        }
    }

    private fun buildChooserIntent(): Intent {
        val contentIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }

        val hasCamera = packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)
        if (hasCamera && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            prepareCameraCaptureUri()
        }

        val chooser = Intent.createChooser(contentIntent, "파일 선택")
        cameraCaptureUri?.let { uri ->
            val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, uri)
            }
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(captureIntent))
        } ?: run {
            if (hasCamera && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestCameraPermission.launch(Manifest.permission.CAMERA)
            }
        }
        return chooser
    }

    private fun prepareCameraCaptureUri() {
        val captureDir = File(cacheDir, "captures").apply { mkdirs() }
        val file = File(captureDir, "upload_${System.currentTimeMillis()}.jpg")
        cameraCaptureUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    }

    private fun launchCameraCapture() {
        prepareCameraCaptureUri()
        val uri = cameraCaptureUri ?: return filePathCallback?.onReceiveValue(null) ?: Unit
        val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
        }
        fileChooserLauncher.launch(captureIntent)
    }

    private fun Intent.dataUris(): Array<Uri>? = data?.let { arrayOf(it) }

    private fun downloadFile(url: String, contentDisposition: String?, mimeType: String?) {
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url))
            addRequestHeader("User-Agent", binding.webView.settings.userAgentString)
            setMimeType(mimeType)
            val fileName = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        }
        (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
    }

    private fun openExternally(uri: Uri) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }

    /** FR-503: block usage entirely below min_supported_version. */
    private fun checkForcedUpdate(): Boolean {
        val minVersion = remoteConfig.getString(RemoteConfigKeys.MIN_SUPPORTED_VERSION)
        if (minVersion.isBlank() || !VersionUtils.isLower(BuildConfig.VERSION_NAME, minVersion)) {
            return false
        }
        AlertDialog.Builder(this)
            .setTitle("업데이트 필요")
            .setMessage("필수 업데이트가 있습니다. 최신 버전으로 업데이트해야 계속 이용할 수 있습니다.")
            .setCancelable(false)
            .setPositiveButton("업데이트") { _, _ -> openApkUrl(); finish() }
            .show()
        return true
    }

    /** FR-501/502: optional update notice. */
    private fun checkOptionalUpdate() {
        val latest = remoteConfig.getString(RemoteConfigKeys.LATEST_VERSION)
        if (latest.isBlank() || !VersionUtils.isLower(BuildConfig.VERSION_NAME, latest)) return

        AlertDialog.Builder(this)
            .setTitle("새 버전 안내")
            .setMessage("새로운 버전($latest)이 있습니다. 업데이트하시겠습니까?")
            .setNegativeButton("나중에", null)
            .setPositiveButton("업데이트") { _, _ -> openApkUrl() }
            .show()
    }

    private fun openApkUrl() {
        val apkUrl = remoteConfig.getString(RemoteConfigKeys.APK_URL)
        if (apkUrl.isNotBlank()) openExternally(Uri.parse(apkUrl))
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            binding.webView.reload()
        }
    }

    private fun showError(message: String) {
        binding.progressBar.visibility = android.view.View.GONE
        binding.errorMessage.text = message
        binding.errorView.visibility = android.view.View.VISIBLE
    }

    private fun hideError() {
        binding.errorView.visibility = android.view.View.GONE
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onDestroy() {
        binding.webView.destroy()
        super.onDestroy()
    }
}
