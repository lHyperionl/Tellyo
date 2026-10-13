package com.tellyo.app.feature.player

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.view.ViewGroup
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tellyo.app.core.player.EmbedAdRequestFilter
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Hosts a broadcaster-published web player without exposing its temporary manifest
 * to Tellyo. The broadcaster remains responsible for playback, access rules,
 * and authentication. Optional network filtering blocks separate ad requests.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OfficialEmbedPlayerScreen(
    url: String,
    onBack: () -> Unit,
    reloadGeneration: Int = 0,
    blockAds: Boolean = true,
) {
    BackHandler(onBack = onBack)
    var webView by remember { mutableStateOf<WebView?>(null) }
    // WebView invokes request interception on a background thread.
    val adBlockingEnabled = remember { AtomicBoolean(blockAds) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(webView, lifecycleOwner) {
        val view = webView
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> view?.setHostActive(true)
                Lifecycle.Event.ON_STOP -> view?.setHostActive(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        view?.setHostActive(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Persist broadcaster session cookies when leaving the player,
            // including after a successful sign-in in the embedded page.
            CookieManager.getInstance().flush()
            webView?.apply {
                stopLoading()
                loadUrl("about:blank")
                clearHistory()
                removeAllViews()
                destroy()
            }
            webView = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ComposeColor.Black),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    webView = this
                    val playerWebView = this
                    setBackgroundColor(Color.BLACK)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        setSupportZoom(false)
                        builtInZoomControls = false
                        displayZoomControls = false
                        allowFileAccess = false
                        allowContentAccess = false
                        setSupportMultipleWindows(false)
                    }
                    CookieManager.getInstance().apply {
                        setAcceptCookie(true)
                        setAcceptThirdPartyCookies(playerWebView, true)
                    }
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        // Installed before loadUrl: pre-roll requests are filtered
                        // during initialization, not after playback has started.
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest,
                        ): WebResourceResponse? {
                            return if (EmbedAdRequestFilter.shouldBlock(
                                    request.url.toString(), request.isForMainFrame, adBlockingEnabled.get(),
                                )) {
                                android.util.Log.d("TellyoEmbedAds", "Blocked ad host: ${request.url.host}")
                                WebResourceResponse(
                                    "text/plain", "UTF-8", 204, "No Content",
                                    mapOf("Cache-Control" to "no-store"),
                                    ByteArrayInputStream(ByteArray(0)),
                                )
                            } else null
                        }

                        override fun onPageCommitVisible(view: WebView, loadedUrl: String) {
                            super.onPageCommitVisible(view, loadedUrl)
                            prepareOfficialPage(view, loadedUrl)
                            view.setHostActive(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
                        }

                        override fun onPageFinished(view: WebView, loadedUrl: String) {
                            super.onPageFinished(view, loadedUrl)
                            prepareOfficialPage(view, loadedUrl)
                            view.setHostActive(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean = !request.url.isApprovedTopLevelDestination()
                    }
                    isFocusable = true
                    isFocusableInTouchMode = true
                    setOnKeyListener { _, keyCode, event ->
                        // WebView can consume TV arrows for its own spatial
                        // navigation before JavaScript receives a keydown.
                        val destination = Uri.parse(this.url.orEmpty())
                        val key = when (keyCode) {
                            KeyEvent.KEYCODE_DPAD_LEFT -> "ArrowLeft"
                            KeyEvent.KEYCODE_DPAD_RIGHT -> "ArrowRight"
                            KeyEvent.KEYCODE_DPAD_UP -> "ArrowUp"
                            KeyEvent.KEYCODE_DPAD_DOWN -> "ArrowDown"
                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> "Enter"
                            else -> null
                        }
                        val remoteFunction = when {
                            destination.scheme != "https" -> null
                            destination.host == "www.ta3.com" && destination.path == "/live" -> "__tellyoTa3RemoteKey"
                            destination.isJojPlayer() -> "__tellyoJojRemoteKey"
                            else -> null
                        }
                        if (remoteFunction != null && key != null) {
                            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                                evaluateJavascript("window.$remoteFunction && window.$remoteFunction('$key')") { result ->
                                    if (result == "\"input\"") {
                                        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                                            .showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                                    }
                                }
                            }
                            true
                        } else false
                    }
                    requestFocus()
                    tag = Triple(url, reloadGeneration, blockAds)
                    loadUrl(url)
                }
            },
            update = { view ->
                adBlockingEnabled.set(blockAds)
                // Accepted login/consent navigation belongs to the current page session.
                if (view.tag != Triple(url, reloadGeneration, blockAds)) {
                    view.tag = Triple(url, reloadGeneration, blockAds)
                    view.loadUrl(url)
                }
            },
        )
    }
}

