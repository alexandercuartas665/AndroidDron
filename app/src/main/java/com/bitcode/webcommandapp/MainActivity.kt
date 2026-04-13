package com.bitcode.webcommandapp

import android.annotation.SuppressLint
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.JavascriptInterface
import android.webkit.WebResourceResponse
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.SslErrorHandler
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.bitcode.webcommandapp.instruction.ScriptVariable
import com.bitcode.webcommandapp.instruction.SoapInstructionProvider
import com.bitcode.webcommandapp.motherdata.AdmDatos
import com.bitcode.webcommandapp.motherdata.servicedata.WebDatos
import com.google.android.material.tabs.TabLayout
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import android.net.http.SslError
import java.net.URLDecoder
import kotlin.math.max
import android.content.Context
import java.util.ArrayDeque

class MainActivity : AppCompatActivity() {

    private lateinit var tabLayout: TabLayout
    private lateinit var browserPage: View
    private lateinit var configPage: ScrollView
    private lateinit var recorderPage: ScrollView
    private lateinit var websocketPage: ScrollView

    private lateinit var webView: WebView
    private lateinit var signalRBridgeWebView: WebView
    private lateinit var urlInput: EditText
    private lateinit var scriptInput: EditText

    private lateinit var dbSoapUrlInput: EditText
    private lateinit var dbBaseInput: EditText
    private lateinit var dbTokenInput: EditText
    private lateinit var dbSqlInput: EditText
    private lateinit var providerClientTokenInput: EditText
    private lateinit var providerPasoInput: EditText
    private lateinit var serverPortInput: EditText
    private lateinit var btnServerToggle: Button

    private lateinit var tvServerInfo: TextView
    private lateinit var tvLog: TextView
    private lateinit var tvConfigSummary: TextView
    private lateinit var tvConfigResult: TextView
    private lateinit var tvRecorderStatus: TextView
    private lateinit var recorderScriptOutput: EditText
    private lateinit var wsServerUrlInput: EditText
    private lateinit var wsDroneIdInput: EditText
    private lateinit var wsStatusPayloadInput: EditText
    private lateinit var tvWsState: TextView
    private lateinit var tvWsDroneInfo: TextView
    private lateinit var tvWsLog: TextView
    private lateinit var btnWsConnect: Button
    private lateinit var btnWsDisconnect: Button
    private lateinit var btnWsSendStatus: Button

    private var server: RemoteControlServer? = null
    private var currentServerPort: Int? = null
    private val admDatos = AdmDatos()
    private val dbExecutor = Executors.newSingleThreadExecutor()
    private val recordedTouchSteps = mutableListOf<TouchStep>()
    
    // Extracted handler classes for refactored code
    private lateinit var droneInstructionHandler: DroneInstructionHandler
    private lateinit var signalRBridgeBuilder: SignalRBridgeBuilder

