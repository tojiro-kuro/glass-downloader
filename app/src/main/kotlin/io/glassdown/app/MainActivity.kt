package io.glassdown.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import io.glassdown.app.ui.DownloaderScreen
import io.glassdown.app.ui.DownloaderViewModel
import io.glassdown.app.ui.glass.GlassPalette

class MainActivity : ComponentActivity() {

    private var sharedUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sharedUrl = extractSharedUrl(intent)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = GlassPalette.Obsidian,
                    surface = GlassPalette.ObsidianSoft,
                    primary = GlassPalette.Pearl,
                    onPrimary = GlassPalette.Obsidian,
                    onBackground = GlassPalette.Pearl,
                    onSurface = GlassPalette.Pearl,
                )
            ) {
                val vm: DownloaderViewModel = viewModel()
                DownloaderScreen(viewModel = vm, initialUrl = sharedUrl)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractSharedUrl(intent)?.let { sharedUrl = it }
    }

    private fun extractSharedUrl(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        val url = Regex("https?://\\S+").find(text)?.value
        return url ?: text.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }
}
