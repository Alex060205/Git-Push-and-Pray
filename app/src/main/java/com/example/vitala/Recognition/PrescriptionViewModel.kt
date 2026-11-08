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
import com.example.vitala.pacing.integration.AdaptiveProgramService
import com.example.vitala.pacing.integration.AdaptiveRepository
import com.example.vitala.pacing.integration.Evaluation
import com.example.vitala.pacing.integration.PreparedProgram
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.UUID

data class ScanPage(
    val path: String,
    val thumbnail: Bitmap
)

data class ScanUiState(
    val pages: List<ScanPage> = emptyList(),
    val preview: Bitmap? = null,
    val previewCaption: String? = null,
    val sourceText: String? = null,
    val prescription: ExercisePrescription? = null,

    val preparedProgram: PreparedProgram? = null,
    val activeProgramId: String? = null,
    val activePatientId: String? = null,

    val evaluations: Map<String, Evaluation> = emptyMap(),

    val isAnalyzing: Boolean = false,
    val isWorking: Boolean = false,

    val status: String? = null,
    val error: String? = null
) {
    val busy: Boolean
        get() = isAnalyzing || isWorking

    val canAddPages: Boolean
        get() = !busy && pages.size < MAX_PAGES

    companion object {
        const val MAX_PAGES = 10
    }
}

