// File: RichMessageWebView.kt
package com.playstudio.aiteacher  // Change to your app's package name

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

class RichMessageWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    init {
        setupWebView()
    }

    private fun setupWebView() {
        settings.javaScriptEnabled = true
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.domStorageEnabled = true
        settings.setSupportZoom(false)

        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                context.startActivity(Intent(Intent.ACTION_VIEW, request.url))
                return true
            }

            // Support older Android versions
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                url?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
                return true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                view?.evaluateJavascript("""
                    if (typeof MathJax !== 'undefined') {
                        MathJax.typesetPromise();
                    }
                    document.querySelectorAll('pre code').forEach((el) => {
                        hljs.highlightElement(el);
                    });
                """.trimIndent(), null)
            }
        }

        addJavascriptInterface(WebAppInterface(context), "Android")
    }

    fun displayFormattedContent(content: String) {
        val html = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
                ${getFormattedStyles()}
            </style>
            <!-- MathJax Configuration -->
            <script>
                MathJax = {
                    tex: {
                        inlineMath: [['$', '$'], ['\\(', '\\)']],
                        displayMath: [['$$', '$$'], ['\\[', '\\]']],
                        processEscapes: true,
                        packages: {'[+]': ['ams']}
                    },
                    options: {
                        skipHtmlTags: ['script', 'noscript', 'style', 'textarea', 'pre', 'code'],
                        ignoreHtmlClass: 'tex2jax_ignore'
                    },
                    startup: {
                        ready: () => {
                            MathJax.startup.defaultReady();
                            MathJax.startup.promise.then(() => {
                                console.log('MathJax initial typesetting complete');
                            });
                        }
                    }
                };
            </script>
            <!-- MathJax Loader -->
            <script id="MathJax-script" async src="https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-mml-chtml.js"></script>
            <!-- Highlight.js -->
            <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.7.0/styles/github.min.css">
            <script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.7.0/highlight.min.js"></script>
            <script>
                function copyCode(element) {
                    const code = element.parentElement.nextElementSibling.textContent;
                    Android.copyToClipboard(code);
                    element.textContent = "Copied!";
                    setTimeout(() => element.textContent = "Copy", 2000);
                }
            </script>
        </head>
        <body>
            <div class="rich-content">
                ${processContent(content)}
            </div>
        </body>
        </html>
        """.trimIndent()

        loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    private fun getFormattedStyles(): String {
        return """
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            color: #333;
            line-height: 1.5;
            padding: 8px;
            margin: 0;
            font-size: 15px;
        }
        .MathJax {
            color: #333 !important;
        }
        .code-block {
            background: #f6f8fa;
            border-radius: 6px;
            margin: 12px 0;
            overflow: hidden;
        }
        .code-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 8px 12px;
            background: #e1e4e8;
            font-family: monospace;
            font-size: 14px;
        }
        .copy-btn {
            background: #0366d6;
            color: white;
            border: none;
            border-radius: 4px;
            padding: 4px 8px;
            font-size: 12px;
            cursor: pointer;
        }
        pre {
            margin: 0;
            padding: 12px;
            overflow-x: auto;
        }
        code {
            font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
            font-size: 14px;
            border-radius: 3px;
            padding: 2px 4px;
        }
        """
    }

    private fun processContent(content: String): String {
        // Process LaTeX math expressions
        var processed = content.replace("\\$(.*?)\\$".toRegex()) { match ->
            "\\\\(${match.groupValues[1]}\\\\)"
        }.replace("\\$\\$(.*?)\\$\\$".toRegex()) { match ->
            "\\\\[${match.groupValues[1]}\\\\]"
        }

        // Process code blocks
        processed = processed.replace("```(\\w*)\n([\\s\\S]*?)\n```".toRegex()) { match ->
            val language = match.groupValues[1].takeIf { it.isNotBlank() } ?: "text"
            val code = match.groupValues[2]
            """
            <div class='code-block'>
                <div class='code-header'>
                    <span>${language.uppercase()}</span>
                    <button class='copy-btn' onclick='copyCode(this)'>Copy</button>
                </div>
                <pre><code class="language-$language">${code.htmlEscape()}</code></pre>
            </div>
            """
        }

        // Process inline code
        processed = processed.replace("`([^`]+)`".toRegex()) { match ->
            "<code>${match.groupValues[1].htmlEscape()}</code>"
        }

        // Process markdown links [text](url)
        processed = processed.replace("\\[([^\\]]+)\\]\((https?://[^)]+)\)".toRegex()) { match ->
            val text = match.groupValues[1]
            val url = match.groupValues[2]
            "<a href=\"$url\">${text.htmlEscape()}</a>"
        }

        // Convert plain URLs into clickable links
        processed = processed.replace("(?<!\")((https?://|www\.)[\w\-._~:/?#@!$&'()*+,;=%]+)".toRegex()) { match ->
            val url = if (match.value.startsWith("http")) match.value else "http://${match.value}"
            "<a href=\"$url\">${match.value}</a>"
        }

        return processed
    }

    private fun String.htmlEscape(): String {
        return this.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    class WebAppInterface(private val context: Context) {
        @JavascriptInterface
        fun copyToClipboard(text: String) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Copied code", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
        }
    }
}