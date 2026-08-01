package com.alastorkaneki.fileeditor

import android.app.Application
import android.app.PendingIntent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alastorkaneki.fileeditor.data.AudioRepository
import com.alastorkaneki.fileeditor.data.AudioTrack
import com.alastorkaneki.fileeditor.data.EditorDraft
import com.alastorkaneki.fileeditor.data.SearchMatcher
import com.alastorkaneki.fileeditor.data.TagData
import com.alastorkaneki.fileeditor.data.WritePermissionRequired
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EditorSession(
    val track: AudioTrack,
    val isLoading: Boolean = true,
    val tags: TagData? = null,
    val error: String? = null,
)

data class FileEditorUiState(
    val hasPermission: Boolean = false,
    val isScanning: Boolean = false,
    val allTracks: List<AudioTrack> = emptyList(),
    val query: String = "",
    val editor: EditorSession? = null,
    val isSaving: Boolean = false,
    val message: String? = null,
) {
    val visibleTracks: List<AudioTrack>
        get() = allTracks.filter { SearchMatcher.matches(it, query) }
}

sealed interface UiEffect {
    data class RequestWriteAccess(val pendingIntent: PendingIntent) : UiEffect
}

private data class PendingSave(val track: AudioTrack, val draft: EditorDraft)

class FileEditorViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AudioRepository(application)

    private val _state = MutableStateFlow(FileEditorUiState())
    val state: StateFlow<FileEditorUiState> = _state.asStateFlow()

    private val effectChannel = Channel<UiEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    private var pendingSave: PendingSave? = null

    fun onPermissionResult(granted: Boolean) {
        _state.update { it.copy(hasPermission = granted) }
        if (granted) scan()
    }

    fun scan() = scanInternal(announceResult = true)

    private fun scanInternal(announceResult: Boolean) {
        if (!_state.value.hasPermission || _state.value.isScanning) return
        viewModelScope.launch {
            _state.update { it.copy(isScanning = true, message = if (announceResult) null else it.message) }
            runCatching { withContext(Dispatchers.IO) { repository.scanAudio() } }
                .onSuccess { tracks ->
                    _state.update {
                        it.copy(
                            isScanning = false,
                            allTracks = tracks,
                            message = if (announceResult) "Found ${tracks.size} audio files" else it.message,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isScanning = false,
                            message = error.userMessage("Unable to scan audio files"),
                        )
                    }
                }
        }
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun openEditor(track: AudioTrack) {
        _state.update { it.copy(editor = EditorSession(track = track), message = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { repository.readTags(track) } }
                .onSuccess { tags ->
                    _state.update { current ->
                        current.copy(editor = current.editor?.copy(isLoading = false, tags = tags))
                    }
                }
                .onFailure { error ->
                    _state.update { current ->
                        current.copy(
                            editor = current.editor?.copy(
                                isLoading = false,
                                error = error.userMessage("This audio format could not be opened"),
                            ),
                        )
                    }
                }
        }
    }

    fun closeEditor() {
        if (_state.value.isSaving) return
        pendingSave = null
        _state.update { it.copy(editor = null) }
    }

    fun save(track: AudioTrack, draft: EditorDraft) {
        if (_state.value.isSaving) return
        pendingSave = PendingSave(track, draft)
        _state.update { it.copy(isSaving = true, message = null) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            viewModelScope.launch {
                runCatching { repository.createWriteRequest(track.uri) }
                    .onSuccess { effectChannel.trySend(UiEffect.RequestWriteAccess(it)) }
                    .onFailure { failSave(it) }
            }
        } else {
            performPendingSave()
        }
    }

    fun onWritePermissionResult(granted: Boolean) {
        if (!granted) {
            pendingSave = null
            _state.update { it.copy(isSaving = false, message = "Write access was not granted") }
            return
        }
        performPendingSave()
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun performPendingSave() {
        val operation = pendingSave ?: run {
            _state.update { it.copy(isSaving = false) }
            return
        }

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.saveTags(operation.track, operation.draft)
                }
            }.onSuccess { result ->
                pendingSave = null
                val warningSuffix = if (result.warnings.isEmpty()) {
                    ""
                } else {
                    " (${result.warnings.size} fields were not supported by this format)"
                }
                _state.update {
                    it.copy(
                        editor = null,
                        isSaving = false,
                        message = "Metadata saved$warningSuffix",
                    )
                }
                scanInternal(announceResult = false)
            }.onFailure { error ->
                if (error is WritePermissionRequired) {
                    effectChannel.trySend(UiEffect.RequestWriteAccess(error.pendingIntent))
                } else {
                    failSave(error)
                }
            }
        }
    }

    private fun failSave(error: Throwable) {
        pendingSave = null
        _state.update {
            it.copy(
                isSaving = false,
                message = error.userMessage("Unable to save metadata"),
            )
        }
    }

    private fun Throwable.userMessage(prefix: String): String {
        val detail = message?.takeIf(String::isNotBlank) ?: javaClass.simpleName
        return "$prefix: $detail"
    }
}
