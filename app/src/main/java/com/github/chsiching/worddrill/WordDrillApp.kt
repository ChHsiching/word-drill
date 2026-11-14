package com.github.chsiching.worddrill

import android.app.Application
import com.github.chsiching.worddrill.data.DictionaryImportOrchestrator
import com.github.chsiching.worddrill.data.PresetImportOrchestrator
import com.github.chsiching.worddrill.data.ReviewBookInitializer
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

// Without the Hilt Gradle Plugin (it requires AGP 9, conflicting with this project's AGP 8.13.x),
// @HiltAndroidApp must explicitly specify the base class and extend the Hilt-generated Hilt_<ClassName>.
@HiltAndroidApp(Application::class)
class WordDrillApp : Hilt_WordDrillApp() {

    @Inject lateinit var presetImportOrchestrator: PresetImportOrchestrator
    @Inject lateinit var dictionaryImportOrchestrator: DictionaryImportOrchestrator
    @Inject lateinit var reviewBookInitializer: ReviewBookInitializer

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // PdfBox-Android requires PDFBoxResourceLoader.init before any PDF parsing; it is idempotent.
        PDFBoxResourceLoader.init(this)

        appScope.launch { presetImportOrchestrator.ensurePresetImported() }
        appScope.launch { dictionaryImportOrchestrator.ensureDictionaryImported() }
        appScope.launch { reviewBookInitializer.ensureReviewBookCreated() }
    }
}
