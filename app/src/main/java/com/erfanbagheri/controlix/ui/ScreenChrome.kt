package com.erfanbagheri.controlix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Screen chrome: status-bar-aware top, guaranteed bottom breathing space. */
object ScreenChrome {
    const val BOTTOM_SPACE_DP = 40
}

/** Status-bar (notification shade) padding for full-bleed Columns. */
fun Modifier.applyTopInset(): Modifier = this.statusBarsPadding()

/** Nav-bar padding for bottom-fixed action rows. */
fun Modifier.applyBottomInset(): Modifier = this.navigationBarsPadding()

/** Scrollable screen shell — hugs status bar, guaranteed bottom space. */
@Composable
fun ScrollScreenShell(
    bottomSpace: Int = ScreenChrome.BOTTOM_SPACE_DP,
    topBar: @Composable () -> Unit,
    gridContent: LazyGridScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .applyTopInset()
            .padding(top = 12.dp)
            .padding(horizontal = 24.dp),
    ) {
        topBar()
        Spacer(Modifier.height(20.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            gridContent()
            item(span = { GridItemSpan(2) }) { Spacer(Modifier.height(bottomSpace.dp)) }
        }
    }
}

/** Back + centered title. */
@Composable
fun BackTitleRow(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ActionIconView(ActionIcon.Back, 26.dp, MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.pressable(onBack).padding(6.dp))
        Spacer(Modifier.weight(1f))
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.size(38.dp))
    }
}
