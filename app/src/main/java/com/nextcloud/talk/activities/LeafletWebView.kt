package com.nextcloud.talk.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.nextcloud.talk.databinding.LeafletWebviewBinding

class LeafletWebView : AppCompatActivity() {
    lateinit var binding: LeafletWebviewBinding


    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = LeafletWebviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.webview.settings.javaScriptEnabled = true

        binding.webview.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                view?.loadUrl(url)
                return true
            }
        }
        binding.webview.loadUrl("file:///android_asset/index.html?51.5263,13.0384");

    }
}