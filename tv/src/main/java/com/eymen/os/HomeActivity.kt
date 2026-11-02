package com.eymen.os

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class HomeActivity : ComponentActivity() {
    lateinit var state: LauncherState
    private val consumed = HashSet<Int>()

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (::state.isInitialized) state.reloadChannels()
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val reassert = Runnable { bringToFront() }

    /** TV woke up from standby: Whale may put its own home screen on top, so watch for a moment. */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!::state.isInitialized || !state.autoStart) return
            state.armWakeGuard()
            handler.postDelayed(reassert, 1500L)
        }
    }

    private fun bringToFront() {
        try {
            val i = Intent(this, HomeActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(i)
        } catch (e: Exception) {
        }
    }

    /** "İnternete bağlı olunca": every time the TV reaches a network the update check runs. */
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            handler.post { if (::state.isInitialized) state.onNetwork(true) }
        }

        override fun onLost(network: Network) {
            handler.post { if (::state.isInitialized) state.onNetwork(false) }
        }
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (::state.isInitialized) state.reloadApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUi()
        Fonts.init(this)
        state = LauncherState(this)
        setContent { LauncherRoot(state) }
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_PACKAGE_ADDED)
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED)
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED)
        filter.addDataScheme("package")
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        val screen = IntentFilter()
        screen.addAction(Intent.ACTION_SCREEN_ON)
        screen.addAction(Intent.ACTION_USER_PRESENT)
        ContextCompat.registerReceiver(this, screenReceiver, screen, ContextCompat.RECEIVER_EXPORTED)
        state.start()
        if (checkSelfPermission(LauncherState.PERM_TV) != PackageManager.PERMISSION_GRANTED) requestTvPermission()
        try {
            (getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager).registerDefaultNetworkCallback(networkCallback)
        } catch (e: Exception) {
        }
        handleRequest(intent)
    }

    private fun handleRequest(intent: Intent?) {
        val request = intent?.getStringExtra("guncelle") ?: return
        intent.removeExtra("guncelle")
        state.onRequest(request)
    }

    fun requestTvPermission() {
        try {
            permissionLauncher.launch(LauncherState.PERM_TV)
        } catch (e: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::state.isInitialized) {
            state.onHome()
            handleRequest(intent)
        }
    }

    private fun isSystemKey(code: Int): Boolean = when (code) {
        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
        KeyEvent.KEYCODE_MUTE, KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_TV_POWER, KeyEvent.KEYCODE_HOME,
        KeyEvent.KEYCODE_SLEEP, KeyEvent.KEYCODE_WAKEUP, KeyEvent.KEYCODE_TV_INPUT, KeyEvent.KEYCODE_ASSIST,
        KeyEvent.KEYCODE_VOICE_ASSIST, KeyEvent.KEYCODE_APP_SWITCH -> true
        else -> false
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!::state.isInitialized) return super.dispatchKeyEvent(event)
        val code = event.keyCode
        if (isSystemKey(code)) return super.dispatchKeyEvent(event)
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (state.onKey(code)) {
                    consumed.add(code)
                    true
                } else super.dispatchKeyEvent(event)
            }
            KeyEvent.ACTION_UP -> if (consumed.remove(code)) true else super.dispatchKeyEvent(event)
            else -> super.dispatchKeyEvent(event)
        }
    }

    override fun onStart() {
        super.onStart()
        handler.removeCallbacks(reassert)
        if (::state.isInitialized) state.onStart()
    }

    override fun onStop() {
        if (::state.isInitialized) {
            state.onStop()
            // Right after power-on eski ana ekran opens its own home screen over ours: come back on top.
            if (state.shouldReassert()) handler.postDelayed(reassert, 900L)
        }
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try {
            unregisterReceiver(packageReceiver)
            unregisterReceiver(screenReceiver)
            (getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
        }
        if (::state.isInitialized) state.onDestroy()
        super.onDestroy()
    }
}
