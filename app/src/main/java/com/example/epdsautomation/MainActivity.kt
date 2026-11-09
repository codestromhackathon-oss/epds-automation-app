package com.example.epdsautomation

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    inner class AutomationBridge {
        @JavascriptInterface
        fun onAutomationSuccess(msg: String) {
            runOnUiThread {
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
            }
        }
        @JavascriptInterface
        fun onFailure(error: String) {
            runOnUiThread {
                Toast.makeText(this@MainActivity, "Error: $error", Toast.LENGTH_LONG).show()
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val webView: WebView = findViewById(R.id.webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.addJavascriptInterface(AutomationBridge(), "AndroidBridge")
        WebView.setWebContentsDebuggingEnabled(true)

        val epdsAutomationScript = """
            (function() {
                function triggerInputEvent(element, value) {
                    element.value = value;
                    element.dispatchEvent(new Event('input', { bubbles: true }));
                    element.dispatchEvent(new Event('change', { bubbles: true }));
                }
                let attempts = 0;
                function executeWorkflow() {
                    try {
                        let radioBtns = document.querySelectorAll("input[type='radio']");
                        if (radioBtns.length >= 2 && !radioBtns[1].checked) {
                            radioBtns[1].click();
                        }
                        let textInputs = document.querySelectorAll("input[type='text']");
                        if (textInputs.length > 0 && textInputs[0].value !== "365330211177") {
                            triggerInputEvent(textInputs[0], "365330211177");
                        }
                        let allInputs = Array.from(document.querySelectorAll('input'));
                        let refreshBtn = allInputs.find(el => el.value === 'Refresh' || (el.innerText && el.innerText.includes('Refresh')));
                        let extractedCaptcha = "";
                        if (refreshBtn) {
                            let parentTd = refreshBtn.closest('td');
                            if (parentTd && parentTd.nextElementSibling) {
                                let target = parentTd.nextElementSibling;
                                let inp = target.querySelector('input');
                                extractedCaptcha = inp ? inp.value.trim() : target.innerText.trim();
                            }
                        }
                        if (extractedCaptcha && extractedCaptcha.toLowerCase() !== "home") {
                            let captchaInput = Array.from(textInputs).find(inp => 
                                (inp.placeholder && inp.placeholder.toLowerCase().includes('captcha')) || (inp === textInputs[1])
                            );
                            if (captchaInput) {
                                triggerInputEvent(captchaInput, extractedCaptcha);
                                let searchBtn = allInputs.find(el => el.value === 'Search' || el.innerText?.trim() === 'Search');
                                if (searchBtn) {
                                    setTimeout(() => { searchBtn.click(); window.AndroidBridge.onAutomationSuccess("Executed!"); }, 500);
                                    return;
                                }
                            }
                        }
                        if (++attempts < 20) { setTimeout(executeWorkflow, 500); }
                        else { window.AndroidBridge.onFailure("Timeout waiting for elements."); }
                    } catch (err) { window.AndroidBridge.onFailure(err.message); }
                }
                setTimeout(executeWorkflow, 1500);
            })();
        """.trimIndent()

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                webView.evaluateJavascript(epdsAutomationScript, null)
            }
        }

        webView.loadUrl("https://epds.telangana.gov.in/FoodSecurityAct/?wicket:bookmarkablePage=:nic.fsc.foodsecurity.FscSearch")
    }
}
