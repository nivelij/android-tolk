package dev.nivelij.tolk

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dev.nivelij.tolk.ui.TolkApp

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT))
        window.isNavigationBarContrastEnforced = false
        if (savedInstanceState == null) handle(intent)
        setContent { TolkApp(vm) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_SETTINGS, false) == true) vm.openSettings()
        when (intent?.action) {
            Intent.ACTION_PROCESS_TEXT ->
                intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.let(vm::receiveText)
            Intent.ACTION_SEND -> {
                val image = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                if (image != null && intent.type?.startsWith("image/") == true) vm.receivePhoto(image)
                else intent.getStringExtra(Intent.EXTRA_TEXT)?.let(vm::receiveText)
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_SETTINGS = "open_settings"
    }
}
