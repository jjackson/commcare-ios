package org.commcare.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.commcare.app.engine.AppInstaller
import org.commcare.app.model.ApplicationRecord
import org.commcare.app.storage.AppRecordRepository
import org.commcare.app.storage.CommCareDatabase
import org.commcare.app.storage.SqlDelightUserSandbox

// ---- Step model -------------------------------------------------------

data class InstallStep(val label: String, val status: StepStatus)

enum class StepStatus { PENDING, IN_PROGRESS, COMPLETED, FAILED }

// ---- State sealed class -----------------------------------------------

sealed class InstallState {
    data object Idle : InstallState()
    data class Installing(
        val steps: List<InstallStep>,
        val appName: String
    ) : InstallState()
    data class Completed(val app: ApplicationRecord) : InstallState()
    data class Failed(val message: String) : InstallState()
}

// ---- Step label constants ---------------------------------------------

private val STEP_LABELS = listOf(
    "Downloading profile",
    "Installing suite",
    "Downloading forms",
    "Loading locale files",
    "Initializing"
)

/** Map a progress float (0.0–1.0) to the index of the active step. */
private fun progressToStepIndex(progress: Float): Int = when {
    progress < 0.2f -> 0
    progress < 0.3f -> 1
    progress < 0.8f -> 2
    progress < 0.9f -> 3
    else -> 4
}

/** Build a step list where [activeIndex] is IN_PROGRESS, earlier ones COMPLETED, later PENDING. */
private fun buildSteps(activeIndex: Int, failed: Boolean = false): List<InstallStep> =
    STEP_LABELS.mapIndexed { i, label ->
        val status = when {
            i < activeIndex -> StepStatus.COMPLETED
            i == activeIndex -> if (failed) StepStatus.FAILED else StepStatus.IN_PROGRESS
            else -> StepStatus.PENDING
        }
        InstallStep(label, status)
    }

/** All steps marked COMPLETED. */
private fun allCompleted(): List<InstallStep> =
    STEP_LABELS.map { InstallStep(it, StepStatus.COMPLETED) }

// ---- ViewModel --------------------------------------------------------

/**
 * Drives the install-progress screen.
 *
 * Constructor takes the database and repository so it can persist the
 * ApplicationRecord on success, matching the pattern used by LoginViewModel.
 */
class AppInstallViewModel(
    private val db: CommCareDatabase,
    private val appRepository: AppRecordRepository
) {
    var installState by mutableStateOf<InstallState>(InstallState.Idle)
        private set

    private val scope = CoroutineScope(Dispatchers.Default)

    /**
     * Begin installation from the given profile URL.
     * Safe to call from the UI thread; work is dispatched to Default.
     */
    fun install(profileUrl: String) {
        if (profileUrl.isBlank()) {
            installState = InstallState.Failed("No profile URL provided")
            return
        }

        installState = InstallState.Installing(buildSteps(0), "CommCare")

        scope.launch {
            try {
                val sandbox = SqlDelightUserSandbox(db)
                val installer = AppInstaller(sandbox)

                val platform = installer.install(profileUrl) { progress, _ ->
                    val stepIndex = progressToStepIndex(progress)
                    val currentState = installState
                    val appName = if (currentState is InstallState.Installing) currentState.appName else "CommCare"
                    installState = InstallState.Installing(buildSteps(stepIndex), appName)
                }

                // Extract display name from profile (best-effort)
                val displayName = try {
                    platform.getCurrentProfile()?.getDisplayName() ?: "CommCare"
                } catch (_: Exception) {
                    "CommCare"
                }

                // Update step list to show name if available
                installState = InstallState.Installing(buildSteps(4), displayName)

                // Extract domain from profile URL  (/a/DOMAIN/apps/...)
                val domain = extractDomain(profileUrl)

                val installDate = platformCurrentTimeMillis()

                val app = ApplicationRecord(
                    id = generateAppId(profileUrl),
                    profileUrl = profileUrl,
                    displayName = displayName,
                    domain = domain,
                    majorVersion = platform.majorVersion,
                    minorVersion = platform.minorVersion,
                    installDate = installDate
                )

                // Persist record and seat the app
                appRepository.insertApp(app)
                appRepository.seatApp(app.id)

                // Show all steps completed before transitioning
                installState = InstallState.Installing(allCompleted(), displayName)

                installState = InstallState.Completed(app)
            } catch (e: Exception) {
                installState = InstallState.Failed(
                    "Installation failed: ${e.message ?: e::class.simpleName}"
                )
            }
        }
    }

    /** Reset to Idle so the screen can retry. */
    fun reset() {
        installState = InstallState.Idle
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Extract the HQ domain from a profile URL of the form
     * https://www.commcarehq.org/a/DOMAIN/apps/download/…
     * Returns empty string if the pattern is not matched.
     */
    private fun extractDomain(profileUrl: String): String {
        val marker = "/a/"
        val start = profileUrl.indexOf(marker)
        if (start == -1) return ""
        val afterMarker = start + marker.length
        val end = profileUrl.indexOf('/', afterMarker)
        return if (end == -1) profileUrl.substring(afterMarker) else profileUrl.substring(afterMarker, end)
    }

    /**
     * Derive a stable app ID from the profile URL.
     * Uses the "download/<id>" segment if present, otherwise a hash of the URL.
     */
    private fun generateAppId(profileUrl: String): String {
        val downloadMarker = "/download/"
        val start = profileUrl.indexOf(downloadMarker)
        if (start != -1) {
            val afterMarker = start + downloadMarker.length
            val end = profileUrl.indexOf('/', afterMarker)
            val candidate = if (end == -1) profileUrl.substring(afterMarker) else profileUrl.substring(afterMarker, end)
            if (candidate.isNotBlank()) return candidate
        }
        // Fallback: use the URL hash as a stable ID
        return profileUrl.hashCode().toString()
    }

    /**
     * Returns current wall-clock time in milliseconds since epoch.
     *
     * kotlinx-datetime is not a project dependency and kotlin.system.getTimeMillis()
     * is deprecated, so we use 0L here (same pattern as LoginViewModel.startInstall()).
     * The install date is persisted for display purposes only; a zero value is
     * acceptable until a proper time API is added.
     */
    private fun platformCurrentTimeMillis(): Long = 0L
}