class PrescriptionViewModel(
    app: Application,
    private val savedState: SavedStateHandle
) : AndroidViewModel(app) {

    private val extractor = PrescriptionExtractor()

    private val programs =
        AdaptiveProgramService(extractor)

    private val repository by lazy {
        AdaptiveRepository()
    }

    private val _state =
        MutableStateFlow(ScanUiState())

    val state: StateFlow<ScanUiState> =
        _state.asStateFlow()

    init {
        val saved = savedState
            .get<ArrayList<String>>(KEY_PAGES)
            .orEmpty()

        if (saved.isNotEmpty()) {
            viewModelScope.launch {
                addPages(
                    files = saved.map(::File),
                    persist = false
                )
            }
        }
    }

    // ---------- Camera ----------

    fun createPhotoUri(): Uri {
        check(!_state.value.busy) {
            "Wait for the current operation"
        }

        val app = getApplication<Application>()

        val folder = File(
            app.filesDir,
            DIR_PHOTOS
        ).apply {
            mkdirs()
        }

        val file = File(
            folder,
            "rx_${UUID.randomUUID()}.jpg"
        )

        savedState[KEY_PENDING_PHOTO] =
            file.absolutePath

        return FileProvider.getUriForFile(
            app,
            "${app.packageName}.fileprovider",
            file
        )
    }

    fun onPhotoResult(success: Boolean) {
        val path = savedState.get<String>(
            KEY_PENDING_PHOTO
        ) ?: return

        savedState.remove<String>(
            KEY_PENDING_PHOTO
        )

        val file = File(path)

        if (
            !success ||
            !file.exists() ||
            file.length() == 0L
        ) {
            file.delete()
            return
        }

        databaseWork {
            addPages(listOf(file))
        }
    }

    fun onCameraUnavailable() {
        onPhotoResult(false)

        _state.update {
            it.copy(
                error = "No camera app available"
            )
        }
    }

    // ---------- Image picker ----------

    fun onImagesPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return

        databaseWork {
            val room = ScanUiState.MAX_PAGES -
                    _state.value.pages.size

            val files = uris
                .take(room)
                .map {
                    copyToPrivateStorage(
                        it,
                        DIR_PHOTOS
                    )
                }

            addPages(files)

            if (uris.size > room) {
                _state.update {
                    it.copy(
                        error =
                            "Only ${ScanUiState.MAX_PAGES} " +
                                    "pages are supported"
                    )
                }
            }
        }
    }

    // ---------- Page management ----------

    fun removePage(path: String) {
        if (_state.value.busy) return

        File(path).delete()

        _state.update {
            it.copy(
                pages = it.pages.filterNot { page ->
                    page.path == path
                },
                prescription = null,
                preparedProgram = null,
                activeProgramId = null,
                activePatientId = null,
                evaluations = emptyMap(),
                status = null,
                error = null
            )
        }

        persistPages()
    }

    fun clearPages() {
        if (_state.value.busy) return

        _state.value.pages.forEach {
            File(it.path).delete()
        }

        _state.value = ScanUiState()
        persistPages()
    }

    private suspend fun addPages(
        files: List<File>,
        persist: Boolean = true
    ) {
        val added = withContext(Dispatchers.IO) {
            files.map { file ->
                ScanPage(
                    path = file.absolutePath,
                    thumbnail = loadScaledBitmap(
                        file,
                        THUMBNAIL_SIDE
                    )
                )
            }
        }

        _state.update {
            it.copy(
                pages = (it.pages + added)
                    .take(ScanUiState.MAX_PAGES),

                preview = null,
                previewCaption = null,
                sourceText = null,

                prescription = null,
                preparedProgram = null,
                activeProgramId = null,
                activePatientId = null,
                evaluations = emptyMap(),

                status = null,
                error = null
            )
        }

        if (persist) {
            persistPages()
        }
    }

    private fun persistPages() {
        savedState[KEY_PAGES] = ArrayList(
            _state.value.pages.map { it.path }
        )
    }

    // ---------- Prescription extraction ----------

    fun analyzePages() {
        val pages = _state.value.pages
        if (pages.isEmpty()) return

        launchAnalysis(keepPages = true) {
            val images = withContext(Dispatchers.IO) {
                pages.map {
                    loadAsJpeg(File(it.path))
                }
            }

            val prescription =
                extractor.extractPhotos(images)

            acceptExtracted(prescription)
        }
    }

    /** Plan typed into the form: composed into text and read with the same extractor and review step. */
    fun analyzeTypedPlan(text: String) {
        if (text.isBlank() || _state.value.busy) return
        clearPages()
        launchAnalysis {
            _state.update { it.copy(sourceText = text) }
            acceptExtracted(extractor.extractText(text))
        }
    }

    /** Forget the scan session after a plan has been saved. Keeps image files (they belong to the plan now). */
    fun finishScan() {
        _state.value = ScanUiState()
        savedState[KEY_PAGES] = ArrayList<String>()
    }

    fun onDocumentPicked(uri: Uri?) {
        if (uri == null || _state.value.busy) {
            return
        }

        clearPages()

        launchAnalysis {
            val mime = getApplication<Application>()
                .contentResolver
                .getType(uri)

            val file = copyToPrivateStorage(
                uri,
                DIR_DOCUMENTS
            )

            require(
                file.length() <= MAX_INLINE_BYTES
            ) {
                "Document exceeds 15 MB"
            }

            val prescription = when {
                mime == "application/pdf" -> {
                    val preview = withContext(
                        Dispatchers.IO
                    ) {
                        runCatching {
                            renderPdfFirstPage(file)
                        }.getOrNull()
                    }

                    _state.update {
                        it.copy(
                            preview = preview?.first,
                            previewCaption =
                                preview?.let { value ->
                                    "Page 1 of ${value.second}"
                                }
                        )
                    }

                    val bytes = withContext(
                        Dispatchers.IO
                    ) {
                        file.readBytes()
                    }

                    extractor.extractPdf(bytes)
                }

                mime?.startsWith("text/") == true -> {
                    val text = withContext(
                        Dispatchers.IO
                    ) {
                        file.readText()
                    }

                    require(text.isNotBlank()) {
                        "The text file is empty"
                    }

                    _state.update {
                        it.copy(sourceText = text)
                    }

                    extractor.extractText(text)
                }

                else -> {
                    error(
                        "Unsupported document type: $mime"
                    )
                }
            }

            acceptExtracted(prescription)
        }
    }

    private suspend fun acceptExtracted(
        prescription: ExercisePrescription
    ) {
        // Keep the original extraction visible
        // if conversion fails.
        _state.update {
            it.copy(
                prescription = prescription,
                status = "Preparing exercise plan"
            )
        }

        val prepared = programs.prepare(
            prescription
        )

        _state.update {
            it.copy(
                preparedProgram = prepared,
                status =
                    "Prescription ready to review and activate"
            )
        }
    }

    // ---------- Program persistence ----------

    fun activateProgram(
        patientId: String
    ) = databaseWork {
        val prepared = requireNotNull(
            _state.value.preparedProgram
        ) {
            "Read and configure a prescription first"
        }

        repository.activate(
            patientId,
            prepared
        )

        _state.update {
            it.copy(
                activePatientId = patientId,
                activeProgramId = prepared.id,
                evaluations = emptyMap(),
                status = "Program saved"
            )
        }
    }

    fun loadProgram(
        patientId: String,
        programId: String
    ) = databaseWork {
        val prepared = repository.loadProgram(
            patientId,
            programId
        )

        _state.value = ScanUiState(
            prescription = prepared.source,
            preparedProgram = prepared,
            activePatientId = patientId,
            activeProgramId = programId,
            isWorking = true,
            status = "Program loaded"
        )
    }

    // ---------- Patient check-in ----------

    fun saveCheckIn(
        exerciseId: String,
        pain: Int,
        answers: Map<String, Boolean>
    ) {
        val capturedAnswers = answers.toMap()

        databaseWork {
            val (patientId, programId) =
                activeContext(exerciseId)

            val today = LocalDate.now()

            // Discard the previous evaluation
            // before changing its inputs.
            _state.update {
                it.copy(
                    evaluations =
                        it.evaluations - exerciseId
                )
            }

            repository.saveCheckIn(
                patientId = patientId,
                programId = programId,
                exerciseId = exerciseId,
                date = today,
                pain = pain,
                answers = capturedAnswers
            )

            val evaluation = repository.evaluate(
                patientId = patientId,
                programId = programId,
                exerciseId = exerciseId,
                today = today
            )

            _state.update {
                it.copy(
                    evaluations = it.evaluations +
                            (exerciseId to evaluation),

                    status = "Recommendation ready"
                )
            }
        }
    }

    fun evaluateExercise(
        exerciseId: String
    ) = databaseWork {
        val (patientId, programId) =
            activeContext(exerciseId)

        val evaluation = repository.evaluate(
            patientId,
            programId,
            exerciseId,
            LocalDate.now()
        )

        _state.update {
            it.copy(
                evaluations = it.evaluations +
                        (exerciseId to evaluation),

                status = "Recommendation ready"
            )
        }
    }

    // ---------- Accept today's assignment ----------

    fun applyAssignment(
        exerciseId: String
    ) = databaseWork {
        val (patientId, programId) =
            activeContext(exerciseId)

        val evaluation = requireNotNull(
            _state.value.evaluations[exerciseId]
        ) {
            "Get a current recommendation first"
        }

        require(
            evaluation.programId == programId
        )

        val applied = repository.applyAssignment(
            patientId,
            evaluation
        )

        _state.update {
            it.copy(
                status = if (applied) {
                    "Today's assignment saved"
                } else {
                    "Today's assignment was already saved"
                }
            )
        }
    }

    // ---------- Record actual exercise ----------

    fun recordSession(
        exerciseId: String,
        actualValue: Double?,
        missed: Boolean
    ) = databaseWork {
        val (patientId, programId) =
            activeContext(exerciseId)

        repository.recordSession(
            patientId = patientId,
            programId = programId,
            exerciseId = exerciseId,
            date = LocalDate.now(),
            actualValue = actualValue,
            missed = missed
        )

        _state.update {
            it.copy(
                evaluations =
                    it.evaluations - exerciseId,

                status = "Session recorded"
            )
        }
    }

    // ---------- Shared helpers ----------

    private fun activeContext(
        exerciseId: String
    ): Pair<String, String> {
        val current = _state.value

        val patientId = requireNotNull(
            current.activePatientId
        ) {
            "Select and activate a patient program first"
        }

        val programId = requireNotNull(
            current.activeProgramId
        ) {
            "Activate the program first"
        }

        require(
            current.preparedProgram
                ?.exercises
                ?.any {
                    it.plan.exerciseId == exerciseId
                } == true
        ) {
            "This exercise does not belong to the active program"
        }

        return patientId to programId
    }

    private fun databaseWork(
        work: suspend () -> Unit
    ) {
        if (_state.value.busy) return

        _state.update {
            it.copy(
                isWorking = true,
                error = null
            )
        }

        viewModelScope.launch {
            try {
                work()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(
                    TAG,
                    "Operation failed",
                    exception
                )

                _state.update {
                    it.copy(
                        error = exception.message
                            ?: "Operation failed"
                    )
                }
            } finally {
                _state.update {
                    it.copy(isWorking = false)
                }
            }
        }
    }

    private fun launchAnalysis(
        keepPages: Boolean = false,
        work: suspend () -> Unit
    ) {
        if (_state.value.busy) return

        _state.update {
            ScanUiState(
                pages = if (keepPages) {
                    it.pages
                } else {
                    emptyList()
                },
                isAnalyzing = true
            )
        }

        viewModelScope.launch {
            try {
                work()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(
                    TAG,
                    "Prescription processing failed",
                    exception
                )

                _state.update {
                    it.copy(
                        error = exception.message
                            ?: "Prescription processing failed"
                    )
                }
            } finally {
                _state.update {
                    it.copy(isAnalyzing = false)
                }
            }
        }
    }

    private suspend fun copyToPrivateStorage(
        uri: Uri,
        directory: String
    ): File = withContext(Dispatchers.IO) {
        val app = getApplication<Application>()

        val extension = app.contentResolver
            .getType(uri)
            ?.let {
                MimeTypeMap.getSingleton()
                    .getExtensionFromMimeType(it)
            }
            ?: "bin"

        val folder = File(
            app.filesDir,
            directory
        ).apply {
            mkdirs()
        }

        val file = File(
            folder,
            "rx_${UUID.randomUUID()}.$extension"
        )

        val input = app.contentResolver
            .openInputStream(uri)
            ?: error("Could not open selected file")

        input.use { source ->
            file.outputStream().use { target ->
                source.copyTo(target)
            }
        }

        file
    }

    private companion object {
        const val TAG = "PrescriptionViewModel"
        const val KEY_PENDING_PHOTO = "pending_photo_path"
        const val KEY_PAGES = "page_paths"
        const val THUMBNAIL_SIDE = 400
        const val DIR_PHOTOS = "photos"
        const val DIR_DOCUMENTS = "documents"
        const val MAX_INLINE_BYTES = 15_000_000L
    }
}