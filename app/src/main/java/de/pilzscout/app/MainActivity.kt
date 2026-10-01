package de.pilzscout.app

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import de.pilzscout.app.ml.DeviceBenchmark
import de.pilzscout.app.ui.AppRoot
import kotlinx.coroutines.launch
import java.io.File

/**
 * AppCompatActivity (not ComponentActivity) so that per-app language selection
 * works on API < 33 through AppCompatDelegate.setApplicationLocales.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { AppRoot() }
        // Debug builds: `am start ... --es pilzscout.benchmark files/bench` benchmarks accelerator variants
        // on model files in that directory (see DeviceBenchmark); the app itself is unaffected.
        maybeBenchmark(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        maybeBenchmark(intent)
    }

    private fun maybeBenchmark(intent: android.content.Intent?) {
        val benchDir = intent?.getStringExtra("pilzscout.benchmark") ?: return
        if (!DeviceBenchmark.isDebuggable(this)) return
        val dir = if (benchDir.startsWith("/")) File(benchDir) else File(filesDir.parentFile, benchDir)
        lifecycleScope.launch { runCatching { DeviceBenchmark.run(applicationContext, dir) } }
    }

    /**
     * uiMode is in configChanges, so a theme switch from settings arrives here instead of recreating
     * the activity. enableEdgeToEdge picks its system-bar icon colours from the configuration at call
     * time, so call it again to keep the status bar icons readable on the new background.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        enableEdgeToEdge()
    }
}
