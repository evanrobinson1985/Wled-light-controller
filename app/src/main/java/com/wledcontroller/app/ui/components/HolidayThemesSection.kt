@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.wledcontroller.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wledcontroller.app.data.HolidayTheme
import com.wledcontroller.app.data.HolidayThemes

/** Blended ramp, or hard-edged repeating bands, matching how WLED will render the theme. */
fun themeBrush(colors: List<Int>, bands: Boolean): Brush {
    val cs = colors.map { it.toComposeColor() }
    if (!bands) return Brush.horizontalGradient(cs)
    val stops = mutableListOf<Pair<Float, Color>>()
    val n = cs.size * 3
    for (i in 0 until n) {
        val c = cs[i % cs.size]
        stops += i / n.toFloat() to c
        stops += (i + 1) / n.toFloat() - 0.0001f to c
    }
    return Brush.horizontalGradient(colorStops = stops.toTypedArray())
}

@Composable
private fun ThemeBar(colors: List<Int>, bands: Boolean, height: Dp, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)).background(themeBrush(colors, bands))
    )
}

/**
 * Horizontal strip of holiday themes, in-season ones first. Tapping one opens a sheet to remix
 * the colours, pick an effect and apply.
 *
 * @param availableEffects effect names on the target device(s), to offer only effects that exist.
 * @param onApply colours, chosen effect name (null = keep Solid), theme.
 */
@Composable
fun HolidayThemesSection(
    availableEffects: List<String>,
    onApply: (colors: List<Int>, effect: String?, theme: HolidayTheme) -> Unit,
    modifier: Modifier = Modifier,
    preferBands: Boolean = false,
) {
    val themes = remember { HolidayThemes.forToday() }
    var open by remember { mutableStateOf<HolidayTheme?>(null) }

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            Icon(Icons.Filled.Celebration, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("Holiday themes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(themes, key = { it.id }) { t ->
                val season = HolidayThemes.inSeason(t)
                Surface(
                    onClick = { open = t },
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.width(156.dp),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        ThemeBar(t.colors, t.bands || preferBands, 22.dp)
                        Spacer(Modifier.height(10.dp))
                        Text(t.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(
                            if (season) "In season" else "Coming up",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (season) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    open?.let { t ->
        ThemeSheet(t, t.bands || preferBands, availableEffects, onDismiss = { open = null }) { colors, effect ->
            onApply(colors, effect, t)
            open = null
        }
    }
}

@Composable
private fun ThemeSheet(
    theme: HolidayTheme,
    bands: Boolean,
    availableEffects: List<String>,
    onDismiss: () -> Unit,
    onApply: (List<Int>, String?) -> Unit,
) {
    var colors by remember(theme.id) { mutableStateOf(theme.colors) }
    val effects = remember(theme.id, availableEffects) {
        val have = availableEffects.map { it.lowercase() }.toSet()
        // Before the device list loads, show the theme's suggestions as-is.
        (if (have.isEmpty()) theme.effects else theme.effects.filter { it.lowercase() in have }) + "Solid"
    }
    var effect by remember(theme.id) { mutableStateOf(effects.first()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text(theme.name, style = MaterialTheme.typography.headlineSmall)
            Text(
                if (bands) "Repeating colour bands" else "Smooth colour ramp",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            ThemeBar(colors, bands, 44.dp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                colors.forEach { ColorSwatch(it.toComposeColor(), onClick = {}, size = 36.dp) }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = { colors = HolidayThemes.remix(theme.colors) }) {
                    Icon(Icons.Filled.Shuffle, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Remix")
                }
                if (colors != theme.colors) {
                    androidx.compose.material3.IconButton(onClick = { colors = theme.colors }) {
                        Icon(Icons.Filled.RestartAlt, "Original colours")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Effect", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                effects.forEach { e -> FilterChip(selected = e == effect, onClick = { effect = e }, label = { Text(e) }) }
            }
            Spacer(Modifier.height(20.dp))
            Button(onClick = { onApply(colors, effect) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Apply ${theme.name}")
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
