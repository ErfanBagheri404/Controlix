package com.erfanbagheri.controlix.ui

import android.appwidget.AppWidgetManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.erfanbagheri.controlix.data.Macro
import com.erfanbagheri.controlix.ui.theme.ControlixTheme
import com.erfanbagheri.controlix.widget.WidgetStore

/**
 * Widget configuration screen (issue #82): picks ONE macro for the widget
 * instance that launched it. The binding is the macro's stable id — never
 * its list position — so reordering the macro list cannot retarget a widget.
 * Second widget instance, second choice: the binding is keyed per appWidgetId.
 */
class WidgetMacroConfigureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED) // cancelled configure never leaves a half-bound widget
        setContent {
            val macros = remember {
                runCatching { MacroStore(this@WidgetMacroConfigureActivity).load() }
                    .getOrDefault(emptyList())
            }
            val boundId = remember { WidgetStore.macroId(this@WidgetMacroConfigureActivity, widgetId) }
            ControlixTheme {
                WidgetMacroConfigureScreen(
                    macros = macros,
                    boundId = boundId,
                    onPick = { macro ->
                        WidgetStore.setMacroId(this@WidgetMacroConfigureActivity, widgetId, macro.id)
                        setResult(RESULT_OK)
                        finish()
                    },
                    onCancel = { finish() },
                )
            }
        }
    }
}

@Composable
private fun WidgetMacroConfigureScreen(
    macros: List<Macro>,
    boundId: Int?,
    onPick: (Macro) -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        BackRow(onCancel)
        Spacer(Modifier.height(8.dp))
        Text("Widget macro", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Pick the macro this widget runs. One widget, one macro; add another widget for another macro.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        if (macros.isEmpty()) {
            Text(
                "No macros yet — create one in Controlix first.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(macros, key = { it.id }) { macro ->
                    val selected = macro.id == boundId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .pressable { onPick(macro) }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(macro.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${macro.steps.size} step${if (macro.steps.size == 1) "" else "s"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.padding(4.dp))
                        Text(
                            if (selected) "✓ Bound" else "Use",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Cancel",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.pressable(onCancel).padding(vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(28.dp))
    }
}
