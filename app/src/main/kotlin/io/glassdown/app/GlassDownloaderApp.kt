package io.glassdown.app

import android.app.Application
import io.glassdown.app.extract.ExtractorRepository

class GlassDownloaderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        runCatching { ExtractorRepository.ensureInit() }
    }
}
