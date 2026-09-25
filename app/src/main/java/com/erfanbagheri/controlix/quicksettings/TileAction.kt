package com.erfanbagheri.controlix.quicksettings

/**
 * Pure tap routing for the Quick Settings tile.
 * Remembered remote → fire its power code with no UI.
 * Nothing remembered → open the app.
 */
object TileAction {

    sealed interface Decision {
        data class TransmitPower(val remoteId: Int) : Decision
        data object OpenApp : Decision
    }

    fun decide(lastRemoteId: Int?): Decision =
        if (lastRemoteId != null && lastRemoteId > 0) Decision.TransmitPower(lastRemoteId)
        else Decision.OpenApp
}
