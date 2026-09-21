package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.ui.theme.ArcheryGold
import com.example.ui.theme.JungleDarkBg
import com.example.ui.theme.JungleGreenLight
import com.example.ui.theme.JungleGreenMedium
import com.example.ui.theme.JungleSurfaceDark
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

  private var activeWebView: WebView? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Full screen setup with edge-to-edge
    enableEdgeToEdge()
    setupImmersiveFullScreen()

    // Route hardware volume keys to media/game audio stream
    volumeControlStream = AudioManager.STREAM_MUSIC

    // Keep screen active while playing the game
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    // Layout in display cutout mode to maximize screen real estate on notched devices
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      window.attributes.layoutInDisplayCutoutMode =
          WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    setContent {
      MyApplicationTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("game_surface"),
            color = JungleDarkBg
        ) {
          JungleArcheryGameScreen(
              gameUrl = "https://junglearchery.vercel.app",
              onWebViewCreated = { activeWebView = it },
              onExitGame = { finish() }
          )
        }
      }
    }
  }

  private fun setupImmersiveFullScreen() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
    insetsController.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    insetsController.hide(WindowInsetsCompat.Type.systemBars())
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) {
      setupImmersiveFullScreen()
    }
  }

  override fun onResume() {
    super.onResume()
    setupImmersiveFullScreen()
    activeWebView?.onResume()
    activeWebView?.resumeTimers()
  }

  override fun onPause() {
    super.onPause()
    activeWebView?.onPause()
    activeWebView?.pauseTimers()
  }

  override fun onDestroy() {
    activeWebView?.let { wv ->
      wv.stopLoading()
      (wv.parent as? ViewGroup)?.removeView(wv)
      wv.destroy()
    }
    activeWebView = null
    super.onDestroy()
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun JungleArcheryGameScreen(
    gameUrl: String,
    onWebViewCreated: (WebView) -> Unit,
    onExitGame: () -> Unit,
    modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var webViewRef by remember { mutableStateOf<WebView?>(null) }
  var isLoading by remember { mutableStateOf(true) }
  var progress by remember { mutableFloatStateOf(0.1f) }
  var hasError by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf("") }
  var showExitDialog by remember { mutableStateOf(false) }

  // Graceful back handling: navigate within game webview or confirm exit
  BackHandler(enabled = true) {
    val wv = webViewRef
    if (wv != null && wv.canGoBack()) {
      wv.goBack()
    } else {
      showExitDialog = true
    }
  }

  if (showExitDialog) {
    AlertDialog(
        onDismissRequest = { showExitDialog = false },
        modifier = Modifier.testTag("exit_dialog"),
        containerColor = JungleSurfaceDark,
        icon = {
          Image(
              painter = painterResource(id = R.drawable.ic_launcher_foreground),
              contentDescription = "Junglearchery",
              modifier = Modifier.size(56.dp)
          )
        },
        title = {
          Text(
              text = "Exit Junglearchery?",
              color = Color.White,
              fontWeight = FontWeight.Bold
          )
        },
        text = {
          Text(
              text = "Are you sure you want to leave the game? Your current session progress will end.",
              color = Color.White.copy(alpha = 0.85f)
          )
        },
        confirmButton = {
          Button(
              onClick = {
                showExitDialog = false
                onExitGame()
              },
              colors = ButtonDefaults.buttonColors(
                  containerColor = ArcheryGold,
                  contentColor = Color.Black
              ),
              modifier = Modifier.testTag("confirm_exit_button")
          ) {
            Text("Exit Game", fontWeight = FontWeight.SemiBold)
          }
        },
        dismissButton = {
          OutlinedButton(
              onClick = { showExitDialog = false },
              modifier = Modifier.testTag("cancel_exit_button")
          ) {
            Text("Keep Playing", color = JungleGreenLight)
          }
        }
    )
  }

  Box(
      modifier = modifier
          .fillMaxSize()
          .background(JungleDarkBg)
          .testTag("game_container")
  ) {
    // Primary Full-Screen Game View
    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .testTag("game_webview"),
        factory = { ctx ->
          WebView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.BLACK)
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true
            overScrollMode = View.OVER_SCROLL_NEVER
            setLayerType(View.LAYER_TYPE_HARDWARE, null)

            settings.apply {
              javaScriptEnabled = true
              domStorageEnabled = true
              // Sound access: enable autoplay without user gestures
              mediaPlaybackRequiresUserGesture = false
              // Mobile & Tablet responsiveness
              loadWithOverviewMode = true
              useWideViewPort = true
              setSupportZoom(false)
              builtInZoomControls = false
              displayZoomControls = false
              cacheMode = WebSettings.LOAD_DEFAULT
              mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
              allowFileAccess = false
              allowContentAccess = true
            }

            webChromeClient = object : WebChromeClient() {
              override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress = newProgress.coerceIn(0, 100) / 100f
                if (newProgress >= 100) {
                  isLoading = false
                }
              }

              override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                return true
              }
            }

            webViewClient = object : WebViewClient() {
              override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                isLoading = true
                hasError = false
              }

              override fun onPageFinished(view: WebView?, url: String?) {
                isLoading = false
                // Optimize viewport styling, disable accidental selection or bounce
                view?.evaluateJavascript(
                    """
                    (function() {
                        var css = 'body, html { margin:0; padding:0; overflow:hidden !important; -webkit-touch-callout:none !important; -webkit-user-select:none !important; user-select:none !important; touch-action:manipulation !important; }';
                        var style = document.createElement('style');
                        style.type = 'text/css';
                        style.appendChild(document.createTextNode(css));
                        document.head.appendChild(style);
                    })();
                    """.trimIndent(),
                    null
                )
              }

              override fun onReceivedError(
                  view: WebView?,
                  request: WebResourceRequest?,
                  error: WebResourceError?
              ) {
                if (request?.isForMainFrame == true) {
                  hasError = true
                  isLoading = false
                  errorMessage = error?.description?.toString()
                      ?: "Unable to load Jungle Archery. Please check your internet connection."
                }
              }
            }

            webViewRef = this
            onWebViewCreated(this)
            loadUrl(gameUrl)
          }
        },
        update = { /* Updates handled via state */ }
    )

    // Loading overlay
    AnimatedVisibility(
        visible = isLoading && !hasError,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
      Box(
          modifier = Modifier
              .fillMaxSize()
              .background(JungleDarkBg)
              .testTag("loading_overlay"),
          contentAlignment = Alignment.Center
      ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(32.dp)
                .widthIn(max = 400.dp)
        ) {
          Box(
              modifier = Modifier
                  .size(110.dp)
                  .clip(RoundedCornerShape(24.dp))
                  .background(JungleSurfaceDark),
              contentAlignment = Alignment.Center
          ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "Jungle Archery Logo",
                modifier = Modifier
                    .size(96.dp)
                    .testTag("app_logo_image")
            )
            CircularProgressIndicator(
                progress = { progress },
                color = ArcheryGold,
                trackColor = Color.Transparent,
                strokeWidth = 4.dp,
                modifier = Modifier.size(106.dp)
            )
          }

          Spacer(modifier = Modifier.height(24.dp))

          Text(
              text = "Junglearchery",
              color = Color.White,
              fontSize = 24.sp,
              fontWeight = FontWeight.Bold,
              letterSpacing = 1.sp
          )

          Spacer(modifier = Modifier.height(8.dp))

          Text(
              text = "Loading archery arena...",
              color = Color.White.copy(alpha = 0.7f),
              fontSize = 14.sp
          )

          Spacer(modifier = Modifier.height(20.dp))

          LinearProgressIndicator(
              progress = { progress },
              color = ArcheryGold,
              trackColor = Color.White.copy(alpha = 0.15f),
              modifier = Modifier
                  .fillMaxWidth(0.6f)
                  .height(6.dp)
                  .clip(RoundedCornerShape(3.dp))
          )
        }
      }
    }

    // Network / Error Fallback Screen
    AnimatedVisibility(
        visible = hasError,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
      Box(
          modifier = Modifier
              .fillMaxSize()
              .background(JungleDarkBg)
              .padding(24.dp)
              .testTag("error_screen"),
          contentAlignment = Alignment.Center
      ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.widthIn(max = 480.dp)
        ) {
          Box(
              modifier = Modifier
                  .size(88.dp)
                  .clip(CircleShape)
                  .background(JungleSurfaceDark),
              contentAlignment = Alignment.Center
          ) {
            Icon(
                imageVector = Icons.Default.WifiOff,
                contentDescription = "Connection Error",
                tint = ArcheryGold,
                modifier = Modifier.size(44.dp)
            )
          }

          Spacer(modifier = Modifier.height(24.dp))

          Text(
              text = "Connection Offline",
              color = Color.White,
              fontSize = 22.sp,
              fontWeight = FontWeight.Bold
          )

          Spacer(modifier = Modifier.height(10.dp))

          Text(
              text = if (errorMessage.isNotBlank()) errorMessage else "Jungle Archery requires an active internet connection to load game assets.",
              color = Color.White.copy(alpha = 0.75f),
              fontSize = 14.sp,
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(horizontal = 16.dp)
          )

          Spacer(modifier = Modifier.height(28.dp))

          Button(
              onClick = {
                hasError = false
                isLoading = true
                progress = 0.1f
                webViewRef?.loadUrl(gameUrl)
              },
              colors = ButtonDefaults.buttonColors(
                  containerColor = ArcheryGold,
                  contentColor = Color.Black
              ),
              shape = RoundedCornerShape(24.dp),
              modifier = Modifier
                  .height(48.dp)
                  .testTag("retry_button")
          ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text("Retry Connection", fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
  MyApplicationTheme { Greeting("Android") }
}