    @Volatile
    private var currentUrl: String = ""
    @Volatile
    private var pendingNavigationLatch: CountDownLatch? = null
    @Volatile
    private var pendingNavigationError: String = ""
    @Volatile
    private var pendingNavigationFinishedUrl: String = ""
    @Volatile
    private var isTouchRecording: Boolean = false
    @Volatile
    private var lastTouchEventTs: Long = 0L
    @Volatile
    private var wsBridgeReady: Boolean = false
    @Volatile
    private var wsConnected: Boolean = false
    @Volatile
    private var wsPendingConnect: Boolean = false
    @Volatile
    private var wsCurrentServerUrl: String = ""
    @Volatile
    private var wsCurrentDroneId: String = ""
    @Volatile
    private var wsPendingCorrelationId: String = ""
    @Volatile
    private var wsAutoReconnectEnabled: Boolean = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val timestampFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    private var isLoadingConfig = false
    private val autoSaveRunnable = Runnable { persistConfig(showMessage = false) }
    private val wsLogLines = ArrayDeque<String>()
    private val wsReconnectRunnable = Runnable {
        if (wsAutoReconnectEnabled && wsCurrentServerUrl.isNotBlank() && wsCurrentDroneId.isNotBlank()) {
            connectDroneSignalR(showToast = false, reconnect = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        
        // Initialize refactored handler classes
        signalRBridgeBuilder = SignalRBridgeBuilder()
        droneInstructionHandler = DroneInstructionHandler()
        
        setupTabs()
        setupWebView()
        setupSignalRBridgeWebView()
        setupUiActions()
        setupBackNavigation()
        loadConfigFromPrefs()
        setupConfigAutoSave()

        startRemoteServer()
        navigateTo("https://www.google.com")
        appendLog("App iniciada")
    }

    private fun bindViews() {
        tabLayout = findViewById(R.id.tabLayout)
        browserPage = findViewById(R.id.browserPage)
        configPage = findViewById(R.id.configPage)
        recorderPage = findViewById(R.id.recorderPage)
        websocketPage = findViewById(R.id.websocketPage)

        webView = findViewById(R.id.webView)
        signalRBridgeWebView = findViewById(R.id.signalRBridgeWebView)
        urlInput = findViewById(R.id.urlInput)
        scriptInput = findViewById(R.id.scriptInput)

        dbSoapUrlInput = findViewById(R.id.dbSoapUrlInput)
        dbBaseInput = findViewById(R.id.dbBaseInput)
        dbTokenInput = findViewById(R.id.dbTokenInput)
        dbSqlInput = findViewById(R.id.dbSqlInput)
        providerClientTokenInput = findViewById(R.id.providerClientTokenInput)
        providerPasoInput = findViewById(R.id.providerPasoInput)
        serverPortInput = findViewById(R.id.serverPortInput)
        btnServerToggle = findViewById(R.id.btnServerToggle)

        tvServerInfo = findViewById(R.id.tvServerInfo)
        tvLog = findViewById(R.id.tvLog)
        tvConfigSummary = findViewById(R.id.tvConfigSummary)
        tvConfigResult = findViewById(R.id.tvConfigResult)
        tvRecorderStatus = findViewById(R.id.tvRecorderStatus)
        recorderScriptOutput = findViewById(R.id.recorderScriptOutput)
        wsServerUrlInput = findViewById(R.id.wsServerUrlInput)
        wsDroneIdInput = findViewById(R.id.wsDroneIdInput)
        wsStatusPayloadInput = findViewById(R.id.wsStatusPayloadInput)
        tvWsState = findViewById(R.id.tvWsState)
        tvWsDroneInfo = findViewById(R.id.tvWsDroneInfo)
        tvWsLog = findViewById(R.id.tvWsLog)
        btnWsConnect = findViewById(R.id.btnWsConnect)
        btnWsDisconnect = findViewById(R.id.btnWsDisconnect)
        btnWsSendStatus = findViewById(R.id.btnWsSendStatus)

        tvLog.movementMethod = ScrollingMovementMethod()
        tvWsLog.movementMethod = ScrollingMovementMethod()
    }

    private fun setupTabs() {
        showTab(0)
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                showTab(tab.position)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    private fun showTab(position: Int) {
        browserPage.visibility = if (position == 0) View.VISIBLE else View.GONE
        configPage.visibility = if (position == 1) View.VISIBLE else View.GONE
        recorderPage.visibility = if (position == 2) View.VISIBLE else View.GONE
        websocketPage.visibility = if (position == 3) View.VISIBLE else View.GONE
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.loadsImagesAutomatically = true
        webView.addJavascriptInterface(TouchRecorderBridge(), "AndroidTouchRecorder")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val rawUrl = request?.url?.toString().orEmpty()
                if (rawUrl.isBlank()) return false

                if (rawUrl.startsWith("intent://", ignoreCase = true)) {
                    val fallback = extractIntentFallbackUrl(rawUrl)
                    if (fallback.isNotBlank()) {
                        appendLog("Intent detectado, usando fallback web")
                        mainHandler.post { webView.loadUrl(fallback) }
                    } else {
                        appendLog("Intent sin fallback bloqueado")
                    }
                    return true
                }

                if (!rawUrl.startsWith("http://", ignoreCase = true) &&
                    !rawUrl.startsWith("https://", ignoreCase = true)
                ) {
                    appendLog("Esquema no soportado bloqueado: $rawUrl")
                    return true
                }

                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val finalUrl = url.orEmpty()
                if (finalUrl.startsWith("intent://", ignoreCase = true)) {
                    appendLog("Intent intermedio detectado, esperando fallback")
                    return
                }
                currentUrl = finalUrl
                pendingNavigationFinishedUrl = finalUrl
                urlInput.setText(finalUrl)
                Log.i("WebCommandApp", "WebView onPageFinished url=$finalUrl")
                appendLog("Navegacion OK: $finalUrl")
                pendingNavigationLatch?.countDown()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    val failedUrl = request.url?.toString().orEmpty()
                    if (failedUrl.startsWith("intent://", ignoreCase = true)) {
                        val fallback = extractIntentFallbackUrl(failedUrl)
                        if (fallback.isNotBlank()) {
                            appendLog("Intent con fallback detectado, reintentando web")
                            mainHandler.post { webView.loadUrl(fallback) }
                            return
                        }
                    }
                    val code = error?.errorCode ?: -1
                    val desc = error?.description?.toString().orEmpty()
                    pendingNavigationError = "code=$code desc=$desc"
                    Log.e("WebCommandApp", "WebView main frame error code=$code desc=$desc url=$failedUrl")
                    appendLog("Error de carga: $desc ($code) url=$failedUrl")
                    pendingNavigationLatch?.countDown()
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (request?.isForMainFrame == true) {
                    val failedUrl = request.url?.toString().orEmpty()
                    val status = errorResponse?.statusCode ?: -1
                    val reason = errorResponse?.reasonPhrase.orEmpty()
                    pendingNavigationError = "http=$status reason=$reason"
                    Log.e("WebCommandApp", "WebView HTTP error status=$status reason=$reason url=$failedUrl")
                    appendLog("HTTP error: $status $reason url=$failedUrl")
                    pendingNavigationLatch?.countDown()
                }
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                super.onReceivedSslError(view, handler, error)
                val failedUrl = error?.url.orEmpty()
                val primary = error?.primaryError ?: -1
                pendingNavigationError = "ssl=$primary"
                Log.e("WebCommandApp", "WebView SSL error primary=$primary url=$failedUrl")
                appendLog("SSL error: $primary url=$failedUrl")
                pendingNavigationLatch?.countDown()
            }
        }
        webView.webChromeClient = WebChromeClient()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupSignalRBridgeWebView() {
        val settings = signalRBridgeWebView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        
        // Habilitar debugging de WebView para Chrome DevTools
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        
        signalRBridgeWebView.addJavascriptInterface(DroneSignalRBridge(), "AndroidDroneSocket")
        signalRBridgeWebView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                if (request == null) return null
                val url = request.url.toString()
                Log.d("SignalRBridge", "Intercepting: $url")
                
                return when {
                    url.contains("jquery") && url.endsWith(".js") && !url.contains("signalR") -> {
                        try {
                            Log.d("SignalRBridge", "Serving jquery.js from assets")
                            val inputStream = assets.open("jquery.js")
                            WebResourceResponse("application/javascript", "utf-8", inputStream)
                        } catch (e: Exception) {
                            Log.e("SignalRBridge", "Error loading jquery.js: ${e.message}")
                            null
                        }
                    }
                    url.contains("/signalr/js") -> {
                        try {
                            Log.d("SignalRBridge", "Serving jquery.signalR.js (core library) from assets")
                            val inputStream = assets.open("jquery.signalR.js")
                            WebResourceResponse("application/javascript", "utf-8", inputStream)
                        } catch (e: Exception) {
                            Log.e("SignalRBridge", "Error loading jquery.signalR.js: ${e.message}")
                            null
                        }
                    }
                    url.contains("/signalr/hubs") -> {
                        try {
                            Log.d("SignalRBridge", "Serving signalr_hubs.js from assets")
                            val inputStream = assets.open("signalr_hubs.js")
                            WebResourceResponse("application/javascript", "utf-8", inputStream)
                        } catch (e: Exception) {
                            Log.e("SignalRBridge", "Error loading signalr_hubs.js: ${e.message}")
                            null
                        }
                    }
                    else -> null
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                appendWsLog("Bridge cargado")
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                appendWsLog("Bridge error: ${error?.description ?: "sin detalle"}")
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                Log.w("SignalRBridge", "SSL error: ${error?.primaryError} url=${error?.url}")
                handler?.proceed()
            }
        }
        signalRBridgeWebView.webChromeClient = WebChromeClient()
    }

    private fun extractIntentFallbackUrl(intentUrl: String): String {
        val marker = "S.browser_fallback_url="
        val start = intentUrl.indexOf(marker)
        if (start < 0) return ""
        val from = start + marker.length
        val end = intentUrl.indexOf(';', from).let { if (it < 0) intentUrl.length else it }
        val encoded = intentUrl.substring(from, end)
        return try {
            URLDecoder.decode(encoded, "UTF-8")
        } catch (_: Exception) {
            ""
        }
    }

    private inner class TouchRecorderBridge {
        @JavascriptInterface
        fun onEvent(payload: String?) {
            if (payload.isNullOrBlank()) return
            mainHandler.post {
                onTouchRecorded(payload)
            }
        }
    }

    private fun onTouchRecorded(payload: String) {
        if (!isTouchRecording) return
        try {
            val obj = JSONObject(payload)
            val type = obj.optString("type").trim().lowercase(Locale.US)
            val ts = obj.optLong("ts", System.currentTimeMillis())
            val delay = if (recordedTouchSteps.isEmpty()) {
                0L
            } else {
                max(0L, ts - lastTouchEventTs)
            }
            lastTouchEventTs = ts

            when (type) {
                "tap" -> {
                    recordedTouchSteps.add(
                        TouchStep(
                            type = "tap",
                            delayMs = delay,
                            selector = obj.optString("selector"),
                            xPercent = obj.optDouble("xPercent", 0.5),
                            yPercent = obj.optDouble("yPercent", 0.5),
                            scrollX = 0,
                            scrollY = 0
                        )
                    )
                }

                "scroll" -> {
                    recordedTouchSteps.add(
                        TouchStep(
                            type = "scroll",
                            delayMs = delay,
                            selector = "",
                            xPercent = 0.0,
                            yPercent = 0.0,
                            scrollX = obj.optInt("scrollX", 0),
                            scrollY = obj.optInt("scrollY", 0)
                        )
                    )
                }

                else -> return
            }

            updateRecorderScriptFromSteps()
        } catch (ex: Exception) {
            appendLog("Grabador error parse: ${ex.message}")
        }
    }

    private fun startTouchRecording() {
        isTouchRecording = true
        recordedTouchSteps.clear()
        lastTouchEventTs = System.currentTimeMillis()
        recorderScriptOutput.setText("")
        updateRecorderStatus("Grabador: grabando...")
        injectTouchRecorder(true)
        appendLog("Grabador tactil iniciado")
    }

    private fun stopTouchRecording() {
        isTouchRecording = false
        injectTouchRecorder(false)
        updateRecorderScriptFromSteps()
        updateRecorderStatus("Grabador: detenido (${recordedTouchSteps.size} pasos)")
        appendLog("Grabador tactil detenido")
    }

    private fun clearTouchRecording() {
        recordedTouchSteps.clear()
        recorderScriptOutput.setText("")
        updateRecorderStatus("Grabador: limpio")
        persistConfig(showMessage = false)
    }

    private fun updateRecorderStatus(text: String) {
        tvRecorderStatus.text = text
    }

    private fun updateRecorderScriptFromSteps() {
        val arr = JSONArray()
        for (step: TouchStep in recordedTouchSteps) {
            val obj: JSONObject = JSONObject()
                .put("type", step.type)
                .put("delayMs", step.delayMs)

            if (step.type == "tap") {
                obj.put("selector", step.selector)
                obj.put("xPercent", step.xPercent)
                obj.put("yPercent", step.yPercent)
            } else if (step.type == "scroll") {
                obj.put("scrollX", step.scrollX)
                obj.put("scrollY", step.scrollY)
            }
            arr.put(obj)
        }

        val script = JSONObject()
            .put("format", TOUCH_SCRIPT_FORMAT)
            .put("steps", arr)
            .toString(2)
        recorderScriptOutput.setText(script)
        persistConfig(showMessage = false)
    }

    private fun injectTouchRecorder(enable: Boolean) {
        val script = if (enable) {
            """
            (function() {
              function cssSelector(el) {
                if (!el || !el.tagName) return "";
                if (el.id) return "#" + el.id;
                var parts = [];
                var curr = el;
                while (curr && curr.nodeType === 1 && parts.length < 6) {
                  var part = curr.nodeName.toLowerCase();
                  if (curr.className && typeof curr.className === "string") {
                    var classes = curr.className.trim().split(/\s+/).slice(0, 2).join(".");
                    if (classes) part += "." + classes;
                  }
                  var parent = curr.parentElement;
                  if (parent) {
                    var siblings = Array.prototype.filter.call(parent.children, function(n) { return n.nodeName === curr.nodeName; });
                    if (siblings.length > 1) part += ":nth-of-type(" + (siblings.indexOf(curr) + 1) + ")";
                  }
                  parts.unshift(part);
                  curr = parent;
                }
                return parts.join(" > ");
              }

              function send(ev) {
                try {
                  if (window.AndroidTouchRecorder && AndroidTouchRecorder.onEvent) {
                    AndroidTouchRecorder.onEvent(JSON.stringify(ev));
                  }
                } catch (e) {}
              }

              if (!window.__androidTouchRecorderInstalled) {
                document.addEventListener("click", function(e) {
                  if (!window.__androidTouchRecorderActive) return;
                  var w = window.innerWidth || 1;
                  var h = window.innerHeight || 1;
                  send({
                    type: "tap",
                    selector: cssSelector(e.target),
                    xPercent: Math.max(0, Math.min(1, e.clientX / w)),
                    yPercent: Math.max(0, Math.min(1, e.clientY / h)),
                    ts: Date.now()
                  });
                }, true);

                window.addEventListener("scroll", function() {
                  if (!window.__androidTouchRecorderActive) return;
                  if (window.__androidTouchScrollTimer) clearTimeout(window.__androidTouchScrollTimer);
                  window.__androidTouchScrollTimer = setTimeout(function() {
                    send({
                      type: "scroll",
                      scrollX: Math.round(window.scrollX || window.pageXOffset || 0),
                      scrollY: Math.round(window.scrollY || window.pageYOffset || 0),
                      ts: Date.now()
                    });
                  }, 150);
                }, true);
              }

              window.__androidTouchRecorderInstalled = true;
              window.__androidTouchRecorderActive = true;
              return "ok";
            })();
            """.trimIndent()
        } else {
            """(function(){window.__androidTouchRecorderActive=false; return "ok";})();"""
        }
        evaluateRawJavaScriptAsync(script)
    }

    private fun evaluateRawJavaScriptAsync(script: String) {
        mainHandler.post {
            webView.evaluateJavascript(script, null)
        }
    }

    private fun evaluateRawJavaScriptSync(script: String, timeoutSeconds: Int = 10): String {
        val result = AtomicReference("timeout")
        val latch = CountDownLatch(1)
        mainHandler.post {
            webView.evaluateJavascript(script) { value ->
                result.set(decodeJsValue(value))
                latch.countDown()
            }
        }
        val completed = latch.await(timeoutSeconds.toLong(), TimeUnit.SECONDS)
        if (!completed) return "timeout"
        return result.get()
    }

    private fun setupUiActions() {
        findViewById<Button>(R.id.btnLoad).setOnClickListener {
            navigateTo(urlInput.text.toString())
        }

        urlInput.setOnEditorActionListener { _, actionId, event ->
            val isGo = actionId == EditorInfo.IME_ACTION_GO ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            if (isGo) {
                navigateTo(urlInput.text.toString())
                true
            } else {
                false
            }
        }

        findViewById<Button>(R.id.btnExecute).setOnClickListener {
            val script = scriptInput.text.toString().trim()
            if (script.isBlank()) {
                toast("Escribe un script")
                return@setOnClickListener
            }
            executeJavaScriptAsync(script)
        }

        findViewById<Button>(R.id.btnClearLog).setOnClickListener {
            tvLog.text = ""
            appendLog("Log reiniciado")
        }

        findViewById<Button>(R.id.btnConfigSave).setOnClickListener {
            saveConfigToPrefs()
        }

        findViewById<Button>(R.id.btnConfigReload).setOnClickListener {
            loadConfigFromPrefs()
            appendConfigResult("Configuracion recargada")
        }

        findViewById<Button>(R.id.btnDbFindDataSoap).setOnClickListener {
            executeFindDataSoap()
        }

        findViewById<Button>(R.id.btnDbFindReaderSoap).setOnClickListener {
            executeFindReaderSoap()
        }

        findViewById<Button>(R.id.btnProviderLoad).setOnClickListener {
            executeProviderLoad()
        }

        btnServerToggle.setOnClickListener {
            if (server == null) {
                startRemoteServer()
            } else {
                stopRemoteServer(showMessage = true)
            }
        }

        findViewById<Button>(R.id.btnRecorderStart).setOnClickListener {
            startTouchRecording()
        }

        findViewById<Button>(R.id.btnRecorderStop).setOnClickListener {
            stopTouchRecording()
        }

        findViewById<Button>(R.id.btnRecorderClear).setOnClickListener {
            clearTouchRecording()
        }

        findViewById<Button>(R.id.btnRecorderApplyToEditor).setOnClickListener {
            val script = recorderScriptOutput.text.toString().trim()
            if (script.isBlank()) {
                toast("No hay script grabado")
                return@setOnClickListener
            }
            scriptInput.setText(script)
            tabLayout.getTabAt(0)?.select()
            appendLog("Script de grabador copiado al editor")
        }

        findViewById<Button>(R.id.btnRecorderRunNow).setOnClickListener {
            val script = recorderScriptOutput.text.toString().trim()
            if (script.isBlank()) {
                toast("No hay script grabado")
                return@setOnClickListener
            }
            dbExecutor.execute {
                val rs = executeTouchScript(script)
                runOnUiThread {
                    if (rs.ok) {
                        appendLog("Grabador test OK pasos=${rs.stepsExecuted}")
                        toast("Grabador test OK")
                    } else {
                        appendLog("Grabador test error: ${rs.error}")
                        toast("Grabador test error")
                    }
                }
            }
        }

        btnWsConnect.setOnClickListener {
            connectDroneSignalR(showToast = true, reconnect = false)
        }

        btnWsDisconnect.setOnClickListener {
            disconnectDroneSignalR(manual = true)
        }

        findViewById<Button>(R.id.btnWsClearLog).setOnClickListener {
            clearWsLog()
        }

        findViewById<Button>(R.id.btnWsUseCurrentToken).setOnClickListener {
            wsDroneIdInput.setText(providerClientTokenInput.text.toString().trim())
            appendWsLog("Drone ID actualizado desde token actual")
        }

        btnWsSendStatus.setOnClickListener {
            sendDroneStatus()
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    private fun startRemoteServer(forceRestart: Boolean = false): Boolean {
        val port = resolveConfiguredPort()
        if (!forceRestart && server != null && currentServerPort == port) {
            return true
        }

        if (server != null) {
            stopRemoteServer(showMessage = false)
        }

        val ip = getLocalIpAddress() ?: "0.0.0.0"
        val apiKey = BuildConfig.REMOTE_API_KEY
        val newServer = RemoteControlServer(
            port = port,
            expectedApiKey = apiKey,
            onNavigate = { url ->
                mainHandler.post {
                    navigateTo(url)
                }
            },
            onExecute = { script -> executeJavaScriptSync(script) },
            onStatus = {
                "ok=true;ip=$ip;port=$port;currentUrl=$currentUrl"
            },
            onDbPing = { request -> runDbPing(request) },
            onRunFlow = { request -> runRemoteFlow(request) }
        )

        return try {
            newServer.start()
            server = newServer
            currentServerPort = port
            Log.i("WebCommandApp", "Remote server started on $ip:$port")
            tvServerInfo.text = "Servidor: http://$ip:$port  | API key requerida"
            btnServerToggle.text = "Detener servidor"
            appendLog("Servidor HTTP iniciado en $ip:$port")
            updateConfigSummary()
            true
        } catch (ex: Exception) {
            Log.e("WebCommandApp", "Remote server start failed", ex)
            server = null
            currentServerPort = null
            tvServerInfo.text = "Servidor: error al iniciar"
            btnServerToggle.text = "Iniciar servidor"
            appendLog("Error servidor: ${ex.message}")
            toast("No se pudo iniciar el servidor")
            false
        }
    }

    private fun stopRemoteServer(showMessage: Boolean) {
        server?.stop()
        server = null
        currentServerPort = null
        tvServerInfo.text = "Servidor: detenido"
        btnServerToggle.text = "Iniciar servidor"
        if (showMessage) {
            appendLog("Servidor detenido")
        }
        updateConfigSummary()
    }

    private fun resolveConfiguredPort(): Int {
        val value = serverPortInput.text.toString().trim()
        return parsePort(value) ?: BuildConfig.REMOTE_PORT
    }

    private fun parsePort(value: String): Int? {
        val port = value.toIntOrNull() ?: return null
        return if (port in 1..65535) port else null
    }

    private fun navigateTo(rawUrl: String) {
        val normalized = normalizeUrl(rawUrl)
        if (normalized.isEmpty()) {
            toast("Ingresa una URL")
            return
        }
        webView.loadUrl(normalized)
    }

    private fun normalizeUrl(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    private fun normalizeServerUrl(input: String): String {
        return normalizeUrl(input).trimEnd('/')
    }

    private fun loadSignalRBridgePage(serverUrl: String) {
        wsBridgeReady = false
        val baseUrl = "${normalizeServerUrl(serverUrl)}/"
        val html = buildSignalRBridgeHtml()
        mainHandler.post {
            signalRBridgeWebView.loadDataWithBaseURL(baseUrl, html, "text/html", "utf-8", null)
        }
    }

    private fun connectDroneSignalR(showToast: Boolean, reconnect: Boolean) {
        val serverUrl = normalizeServerUrl(wsServerUrlInput.text.toString())
        val droneId = wsDroneIdInput.text.toString().trim()
        if (serverUrl.isBlank() || droneId.isBlank()) {
            toast("Completa servidor y Drone ID")
            return
        }

        wsCurrentServerUrl = serverUrl
        wsCurrentDroneId = droneId
        wsAutoReconnectEnabled = true
        wsPendingConnect = true
        mainHandler.removeCallbacks(wsReconnectRunnable)
        updateWsState(if (reconnect) "Reconnecting" else "Connecting")
        appendWsLog("Preparando conexion a $serverUrl como $droneId")
        persistConfig(showMessage = false)
        loadSignalRBridgePage(serverUrl)
        if (showToast) {
            toast("Conectando websocket")
        }
    }

    private fun disconnectDroneSignalR(manual: Boolean) {
        if (manual) {
            wsAutoReconnectEnabled = false
        }
        wsPendingConnect = false
        wsConnected = false
        mainHandler.removeCallbacks(wsReconnectRunnable)
        if (wsBridgeReady) {
            evalSignalRBridge("window.droneSocket && window.droneSocket.disconnect();")
        }
        updateWsState("Disconnected")
        appendWsLog("Conexion websocket detenida")
    }

    private fun sendDroneStatus() {
        if (!wsConnected) {
            toast("WebSocket no conectado")
            return
        }

        val payload = wsStatusPayloadInput.text.toString().trim()
        if (payload.isBlank()) {
            toast("Escribe un JSON de estado")
            return
        }

        if (wsPendingCorrelationId.isNotBlank()) {
            val corr = wsPendingCorrelationId
            evalSignalRBridge(
                "window.droneSocket && window.droneSocket.sendResponse(${JSONObject.quote(corr)}, ${JSONObject.quote(payload)});"
            )
            appendWsLog("Respuesta enviada correlationId=${corr.take(8)}...")
            wsPendingCorrelationId = ""
        }

        evalSignalRBridge(
            "window.droneSocket && window.droneSocket.sendStatus(${JSONObject.quote(payload)});"
        )
        appendWsLog("Estado enviado: $payload")
    }

    private fun evalSignalRBridge(script: String) {
        mainHandler.post {
            signalRBridgeWebView.evaluateJavascript(script, null)
        }
    }

    private fun buildSignalRBridgeHtml(): String {
        return """
            <!doctype html>
            <html>
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <script src="/jquery.js"></script>
              <script src="/signalr/js"></script>
              <script src="/signalr/hubs"></script>
            </head>
            <body>
            <script>
            function initializeApp() {
              (function () {
                function callNative(name) {
                  try {
                    if (!window.AndroidDroneSocket || !window.AndroidDroneSocket[name]) return;
                    var args = Array.prototype.slice.call(arguments, 1);
                    window.AndroidDroneSocket[name].apply(window.AndroidDroneSocket, args);
                  } catch (e) {
                    console.error("callNative error: " + e);
                  }
                }

                function mapState(state) {
                  switch (state) {
                    case 0: return "Connecting";
                    case 1: return "Connected";
                    case 2: return "Reconnecting";
                    case 4: return "Disconnected";
                    default: return String(state || "");
                  }
                }

                var hub = null;
                var currentDroneId = "";

                function waitForSignalR(callback, attempts) {
                  attempts = attempts || 0;
                  if (attempts > 50) {
                    var msg = "Timeout esperando SignalR. Estado: jQuery=" + (typeof jQuery) + ", $=" + (typeof $) + ", $.connection=" + (typeof ($ && $.connection));
                    console.error(msg);
                    console.error("Window.$:", window.$);
                    console.error("Window.jQuery:", window.jQuery);
                    console.error("Window.$.connection:", window.$ ? window.$.connection : "N/A");
                    callNative("onLog", msg);
                    callNative("onStateChanged", "Error: Timeout esperando SignalR");
                    return;
                  }
                  
                  // Verificar que jQuery esté disponible
                  if (!window.$ && window.jQuery) {
                    window.$ = window.jQuery;
                  }
                  
                  if (typeof $ !== 'undefined' && $ && $.connection && $.connection.dronesHub) {
                    console.log("✓ SignalR LISTO! jQuery=" + typeof $ + ", $.connection.dronesHub=" + typeof $.connection.dronesHub);
                    callNative("onLog", "SignalR listo! jQuery=" + typeof $ + ", $.connection.dronesHub=" + typeof $.connection.dronesHub);
                    callback();
                  } else {
                    var jqStatus = typeof $ !== 'undefined' ? "SI (" + typeof $ + ")" : "NO";
                    var connStatus = ($ && $.connection) ? "SI" : "NO";
                    var hubStatus = ($ && $.connection && $.connection.dronesHub) ? "SI" : "NO";
                    var msg = "Esperando SignalR #" + (attempts + 1) + " - jQuery:" + jqStatus + " connection:" + connStatus + " dronesHub:" + hubStatus;
                    console.log(msg);
                    callNative("onLog", msg);
                    setTimeout(function() {
                      waitForSignalR(callback, attempts + 1);
                    }, 100);
                  }
                }

                window.droneSocket = {
                  connect: function (serverUrl, droneId) {
                    currentDroneId = String(droneId || "");
                    console.log("🔄 Iniciando conexión SignalR a: " + serverUrl + " como " + droneId);
                    callNative("onLog", "Iniciando conexión a " + serverUrl + " como " + droneId);
                    
                    waitForSignalR(function() {
                      try {
                        $.connection.hub.url = String(serverUrl || "").replace(/\/+$/, "") + "/signalr";
                        console.log("⚙️ URL del hub configurada: " + $.connection.hub.url);
                        hub = $.connection.dronesHub;
                        if (!hub) {
                          var errMsg = "Hub dronesHub no disponible";
                          console.error("✗ " + errMsg);
                          callNative("onLog", errMsg);
                          callNative("onStateChanged", "Error: " + errMsg);
                          return;
                        }
                        console.log("✓ Hub dronesHub disponible");

                        hub.client.recibirInstruccion = function (droneIdValue, instruccion, parametros, correlationId, timestamp) {
                          console.log("📨 Instrucción recibida: " + instruccion + " para " + droneIdValue);
                          callNative(
                            "onInstruction",
                            String(droneIdValue || ""),
                            String(instruccion || ""),
                            parametros == null ? "" : String(parametros),
                            String(correlationId || ""),
                            String(timestamp || "")
                        );
                      };
                      hub.client.registroConfirmado = function (droneIdValue, connectionId, timestamp) {
                        console.log("✓ Registro confirmado: " + droneIdValue);
                        callNative("onRegistered", String(droneIdValue || ""), String(connectionId || ""), String(timestamp || ""));
                      };
                      hub.client.actualizarEstado = function (droneIdValue, statusJson, timestamp) {
                        console.log("📊 Estado actualizado para: " + droneIdValue);
                        callNative("onStatusUpdated", String(droneIdValue || ""), String(statusJson || ""), String(timestamp || ""));
                      };
                      hub.client.droneConectado = function (droneIdValue, timestamp) {
                        console.log("✓✓✓ DRON CONECTADO: " + droneIdValue);
                        callNative("onDroneConnected", String(droneIdValue || ""), String(timestamp || ""));
                      };
                      hub.client.droneDesconectado = function (droneIdValue, timestamp) {
                        console.log("✗✗✗ DRON DESCONECTADO: " + droneIdValue);
                        callNative("onDroneDisconnected", String(droneIdValue || ""), String(timestamp || ""));
                      };

                        $.connection.hub.stateChanged(function (change) {
                          var state = mapState(change.newState);
                          console.log("🔄 Estado hub cambiado a: " + state);
                          callNative("onStateChanged", state);
                        });
                        $.connection.hub.reconnecting(function () {
                          callNative("onStateChanged", "Reconnecting");
                        });
                        $.connection.hub.disconnected(function () {
                          callNative("onDisconnected");
                        });
                        $.connection.hub.error(function (error) {
                          var message = error && error.message ? error.message : String(error || "Error websocket");
                          console.error("✗ ERROR SignalR: ", error, " Mensaje: " + message);
                          callNative("onLog", "Error SignalR: " + message);
                          callNative("onStateChanged", "Error: " + message);
                        });

                        callNative("onStateChanged", "Connecting");
                        console.log("⚙️ Iniciando conexión del hub con transporte: webSockets, longPolling");
                        $.connection.hub.start({ transport: ["webSockets", "longPolling"] })
                          .done(function () {
                            console.log("✓ Conexión del hub iniciada correctamente");
                            callNative("onStateChanged", "Connected");
                            console.log("📤 Enviando register para: " + currentDroneId);
                            hub.server.register(currentDroneId)
                              .done(function () {
                                console.log("✓ Register confirmado para: " + currentDroneId);
                                callNative("onLog", "Register enviado para " + currentDroneId);
                              })
                              .fail(function (err) {
                                var errMsg = String(err || "");
                                console.error("✗ Error en register: ", err, " Detalle: " + errMsg);
                                callNative("onLog", "Error Register: " + errMsg);
                              });
                          })
                          .fail(function (err) {
                            var message = String(err || "No fue posible conectar");
                            console.error("✗✗ ERROR al iniciar conexión: ", err, " Detalle: " + message);
                            callNative("onLog", "Error conectar: " + message);
                            callNative("onStateChanged", "Error: " + message);
                          });
                      } catch (ex) {
                        var errMsg = "Excepción en websocket: " + String(ex);
                        console.error("✗✗✗ EXCEPCIÓN: ", ex, " - " + errMsg);
                        callNative("onLog", errMsg);
                        callNative("onStateChanged", "Error: " + errMsg);
                      }
                    });
                  },
                  disconnect: function () {
                    try {
                      $.connection.hub.stop();
                    } catch (e) {}
                  },
                  sendStatus: function (payload) {
                    if (!hub) return;
                    hub.server.reportStatus(currentDroneId, String(payload || ""));
                  },
                  sendResponse: function (correlationId, payload) {
                    if (!hub) return;
                    hub.server.informarRespuesta(String(correlationId || ""), String(payload || ""));
                  }
                };

                callNative("onPageReady");
              })();
            }
            initializeApp();
            </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun handleDroneInstruction(
        droneId: String,
        instruction: String,
        paramsJson: String,
        correlationId: String,
        timestamp: String
    ) {
        val requiresResponse = correlationId.isNotBlank()
        val prefix = if (requiresResponse) "[REQ]" else "[MSG]"
        appendWsLog("$prefix $timestamp $instruction para $droneId")
        if (paramsJson.isNotBlank()) {
            appendWsLog("Parametros: $paramsJson")
        }

        if (requiresResponse) {
            wsPendingCorrelationId = correlationId
            wsStatusPayloadInput.setText("""{"respuesta":"ok","instruccion":"$instruction"}""")
            appendWsLog("CorrelationId pendiente: ${correlationId.take(8)}...")
        }

        if (!instruction.equals("CARGAR_DRON_BRUTO", ignoreCase = true)) {
            return
        }

        val payload = try {
            JSONObject(paramsJson)
        } catch (ex: Exception) {
            appendWsLog("JSON de instruccion invalido: ${ex.message}")
            return
        }

        val tramiteId = payload.opt("tramiteId")?.toString().orEmpty()
        val tokenHijo = payload.opt("tokenHijo")?.toString().orEmpty()
        val paso = payload.opt("paso")?.toString().orEmpty().ifBlank { DEFAULT_PASO }
        val urlCommand = payload.opt("urlcommand")?.toString().orEmpty()
        val bodyValue = payload.opt("body")
        val bodyJson = when (bodyValue) {
            is JSONObject, is JSONArray -> bodyValue.toString()
            null -> ""
            else -> bodyValue.toString()
        }

        appendWsLog("Ejecutando CARGAR_DRON_BRUTO tramite=$tramiteId paso=$paso")
        tabLayout.getTabAt(0)?.select()
        dbExecutor.execute {
            val queryParams = mutableMapOf<String, String>()
            queryParams.putAll(parseQueryParams(urlCommand))
            queryParams.putAll(flattenBodyParams(bodyValue))
            if (tramiteId.isNotBlank()) {
                queryParams["tramiteId"] = tramiteId
            }

            val result = runRemoteFlow(
                RunFlowRequest(
                    paso = paso,
                    clientToken = tokenHijo,
                    soapUrl = "",
                    base = "",
                    soapToken = "",
                    queryParams = queryParams,
                    bodyJson = bodyJson
                )
            )

            runOnUiThread {
                appendWsLog("Resultado instruccion: $result")
                wsStatusPayloadInput.setText(result)
            }
        }
    }

    private fun parseQueryParams(rawQuery: String): Map<String, String> {
        val trimmed = rawQuery.trim()
        if (trimmed.isBlank()) return emptyMap()
        val query = when {
            trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) ->
                trimmed.substringAfter('?', "")
            trimmed.startsWith("?") -> trimmed.removePrefix("?")
            else -> trimmed
        }
        if (query.isBlank()) return emptyMap()

        val result = linkedMapOf<String, String>()
        query.split("&")
            .filter { it.isNotBlank() }
            .forEach { part ->
                val pieces = part.split("=", limit = 2)
                val key = URLDecoder.decode(pieces[0], "UTF-8")
                val value = if (pieces.size > 1) URLDecoder.decode(pieces[1], "UTF-8") else ""
                result[key] = value
            }
        return result
    }

    private fun flattenBodyParams(bodyValue: Any?): Map<String, String> {
        if (bodyValue == null) return emptyMap()
        val result = linkedMapOf<String, String>()
        when (bodyValue) {
            is JSONObject -> {
                val keys = bodyValue.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    result[key] = bodyValue.opt(key)?.toString().orEmpty()
                }
                result["body"] = bodyValue.toString()
            }
            is JSONArray -> {
                result["body"] = bodyValue.toString()
            }
            else -> {
                val raw = bodyValue.toString()
                result["body"] = raw
                if (raw.startsWith("{") && raw.endsWith("}")) {
                    try {
                        val obj = JSONObject(raw)
                        val keys = obj.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            result[key] = obj.opt(key)?.toString().orEmpty()
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        }
        return result
    }

    private fun executeJavaScriptAsync(script: String) {
        val wrapped = wrapScript(script)
        webView.evaluateJavascript(wrapped) { value ->
            val decoded = decodeJsValue(value)
            appendLog("JS local => $decoded")
            toast("JS ejecutado")
        }
    }

    private fun executeJavaScriptSync(script: String): String {
        val result = AtomicReference("timeout")
        val latch = CountDownLatch(1)
        val wrapped = wrapScript(script)

        mainHandler.post {
            webView.evaluateJavascript(wrapped) { value ->
                result.set(decodeJsValue(value))
                latch.countDown()
            }
        }

        val completed = latch.await(8, TimeUnit.SECONDS)
        if (!completed) {
            appendLog("JS remoto timeout")
            return "timeout"
        }

        val finalResult = result.get()
        appendLog("JS remoto => $finalResult")
        return finalResult
    }

    private fun wrapScript(script: String): String {
        val quoted = JSONObject.quote(script)
        return "(function(){try{var fn=new Function($quoted);var out=fn();return JSON.stringify({ok:true,result:out});}catch(e){return JSON.stringify({ok:false,error:String(e)});}})();"
    }

    private fun decodeJsValue(value: String?): String {
        if (value.isNullOrBlank()) return "null"
        return try {
            val token = JSONTokener(value).nextValue()
            token?.toString() ?: "null"
        } catch (_: Exception) {
            value
        }
    }

    private fun getLocalIpAddress(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (!networkInterface.isUp || networkInterface.isLoopback) continue

                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        return address.hostAddress
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun appendLog(message: String) {
        val ts = timestampFormat.format(Date())
        val line = "[$ts] $message\n"
        if (Looper.myLooper() == Looper.getMainLooper()) {
            tvLog.append(line)
        } else {
            mainHandler.post { tvLog.append(line) }
        }
    }

    private fun appendConfigResult(message: String) {
        val ts = timestampFormat.format(Date())
        val text = "[$ts] $message"
        if (Looper.myLooper() == Looper.getMainLooper()) {
            tvConfigResult.text = text
        } else {
            mainHandler.post { tvConfigResult.text = text }
        }
    }

    private fun appendWsLog(message: String) {
        val ts = timestampFormat.format(Date())
        val line = "[$ts] $message"
        val render = {
            wsLogLines.addLast(line)
            while (wsLogLines.size > 200) {
                wsLogLines.removeFirst()
            }
            tvWsLog.text = wsLogLines.joinToString("\n")
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            render()
        } else {
            mainHandler.post { render() }
        }
    }

    private fun clearWsLog() {
        wsLogLines.clear()
        tvWsLog.text = ""
        appendWsLog("Log websocket reiniciado")
    }

    private fun updateWsState(state: String) {
        val normalized = state.trim()
        val color = when {
            normalized.startsWith("Connected", ignoreCase = true) -> Color.parseColor("#27AE60")
            normalized.startsWith("Connecting", ignoreCase = true) ||
                normalized.startsWith("Reconnecting", ignoreCase = true) -> Color.parseColor("#F39C12")
            normalized.startsWith("Error", ignoreCase = true) -> Color.parseColor("#C0392B")
            else -> Color.parseColor("#7F8C8D")
        }
        tvWsState.text = "Estado: $normalized"
        tvWsState.setTextColor(color)
        wsConnected = normalized.startsWith("Connected", ignoreCase = true)
        btnWsConnect.isEnabled = !wsConnected
        btnWsDisconnect.isEnabled = wsConnected || normalized.startsWith("Connecting", ignoreCase = true) ||
            normalized.startsWith("Reconnecting", ignoreCase = true)
        btnWsSendStatus.isEnabled = wsConnected
        if (!wsConnected && normalized.startsWith("Disconnected", ignoreCase = true)) {
            tvWsDroneInfo.text = "ID: (no registrado)"
        }
    }

    private fun loadConfigFromPrefs() {
        isLoadingConfig = true
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        dbSoapUrlInput.setText(prefs.getString(KEY_SOAP_URL, WebDatos.DEFAULT_URL))
        dbBaseInput.setText(prefs.getString(KEY_DB_BASE, DEFAULT_DB_BASE))
        dbTokenInput.setText(prefs.getString(KEY_SOAP_TOKEN, DEFAULT_SOAP_TOKEN))
        providerClientTokenInput.setText(prefs.getString(KEY_CLIENT_TOKEN, DEFAULT_CLIENT_TOKEN))
        providerPasoInput.setText(prefs.getString(KEY_PASO, DEFAULT_PASO))
        serverPortInput.setText(prefs.getString(KEY_SERVER_PORT, BuildConfig.REMOTE_PORT.toString()))
        dbSqlInput.setText(prefs.getString(KEY_SQL_QUERY, DEFAULT_SQL))
        recorderScriptOutput.setText(prefs.getString(KEY_TOUCH_SCRIPT, ""))
        wsServerUrlInput.setText(prefs.getString(KEY_WS_SERVER_URL, DEFAULT_WS_SERVER_URL))
        wsDroneIdInput.setText(prefs.getString(KEY_WS_DRONE_ID, DEFAULT_WS_DRONE_ID))
        wsStatusPayloadInput.setText(prefs.getString(KEY_WS_STATUS_PAYLOAD, DEFAULT_WS_STATUS_PAYLOAD))

        updateConfigSummary()
        updateRecorderStatus("Grabador: detenido")
        updateWsState("Desconectado")
        isLoadingConfig = false
    }

    private fun saveConfigToPrefs() {
        persistConfig(showMessage = true)
    }

    private fun persistConfig(showMessage: Boolean) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_SOAP_URL, dbSoapUrlInput.text.toString().trim())
            .putString(KEY_DB_BASE, dbBaseInput.text.toString().trim())
            .putString(KEY_SOAP_TOKEN, dbTokenInput.text.toString().trim())
            .putString(KEY_CLIENT_TOKEN, providerClientTokenInput.text.toString().trim())
            .putString(KEY_PASO, providerPasoInput.text.toString().trim().ifBlank { DEFAULT_PASO })
            .putString(KEY_SERVER_PORT, serverPortInput.text.toString().trim())
            .putString(KEY_SQL_QUERY, dbSqlInput.text.toString())
            .putString(KEY_TOUCH_SCRIPT, recorderScriptOutput.text.toString())
            .putString(KEY_WS_SERVER_URL, wsServerUrlInput.text.toString().trim())
            .putString(KEY_WS_DRONE_ID, wsDroneIdInput.text.toString().trim())
            .putString(KEY_WS_STATUS_PAYLOAD, wsStatusPayloadInput.text.toString())
            .apply()

        updateConfigSummary()
        if (showMessage) {
            appendConfigResult("Configuracion guardada")
        }
    }

    private fun setupConfigAutoSave() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                if (isLoadingConfig) return
                mainHandler.removeCallbacks(autoSaveRunnable)
                mainHandler.postDelayed(autoSaveRunnable, AUTOSAVE_DELAY_MS)
            }
        }

        dbSoapUrlInput.addTextChangedListener(watcher)
        dbBaseInput.addTextChangedListener(watcher)
        dbTokenInput.addTextChangedListener(watcher)
        providerClientTokenInput.addTextChangedListener(watcher)
        providerPasoInput.addTextChangedListener(watcher)
        serverPortInput.addTextChangedListener(watcher)
        dbSqlInput.addTextChangedListener(watcher)
        recorderScriptOutput.addTextChangedListener(watcher)
        wsServerUrlInput.addTextChangedListener(watcher)
        wsDroneIdInput.addTextChangedListener(watcher)
        wsStatusPayloadInput.addTextChangedListener(watcher)
    }

    private fun updateConfigSummary() {
        val summary = buildString {
            appendLine("Configuracion actual")
            appendLine("SOAP URL: ${dbSoapUrlInput.text.toString().trim()}")
            appendLine("Base: ${dbBaseInput.text.toString().trim()}")
            appendLine("Token SOAP: ${dbTokenInput.text.toString().trim().ifBlank { "(vacio)" }}")
            appendLine("Token cliente: ${maskValue(providerClientTokenInput.text.toString().trim())}")
            appendLine("Paso: ${providerPasoInput.text.toString().trim().ifBlank { DEFAULT_PASO }}")
            appendLine("Puerto configurado: ${serverPortInput.text.toString().trim().ifBlank { BuildConfig.REMOTE_PORT.toString() }}")
            appendLine("Puerto escuchando: ${currentServerPort?.toString() ?: "(detenido)"}")
            appendLine("WS URL: ${wsServerUrlInput.text.toString().trim().ifBlank { DEFAULT_WS_SERVER_URL }}")
            appendLine("WS Drone ID: ${wsDroneIdInput.text.toString().trim().ifBlank { DEFAULT_WS_DRONE_ID }}")
        }
        tvConfigSummary.text = summary.trimEnd()
    }

    private fun executeFindDataSoap() {
        val sql = dbSqlInput.text.toString().trim()
        val base = dbBaseInput.text.toString().trim()
        val token = dbTokenInput.text.toString().trim()
        val url = dbSoapUrlInput.text.toString().trim()

        if (sql.isBlank() || base.isBlank() || token.isBlank() || url.isBlank()) {
            toast("Completa URL, Base, Token y SQL")
            return
        }

        appendLog("FindDataSOAP ejecutando...")
        dbExecutor.execute {
            admDatos.SetServiceUrl(url)
            val data = admDatos.FindDataSOAP(sql, base, token)
            val message = if (admDatos.ErrorSql.isNotBlank()) {
                "FindDataSOAP error: ${admDatos.ErrorSql}"
            } else {
                val rowCount = data.rows.size
                val preview = data.rows.take(2).joinToString(" | ") { row ->
                    row.entries.take(4).joinToString(",") { "${it.key}=${it.value}" }
                }
                "FindDataSOAP ok. filas=$rowCount preview=$preview"
            }
            runOnUiThread {
                appendLog(message)
                appendConfigResult(message)
            }
        }
    }

    private fun executeFindReaderSoap() {
        val sql = dbSqlInput.text.toString().trim()
        val base = dbBaseInput.text.toString().trim()
        val token = dbTokenInput.text.toString().trim()
        val url = dbSoapUrlInput.text.toString().trim()

        if (sql.isBlank() || base.isBlank() || token.isBlank() || url.isBlank()) {
            toast("Completa URL, Base, Token y SQL")
            return
        }

        appendLog("FindReaderSOAP ejecutando...")
        dbExecutor.execute {
            admDatos.SetServiceUrl(url)
            val value = admDatos.FindReaderSOAP(sql, base, token)
            val message = if (admDatos.ErrorSql.isNotBlank()) {
                "FindReaderSOAP error: ${admDatos.ErrorSql}"
            } else {
                "FindReaderSOAP ok. valor=$value"
            }
            runOnUiThread {
                appendLog(message)
                appendConfigResult(message)
            }
        }
    }

    private fun executeProviderLoad() {
        val soapUrl = dbSoapUrlInput.text.toString().trim()
        val base = dbBaseInput.text.toString().trim()
        val soapToken = dbTokenInput.text.toString().trim()
        val clientToken = providerClientTokenInput.text.toString().trim()

        if (soapUrl.isBlank() || base.isBlank() || soapToken.isBlank() || clientToken.isBlank()) {
            toast("Completa URL/Base/Token SOAP y Token cliente")
            return
        }

        appendLog("Cargando cliente por token...")

        dbExecutor.execute {
            val provider = SoapInstructionProvider(
                admDatos = AdmDatos(),
                soapUrl = soapUrl,
                baseSistema = base,
                soapToken = soapToken
            )

            val clientRs = provider.loadClientConfig(clientToken)
            if (!clientRs.ok || clientRs.data == null) {
                runOnUiThread {
                    val msg = "Provider error CLIENT: ${clientRs.error}"
                    appendLog(msg)
                    appendConfigResult(msg)
                }
                return@execute
            }

            val client = clientRs.data
            runOnUiThread {
                val portFromReference = parsePort(client.referencia.trim())
                val baseSummary = "Cliente cargado: ${client.nombre} | sucursal=${client.sucursal} pagina=${client.codigoPagina}"

                if (portFromReference == null) {
                    val msg = "$baseSummary | REFERENCIA='${client.referencia}' no es un puerto valido"
                    appendLog(msg)
                    appendConfigResult(msg)
                    return@runOnUiThread
                }

                serverPortInput.setText(portFromReference.toString())
                persistConfig(showMessage = false)
                val started = startRemoteServer(forceRestart = true)

                val msg = if (started) {
                    "$baseSummary | Puerto aplicado desde REFERENCIA: $portFromReference"
                } else {
                    "$baseSummary | Puerto $portFromReference detectado, pero no se pudo iniciar el servidor"
                }
                appendLog(msg)
                appendConfigResult(msg)
            }
        }
    }

    private fun runDbPing(request: RunFlowRequest): String {
        return try {
            val savedConfig = loadConfigSnapshot()
            val config = FlowConfigSnapshot(
                soapUrl = request.soapUrl.ifBlank { savedConfig.soapUrl },
                base = request.base.ifBlank { savedConfig.base },
                soapToken = request.soapToken.ifBlank { savedConfig.soapToken },
                clientToken = request.clientToken.ifBlank { savedConfig.clientToken },
                paso = request.paso.ifBlank { savedConfig.paso }
            )
            val localAdm = AdmDatos()
            localAdm.SetServiceUrl(config.soapUrl)
            val sql = request.queryParams["sql"]
                ?.takeIf { it.isNotBlank() }
                ?: "SELECT CONVERT(VARCHAR(19),GETDATE(),120) AS FECHA_SERVIDOR"
            val value = localAdm.FindReaderSOAP(
                sql,
                config.base,
                config.soapToken
            )

            if (localAdm.ErrorSql.isNotBlank()) {
                return errorJson("DBPING error: ${localAdm.ErrorSql} (url=${config.soapUrl}, base=${config.base}, token=${maskValue(config.soapToken)})")
            }

            JSONObject()
                .put("ok", true)
                .put("serverDate", value)
                .put("sql", sql)
                .put("base", config.base)
                .put("soapUrl", config.soapUrl)
                .toString()
        } catch (ex: Exception) {
            errorJson("DBPING exception: ${ex.message}")
        }
    }

    private fun runRemoteFlow(request: RunFlowRequest): String {
        return try {
            val savedConfig = loadConfigSnapshot()
            val config = FlowConfigSnapshot(
                soapUrl = request.soapUrl.ifBlank { savedConfig.soapUrl },
                base = request.base.ifBlank { savedConfig.base },
                soapToken = request.soapToken.ifBlank { savedConfig.soapToken },
                clientToken = request.clientToken.ifBlank { savedConfig.clientToken },
                paso = request.paso.ifBlank { savedConfig.paso }
            )
            val clientToken = config.clientToken
            val paso = request.paso.ifBlank { config.paso }

            if (config.soapUrl.isBlank() || config.base.isBlank() || config.soapToken.isBlank()) {
                return errorJson("Configuracion SOAP incompleta")
            }
            if (clientToken.isBlank()) {
                return errorJson("clientToken requerido")
            }

            val provider = SoapInstructionProvider(
                admDatos = AdmDatos(),
                soapUrl = config.soapUrl,
                baseSistema = config.base,
                soapToken = config.soapToken
            )

            appendLog("RUN inicio token=${maskValue(clientToken)} paso=$paso")
            val clientRs = provider.loadClientConfig(clientToken)
            if (!clientRs.ok || clientRs.data == null) {
                return errorJson(
                    "Error CLIENT: ${clientRs.error} (url=${config.soapUrl}, base=${config.base}, token=${maskValue(config.soapToken)}, sql=${clientRs.sql})"
                )
            }
            val client = clientRs.data

            val stepRs = provider.loadStep(
                sucursal = client.sucursal,
                pagina = client.codigoPagina,
                paso = paso,
                queryParams = request.queryParams
            )
            if (!stepRs.ok) {
                return errorJson("Error STEP: ${stepRs.error} (sql=${stepRs.sql})")
            }

            val step = stepRs.data ?: return errorJson("No existe paso ORDEN=$paso")
            val pedReg = step.reg.trim()

            if (step.flagNoNav == 0 && step.urlPasoResolved.isNotBlank()) {
                val nav = navigateWithFallback(step.urlPasoResolved, NAV_TIMEOUT_SECONDS)
                appendLog("RUN navigate=${nav.ok} url=${nav.urlUsed}")
                if (!nav.ok) {
                    return errorJson("Error navegacion: ${nav.error} url=${nav.urlUsed}")
                }
            } else {
                appendLog("RUN sin navegacion flagNoNav=${step.flagNoNav}")
            }

            if (step.tiempo > 0) {
                TimeUnit.SECONDS.sleep(step.tiempo.toLong())
            }

            val actionsRs = provider.loadActions(
                sucursal = client.sucursal,
                pagina = client.codigoPagina,
                pedReg = pedReg
            )
            if (!actionsRs.ok) {
                return errorJson("Error ACTIONS: ${actionsRs.error} (sql=${actionsRs.sql})")
            }

            val varsRs = provider.loadVariables(
                sucursal = client.sucursal,
                pagina = client.codigoPagina,
                pedRegToken = client.pedRegToken
            )
            if (!varsRs.ok) {
                return errorJson("Error VARS: ${varsRs.error}")
            }

            val actions = actionsRs.data.orEmpty().sortedBy { it.orden }
            val variables = varsRs.data.orEmpty()
            val replacements = mutableMapOf<String, String>()
            replacements["iniciar_procesar"] = "1"
            replacements["prefijo"] = client.prefijo
            if (request.bodyJson.isNotBlank()) {
                replacements["body"] = request.bodyJson
            }
            variables.forEach { replacements[it.variable] = it.valor }
            request.queryParams.forEach { (k, v) -> replacements[k] = v }

            val actionLog = JSONArray()
            var executedCount = 0
            var lastVariableResult = ""

            for (action in actions) {
                val actionType = action.tipo.trim().uppercase(Locale.US)
                val pageFrom = if (action.paginaDesde <= 0) 1 else action.paginaDesde
                val pageTo = if (action.paginaHasta <= 0) 1 else action.paginaHasta
                val preparedBaseScript = applyTemplate(action.script, replacements)

                if (preparedBaseScript.isBlank()) {
                    continue
                }

                for (page in pageFrom..pageTo) {
                    val script = preparedBaseScript.replace("@@PAGINA@@", page.toString())
                    if (action.espera > 0) {
                        TimeUnit.SECONDS.sleep(action.espera.toLong())
                    }

                    when (actionType) {
                        "", "JS" -> {
                            executeJavaScriptSync(script)
                            executedCount += 1
                            actionLog.put(JSONObject().put("orden", action.orden).put("tipo", "JS").put("pagina", page))
                        }

                        "VARIABLE" -> {
                            lastVariableResult = executeJavaScriptSync(script)
                            executedCount += 1
                            actionLog.put(
                                JSONObject()
                                    .put("orden", action.orden)
                                    .put("tipo", "VARIABLE")
                                    .put("pagina", page)
                                    .put("resultado", lastVariableResult)
                            )
                        }

                        "MOUSE" -> {
                            val mouseRs = executeTouchScript(script)
                            if (mouseRs.ok) {
                                executedCount += 1
                                actionLog.put(
                                    JSONObject()
                                        .put("orden", action.orden)
                                        .put("tipo", "MOUSE")
                                        .put("pagina", page)
                                        .put("pasos", mouseRs.stepsExecuted)
                                )
                            } else {
                                actionLog.put(
                                    JSONObject()
                                        .put("orden", action.orden)
                                        .put("tipo", "MOUSE")
                                        .put("pagina", page)
                                        .put("omitida", true)
                                        .put("motivo", mouseRs.error)
                                )
                            }
                        }

                        else -> {
                            actionLog.put(
                                JSONObject()
                                    .put("orden", action.orden)
                                    .put("tipo", actionType)
                                    .put("pagina", page)
                                    .put("omitida", true)
                                    .put("motivo", "Tipo no soportado en Android")
                            )
                        }
                    }
                }
            }

            appendLog("RUN completado acciones=$executedCount paso=$paso")
            JSONObject()
                .put("ok", true)
                .put("paso", paso)
                .put("cliente", client.nombre)
                .put("sucursal", client.sucursal)
                .put("pagina", client.codigoPagina)
                .put("urlPaso", step.urlPasoResolved)
                .put("accionesTotal", actions.size)
                .put("accionesEjecutadas", executedCount)
                .put("resultadoVariable", lastVariableResult)
                .put("acciones", actionLog)
                .toString()
        } catch (ex: Exception) {
            appendLog("RUN error: ${ex.message}")
            errorJson(ex.message ?: "Error desconocido")
        }
    }

    private fun navigateWithFallback(rawUrl: String, timeoutSeconds: Int): NavigationAttempt {
        val candidates = buildNavigationCandidates(rawUrl)
        var lastError = "timeout"
        var usedUrl = ""
        for (candidate in candidates) {
            val attempt = navigateToSync(candidate, timeoutSeconds)
            usedUrl = attempt.urlUsed
            if (attempt.ok) return attempt
            lastError = attempt.error
            appendLog("Reintento navegacion por error: $lastError")
        }
        return NavigationAttempt(ok = false, urlUsed = usedUrl, error = lastError)
    }

    private fun buildNavigationCandidates(rawUrl: String): List<String> {
        val normalized = normalizeUrl(rawUrl)
        if (normalized.isBlank()) return emptyList()

        val list = mutableListOf(normalized)
        if (normalized.contains("google.com/maps", ignoreCase = true)) {
            list.add("https://maps.google.com")
            list.add("https://www.google.com/maps?hl=es")
        }
        return list.distinct()
    }

    private fun navigateToSync(rawUrl: String, timeoutSeconds: Int): NavigationAttempt {
        val normalized = normalizeUrl(rawUrl)
        if (normalized.isBlank()) {
            return NavigationAttempt(ok = false, urlUsed = rawUrl, error = "URL vacia")
        }

        val latch = CountDownLatch(1)
        pendingNavigationError = ""
        pendingNavigationFinishedUrl = ""
        pendingNavigationLatch = latch
        mainHandler.post { webView.loadUrl(normalized) }
        val completed = latch.await(timeoutSeconds.toLong(), TimeUnit.SECONDS)
        if (pendingNavigationLatch === latch) {
            pendingNavigationLatch = null
        }

        if (!completed) {
            return NavigationAttempt(ok = false, urlUsed = normalized, error = "timeout")
        }

        if (pendingNavigationError.isNotBlank()) {
            return NavigationAttempt(ok = false, urlUsed = normalized, error = pendingNavigationError)
        }

        val finalUrl = pendingNavigationFinishedUrl
        if (finalUrl.startsWith("chrome-error://", ignoreCase = true)) {
            return NavigationAttempt(ok = false, urlUsed = normalized, error = "chrome-error")
        }

        return NavigationAttempt(ok = true, urlUsed = normalized, error = "")
    }

    private fun executeTouchScript(script: String): TouchExecutionResult {
        val trimmed = script.trim()
        if (trimmed.isBlank()) {
            return TouchExecutionResult(ok = false, stepsExecuted = 0, error = "Script vacio")
        }

        val root = try {
            JSONObject(trimmed)
        } catch (_: Exception) {
            return TouchExecutionResult(ok = false, stepsExecuted = 0, error = "MOUSE no es JSON")
        }

        val format = root.optString("format")
        if (!format.equals(TOUCH_SCRIPT_FORMAT, ignoreCase = true)) {
            return TouchExecutionResult(ok = false, stepsExecuted = 0, error = "Formato '$format' no soportado")
        }

        val steps = root.optJSONArray("steps") ?: JSONArray()
        var executed = 0
        for (i in 0 until steps.length()) {
            val step = steps.optJSONObject(i) ?: continue
            val delayMs = step.optLong("delayMs", 0L)
            if (delayMs > 0) {
                TimeUnit.MILLISECONDS.sleep(delayMs)
            }

            val type = step.optString("type").trim().lowercase(Locale.US)
            when (type) {
                "tap" -> {
                    val ok = runTouchTap(step)
                    if (!ok) {
                        return TouchExecutionResult(ok = false, stepsExecuted = executed, error = "Tap fallo en paso ${i + 1}")
                    }
                    executed += 1
                }

                "scroll" -> {
                    val ok = runTouchScroll(step)
                    if (!ok) {
                        return TouchExecutionResult(ok = false, stepsExecuted = executed, error = "Scroll fallo en paso ${i + 1}")
                    }
                    executed += 1
                }

                else -> {
                    return TouchExecutionResult(ok = false, stepsExecuted = executed, error = "Tipo '$type' no soportado")
                }
            }
        }

        return TouchExecutionResult(ok = true, stepsExecuted = executed, error = "")
    }

    private fun runTouchTap(step: JSONObject): Boolean {
        val selector = step.optString("selector")
        val xPercent = step.optDouble("xPercent", 0.5).coerceIn(0.0, 1.0)
        val yPercent = step.optDouble("yPercent", 0.5).coerceIn(0.0, 1.0)
        val selectorQ = JSONObject.quote(selector)

        val js = """
            (function(){
              try {
                var selector = $selectorQ;
                var xp = $xPercent;
                var yp = $yPercent;
                var el = null;
                if (selector) {
                  try { el = document.querySelector(selector); } catch (e) {}
                }
                if (!el) {
                  var x = Math.round((window.innerWidth || 1) * xp);
                  var y = Math.round((window.innerHeight || 1) * yp);
                  el = document.elementFromPoint(x, y);
                }
                if (!el) return "NO_ELEMENT";
                try { if (el.focus) el.focus(); } catch (e) {}
                try { if (el.click) el.click(); } catch (e) {}
                try {
                  var ev = new MouseEvent("click", { view: window, bubbles: true, cancelable: true });
                  el.dispatchEvent(ev);
                } catch (e) {}
                return "OK";
              } catch (ex) {
                return "ERR:" + String(ex);
              }
            })();
        """.trimIndent()

        val rs = evaluateRawJavaScriptSync(js, timeoutSeconds = 8)
        return rs.contains("OK")
    }

    private fun runTouchScroll(step: JSONObject): Boolean {
        val scrollX = step.optInt("scrollX", 0)
        val scrollY = step.optInt("scrollY", 0)
        val js = """
            (function(){
              try {
                window.scrollTo($scrollX, $scrollY);
                return "OK";
              } catch (ex) {
                return "ERR:" + String(ex);
              }
            })();
        """.trimIndent()
        val rs = evaluateRawJavaScriptSync(js, timeoutSeconds = 8)
        return rs.contains("OK")
    }

    private fun applyTemplate(template: String, replacements: Map<String, String>): String {
        var result = template
        replacements.forEach { (k, v) ->
            result = result.replace("@@$k@@", v)
            result = result.replace("@@${k.uppercase()}@@", v)
            result = result.replace("@@${k.lowercase()}@@", v)
        }
        return result
    }

    private fun loadConfigSnapshot(): FlowConfigSnapshot {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return FlowConfigSnapshot(
            soapUrl = prefs.getString(KEY_SOAP_URL, WebDatos.DEFAULT_URL).orEmpty().ifBlank { WebDatos.DEFAULT_URL },
            base = prefs.getString(KEY_DB_BASE, DEFAULT_DB_BASE).orEmpty().ifBlank { DEFAULT_DB_BASE },
            soapToken = prefs.getString(KEY_SOAP_TOKEN, DEFAULT_SOAP_TOKEN).orEmpty().ifBlank { DEFAULT_SOAP_TOKEN },
            clientToken = prefs.getString(KEY_CLIENT_TOKEN, DEFAULT_CLIENT_TOKEN).orEmpty().ifBlank { DEFAULT_CLIENT_TOKEN },
            paso = prefs.getString(KEY_PASO, DEFAULT_PASO).orEmpty().ifBlank { DEFAULT_PASO }
        )
    }

    inner class DroneSignalRBridge {
        @JavascriptInterface
        fun onPageReady() {
            runOnUiThread {
                wsBridgeReady = true
                appendWsLog("Bridge listo")
                if (wsPendingConnect && wsCurrentServerUrl.isNotBlank() && wsCurrentDroneId.isNotBlank()) {
                    wsPendingConnect = false
                    evalSignalRBridge(
                        "window.droneSocket && window.droneSocket.connect(${JSONObject.quote(wsCurrentServerUrl)}, ${JSONObject.quote(wsCurrentDroneId)});"
                    )
                }
            }
        }

        @JavascriptInterface
        fun onLog(message: String) {
            appendWsLog(message)
        }

        @JavascriptInterface
        fun onStateChanged(state: String) {
            runOnUiThread {
                updateWsState(state)
                if (state.startsWith("Error", ignoreCase = true) && wsAutoReconnectEnabled) {
                    mainHandler.removeCallbacks(wsReconnectRunnable)
                    mainHandler.postDelayed(wsReconnectRunnable, WS_RECONNECT_DELAY_MS)
                }
            }
        }

        @JavascriptInterface
        fun onInstruction(
            droneId: String,
            instruction: String,
            paramsJson: String,
            correlationId: String,
            timestamp: String
        ) {
            runOnUiThread {
                handleDroneInstruction(droneId, instruction, paramsJson, correlationId, timestamp)
            }
        }

        @JavascriptInterface
        fun onRegistered(droneId: String, connectionId: String, timestamp: String) {
            runOnUiThread {
                wsConnected = true
                updateWsState("Connected")
                val shortId = if (connectionId.length > 8) connectionId.take(8) + "..." else connectionId
                tvWsDroneInfo.text = "ID: $droneId ($shortId)"
                appendWsLog("[$timestamp] Registrado como: $droneId")
            }
        }

        @JavascriptInterface
        fun onStatusUpdated(droneId: String, statusJson: String, timestamp: String) {
            appendWsLog("[$timestamp] ESTADO [$droneId]: $statusJson")
        }

        @JavascriptInterface
        fun onDroneConnected(droneId: String, timestamp: String) {
            appendWsLog("[$timestamp] + Drone conectado: $droneId")
        }

        @JavascriptInterface
        fun onDroneDisconnected(droneId: String, timestamp: String) {
            appendWsLog("[$timestamp] - Drone desconectado: $droneId")
        }

        @JavascriptInterface
        fun onDisconnected() {
            runOnUiThread {
                wsConnected = false
                updateWsState("Disconnected")
                appendWsLog("Conexion cerrada")
                if (wsAutoReconnectEnabled) {
                    mainHandler.removeCallbacks(wsReconnectRunnable)
                    mainHandler.postDelayed(wsReconnectRunnable, WS_RECONNECT_DELAY_MS)
                    appendWsLog("Reconexion programada")
                }
            }
        }
    }

    private fun errorJson(message: String): String {
        return JSONObject()
            .put("ok", false)
            .put("error", message)
            .toString()
    }

    private fun maskValue(value: String): String {
        if (value.isBlank()) return "(vacio)"
        if (value.length <= 8) return "****"
        return value.take(4) + "..." + value.takeLast(4)
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        wsAutoReconnectEnabled = false
        mainHandler.removeCallbacks(wsReconnectRunnable)
        disconnectDroneSignalR(manual = false)
        stopRemoteServer(showMessage = false)
        dbExecutor.shutdownNow()
        mainHandler.removeCallbacks(autoSaveRunnable)
    }

    companion object {
        private const val PREFS_NAME = "webcommandapp_config"
        private const val KEY_SOAP_URL = "soap_url"
        private const val KEY_DB_BASE = "db_base"
        private const val KEY_SOAP_TOKEN = "soap_token"
        private const val KEY_CLIENT_TOKEN = "client_token"
        private const val KEY_PASO = "paso"
        private const val KEY_SERVER_PORT = "server_port"
        private const val KEY_SQL_QUERY = "sql_query"
        private const val KEY_TOUCH_SCRIPT = "touch_script"
        private const val KEY_WS_SERVER_URL = "ws_server_url"
        private const val KEY_WS_DRONE_ID = "ws_drone_id"
        private const val KEY_WS_STATUS_PAYLOAD = "ws_status_payload"

        private const val DEFAULT_DB_BASE = "AZURE_VPS"
        private const val DEFAULT_SOAP_TOKEN = "6%9FioCcj&HMPlq-K13*86evSXuUL-EiLoZFblCI"
        private const val DEFAULT_CLIENT_TOKEN = "C2LvqIsEdZu1n1z6WWaiBmOSjJJE3N"
        private const val DEFAULT_PASO = "1"
        private const val DEFAULT_SQL = "SELECT TOP 1 TOKEN, NOMBRE, SUCURSAL, CODIGO FROM [dbx.GENE].dbo.WEB_SCRAPING_CLI"
        private const val DEFAULT_WS_SERVER_URL = "https://test1.bitcode.com.co"
        private const val DEFAULT_WS_DRONE_ID = "12345"
        private const val DEFAULT_WS_STATUS_PAYLOAD = """{"respuesta":"ok"}"""
        private const val AUTOSAVE_DELAY_MS = 600L
        private const val NAV_TIMEOUT_SECONDS = 25
        private const val TOUCH_SCRIPT_FORMAT = "android_touch_v1"
        private const val WS_RECONNECT_DELAY_MS = 10000L
    }
}

private data class FlowConfigSnapshot(
    val soapUrl: String,
    val base: String,
    val soapToken: String,
    val clientToken: String,
    val paso: String
)

private data class NavigationAttempt(
    val ok: Boolean,
    val urlUsed: String,
    val error: String
)

private data class TouchStep(
    val type: String,
    val delayMs: Long,
    val selector: String,
    val xPercent: Double,
    val yPercent: Double,
    val scrollX: Int,
    val scrollY: Int
)

private data class TouchExecutionResult(
    val ok: Boolean,
    val stepsExecuted: Int,
    val error: String
)
