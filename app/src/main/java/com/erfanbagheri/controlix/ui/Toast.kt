package com.erfanbagheri.controlix.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** One visible message. Replacement cancels the old timeout and action. */
class ToastState {
    var current by mutableStateOf<ToastData?>(null)
        private set
    fun show(message: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        current = ToastData(message, actionLabel, action)
    }
    fun clear(data: ToastData) { if (current === data) current = null }
}
class ToastData(val message: String, val actionLabel: String?, val action: (() -> Unit)?)

@Composable
fun rememberToastState(): ToastState = remember { ToastState() }

@Composable
fun ToastHost(state: ToastState, modifier: Modifier = Modifier) {
    val host = remember { SnackbarHostState() }
    LaunchedEffect(state.current) {
        val data = state.current ?: return@LaunchedEffect
        val result = host.showSnackbar(
            message = data.message, actionLabel = data.actionLabel,
            withDismissAction = true,
            duration = if (data.action != null) SnackbarDuration.Long else SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) data.action?.invoke()
        state.clear(data)
    }
    SnackbarHost(hostState = host, modifier = modifier) { data ->
        Snackbar(
            snackbarData = data,
            modifier = Modifier.padding(12.dp),
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            actionColor = MaterialTheme.colorScheme.inversePrimary,
        )
    }
}
