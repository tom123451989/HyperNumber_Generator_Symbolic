package com.hypernumber.singularity

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.URLDecoder
import java.util.Base64

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var assetLoader: WebViewAssetLoader
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooserRequest = 7001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(247, 248, 252)
        window.navigationBarColor = android.graphics.Color.rgb(247, 248, 252)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView = WebView(this)
        setContentView(webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = true
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
        webView.settings.setSupportMultipleWindows(false)
        webView.settings.loadsImagesAutomatically = true
        webView.addJavascriptInterface(DownloadBridge(), "HyperNumberDownload")
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest) =
                assetLoader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                val url = request.url
                if (url.scheme == "https" && url.host == "appassets.androidplatform.net") return false
                if (url.scheme == "https" || url.scheme == "http") {
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, url)) }
                    return true
                }
                return url.scheme != "about"
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                installDownloadHook()
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                this@MainActivity.fileChooserCallback?.onReceiveValue(null)
                this@MainActivity.fileChooserCallback = filePathCallback
                val intent = fileChooserParams.createIntent().apply {
                    action = Intent.ACTION_OPEN_DOCUMENT
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                return try {
                    startActivityForResult(intent, fileChooserRequest)
                    true
                } catch (_: Exception) {
                    this@MainActivity.fileChooserCallback = null
                    false
                }
            }
        }
        if (savedInstanceState == null) {
            webView.loadUrl("https://appassets.androidplatform.net/assets/public/index.html")
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    private fun installDownloadHook() {
        val js = """
            (function(){
              if (window.__hnDownloadHook) return; window.__hnDownloadHook = true;
              document.addEventListener('click', function(ev){
                var a = ev.target && ev.target.closest ? ev.target.closest('a') : null;
                if (!a || !a.href || (!a.href.startsWith('blob:') && !a.href.startsWith('data:'))) return;
                ev.preventDefault(); ev.stopPropagation();
                var name = a.download || 'HyperNumber-export.xlsx';
                if (a.href.startsWith('blob:')) {
                  fetch(a.href).then(function(r){return r.blob()}).then(function(b){
                    var fr=new FileReader(); fr.onloadend=function(){
                      var data=String(fr.result).split(',')[1]||'';
                      window.HyperNumberDownload.saveBase64(data,name,b.type||'application/octet-stream');
                    }; fr.readAsDataURL(b);
                  }).catch(function(e){console.error('Export failed',e)});
                } else {
                  var parts=a.href.split(','); var meta=parts[0]; var data=parts.slice(1).join(',');
                  if (meta.indexOf(';base64')>=0) window.HyperNumberDownload.saveBase64(data,name, (meta.match(/data:([^;]+)/)||[])[1]||'application/octet-stream');
                  else window.HyperNumberDownload.saveText(decodeURIComponent(data),name,(meta.match(/data:([^;]+)/)||[])[1]||'text/plain');
                }
              }, true);
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    inner class DownloadBridge {
        @JavascriptInterface
        fun saveBase64(base64: String, filename: String, mimeType: String) {
            try {
                val bytes = Base64.getDecoder().decode(base64)
                saveToDownloads(bytes, filename, mimeType)
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this@MainActivity, "Export failed: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }

        @JavascriptInterface
        fun saveText(text: String, filename: String, mimeType: String) {
            saveToDownloads(text.toByteArray(Charsets.UTF_8), filename, mimeType)
        }
    }

    private fun saveToDownloads(bytes: ByteArray, requestedName: String, mimeType: String) {
        Thread {
            var stream: OutputStream? = null
            try {
                val safeName = requestedName.substringAfterLast('/').substringAfterLast('\\').take(120).ifBlank { "HyperNumber-export.bin" }
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType.ifBlank { "application/octet-stream" })
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/HyperNumber")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Could not create Downloads entry")
                stream = contentResolver.openOutputStream(uri) ?: throw IllegalStateException("Could not open output stream")
                stream.write(bytes)
                stream.flush()
                stream.close()
                stream = null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                    contentResolver.update(uri, done, null, null)
                }
                runOnUiThread { Toast.makeText(this, "Saved to Downloads/HyperNumber/$safeName", Toast.LENGTH_LONG).show() }
            } catch (e: Exception) {
                try { stream?.close() } catch (_: Exception) {}
                runOnUiThread { Toast.makeText(this, "Could not save export: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    @Deprecated("Deprecated by Android; retained for file chooser compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == fileChooserRequest) {
            val result = if (resultCode == Activity.RESULT_OK && data?.data != null) arrayOf(data.data!!) else null
            fileChooserCallback?.onReceiveValue(result)
            fileChooserCallback = null
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated by Android; retained for compatibility")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.removeJavascriptInterface("HyperNumberDownload")
            webView.destroy()
        }
        super.onDestroy()
    }
}
