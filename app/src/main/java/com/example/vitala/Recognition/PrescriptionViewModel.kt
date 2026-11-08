package com.example.vitala.Recognition

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** One photographed page: the file on disk plus a small bitmap for the strip. */
data class ScanPage(val path: String, val thumbnail: Bitmap)

data class ScanUiState(
    val pages: List<ScanPage> = emptyList(), // photos collected, not yet sent
    val preview: Bitmap? = null,          // page 1 of a PDF
    val previewCaption: String? = null,   // e.g. "Page 1 of 3"
    val sourceText: String? = null,       // original text of a .txt prescription
    val prescription: ExercisePrescription? = null,
    val isAnalyzing: Boolean = false,
    val error: String? = null
) {
    val canAddPages get() = !isAnalyzing && pages.size < MAX_PAGES

    companion object {
        /** Keeps the request size and memory use reasonable. */
        const val MAX_PAGES = 10
    }
}

class PrescriptionViewModel(
    app: Application,
    private val savedState: SavedStateHandle
) : AndroidViewModel(app) {

    private val extractor = PrescriptionExtractor()

    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    init {
        // Android may kill the process while the camera app is open; bring back
        // the pages taken so far.
        val saved = savedState.get<ArrayList<String>>(KEY_PAGES).orEmpty()
        if (saved.isNotEmpty()) viewModelScope.launch { addPages(saved.map(::File), persist = false) }
    }

    // ---------- camera ----------

    /**
     * Creates an empty file in app-private storage (filesDir/photos) and
     * returns a content:// URI the camera app is allowed to write into.
     *
     * The path is kept in SavedStateHandle because Android may kill this
     * process while the camera app is in front; a plain field would be lost.
     */
    fun createPhotoUri(): Uri {
        val app = getApplication<Application>()
        val dir = File(app.filesDir, DIR_PHOTOS).apply { mkdirs() }
        val file = File(dir, "rx_${System.currentTimeMillis()}.jpg")
        savedState[KEY_PENDING_PHOTO] = file.absolutePath
        return FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
    }

    /** Called with the camera result. success = false means the user backed out. */
    fun onPhotoResult(success: Boolean) {
        val path = savedState.get<String>(KEY_PENDING_PHOTO) ?: return
        savedState.remove<String>(KEY_PENDING_PHOTO)
        val file = File(path)
        if (!success || !file.exists() || file.length() == 0L) {
            file.delete() // don't leave empty files behind
            return
        }
        viewModelScope.launch { addPages(listOf(file)) }
    }

    fun onCameraUnavailable() {
        onPhotoResult(false)
        _state.update { it.copy(error = "No camera app available on this device") }
    }

    // ---------- photo picker ----------

    /** Called with the photo picker result. Empty list = the user cancelled. */
    fun onImagesPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val room = ScanUiState.MAX_PAGES - _state.value.pages.size
            val files = try {
                uris.take(room).map { copyToPrivateStorage(it, DIR_PHOTOS) }
            } catch (e: Exception) {
                Log.e(TAG, "Could not copy picked images", e)
                _state.update { it.copy(error = e.message ?: "Could not open the selected images") }
                return@launch
            }
            addPages(files)
            if (uris.size > room) {
                _state.update {
                    it.copy(error = "Only ${ScanUiState.MAX_PAGES} pages fit in one scan; " +
                            "${uris.size - room} not added.")
                }
            }
        }
    }

    // ---------- pages ----------

    fun removePage(path: String) {
        File(path).delete()
        _state.update { s -> s.copy(pages = s.pages.filterNot { it.path == path }) }
        persistPages()
    }

    fun clearPages() {
        _state.value.pages.forEach { File(it.path).delete() }
        _state.update { it.copy(pages = emptyList(), prescription = null, error = null) }
        persistPages()
    }

    /** Sends every collected page to Gemini in one request. */
    fun analyzePages() {
        val pages = _state.value.pages
        if (pages.isEmpty() || _state.value.isAnalyzing) return
        launchAnalysis(keepPages = true) {
            val jpegs = pages.map { loadAsJpeg(File(it.path)) }
            val rx = extractor.extractPhotos(jpegs)
            _state.update { it.copy(prescription = rx, isAnalyzing = false) }
        }
    }

    private suspend fun addPages(files: List<File>, persist: Boolean = true) {
        val added = files.mapNotNull { file ->
            try {
                ScanPage(file.absolutePath, loadScaledBitmap(file, THUMBNAIL_SIDE))
            } catch (e: Exception) {
                Log.e(TAG, "Could not read photo ${file.name}", e)
                file.delete()
                null
            }
        }
        _state.update { s ->
            s.copy(
                // A new page means any earlier result or document is out of date.
                pages = (s.pages + added).take(ScanUiState.MAX_PAGES),
                preview = null, previewCaption = null, sourceText = null,
                prescription = null,
                error = if (added.size < files.size) "Some photos could not be read" else null
            )
        }
        if (persist) persistPages()
    }

    private fun persistPages() {
        savedState[KEY_PAGES] = ArrayList(_state.value.pages.map { it.path })
    }

    // ---------- file picker (PDF / text) ----------

    /**
     * Called with the file picker result. PDFs go to Gemini as PDFs and text
     * goes as text, so nothing is lost converting them to an image.
     */
    fun onDocumentPicked(uri: Uri?) {
        if (uri == null) return
        clearPages() // a document is its own scan, not one more page
        launchAnalysis {
            val mime = getApplication<Application>().contentResolver.getType(uri)
            val file = copyToPrivateStorage(uri, DIR_DOCUMENTS)
            val rx = when {
                mime == "application/pdf" -> {
                    require(file.length() <= MAX_INLINE_BYTES) {
                        "PDF is too large (${file.length() / 1_000_000} MB). Limit is " +
                                "${MAX_INLINE_BYTES / 1_000_000} MB."
                    }
                    // Preview is only for checking; if it fails, still analyse.
                    val preview = runCatching { renderPdfFirstPage(file) }.getOrNull()
                    _state.value = ScanUiState(
                        preview = preview?.first,
                        previewCaption = preview?.let { "Page 1 of ${it.second}" },
                        isAnalyzing = true
                    )
                    extractor.extractPdf(withContext(Dispatchers.IO) { file.readBytes() })
                }
                mime?.startsWith("text/") == true -> {
                    val text = withContext(Dispatchers.IO) { file.readText() }
                    require(text.isNotBlank()) { "The text file is empty" }
                    _state.value = ScanUiState(sourceText = text, isAnalyzing = true)
                    extractor.extractText(text)
                }
                else -> {
                    file.delete()
                    throw IllegalArgumentException("Unsupported file type: ${mime ?: "unknown"}")
                }
            }
            _state.update { it.copy(prescription = rx, isAnalyzing = false) }
        }
    }

    // ---------- shared ----------

    /** Runs one analysis with a single place for error handling. */
    private fun launchAnalysis(keepPages: Boolean = false, work: suspend () -> Unit) {
        _state.update {
            if (keepPages) it.copy(isAnalyzing = true, prescription = null, error = null)
            else ScanUiState(isAnalyzing = true)
        }
        viewModelScope.launch {
            try {
                work()
            } catch (e: CancellationException) {
                throw e // let the coroutine cancel normally
            } catch (e: Exception) {
                Log.e(TAG, "Prescription analysis failed", e)
                _state.update {
                    it.copy(isAnalyzing = false, error = e.message ?: "Analysis failed")
                }
            }
        }
    }

    /**
     * Copies a picked file into filesDir/<dir>. Read access to a picker URI is
     * temporary, and this keeps every prescription in one private place.
     */
    private suspend fun copyToPrivateStorage(uri: Uri, dir: String): File =
        withContext(Dispatchers.IO) {
            val app = getApplication<Application>()
            val ext = app.contentResolver.getType(uri)
                ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
                ?: "bin"
            val folder = File(app.filesDir, dir).apply { mkdirs() }
            val file = File(folder, "rx_${System.currentTimeMillis()}_${java.util.UUID.randomUUID()}.$ext")
            val input = app.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Could not open the selected file")
            input.use { src -> file.outputStream().use { dst -> src.copyTo(dst) } }
            file
        }

    private companion object {
        const val TAG = "PrescriptionViewModel"
        const val KEY_PENDING_PHOTO = "pending_photo_path"
        const val KEY_PAGES = "page_paths"
        const val THUMBNAIL_SIDE = 400
        const val DIR_PHOTOS = "photos"
        const val DIR_DOCUMENTS = "documents"

        /**
         * Inline files count toward Gemini's per-request size limit (about 20 MB,
         * check current docs). 15 MB leaves room for base64 overhead and the prompt.
         * A one- or two-page prescription PDF is normally well under 1 MB.
         */
        const val MAX_INLINE_BYTES = 15_000_000L
    }
}