internal fun WebView.setHostActive(active: Boolean) {
    if (active) {
        resumeTimers()
        onResume()
    }
    evaluateJavascript("($EMBED_MEDIA_LIFECYCLE)($active)", null)
    if (!active) {
        onPause()
        pauseTimers()
    }
}

// Pause loaded and newly autoplaying media while stopped; preserve manual pause choices.
// Apply to same-origin child frames as well as the broadcaster's top-level embed.
private val EMBED_MEDIA_LIFECYCLE = """
    function(active) {
        function apply(win) {
            try {
                var doc = win.document;
                var state = win.__tellyoMediaLifecycle;
                if (!state) {
                    state = win.__tellyoMediaLifecycle = {active:true, resume:new Set()};
                    doc.addEventListener('play', function(event) {
                        if (!state.active && event.target.pause) {
                            state.resume.add(event.target);
                            event.target.pause();
                        }
                    }, true);
                }
                if (active && !state.active) {
                    state.active = true;
                    state.resume.forEach(function(media) {
                        if (media.isConnected) {
                            var result = media.play();
                            if (result && result.catch) result.catch(function() {});
                        }
                    });
                    state.resume.clear();
                } else if (!active) {
                    state.active = false;
                    doc.querySelectorAll('video,audio').forEach(function(media) {
                        if (!media.paused) state.resume.add(media);
                        media.pause();
                    });
                }
                for (var i = 0; i < win.frames.length; i++) apply(win.frames[i]);
            } catch (_) {}
        }
        apply(window);
    }
""".trimIndent()

private fun prepareOfficialPage(view: WebView, loadedUrl: String) {
    val destination = Uri.parse(loadedUrl)
    if (destination.scheme != "https") return
    if (destination.host == "media.cms.markiza.sk" && destination.path?.startsWith("/embed/") == true) {
        view.evaluateJavascript(CENTER_EMBED_VIDEO, null)
        view.evaluateJavascript(START_AUDIBLE_EMBED, null)
    } else if (destination.host == "www.ta3.com" && destination.path == "/live") {
        view.requestFocus()
        view.evaluateJavascript(FIT_TA3_PLAYER, null)
    } else if (destination.isJojPlayer()) {
        view.requestFocus()
        val script = view.context.assets.open("players/joj.js").bufferedReader().use { it.readText() }
        view.evaluateJavascript(script, null)
    }
}

private fun Uri.isJojPlayer(): Boolean = scheme == "https" && (
    host == "www.joj.sk" && path?.startsWith("/live/") == true ||
        host == "media.joj.sk" && path?.startsWith("/embed/") == true
    )

// Keep the page at its TV viewport and fit the entire video inside the
// broadcaster's media element, including non-16:9 programmes. Do not change
// element sizes: the broadcaster's ad player can use a zero-height wrapper.
private val CENTER_EMBED_VIDEO = """
    (function() {
        if (document.getElementById('tellyo-video-fit')) return;
        var style = document.createElement('style');
        style.id = 'tellyo-video-fit';
        style.textContent = '#ef-player video {' +
            'object-fit: contain !important;' +
            'object-position: 50% 50% !important;' +
            '}';
        document.head.appendChild(style);
    })()
""".trimIndent()

// The official embed deliberately mutes unattended autoplay. Opening a channel
// in Tellyo is a playback request, so once its first video starts, send the
// broadcaster's normal Play command. This clears its autoplay mute through its
// own controls. Run once so later user mute choices survive.
private val START_AUDIBLE_EMBED = """
    (function() {
        if (window.__tellyoAudibleStartInstalled) return;
        window.__tellyoAudibleStartInstalled = true;
        function startAudible() {
            var root = document.querySelector('#ef-player');
            if (!root || !Array.from(root.querySelectorAll('video')).some(function(v) {
                return !v.paused && v.videoWidth > 0;
            })) return;
            document.removeEventListener('playing', startAudible, true);
            window.postMessage({
                klebetnica: true,
                command: 'data',
                channels: ['ef-player'],
                data: {command: 'play', initiator: 'user'}
            }, window.location.origin);
        }
        document.addEventListener('playing', startAudible, true);
        startAudible();
    })()
""".trimIndent()

