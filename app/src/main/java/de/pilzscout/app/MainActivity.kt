package de.pilzscout.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import de.pilzscout.app.ui.AppRoot

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
    }
}
