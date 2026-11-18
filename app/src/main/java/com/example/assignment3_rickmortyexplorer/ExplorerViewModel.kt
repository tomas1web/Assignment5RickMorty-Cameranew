package com.example.assignment3_rickmortyexplorer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

data class ExplorerUiState(
    val category: Category = Category.CHARACTER,
    val filter: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,
    val total: Int = 0,
    val items: List<DisplayItem> = emptyList(),
    val current: DisplayItem? = null,
    val totalRolls: Int = 0,
    val history: List<SavedItem> = emptyList(),
    val message: String? = null,
    val photo: LocationPhoto? = null,   // TP5: foto da localização atual
    val showCamera: Boolean = false     // TP5: ecrã da câmara aberto?
)

class ExplorerViewModel : ViewModel() {

    var uiState by mutableStateOf(ExplorerUiState())
        private set

    private var searchJob: Job? = null
    private val repository = FirebaseRepository()

    init {
        search()
        observeFirebase()
    }

    // ---------- Firebase ----------

    private fun observeFirebase() {
        viewModelScope.launch {
            repository.rollCountFlow()
                .catch { e -> showMessage("Firebase error: ${e.message}") }
                .collect { uiState = uiState.copy(totalRolls = it) }
        }
        viewModelScope.launch {
            repository.historyFlow()
                .catch { e -> showMessage("Firebase error: ${e.message}") }
                .collect { uiState = uiState.copy(history = it) }
        }
    }

    private fun showMessage(text: String?) {
        uiState = uiState.copy(message = text)
    }

    private fun safeFirebase(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            showMessage("Firebase error: ${e.message}")
        }
    }

    fun saveCurrent() {
        val item = uiState.current ?: return
        val saved = SavedItem(
            name = item.name,
            imageUrl = item.imageUrl,
            category = uiState.category.label,
            label1 = item.label1, value1 = item.value1,
            label2 = item.label2, value2 = item.value2
        )
        safeFirebase {
            repository.saveItem(saved) { ok ->
                showMessage(if (ok) "Saved: ${item.name}" else "Could not save to Firebase")
            }
        }
    }

    fun loadLast() {
        safeFirebase {
            repository.loadLast(
                onResult = { saved ->
                    if (saved == null) {
                        uiState = uiState.copy(message = "No saved item yet")
                    } else {
                        uiState = uiState.copy(
                            current = saved.toDisplayItem(),
                            message = "Loaded: ${saved.name} (${saved.category})"
                        )
                        refreshPhoto()
                    }
                },
                onError = { showMessage("Could not load from Firebase") }
            )
        }
    }

    // ---------- TP5: câmara e foto da localização ----------

    fun openCamera() {
        if (uiState.current != null) {
            uiState = uiState.copy(showCamera = true, message = null)
        }
    }

    fun closeCamera() {
        uiState = uiState.copy(showCamera = false)
    }

    // Chamado pelo ecrã da câmara: mostra a foto e guarda-a (com coordenadas) no Firebase
    fun onPhotoCaptured(imageBase64: String, latitude: Double?, longitude: Double?) {
        val item = uiState.current
        if (item == null) {
            uiState = uiState.copy(showCamera = false)
            return
        }
        val photo = LocationPhoto(
            locationName = item.name,
            imageBase64 = imageBase64,
            latitude = latitude,
            longitude = longitude,
            takenAt = System.currentTimeMillis()
        )
        uiState = uiState.copy(photo = photo, showCamera = false, message = "Saving photo...")
        safeFirebase {
            repository.saveLocationPhoto(photo) { ok ->
                showMessage(
                    if (ok) "Photo saved for ${item.name}" else "Could not save photo to Firebase"
                )
            }
        }
    }

    // Vai buscar ao Firebase a foto da localização que está no ecrã
    private fun refreshPhoto() {
        val item = uiState.current
        if (uiState.category != Category.LOCATION || item == null) {
            uiState = uiState.copy(photo = null)
            return
        }
        val name = item.name
        uiState = uiState.copy(photo = null)
        safeFirebase {
            repository.loadLocationPhoto(name) { photo ->
                if (uiState.current?.name == name) {
                    uiState = uiState.copy(photo = photo)
                }
            }
        }
    }

    // ---------- Ações da UI ----------

    fun onCategorySelected(category: Category) {
        uiState = uiState.copy(category = category, filter = "")
        search()
    }

    fun onFilterChanged(text: String) {
        uiState = uiState.copy(filter = text)
        search(debounce = true)
    }

    fun roll() {
        val items = uiState.items
        if (items.isEmpty()) return
        uiState = uiState.copy(current = items.random(), message = null)
        refreshPhoto()
        safeFirebase { repository.incrementRollCount() }
    }

    // ---------- Pesquisa na API ----------

    private fun search(debounce: Boolean = false) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (debounce) delay(400)
            val category = uiState.category
            val name = uiState.filter.trim().ifEmpty { null }
            uiState = uiState.copy(isLoading = true, error = null)

            try {
                val api = RickMortyApi.retrofitService
                val (total, items) = when (category) {
                    Category.CHARACTER -> {
                        val r = api.getCharacters(name)
                        r.info.count to r.results.map { it.toDisplayItem() }
                    }
                    Category.EPISODE -> {
                        val r = api.getEpisodes(name)
                        r.info.count to r.results.map { it.toDisplayItem() }
                    }
                    Category.LOCATION -> {
                        val r = api.getLocations(name)
                        r.info.count to r.results.map { it.toDisplayItem() }
                    }
                }
                uiState = uiState.copy(
                    isLoading = false,
                    total = total,
                    items = items,
                    current = items.randomOrNull()
                )
                refreshPhoto()
            } catch (e: HttpException) {
                uiState = if (e.code() == 404) {
                    uiState.copy(
                        isLoading = false, total = 0, items = emptyList(),
                        current = null, photo = null
                    )
                } else {
                    uiState.copy(isLoading = false, error = "Server error (${e.code()})")
                }
            } catch (e: IOException) {
                uiState = uiState.copy(isLoading = false, error = "No internet connection")
            }
        }
    }
}