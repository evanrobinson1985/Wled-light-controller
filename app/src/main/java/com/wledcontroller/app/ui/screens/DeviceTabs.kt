@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.wledcontroller.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wledcontroller.app.LocalAppContainer
import com.wledcontroller.app.data.DeviceController
import com.wledcontroller.app.data.DeviceSnapshot
import com.wledcontroller.app.data.EffectMeta
import com.wledcontroller.app.data.LightProfile
import com.wledcontroller.app.data.profileFor
import com.wledcontroller.app.data.Preset
import com.wledcontroller.app.data.Segment
import com.wledcontroller.app.data.WledColor
import com.wledcontroller.app.data.isRealName
import com.wledcontroller.app.ui.components.ColorPickerPanel
import com.wledcontroller.app.ui.components.ColorSwatch
import com.wledcontroller.app.ui.components.GradientSlider
import com.wledcontroller.app.ui.components.HolidayThemesSection
import com.wledcontroller.app.ui.components.PaletteBar
import com.wledcontroller.app.ui.components.QuickColors
import com.wledcontroller.app.ui.components.SectionTitle
import com.wledcontroller.app.ui.components.hsvToRgb
import com.wledcontroller.app.ui.components.kelvinToRgb
import com.wledcontroller.app.ui.components.paletteBrush
import com.wledcontroller.app.ui.components.rgbHex
import com.wledcontroller.app.ui.components.toComposeColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.roundToInt
import kotlin.random.Random

private val SlotFallback = listOf("Primary", "Secondary", "Tertiary")
private val KelvinPresets = listOf(2200, 2700, 3500, 4500, 5500, 6500)

// ================================================================== Colours

