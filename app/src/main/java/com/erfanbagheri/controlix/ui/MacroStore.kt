package com.erfanbagheri.controlix.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.erfanbagheri.controlix.feature.Macro
import com.erfanbagheri.controlix.feature.MacroStep

/**
 * Persisted macro list, same SharedPreferences shape as DeviceStore.
 * Format: each macro is `id~name~step1|step2|...`; steps are `remoteId:buttonName:delayMs`.
 * Macros survive DB updates because steps reference (remoteId, buttonName), not indices.
 */
class MacroStore(context: Context) {
    private val prefs = context.getSharedPreferences("macros", Context.MODE_PRIVATE)

    fun load(): List<Macro> {
        val raw = prefs.getString("macros", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(";;").filter { it.isNotBlank() }.mapNotNull { runCatching { parseMacro(it) }.getOrNull() }
    }

    fun save(macros: List<Macro>) {
        prefs.edit().putString("macros", macros.joinToString(";;") { serializeMacro(it) }).apply()
    }

    private fun parseMacro(s: String): Macro {
        val parts = s.split("~")
        val id = parts[0].toInt()
        val name = parts[1]
        val steps = if (parts.size > 2 && parts[2].isNotBlank()) {
            parts[2].split("|").mapNotNull { runCatching { parseStep(it) }.getOrNull() }
        } else emptyList()
        return Macro(id, name, steps)
    }

    private fun parseStep(s: String): MacroStep {
        val f = s.split(":")
        return MacroStep(remoteId = f[0].toInt(), buttonName = f[1], delayMs = f[2].toLongOrNull() ?: 350)
    }

    private fun serializeMacro(m: Macro): String {
        val steps = m.steps.joinToString("|") { "${it.remoteId}:${it.buttonName}:${it.delayMs}" }
        return "${m.id}~${m.name}~$steps"
    }

    fun nextId(macros: List<Macro>): Int = (macros.maxOfOrNull { it.id } ?: 0) + 1
}

class MacroModel(private val store: MacroStore) {
    var macros: List<Macro> by mutableStateOf(store.load())
        private set

    fun save(macros: List<Macro>) { store.save(macros); this.macros = store.load() }
    fun add(macro: Macro) { save(macros + macro) }
    fun remove(id: Int) { save(macros.filterNot { it.id == id }) }
    fun update(macro: Macro) { save(macros.map { if (it.id == macro.id) macro else it }) }
    fun reload() { macros = store.load() }
}

@Composable
fun rememberMacroModel(context: Context = LocalContext.current): MacroModel {
    return remember { MacroModel(MacroStore(context)) }
}
