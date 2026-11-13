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

/**
 * Application entry point. @HiltAndroidApp triggers Hilt's code generation, producing the ApplicationComponent.
 * All subsequent ViewModel / Repository dependency injection hangs off this root component.
 *
 * Note: this project does not enable the Hilt Gradle Plugin (it requires AGP 9, which conflicts with the specified AGP 8.13.x).
 * Therefore, per the official Dagger docs, @HiltAndroidApp must explicitly specify the base class,
 * and the annotated class must extend the Hilt-generated Hilt_<ClassName>.
 */
@HiltAndroidApp(Application::class)
class WordDrillApp : Hilt_WordDrillApp() {

    @Inject lateinit var presetImportOrchestrator: PresetImportOrchestrator
    @Inject lateinit var dictionaryImportOrchestrator: DictionaryImportOrchestrator
    @Inject lateinit var reviewBookInitializer: ReviewBookInitializer

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Ticket #21: PdfBox-Android's native library (libpdfbox.so) must be initialized before use.
        // PDF file import relies on it via [com.github.chsiching.worddrill.data.wordimport.PdfTableParser];
        // initialized once here at Application startup, idempotent (PDFBoxResourceLoader already guards against re-init internally).
        PDFBoxResourceLoader.init(this)

        // On first launch, idempotently import the preset word books in the background; if already imported, return immediately with no duplicated cost.
        appScope.launch { presetImportOrchestrator.ensurePresetImported() }
        // Ticket #19: on first launch, idempotently import the built-in dictionary in the background (ECDICT, 100k words, read-only reference data).
        // Runs in parallel with the preset word book import; a dictionary failure does not affect user word books.
        appScope.launch { dictionaryImportOrchestrator.ensureDictionaryImported() }
        // Ticket #20: on first launch, idempotently create the preset "Review" word book (skipped words go here; empty by default).
        // Separate coroutine, parallel to the word book/dictionary imports; creating the book itself is a single INSERT, negligible cost.
        appScope.launch { reviewBookInitializer.ensureReviewBookCreated() }
    }
}