// Keep TA3's complete live page and player initialization, including consent
// and consent. Its existing player container is fitted to the TV viewport;
// source URLs and playback configuration stay with the broadcaster.
private val FIT_TA3_PLAYER = """
    (function() {
        if (document.getElementById('tellyo-ta3-fit')) return;
        var style = document.createElement('style');
        style.id = 'tellyo-ta3-fit';
        style.textContent = 'html, body {margin:0 !important;overflow:hidden !important;background:black !important;}' +
            '.live_embed {position:fixed !important;inset:0 !important;margin:0 !important;' +
                'width:100vw !important;height:100vh !important;z-index:2147483000 !important;background:black;}' +
            '.live_embed .video-header-embed-inner, #liveplayer, #videojs-liveplayer {' +
                'width:100% !important;height:100% !important;padding:0 !important;}' +
            '.live_embed video {object-fit:contain !important;object-position:50% 50% !important;}' +
            '.didomi-popup-notice button:focus, .live_embed button:focus {' +
                'outline:3px solid #1aa8ff !important;outline-offset:3px !important;}';
        document.head.appendChild(style);
        window.scrollTo(0, 0);
        function consentButtons() {
            return ['didomi-notice-disagree-button', 'didomi-notice-learn-more-button',
                'didomi-notice-agree-button'].map(function(id) {return document.getElementById(id);})
                .filter(function(b) {return b && b.closest('.didomi-popup-notice') && b.getBoundingClientRect().width > 0;});
        }
        var consentSelection = 'didomi-notice-disagree-button';
        function focusConsent() {
            var buttons = consentButtons();
            if (buttons.length && !buttons.includes(document.activeElement)) {
                (buttons.find(function(b) {return b.id === consentSelection;}) || buttons[0]).focus({preventScroll:true});
            }
        }
        // Focus choices, never make a consent decision on the user's behalf.
        new MutationObserver(focusConsent).observe(document.body, {childList:true,subtree:true});
        focusConsent();
        function remoteKey(key) {
            var buttons = consentButtons();
            var isConsent = buttons.length > 0;
            var popup = document.querySelector('#didomi-popup');
            var popupVisible = popup && popup.getBoundingClientRect().width > 0;
            if (!buttons.length) {
                var scope = popupVisible ? popup : document.querySelector('.live_embed');
                buttons = scope ? Array.from(scope.querySelectorAll('button')).filter(function(b) {
                    return b.getBoundingClientRect().width > 0 && !b.disabled;
                }) : [];
            }
            if (!buttons.length) return false;
            var index = buttons.indexOf(document.activeElement);
            if (key === 'Enter' && !popupVisible && index < 0) {
                var player = window.LBXPlayer && LBXPlayer.KalturaPlayer.getPlayer('player-liveplayer');
                if (player) {
                    if (player.paused) player.play(); else player.pause();
                    return true;
                }
            }
            if (isConsent && index < 0) index = buttons.findIndex(function(b) {return b.id === consentSelection;});
            if (key === 'ArrowRight' || key === 'ArrowDown' || key === 'ArrowLeft' || key === 'ArrowUp') {
                var step = key === 'ArrowRight' || key === 'ArrowDown' ? 1 : -1;
                var selected = buttons[(Math.max(index, 0) + step + buttons.length) % buttons.length];
                if (isConsent) consentSelection = selected.id;
                selected.focus({preventScroll:true});
            } else if (key === 'Enter') {
                buttons[Math.max(index, 0)].click();
            } else return false;
            return true;
        }
        window.__tellyoTa3RemoteKey = remoteKey;
        document.addEventListener('keydown', function(event) {
            if (remoteKey(event.key)) {
                event.preventDefault();
                event.stopPropagation();
            }
        }, true);
    })()
""".trimIndent()

private fun Uri.isApprovedTopLevelDestination(): Boolean {
    if (scheme != "https") return false
    val normalizedHost = host?.lowercase().orEmpty()
    return normalizedHost == "markiza.sk" ||
        normalizedHost.endsWith(".markiza.sk") ||
        normalizedHost == "joj.sk" || normalizedHost.endsWith(".joj.sk") ||
        normalizedHost == "ta3.com" || normalizedHost == "www.ta3.com"
}
