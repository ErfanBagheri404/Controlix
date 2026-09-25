package com.erfanbagheri.controlix.data

/**
 * Refresh plan states:
 * Idle -> Checked -> Downloading -> Verified -> Applying -> Done,
 * with RolledBack (mismatch / failed apply, previous state preserved) and
 * Failed (illegal move). Pure Kotlin — flavor-safe.
 */
sealed interface RefreshState {
    data object Idle : RefreshState
    data class Checked(val manifest: DbUpdateManifest) : RefreshState
    data class Downloading(val manifest: DbUpdateManifest) : RefreshState
    data class Verified(val manifest: DbUpdateManifest) : RefreshState
    data class Applying(val manifest: DbUpdateManifest) : RefreshState
    data object Done : RefreshState
    data class RolledBack(val previous: RefreshState, val reason: String) : RefreshState
    data class Failed(val reason: String) : RefreshState
}

/** Pure transition rules: Verified requires size AND sha match; Applying requires Verified. */
object RefreshPlan {

    fun checked(manifest: DbUpdateManifest): RefreshState = RefreshState.Checked(manifest)

    fun startDownload(state: RefreshState): RefreshState =
        if (state is RefreshState.Checked) RefreshState.Downloading(state.manifest)
        else RefreshState.Failed("download requires Checked")

    /** Size and sha256 must both match the manifest, else roll back to Downloading. */
    fun verified(state: RefreshState, actualSize: Long, actualSha: String, manifest: DbUpdateManifest): RefreshState {
        if (state !is RefreshState.Downloading) return RefreshState.Failed("verify requires Downloading")
        if (actualSize != manifest.sizeBytes) return RefreshState.RolledBack(state, "size mismatch")
        if (!actualSha.equals(manifest.sha256, ignoreCase = true)) return RefreshState.RolledBack(state, "sha256 mismatch")
        return RefreshState.Verified(state.manifest)
    }

    fun startApply(state: RefreshState): RefreshState =
        if (state is RefreshState.Verified) RefreshState.Applying(state.manifest)
        else RefreshState.Failed("apply requires Verified")

    fun finishApply(state: RefreshState, ok: Boolean): RefreshState =
        if (state !is RefreshState.Applying) RefreshState.Failed("finish requires Applying")
        else if (ok) RefreshState.Done
        else RefreshState.RolledBack(state, "apply failed")
}
