package az.cachecleaner

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

enum class Phase { IDLE, RUNNING, FINISHED, STOPPED }

data class CleanedApp(val label: String, val pkg: String, val bytes: Long)

data class UiState(
    val phase: Phase = Phase.IDLE,
    val total: Int = 0,
    val processed: Int = 0,
    val currentApp: String = "",
    val bytesCleaned: Long = 0,
    val okCount: Int = 0,
    val skipped: Int = 0,
    val cleaned: List<CleanedApp> = emptyList(),
    val startMs: Long = 0,
    val endMs: Long = 0
) {
    val percent: Int get() = if (total == 0) 0 else (processed * 100 / total).coerceIn(0, 100)
}

object CleanerState {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun modify(block: (UiState) -> UiState) {
        _state.update(block)
    }

    fun reset() {
        _state.value = UiState()
    }
}

fun formatBytes(b: Long): String {
    val kb = 1024.0
    val loc = Locale.getDefault()
    return when {
        b >= kb * kb * kb -> String.format(loc, "%.2f GB", b / (kb * kb * kb))
        b >= kb * kb -> String.format(loc, "%.1f MB", b / (kb * kb))
        b >= kb -> String.format(loc, "%.0f KB", b / kb)
        else -> "$b B"
    }
}

fun formatDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "${s / 60} dəq ${s % 60} san"
}
