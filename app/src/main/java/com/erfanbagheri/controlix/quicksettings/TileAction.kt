package com.erfanbagheri.controlix.quicksettings

/**
 * Pure tap routing for the Quick Settings tile.
 *
 * Issue #78: the tile fires a *pinned* target, not whichever pad was opened
 * last. A stored target with a live DB id transmits; a stored target whose
 * remote left the DB, or no target at all, opens the app so the user can fix
 * the pin rather than getting a silent no-op.
 */
object TileAction {

    sealed interface Decision {
        /** Fire [key] on the remote currently at [remoteId]. */
        data class Transmit(val remoteId: Int, val key: String) : Decision
        data object OpenApp : Decision
    }

    /**
     * @param target the pinned target as stored, or null when the slot is empty
     *   or still holds a pre-#78 int.
     * @param remoteId the id [target] resolves to in the current DB, or null.
     */
    fun decide(target: TileTarget?, remoteId: Int?): Decision =
        if (target != null && remoteId != null && remoteId > 0) {
            Decision.Transmit(remoteId, target.lookupKey)
        } else {
            Decision.OpenApp
        }

    /**
     * Pre-#78 installs stored a bare int. Honour it once so the tile keeps
     * working after the upgrade; the picker re-pins a [TileTarget] the next
     * time the user chooses one.
     */
    fun decideLegacy(lastRemoteId: Int?): Decision =
        if (lastRemoteId != null && lastRemoteId > 0) Decision.Transmit(lastRemoteId, "power")
        else Decision.OpenApp
}