@Composable
fun ColorsTab(controller: DeviceController, snap: DeviceSnapshot) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val seg = snap.state?.activeSegment ?: return NoSegments()
    val info = snap.info
    val meta = snap.effectMeta(seg.fx)
    var slot by rememberSaveable { mutableIntStateOf(0) }
    val current = seg.color(slot)
    val favorites by container.store.favoriteColors.collectAsStateWithLifecycle(emptyList())
    val recents by container.store.recentColors.collectAsStateWithLifecycle(emptyList())
    var showHex by remember { mutableStateOf(false) }

    fun pick(rgb: Int) {
        controller.setColor(slot, WledColor.fromRgb(rgb, current.w))
        scope.launch { container.store.addRecentColor(rgb) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (i in 0..2) {
                val used = meta.colors[i] != null
                SlotButton(
                    label = meta.colors[i] ?: SlotFallback[i],
                    color = seg.color(i).toComposeColor(),
                    selected = slot == i,
                    dimmed = !used,
                    onClick = { slot = i },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        ColorPickerPanel(
            color = current.rgb,
            onColorChange = { controller.setColor(slot, WledColor.fromRgb(it, current.w)) },
            onCommit = { rgb -> scope.launch { container.store.addRecentColor(rgb) } },
            modifier = Modifier.fillMaxWidth(),
        )

        if (info?.hasWhite == true) {
            Spacer(Modifier.height(16.dp))
            GradientSlider(
                value = current.w / 255f,
                onValueChange = { controller.setColor(slot, current.copy(w = (it * 255).roundToInt())) },
                brush = Brush.horizontalGradient(listOf(Color.Black, Color(0xFFFFF4E0))),
                label = "White channel",
                icon = Icons.Filled.WbSunny,
                valueText = "${current.w * 100 / 255}%",
            )
        }
        if (info?.hasCct == true) {
            Spacer(Modifier.height(16.dp))
            GradientSlider(
                value = seg.cct / 255f,
                onValueChange = { controller.setSegValue("cct", (it * 255).roundToInt()) },
                brush = Brush.horizontalGradient(listOf(kelvinToRgb(2200).toComposeColor(), Color.White, kelvinToRgb(7500).toComposeColor())),
                label = "White temperature",
                icon = Icons.Filled.Thermostat,
                valueText = "${2700 + seg.cct * (6500 - 2700) / 255} K",
            )
        }

        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { showHex = true }, label = { Text("#${rgbHex(current.rgb)}") }, leadingIcon = { Icon(Icons.Filled.Tag, null, Modifier.size(18.dp)) })
            val isFav = (current.rgb or (0xFF shl 24)) in favorites
            AssistChip(
                onClick = { scope.launch { container.store.toggleFavoriteColor(current.rgb) } },
                label = { Text(if (isFav) "Saved" else "Save colour") },
                leadingIcon = { Icon(if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null, Modifier.size(18.dp)) },
            )
            AssistChip(
                onClick = { pick(hsvToRgb(Random.nextFloat() * 360f, 0.75f + Random.nextFloat() * 0.25f, 1f)) },
                label = { Text("Random") },
                leadingIcon = { Icon(Icons.Filled.Shuffle, null, Modifier.size(18.dp)) },
            )
        }

        SectionTitle("Quick colours")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            (QuickColors + (0xFF000000).toInt()).forEach { c ->
                ColorSwatch(c.toComposeColor(), onClick = { pick(c) }, selected = current.rgb == c)
            }
        }

        SectionTitle("Whites")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            KelvinPresets.forEach { k ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ColorSwatch(kelvinToRgb(k).toComposeColor(), onClick = {
                        if (info?.hasWhite == true) {
                            controller.setColor(slot, WledColor(0, 0, 0, 255))
                            if (info.hasCct) controller.setSegValue("cct", ((k - 2700) * 255 / (6500 - 2700)).coerceIn(0, 255))
                        } else {
                            pick(kelvinToRgb(k))
                        }
                    })
                    Text("${k}K", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        if (favorites.isNotEmpty()) {
            SectionTitle("Favourites")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                favorites.forEach { c ->
                    ColorSwatch(
                        c.toComposeColor(), onClick = { pick(c) }, selected = current.rgb == c,
                        onLongClick = { scope.launch { container.store.toggleFavoriteColor(c) } },
                    )
                }
            }
            Text("Long-press a favourite to remove it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
        if (recents.isNotEmpty()) {
            SectionTitle("Recent")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                recents.forEach { c -> ColorSwatch(c.toComposeColor(), onClick = { pick(c) }, size = 34.dp) }
            }
        }
        Spacer(Modifier.height(32.dp))
    }

    if (showHex) HexColorDialog(current.rgb, onDismiss = { showHex = false }, onConfirm = { pick(it) })
}

@Composable
private fun SlotButton(label: String, color: Color, selected: Boolean, dimmed: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.secondary) else null,
        modifier = modifier,
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                color = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun NoSegments() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Waiting for device…", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ================================================================== Effects

private val EffectFilters = listOf("All", "Favourites", "2D", "Audio")

@Composable
fun EffectsTab(controller: DeviceController, snap: DeviceSnapshot) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val seg = snap.state?.activeSegment ?: return NoSegments()
    val meta = snap.effectMeta(seg.fx)
    val favorites by container.store.favoriteEffects.collectAsStateWithLifecycle(emptySet())
    val profiles by container.store.lightProfiles.collectAsStateWithLifecycle(emptyMap())
    val profile = profiles.profileFor(controller.id, seg.id)
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val filters = EffectFilters + listOfNotNull(profile?.let { "Best for ${it.label.substringBefore(" /").lowercase()}" })
    if (filter >= filters.size) filter = 0

    val items = remember(snap.effects, snap.fxData, query, filter, favorites, profile) {
        snap.effects.withIndex()
            .filter { (_, n) -> isRealName(n) }
            .filter { (_, n) -> query.isBlank() || n.contains(query.trim(), ignoreCase = true) }
            .filter { (i, n) ->
                when (filter) {
                    1 -> n in favorites
                    2 -> snap.effectMeta(i).is2D
                    3 -> snap.effectMeta(i).isAudio
                    4 -> profile?.suggests(n, snap.effectMeta(i)) ?: true
                    else -> true
                }
            }
            .sortedWith(compareBy<IndexedValue<String>>({ it.index != 0 }, { it.value.lowercase() }))
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            EffectControlsCard(
                effectName = snap.effects.getOrNull(seg.fx) ?: "Effect ${seg.fx}",
                paletteName = if (meta.usesPalette) snap.palettes.getOrNull(seg.pal) else null,
                seg = seg, meta = meta, controller = controller,
            )
            Spacer(Modifier.height(10.dp))
        }
        item {
            SearchField(query, { query = it }, "Search ${snap.effects.count(::isRealName)} effects")
            Row(Modifier.padding(vertical = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                filters.forEachIndexed { i, label ->
                    FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(label) })
                }
            }
        }
        if (items.isEmpty()) {
            item { Text("No effects match.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
        }
        items(items, key = { it.index }) { (i, name) ->
            val m = snap.effectMeta(i)
            SelectableRow(
                selected = seg.fx == i,
                onClick = {
                    controller.setEffect(i)
                    profile?.effectSpeed?.let { controller.setSegValue("sx", it) }
                },
            ) {
                Text(name, Modifier.weight(1f), fontWeight = if (seg.fx == i) FontWeight.SemiBold else FontWeight.Normal)
                if (m.is2D) Badge("2D")
                if (m.isAudio) Badge("♪")
                IconButton(onClick = { scope.launch { container.store.toggleFavoriteEffect(name) } }) {
                    val fav = name in favorites
                    Icon(
                        if (fav) Icons.Filled.Star else Icons.Filled.StarBorder,
                        if (fav) "Unfavourite" else "Favourite",
                        tint = if (fav) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun EffectControlsCard(
    effectName: String,
    paletteName: String?,
    seg: Segment,
    meta: EffectMeta,
    controller: DeviceController,
) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp)) {
            Text(effectName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (paletteName != null) {
                Text("Palette: $paletteName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val values = listOf(seg.sx, seg.ix, seg.c1, seg.c2, seg.c3)
            var any = false
            meta.sliders.forEachIndexed { i, label ->
                if (label == null) return@forEachIndexed
                any = true
                val max = if (i == 4) 31f else 255f
                Spacer(Modifier.height(14.dp))
                GradientSlider(
                    value = values[i] / max,
                    onValueChange = { controller.setSegValue(EffectMeta.SLIDER_KEYS[i], (it * max).roundToInt()) },
                    label = label,
                    valueText = "${(values[i] / max * 100).roundToInt()}%",
                    height = 34.dp,
                )
            }
            val opts = listOf(seg.o1, seg.o2, seg.o3)
            meta.options.forEachIndexed { i, label ->
                if (label == null) return@forEachIndexed
                any = true
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f))
                    Switch(checked = opts[i], onCheckedChange = { controller.setSegFlag(EffectMeta.OPTION_KEYS[i], it) })
                }
            }
            if (!any) {
                Spacer(Modifier.height(8.dp))
                Text("This effect has no adjustable parameters.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Badge(text: String) {
    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.padding(start = 6.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = { if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Clear, "Clear") } },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun SelectableRow(selected: Boolean, onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp).height(52.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

// ================================================================== Palettes

@Composable
fun PalettesTab(controller: DeviceController, snap: DeviceSnapshot) {
    val seg = snap.state?.activeSegment ?: return NoSegments()
    val meta = snap.effectMeta(seg.fx)
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(controller) { controller.loadPalettePreviews() }
    val profiles by LocalAppContainer.current.store.lightProfiles.collectAsStateWithLifecycle(emptyMap())
    val profile = profiles.profileFor(controller.id, seg.id)

    val items = remember(snap.palettes, query) {
        snap.palettes.withIndex()
            .filter { (_, n) -> isRealName(n) }
            .filter { (_, n) -> query.isBlank() || n.contains(query.trim(), ignoreCase = true) }
            .sortedWith(compareBy<IndexedValue<String>>({ it.index != 0 }, { it.value.lowercase() }))
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            HolidayThemesSection(
                availableEffects = snap.effects,
                preferBands = profile?.preferBands == true,
                onApply = { colors, effect, theme ->
                    val scale = profile?.speedScale ?: 1f
                    controller.applyTheme(colors, effect, theme.bands || profile?.preferBands == true, (theme.speed * scale).toInt(), theme.intensity)
                },
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        if (profile == LightProfile.BULLETS) {
            item {
                Text(
                    "Tip for bullet strings: bulbs are far apart, so smooth gradients change very little from bulb to bulb. High-contrast palettes such as Party, Rainbow Bands or \"* Colors Only\" read best.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }
        if (!meta.usesPalette) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Text(
                        "The current effect doesn't use a palette — pick an effect like Rainbow, Palette or Colorwaves to see it.",
                        Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        item { SearchField(query, { query = it }, "Search ${items.size} palettes") }
        items(items, key = { it.index }) { (i, name) ->
            val selected = seg.pal == i
            Surface(
                onClick = { controller.setPalette(i) },
                shape = RoundedCornerShape(16.dp),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, Modifier.weight(1f), fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                        if (selected) Icon(Icons.Filled.CheckCircle, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(8.dp))
                    PaletteBar(paletteBrush(snap.palettePreviews[i], seg.colors))
                }
            }
        }
    }
}

// ================================================================== Segments

@Composable
fun SegmentsTab(controller: DeviceController, snap: DeviceSnapshot) {
    val state = snap.state ?: return NoSegments()
    val ledCount = snap.info?.ledCount ?: 0
    val maxSeg = snap.info?.maxSegments ?: 16
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(
                "Tap a segment's checkbox to choose which segments the colour and effect controls change. Long strips can be split into independently animated zones.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(state.segments, key = { it.id }) { seg ->
            SegmentCard(seg, ledCount, snap, controller, canDelete = state.segments.size > 1)
        }
        item {
            OutlinedButton(
                onClick = { controller.addSegment() },
                enabled = state.segments.size < maxSeg && ledCount > 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp)); Text("Add segment")
            }
        }
    }
}

@Composable
private fun SegmentCard(seg: Segment, ledCount: Int, snap: DeviceSnapshot, controller: DeviceController, canDelete: Boolean) {
    var expanded by rememberSaveable(seg.id) { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = seg.sel, onCheckedChange = { controller.selectSegment(seg.id, additive = true) })
                Box(Modifier.size(18.dp).clip(CircleShape).background(seg.color(0).toComposeColor()))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f).clickable { expanded = !expanded }) {
                    Text(seg.displayName, fontWeight = FontWeight.SemiBold)
                    Text(
                        "LEDs ${seg.start}–${seg.stop - 1} · ${snap.effects.getOrNull(seg.fx) ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = seg.on, onCheckedChange = { controller.updateSegment(seg.id, buildJsonObject { put("on", it) }) })
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, "Details")
                }
            }
            GradientSlider(
                value = seg.bri / 255f,
                onValueChange = { controller.updateSegment(seg.id, buildJsonObject { put("bri", (it * 255).roundToInt().coerceAtLeast(1)) }) },
                height = 28.dp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            )
            if (expanded) {
                SegmentDetails(seg, ledCount, controller)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { renaming = true }) { Text("Rename") }
                    if (canDelete) {
                        TextButton(onClick = { controller.deleteSegment(seg.id) }) {
                            Icon(Icons.Filled.Delete, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Delete")
                        }
                    }
                }
            }
        }
    }
    if (renaming) {
        TextInputDialog("Rename segment", seg.name ?: "", "Name", onDismiss = { renaming = false }) { name ->
            controller.updateSegment(seg.id, buildJsonObject { put("n", name) })
        }
    }
}

@Composable
private fun SegmentDetails(seg: Segment, ledCount: Int, controller: DeviceController) {
    var start by remember(seg.start) { mutableStateOf(seg.start.toString()) }
    var stop by remember(seg.stop) { mutableStateOf(seg.stop.toString()) }
    var grp by remember(seg.grp) { mutableStateOf(seg.grp.toString()) }
    var spc by remember(seg.spc) { mutableStateOf(seg.spc.toString()) }
    var off by remember(seg.of) { mutableStateOf(seg.of.toString()) }
    val s = start.toIntOrNull(); val e = stop.toIntOrNull()
    val valid = s != null && e != null && s in 0 until e && (ledCount == 0 || e <= ledCount)
    Column(Modifier.padding(horizontal = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Start LED", start, { start = it }, Modifier.weight(1f))
            NumberField("Stop LED", stop, { stop = it }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Grouping", grp, { grp = it }, Modifier.weight(1f))
            NumberField("Spacing", spc, { spc = it }, Modifier.weight(1f))
            NumberField("Offset", off, { off = it }, Modifier.weight(1f))
        }
        FilledTonalButton(
            enabled = valid,
            onClick = {
                controller.updateSegment(seg.id, buildJsonObject {
                    put("start", s!!); put("stop", e!!)
                    put("grp", (grp.toIntOrNull() ?: 1).coerceIn(1, 255))
                    put("spc", (spc.toIntOrNull() ?: 0).coerceIn(0, 255))
                    put("of", off.toIntOrNull() ?: 0)
                })
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        ) { Text("Apply layout") }
        ToggleRow("Reverse direction", seg.rev) { controller.updateSegment(seg.id, buildJsonObject { put("rev", it) }) }
        ToggleRow("Mirror", seg.mi) { controller.updateSegment(seg.id, buildJsonObject { put("mi", it) }) }
        ToggleRow("Freeze animation", seg.frz) { controller.updateSegment(seg.id, buildJsonObject { put("frz", it) }) }
        val container = LocalAppContainer.current
        val scope = rememberCoroutineScope()
        val profiles by container.store.lightProfiles.collectAsStateWithLifecycle(emptyMap())
        val own = LightProfile.parse(profiles[LightProfile.segmentKey(controller.id, seg.id)])
        val inherited = LightProfile.parse(profiles[LightProfile.deviceKey(controller.id)])
        Text("Light type", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        LightProfilePicker(own, inheritLabel = "Same as device" + (inherited?.let { " (${it.label})" } ?: "")) { p ->
            scope.launch { container.store.setLightProfile(LightProfile.segmentKey(controller.id, seg.id), p) }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(5)) },
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ================================================================== Presets

@Composable
fun PresetsTab(controller: DeviceController, snap: DeviceSnapshot) {
    val scope = rememberCoroutineScope()
    var showSave by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Preset?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(controller) { controller.loadPresets() }
    val presets = snap.presets
    val current = snap.state?.ps ?: -1
    val playing = snap.state?.pl ?: -1

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            FilledTonalButton(onClick = { showSave = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Save, null); Spacer(Modifier.width(8.dp)); Text("Save current look as preset")
            }
            Spacer(Modifier.height(8.dp))
            if (presets != null && presets.size > 8) SearchField(query, { query = it }, "Search presets")
        }
        when {
            presets == null -> item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            presets.isEmpty() -> item {
                Text("No presets saved on this device yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            }
            else -> items(presets.filter { query.isBlank() || it.name.contains(query.trim(), true) }, key = { it.id }) { p ->
                var menu by remember { mutableStateOf(false) }
                val active = p.id == current || p.id == playing
                SelectableRow(selected = active, onClick = { controller.applyPreset(p.id) }) {
                    Icon(
                        if (p.isPlaylist) Icons.Filled.PlaylistPlay else Icons.Filled.Bookmark, null,
                        tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                        Text(
                            "#${p.id}" + (if (p.isPlaylist) " · Playlist" else "") + (p.quickLabel?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Preset options") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (!p.isPlaylist) {
                                DropdownMenuItem(text = { Text("Overwrite with current") }, onClick = {
                                    menu = false
                                    controller.savePreset(p.id, p.name, includeBrightness = true, saveSegmentBounds = true)
                                    scope.launch { delay(700); controller.loadPresets() }
                                })
                            }
                            DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; confirmDelete = p })
                        }
                    }
                }
            }
        }
    }

    if (showSave) SavePresetDialog(controller, onDismiss = { showSave = false })
    confirmDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete preset?") },
            text = { Text("\"${p.name}\" will be removed from the device.") },
            confirmButton = {
                TextButton(onClick = {
                    controller.deletePreset(p.id)
                    scope.launch { delay(700); controller.loadPresets() }
                    confirmDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}
