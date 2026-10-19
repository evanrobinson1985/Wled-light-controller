@file:OptIn(ExperimentalMaterial3Api::class)

package com.wledcontroller.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wledcontroller.app.LocalAppContainer
import com.wledcontroller.app.house.Vendor
import com.wledcontroller.app.house.VendorCatalog
import com.wledcontroller.app.house.VendorInventory
import com.wledcontroller.app.house.VendorModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Catalogues fetched this session, so switching vendors back and forth is instant. */
private object CatalogCache {
    var vendors: List<Vendor>? = null
    val inventories = HashMap<String, VendorInventory>()
}

/**
 * Browses prop makers' models (or the DMX fixture library) from xLights' vendor catalogue and
 * hands back the chosen .xmodel. [dmx] shows only the fixture libraries.
 */
@Composable
internal fun VendorBrowser(dmx: Boolean, onDismiss: () -> Unit, onPicked: (xml: String, model: VendorModel) -> Unit) {
    val container = LocalAppContainer.current
    val client = container.api.client
    val scope = rememberCoroutineScope()
    var vendors by remember { mutableStateOf(CatalogCache.vendors) }
    var vendor by remember { mutableStateOf<Vendor?>(null) }
    var inventory by remember { mutableStateOf<VendorInventory?>(null) }
    var category by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf<String?>(null) }

    fun isDmxVendor(v: Vendor) = v.name.contains("DMX", ignoreCase = true)
    val shown = vendors.orEmpty().filter { isDmxVendor(it) == dmx }

    LaunchedEffect(Unit) {
        if (vendors == null) {
            busy = "Loading the catalogue…"
            runCatching { withContext(Dispatchers.IO) { VendorCatalog.parseVendors(VendorCatalog.fetch(client, VendorCatalog.VENDORS_URL)) } }
                .onSuccess { vendors = it; CatalogCache.vendors = it }
                .onFailure { error = "Couldn't load the catalogue: ${it.message}. Check you're online." }
            busy = null
        }
    }
    LaunchedEffect(vendors) { if (vendor == null) vendor = vendors.orEmpty().firstOrNull { isDmxVendor(it) == dmx } }
    LaunchedEffect(vendor) {
        val v = vendor ?: return@LaunchedEffect
        inventory = null; category = null; error = null
        CatalogCache.inventories[v.link]?.let { inventory = it; return@LaunchedEffect }
        busy = "Loading ${v.name}…"
        runCatching { withContext(Dispatchers.IO) { VendorCatalog.parseInventory(VendorCatalog.fetch(client, v.link)) } }
            .onSuccess { inventory = it; CatalogCache.inventories[v.link] = it }
            .onFailure { error = "Couldn't load ${v.name}'s models: ${it.message}" }
        busy = null
    }

    fun download(m: VendorModel, link: String) = scope.launch {
        busy = "Downloading ${m.name}…"; error = null
        runCatching { withContext(Dispatchers.IO) { VendorCatalog.fetch(client, link) } }
            .onSuccess { onPicked(it, m) }
            .onFailure { error = "Couldn't download ${m.name}: ${it.message}" }
        busy = null
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (dmx) "DMX fixture library" else "Props from vendors") },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Close") } },
                )
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker(
                    label = vendor?.name ?: "Choose a vendor",
                    options = shown.map { it.name },
                    onPick = { i -> vendor = shown[i] },
                )
                val inv = inventory
                if (inv != null) {
                    val used = inv.models.flatMap { it.categoryIds }.toSet()
                    val cats = inv.categories.filter { it.id in used }
                    Picker(
                        label = cats.firstOrNull { it.id == category }?.path ?: "All categories (${inv.models.size})",
                        options = listOf("All categories") + cats.map { it.path },
                        onPick = { i -> category = if (i == 0) null else cats[i - 1].id },
                    )
                    OutlinedTextField(
                        query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search ${inv.vendor.ifBlank { "models" }}") },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                    )
                }
                busy?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text(it)
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (inv != null) {
                    val models = inv.models.filter { m ->
                        (category == null || category in m.categoryIds) &&
                            (query.isBlank() || m.name.contains(query, ignoreCase = true) || m.type.contains(query, ignoreCase = true))
                    }
                    Text("${models.size} models", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                        items(models) { m ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                modifier = Modifier.fillMaxWidth().clickable(enabled = busy == null) {
                                    if (m.wirings.size == 1) download(m, m.wirings[0].xmodelLink) else expanded = if (expanded == m.id) null else m.id
                                },
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(m.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    val details = listOfNotNull(
                                        m.type.takeIf { it.isNotBlank() },
                                        if (m.width.isNotBlank() && m.height.isNotBlank()) "${m.width} × ${m.height}" else null,
                                        m.pixelCount.takeIf { it.isNotBlank() }?.let { "$it pixels" },
                                        m.pixelSpacing.takeIf { it.isNotBlank() && it.length < 12 }?.let { "$it apart" },
                                    )
                                    if (details.isNotEmpty()) Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (expanded == m.id) {
                                        Spacer(Modifier.height(6.dp))
                                        Text("Choose the wiring:", style = MaterialTheme.typography.labelMedium)
                                        m.wirings.forEach { w ->
                                            FilledTonalButton(onClick = { download(m, w.xmodelLink) }, enabled = busy == null, modifier = Modifier.padding(top = 4.dp)) { Text(w.name) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else Spacer(Modifier.weight(1f))
                Text(
                    "Models come from the prop makers via xLights' catalogue. Check the wiring matches how your prop is built.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun Picker(label: String, options: List<String>, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth(), enabled = options.isNotEmpty()) {
            Text(label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Filled.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { i, o -> DropdownMenuItem(text = { Text(o) }, onClick = { onPick(i); open = false }) }
        }
    }
}
