@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.wledcontroller.app.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wledcontroller.app.LocalAppContainer
import com.wledcontroller.app.house.HouseMap
import com.wledcontroller.app.house.MapPoint
import com.wledcontroller.app.house.formatLength
import com.wledcontroller.app.house.model3d.Face
import com.wledcontroller.app.house.model3d.FacePhoto
import com.wledcontroller.app.house.model3d.House3D
import com.wledcontroller.app.house.model3d.Led3D
import com.wledcontroller.app.house.model3d.Lineup
import com.wledcontroller.app.house.model3d.Mesh
import com.wledcontroller.app.house.model3d.P3
import com.wledcontroller.app.house.model3d.Roof
import com.wledcontroller.app.house.model3d.Shell
import com.wledcontroller.app.house.model3d.ShellSpec
import com.wledcontroller.app.house.placedLeds
import com.wledcontroller.app.ui.components.CornerPicker
import com.wledcontroller.app.ui.components.HouseModelView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.hypot

private val CORNER_NAMES = listOf("top-left", "top-right", "bottom-right", "bottom-left")
private val CORNER_COLORS = intArrayOf(0xFF5252, 0x69F0AE, 0x40C4FF, 0xFFD740)

/**
 * The house in 3D (optional): build it from photos of each side or import a 3D scan, line it
 * up with the house photo so the lights on the map land on it, and trace lights the photo
 * can't show (round the side, at the back) straight onto the model.
 */
@Composable
fun Model3DScreen(onBack: () -> Unit, onScan: () -> Unit = {}) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val map by container.store.houseMap.collectAsStateWithLifecycle(HouseMap())
    val model = map.model3d
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var building by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var meshVersion by remember { mutableIntStateOf(0) }

    /** Puts a new model in place of the old one: its files go, and lights traced on it need tracing again. */
    fun replaceWith(m: House3D) = scope.launch {
        val old = map.model3d
        container.store.updateHouseMap { it.copy(model3d = m, runs = it.runs.map { r -> r.copy(points3d = null) }) }
        if (old != null && old.dir != m.dir) container.models.delete(old)
        meshVersion++
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = "Importing the scan…"; message = null
            runCatching { container.models.importScan(uri) }
                .onSuccess { (m, lights) ->
                    if (lights == null) { replaceWith(m).join(); message = "Loaded ${m.triangles / 1000}k triangles. Now line it up with your house photo." }
                    else {
                        // One of this app's own: everything back where it was.
                        val old = map.model3d
                        container.store.updateHouseMap { lights.applyTo(it, m) }
                        if (old != null && old.dir != m.dir) container.models.delete(old)
                        meshVersion++
                        val now = container.store.houseMap.first()
                        message = "Loaded, with your lights back where they were" + lights.summary(now).let { if (it.isBlank()) "." else ": $it." }
                    }
                }
                .onFailure { message = it.message ?: "Couldn't import that file." }
            busy = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("3D house model") },
                navigationIcon = { IconButton(onClick = { if (building) building = false else onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            busy?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Text(it)
                }
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

            when {
                building -> BuildFromPhotos(
                    map, model?.takeIf { it.kind == House3D.Kind.PHOTOS },
                    onBuilt = { m -> building = false; replaceWith(m); message = if (m.lineup != null) "Built, and lined up with your house photo." else "Built. Now line it up with your house photo." },
                    onBusy = { busy = it },
                    onCancel = { building = false },
                )
                model == null -> NoModel(onScan = onScan, onBuild = { building = true; message = null }, onImport = { importer.launch(arrayOf("*/*")) })
                else -> ModelEditor(
                    map, model, meshVersion,
                    onMessage = { message = it },
                    onRebuild = { building = true; message = null },
                    onImport = { importer.launch(arrayOf("*/*")) },
                    onScan = onScan,
                    onTurn = { aboutX ->
                        scope.launch {
                            busy = "Turning…"
                            runCatching { container.models.turn(model, aboutX) }.onSuccess { m ->
                                container.store.updateHouseMap { it.copy(model3d = m, runs = it.runs.map { r -> r.copy(points3d = null) }) }
                                meshVersion++
                            }.onFailure { message = it.message }
                            busy = null
                        }
                    },
                    onRemove = { confirmRemove = true },
                )
            }
        }
    }

    if (confirmRemove && model != null) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove the 3D model?") },
            text = { Text("Your lights and house map stay as they are; only the 3D model (and any lights traced on it) goes.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    scope.launch {
                        container.store.updateHouseMap { it.copy(model3d = null, runs = it.runs.map { r -> r.copy(points3d = null) }) }
                        container.models.delete(model)
                    }
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun NoModel(onScan: () -> Unit, onBuild: () -> Unit, onImport: () -> Unit) {
    Text(
        "Optional: put your house in 3D, with your own photos on it, and your lights where they really are — round the sides and back too. The 3D previews then show your show on it.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Scan it with this phone", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Walk along the house with the camera on it and the app builds a true-size 3D model in colour as you go: porches, posts, set-back parts, bushes and all. Takes a few minutes in daylight. Uses Google Play Services for AR.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onScan) { Text("Scan my house") }
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Build it from photos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Take a straight-on photo of each side of the house, mark each wall's corners, and enter its size and roof shape. The app straightens the photos and wraps them onto the walls. Quick, and works with just the front.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onBuild) { Text("Build from photos") }
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Load a 3D model", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "A model you exported from here before, one made in 3D software like Blender or SketchUp, or a scan from another app (Scaniverse, Polycam, KIRI Engine). GLB, or OBJ in a .zip with its pictures. In metres, the right way up; a medium level of detail works best.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onImport) { Text("Load .glb / .obj / .zip") }
        }
    }
}

@Composable
private fun ModelEditor(
    map: HouseMap,
    model: House3D,
    meshVersion: Int,
    onMessage: (String?) -> Unit,
    onRebuild: () -> Unit,
    onScan: () -> Unit,
    onImport: () -> Unit,
    onTurn: (aboutX: Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf<Pair<Mesh, List<File>>?>(null) }
    var missing by remember { mutableStateOf(false) }
    LaunchedEffect(model.dir, meshVersion, model.sharpened, model.edits) {
        loaded = null; missing = false
        runCatching { container.models.mesh(model) to container.models.textures(model) }
            .onSuccess { loaded = it }.onFailure { missing = true }
    }
    val leds = remember(map) { map.placedLeds() }
    val positions by produceState<List<P3?>?>(null, loaded, model, map.runs, map.aspect) {
        value = loaded?.let { (mesh, _) -> withContext(Dispatchers.Default) { Led3D.positions(map, leds, model, mesh) } }
    }
    // Each run in its own colour, so it's clear which is which.
    val runColors = remember(leds) {
        IntArray(leds.size) { i -> android.graphics.Color.HSVToColor(floatArrayOf((leds[i].runIndex * 47f) % 360f, 0.7f, 1f)) and 0xFFFFFF }
    }

    // Saving the model for other 3D software.
    var exporting by remember { mutableStateOf(false) }
    var exportLights by remember { mutableStateOf(true) }
    var exportBusy by remember { mutableStateOf(false) }
    fun exportTo(uri: android.net.Uri?, glb: Boolean) {
        if (uri == null) return
        val lights = if (exportLights) positions?.let { com.wledcontroller.app.house.model3d.ModelExport.lightMarkers(it, runColors) } else null
        scope.launch {
            exportBusy = true
            runCatching { container.models.export(model, uri, glb, lights, com.wledcontroller.app.house.model3d.ModelLights.of(map, model)) }
                .onSuccess { onMessage("Exported. Open it in Blender or other 3D software: it's in metres, the right way up.") }
                .onFailure { onMessage("Couldn't export: ${it.message}") }
            exportBusy = false; exporting = false
        }
    }
    val saveGlb = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("model/gltf-binary")) { exportTo(it, true) }
    val saveObj = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { exportTo(it, false) }
    val fileName = model.name.ifBlank { "My house" }.replace(Regex("[^A-Za-z0-9 _-]"), "").ifBlank { "house" }
    if (exporting) AlertDialog(
        onDismissRequest = { if (!exportBusy) exporting = false },
        title = { Text("Export the 3D model") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("For Blender, SketchUp, Windows 3D Viewer and other 3D software. In metres, the right way up, with its colours and pictures.", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = exportLights, onCheckedChange = { exportLights = it })
                    Column {
                        Text("Include the lights")
                        Text("A small marker at each light, coloured by run, as an object of its own.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (exportBusy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Text("Saving…")
                }
                Button(enabled = !exportBusy, onClick = { saveGlb.launch("$fileName.glb") }, modifier = Modifier.fillMaxWidth()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("GLB (recommended)"); Text("One file, pictures inside", style = MaterialTheme.typography.labelSmall) }
                }
                OutlinedButton(enabled = !exportBusy, onClick = { saveObj.launch("$fileName-obj.zip") }, modifier = Modifier.fillMaxWidth()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("OBJ (.zip)"); Text("For older software: model, materials and pictures", style = MaterialTheme.typography.labelSmall) }
                }
            }
        },
        confirmButton = { TextButton(enabled = !exportBusy, onClick = { exporting = false }) { Text("Close") } },
    )

    // Lining up: four corners on the model, then on the photo.
    var picking by remember { mutableStateOf(false) }
    val picked = remember { mutableStateListOf<P3>() }
    var askPhoto by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var overlay by remember { mutableFloatStateOf(0.5f) }
    // Tracing a run on the model.
    var tracing by remember { mutableStateOf<String?>(null) }
    val draft = remember { mutableStateListOf<P3>() }
    val traceRun = map.runs.firstOrNull { it.id == tracing }

    fun save(transform: (HouseMap) -> HouseMap) = scope.launch { container.store.updateHouseMap(transform) }

    if (missing) {
        Text("The 3D model's files are missing (it may have come from another phone). Build or import it again.", color = MaterialTheme.colorScheme.error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRebuild) { Text("Build from photos") }
            OutlinedButton(onClick = onImport) { Text("Import a scan") }
        }
        TextButton(onClick = onRemove) { Text("Remove it") }
        return
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(model.name.ifBlank { "My house" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                (if (model.kind == House3D.Kind.SCAN) "3D scan" else "Built from photos") + " · " + (if (model.triangles < 1000) "${model.triangles} triangles" else "${model.triangles / 1000}k triangles"),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("Use in 3D previews", style = MaterialTheme.typography.bodySmall)
        Switch(checked = model.show, onCheckedChange = { on -> save { it.copy(model3d = it.model3d?.copy(show = on)) } }, modifier = Modifier.padding(start = 8.dp))
    }

    // Editing the model: cropping to a box, or erasing with a brush.
    var editTool by remember { mutableStateOf<String?>(null) }
    var brushCm by remember { mutableFloatStateOf(40f) }
    // Erasing by circling an area (rather than brushing), and whether right through the model.
    var eraseLoop by remember { mutableStateOf(false) }
    var eraseThrough by remember { mutableStateOf(false) }
    val stroke = remember { mutableStateListOf<P3>() }
    var cropX by remember { mutableStateOf(0f..1f) }
    var cropY by remember { mutableStateOf(0f..1f) }
    var cropZ by remember { mutableStateOf(0f..1f) }
    var editBusy by remember { mutableStateOf(false) }
    var askBits by remember { mutableStateOf(false) }
    fun commit(edits: List<com.wledcontroller.app.house.model3d.MeshEdit>, done: String) {
        scope.launch {
            editBusy = true
            runCatching { container.models.edit(model, edits) }
                .onSuccess { m -> container.store.updateHouseMap { it.copy(model3d = m) }; onMessage(done) }
                .onFailure { onMessage(it.message ?: "Couldn't change the model.") }
            editBusy = false
        }
    }
    val bounds = loaded?.first?.bounds
    fun along(range: ClosedFloatingPointRange<Float>, lo: Float, hi: Float) = (lo + (hi - lo) * range.start) to (lo + (hi - lo) * range.endInclusive)
    fun cropBox(): Pair<P3, P3>? = bounds?.let { (lo, hi) ->
        val (x0, x1) = along(cropX, lo.x, hi.x); val (y0, y1) = along(cropY, lo.y, hi.y); val (z0, z1) = along(cropZ, lo.z, hi.z)
        P3(x0, y0, z0) to P3(x1, y1, z1)
    }

    // What to tap, when tapping.
    when {
        editTool == "erase" -> Text(
            if (eraseLoop) "Erase: draw a loop round what you don't want with one finger; lift it to erase inside. Two fingers turn the model; pinch to zoom."
            else "Erase: paint over what you don't want with one finger. Two fingers turn the model; pinch to zoom.",
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
        )
        editTool == "crop" -> Text("Crop: move the sliders until the yellow box holds just what you want to keep. Drag to turn the model to check.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        picking -> Text("Tap the front wall's ${CORNER_NAMES[picked.size.coerceAtMost(3)]} corner on the model (${picked.size}/4). Drag to turn the model, pinch to zoom.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        traceRun != null -> Text("Tracing \"${traceRun.name}\": tap along where its lights go on the model, starting at its first light. ${draft.size} point${if (draft.size == 1) "" else "s"}.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        checking -> Text("Your house photo over the model, from where it was taken. The two should match.", style = MaterialTheme.typography.bodySmall)
        else -> Text("Drag to turn · two fingers to move · pinch to zoom", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    val mesh = loaded
    Box(
        Modifier.fillMaxWidth().aspectRatio(if (checking) map.aspect.coerceIn(0.5f, 2.5f) else 1.25f).clip(RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (mesh == null) { CircularProgressIndicator(Modifier.size(28.dp)); return@Box }
        // Lights in their run colours, then any corners or tracing on top.
        val pts = ArrayList<P3?>(positions ?: emptyList())
        val cols = ArrayList<Int>(runColors.toList().take(pts.size))
        if (traceRun != null) {
            // The traced run's lights as they'd be, and the points tapped.
            Led3D.along(draft.toList(), traceRun).forEach { pts += it; cols += 0xFFE040 }
            draft.forEach { pts += it; cols += 0xFFFFFF }
        }
        if (picking) picked.forEachIndexed { i, p -> pts += p; cols += CORNER_COLORS[i] }
        if (!picking && !checking && traceRun == null && editTool == null) model.lineupModel?.forEachIndexed { i, p -> pts += p; cols += CORNER_COLORS[i % 4] }
        // The eraser's stroke so far, in red.
        // The crop box's edges, in yellow dots.
        if (editTool == "crop") cropBox()?.let { (lo, hi) ->
            val c = listOf(P3(lo.x, lo.y, lo.z), P3(hi.x, lo.y, lo.z), P3(hi.x, hi.y, lo.z), P3(lo.x, hi.y, lo.z), P3(lo.x, lo.y, hi.z), P3(hi.x, lo.y, hi.z), P3(hi.x, hi.y, hi.z), P3(lo.x, hi.y, hi.z))
            listOf(0 to 1, 1 to 2, 2 to 3, 3 to 0, 4 to 5, 5 to 6, 6 to 7, 7 to 4, 0 to 4, 1 to 5, 2 to 6, 3 to 7).forEach { (a, b) ->
                for (k in 0..30) { pts += c[a] + (c[b] - c[a]) * (k / 30f); cols += 0xFFE040 }
            }
        }
        HouseModelView(
            mesh.first, mesh.second, pts, cols.toIntArray(), Modifier.fillMaxSize(),
            brightness = if (checking) 0.8f else 1f,
            fixedCamera = if (checking) model.lineup else null,
            onTap = when {
                picking -> { p ->
                    if (p == null) onMessage("Missed the model — tap right on it.")
                    else { onMessage(null); picked += p; if (picked.size == 4) { picking = false; askPhoto = true } }
                }
                traceRun != null -> { p -> if (p == null) onMessage("Missed the model — tap right on it.") else { onMessage(null); draft += p } }
                else -> null
            },
            onPaintCancel = { stroke.clear() },
            onLasso = if (editTool == "erase" && eraseLoop && !editBusy) { { loop, cam, aspect ->
                commit(
                    model.edits + com.wledcontroller.app.house.model3d.MeshEdit.Lasso(cam, aspect, loop.map { it.first }, loop.map { it.second }, eraseThrough),
                    "Erased inside the loop. Undo puts it back.",
                )
            } } else null,
            onPaint = if (editTool == "erase" && !eraseLoop && !editBusy) { { p, end ->
                val r = brushCm / 100f
                if (p != null && (stroke.isEmpty() || (stroke.last() - p).length > r * 0.3f)) stroke += p
                if (end && stroke.isNotEmpty()) {
                    val spots = stroke.toList()
                    stroke.clear()
                    commit(model.edits + com.wledcontroller.app.house.model3d.MeshEdit.Erase(spots, r), "Erased. Undo puts it back.")
                }
            } } else null,
        )
        if (editBusy) CircularProgressIndicator(Modifier.size(32.dp))
        if (checking) map.photoPath?.let { path ->
            val photo by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, path) {
                value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 2 })?.asImageBitmap() }
            }
            photo?.let { Image(it, null, Modifier.fillMaxSize().alpha(overlay), contentScale = ContentScale.FillBounds) }
        }
    }

    when {
        editTool == "erase" -> Column {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !eraseLoop, onClick = { eraseLoop = false }, label = { Text("Brush") })
                FilterChip(selected = eraseLoop, onClick = { eraseLoop = true }, label = { Text("Circle") })
            }
            if (eraseLoop) Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Through the whole model", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (eraseThrough) "Everything inside the loop goes, behind as well — e.g. to slice off the yard."
                        else "Only what you can see inside the loop goes; what's behind it stays.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = eraseThrough, onCheckedChange = { eraseThrough = it })
            } else {
                Text("Brush: ${formatLength(brushCm, map.useInches)} across", style = MaterialTheme.typography.labelSmall)
                Slider(value = brushCm, onValueChange = { brushCm = it }, valueRange = 10f..200f)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = model.edits.isNotEmpty() && !editBusy, onClick = { commit(model.edits.dropLast(1), "Undone.") }) { Text("Undo") }
                Button(onClick = { editTool = null; stroke.clear() }) { Text("Done") }
            }
        }
        editTool == "crop" -> Column {
            val b = bounds
            if (b != null) {
                val (lo, hi) = b
                @Composable
                fun Range(label: String, value: ClosedFloatingPointRange<Float>, from: Float, to: Float, set: (ClosedFloatingPointRange<Float>) -> Unit) {
                    val (a, c) = along(value, from, to)
                    Text("$label: ${formatLength((c - a) * 100, map.useInches)}", style = MaterialTheme.typography.labelSmall)
                    androidx.compose.material3.RangeSlider(value = value, onValueChange = set, valueRange = 0f..1f)
                }
                Range("Left to right", cropX, lo.x, hi.x) { cropX = it }
                // Height as two plain sliders, each doing what it says.
                val (bottom, top) = along(cropY, lo.y, hi.y)
                Text("Raise the bottom: ${formatLength((bottom - lo.y) * 100, map.useInches)} cut off (slide right to cut off the lawn)", style = MaterialTheme.typography.labelSmall)
                Slider(value = cropY.start, onValueChange = { v -> cropY = v.coerceAtMost(cropY.endInclusive - 0.02f)..cropY.endInclusive })
                Text("Lower the top: ${formatLength((hi.y - top) * 100, map.useInches)} cut off", style = MaterialTheme.typography.labelSmall)
                Slider(value = 1f - cropY.endInclusive, onValueChange = { v -> cropY = cropY.start..(1f - v).coerceAtLeast(cropY.start + 0.02f) })
                Range("Back to front", cropZ, lo.z, hi.z) { cropZ = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { editTool = null }) { Text("Cancel") }
                Button(enabled = !editBusy, onClick = {
                    cropBox()?.let { (lo, hi) -> commit(model.edits + com.wledcontroller.app.house.model3d.MeshEdit.Crop(lo, hi), "Cropped. Undo puts it back.") }
                    editTool = null
                }) { Text("Crop") }
            }
        }
        picking -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = picked.isNotEmpty(), onClick = { picked.removeAt(picked.lastIndex) }) { Text("Undo") }
            TextButton(onClick = { picking = false; picked.clear() }) { Text("Cancel") }
        }
        traceRun != null -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = draft.isNotEmpty(), onClick = { draft.removeAt(draft.lastIndex) }) { Text("Undo") }
            OutlinedButton(enabled = draft.isNotEmpty(), onClick = { draft.clear() }) { Text("Clear") }
            TextButton(onClick = { tracing = null; draft.clear() }) { Text("Cancel") }
            Button(enabled = draft.isNotEmpty(), onClick = {
                val run = traceRun
                val points = draft.toList()
                val cam = model.lineup
                // Not on the photo yet: give it a place there too, so effects reach it.
                val photoPoints = if (run.points.isEmpty() && run.ledPoints == null && cam != null) Led3D.toPhoto(points, cam, map.aspect) else null
                save { m -> m.copy(runs = m.runs.map { if (it.id == run.id) it.copy(points3d = points, points = photoPoints ?: it.points) else it }) }
                if (run.points.isEmpty() && run.ledPoints == null && cam == null)
                    onMessage("Saved. Line the model up with your photo too, so effects can reach \"${run.name}\".")
                tracing = null; draft.clear()
            }) { Text("Save") }
        }
        checking -> Column {
            Text("Photo", style = MaterialTheme.typography.labelSmall)
            Slider(value = overlay, onValueChange = { overlay = it })
            TextButton(onClick = { checking = false }) { Text("Done checking") }
        }
    }

    if (!picking && traceRun == null && !checking && editTool == null) {
        // Tidying up the model: cropping, erasing, loose bits.
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Edit the model", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Cut away the lawn, trees and stray bits. " +
                        if (model.edits.isEmpty()) "The original is kept, so any edit can be undone."
                        else "${model.edits.size} edit${if (model.edits.size == 1) "" else "s"} so far; the original is kept.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !editBusy && bounds != null, onClick = { cropX = 0f..1f; cropY = 0f..1f; cropZ = 0f..1f; editTool = "crop"; onMessage(null) }) { Text("Crop") }
                    OutlinedButton(enabled = !editBusy, onClick = { stroke.clear(); editTool = "erase"; onMessage(null) }) { Text("Erase") }
                    OutlinedButton(enabled = !editBusy, onClick = { askBits = true }) { Text("Remove loose bits") }
                }
                if (model.edits.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = !editBusy, onClick = { commit(model.edits.dropLast(1), "Undone.") }) { Text("Undo") }
                    TextButton(enabled = !editBusy, onClick = { commit(emptyList(), "Back to the original.") }) { Text("Reset to original") }
                }
                if (editBusy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Text("Working…", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        // Lining up with the house photo.
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Line up with your house photo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    if (model.lineup != null) "Lined up: the lights on your house map are on the model (${"%.0f".format(Lineup.fieldOfView(model.lineup))}° lens)."
                    else "Tap the front wall's four corners on the model, then mark the same corners on your house photo. The app works out where you stood for the photo and puts every light from your map onto the model.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (map.photoPath == null) Text("Add a house photo on the house map first.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = map.photoPath != null, onClick = { picked.clear(); picking = true; onMessage(null) }) { Text(if (model.lineup != null) "Line up again" else "Line up") }
                    if (model.lineup != null) OutlinedButton(onClick = { checking = true }) { Text("Check against the photo") }
                }
            }
        }
        // Lights the photo can't show: traced on the model.
        if (map.runs.isNotEmpty()) Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Lights on the model", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Lights come from the photo once it's lined up. For lights round the side or back, or anywhere the photo puts them wrong, trace them on the model.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                map.runs.forEachIndexed { i, run ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.Canvas(Modifier.size(10.dp)) {
                            drawCircle(androidx.compose.ui.graphics.Color(android.graphics.Color.HSVToColor(floatArrayOf((i * 47f) % 360f, 0.7f, 1f))))
                        }
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(run.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                when {
                                    run.points3d != null -> "Traced on the model"
                                    model.lineup != null && run.placedCount > 0 -> "From the photo"
                                    else -> "Not on the model yet"
                                },
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (run.points3d != null) TextButton(onClick = { save { m -> m.copy(runs = m.runs.map { if (it.id == run.id) it.copy(points3d = null) else it }) } }) { Text("Use photo") }
                        if (run.model == null) TextButton(onClick = { draft.clear(); run.points3d?.let { draft.addAll(it) }; tracing = run.id; onMessage(null) }) { Text("Trace") }
                    }
                }
            }
        }
        // Colours from the scan's photos (optional).
        if (model.kind == House3D.Kind.SCAN && (model.photos > 0 || model.sharpened)) Card(Modifier.fillMaxWidth()) {
            var progress by remember { mutableStateOf<Float?>(null) }
            val mb = remember(model.photos) { container.models.photoBytes(model) / 1_000_000 }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Colours", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    if (model.sharpened) "Painted from the photos taken while scanning."
                    else "The scan's own colours. The ${model.photos} photos taken while scanning can paint it much sharper (a minute or two).",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                progress?.let { p -> androidx.compose.material3.LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth()) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!model.sharpened && model.photos > 0) Button(enabled = progress == null, onClick = {
                        scope.launch {
                            progress = 0f
                            runCatching { container.models.sharpen(model) { progress = it } }
                                .onSuccess { s -> save { it.copy(model3d = s) }; onMessage("Colours sharpened.") }
                                .onFailure { onMessage(it.message ?: "Couldn't sharpen the colours.") }
                            progress = null
                        }
                    }) { Text("Sharpen colours") }
                    if (model.sharpened) OutlinedButton(enabled = progress == null, onClick = {
                        scope.launch { val s = container.models.plainColours(model); save { it.copy(model3d = s) } }
                    }) { Text("Back to the scan's colours") }
                    if (model.photos > 0) TextButton(enabled = progress == null, onClick = {
                        val s = container.models.dropPhotos(model); save { it.copy(model3d = s) }
                    }) { Text("Delete the photos ($mb MB)") }
                }
            }
        }
        // The model itself.
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("The model", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (model.kind == House3D.Kind.SCAN) {
                    Text("Lying on its side or facing the wrong way? Turn it (this clears the line-up and any tracing).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onTurn(true) }) { Text("Tip it over") }
                        OutlinedButton(onClick = { onTurn(false) }) { Text("Turn it round") }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { exporting = true }) { Text("Export…") }
                    OutlinedButton(onClick = onScan) { Text("Scan again") }
                    OutlinedButton(onClick = onRebuild) { Text(if (model.kind == House3D.Kind.PHOTOS) "Change size or photos" else "Build from photos instead") }
                    OutlinedButton(onClick = onImport) { Text("Load a different model") }
                    TextButton(onClick = onRemove) { Text("Remove") }
                }
            }
        }
    }

    if (askBits) AlertDialog(
        onDismissRequest = { askBits = false },
        title = { Text("Remove loose bits") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Bits of the scan that float on their own, not joined to the rest.", style = MaterialTheme.typography.bodySmall)
                listOf(
                    Triple("Tiny specks", "Pieces under 200 triangles", com.wledcontroller.app.house.model3d.MeshEdit.DropBits(200)),
                    Triple("Bigger bits too", "Pieces under 3,000 triangles", com.wledcontroller.app.house.model3d.MeshEdit.DropBits(3000)),
                    Triple("Keep only the biggest piece", "Everything not joined to the main part goes", com.wledcontroller.app.house.model3d.MeshEdit.DropBits(keepLargestOnly = true)),
                ).forEach { (label, about, e) ->
                    TextButton(onClick = { askBits = false; commit(model.edits + e, "Loose bits removed. Undo puts them back.") }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) { Text(label); Text(about, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { askBits = false }) { Text("Cancel") } },
    )

    if (askPhoto) {
        val path = map.photoPath
        if (path == null) { askPhoto = false; return }
        CornerPicker(
            path, map.aspect, "Now the same four corners on your house photo", model.lineupPhoto,
            onDone = { corners ->
                askPhoto = false
                val cam = Lineup.solve(picked.toList(), corners, map.aspect)
                if (cam == null) onMessage("Couldn't line it up — check the corners are in the same order on the model and the photo, and try again.")
                else {
                    val pts = picked.toList()
                    save { it.copy(model3d = it.model3d?.copy(lineup = cam, lineupModel = pts, lineupPhoto = corners)) }
                    onMessage("Lined up. Check it against the photo: the outlines should match.")
                    checking = true
                }
                picked.clear()
            },
            onCancel = { askPhoto = false; picked.clear() },
        )
    }
}

/** Building the house from its size and photos of its sides. */
@Composable
private fun BuildFromPhotos(map: HouseMap, existing: House3D?, onBuilt: (House3D) -> Unit, onBusy: (String?) -> Unit, onCancel: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var spec by remember { mutableStateOf(existing?.shell ?: ShellSpec()) }
    val faces = remember { mutableStateMapOf<Face, FacePhoto>().apply { existing?.faces?.forEach { put(it.face, it) } } }
    var cornersFor by remember { mutableStateOf<Face?>(null) }
    var pickFor by remember { mutableStateOf<Face?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var sizeKey by remember { mutableIntStateOf(0) }
    val inches = map.useInches

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val f = pickFor
        if (uri != null && f != null) scope.launch {
            runCatching { container.models.keepPhoto(uri) }
                .onSuccess { (path, aspect) -> faces[f] = FacePhoto(f, path, emptyList(), aspect); cornersFor = f }
                .onFailure { error = it.message }
        }
    }

    Text("Build from photos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Text(
        "Stand back from each side and take a straight-on photo with the whole wall in it (take them with your camera first, then pick them here). Only the front is needed; sides without a photo are plain.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Text("Size", style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LengthField("Width", spec.widthM, inches, sizeKey, Modifier.weight(1f)) { spec = spec.copy(widthM = it) }
        LengthField("Depth", spec.depthM, inches, sizeKey, Modifier.weight(1f)) { spec = spec.copy(depthM = it) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LengthField("Wall height", spec.wallM, inches, sizeKey, Modifier.weight(1f)) { spec = spec.copy(wallM = it) }
        if (spec.roof != Roof.FLAT) LengthField("Roof height", spec.roofM, inches, sizeKey, Modifier.weight(1f)) { spec = spec.copy(roofM = it) }
    }
    // The front wall measured on the house photo, when it's the house photo and the map has a scale.
    val front = faces[Face.FRONT]
    val scale = map.cmPerHeight
    if (front != null && front.path == map.photoPath && front.corners.size == 4 && scale != null) {
        val c = front.corners
        val widthM = hypot((c[1].x - c[0].x) * map.aspect, c[1].y - c[0].y) * scale / 100f
        val wallM = hypot((c[3].x - c[0].x) * map.aspect, c[3].y - c[0].y) * scale / 100f
        TextButton(onClick = { spec = spec.copy(widthM = widthM, wallM = wallM); sizeKey++ }) {
            Text("Use the map's scale: ${formatLength(widthM * 100, inches)} wide, walls ${formatLength(wallM * 100, inches)}")
        }
    }

    Text("Roof", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Roof.entries.forEach { r -> FilterChip(selected = spec.roof == r, onClick = { spec = spec.copy(roof = r) }, label = { Text(r.label) }) }
    }
    if (spec.roof == Roof.GABLE) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = spec.ridgeAcross, onClick = { spec = spec.copy(ridgeAcross = true) }, label = { Text("Ridge left to right") })
        FilterChip(selected = !spec.ridgeAcross, onClick = { spec = spec.copy(ridgeAcross = false) }, label = { Text("Ridge front to back") })
    }

    Text("Photos", style = MaterialTheme.typography.titleSmall)
    Face.entries.forEach { face ->
        val fp = faces[face]
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(face.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        fp == null -> "No photo"
                        fp.corners.size < 4 -> "Photo chosen — mark the wall's corners"
                        fp.path == map.photoPath -> "Your house photo, corners marked"
                        else -> "Photo, corners marked"
                    },
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (Shell.hasGable(spec, face))
                    Text("Gable end: mark the corners of the rectangular wall; the triangle above comes from the photo.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickFor = face; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text(if (fp == null) "Choose photo" else "Change photo") }
                    val housePhoto = map.photoPath
                    if (face == Face.FRONT && housePhoto != null && fp?.path != housePhoto)
                        OutlinedButton(onClick = { faces[face] = FacePhoto(face, housePhoto, emptyList(), map.aspect); cornersFor = face }) { Text("Use my house photo") }
                    if (fp != null) TextButton(onClick = { cornersFor = face }) { Text("Corners") }
                    if (fp != null) TextButton(onClick = { faces.remove(face) }) { Text("Remove") }
                }
            }
        }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onCancel) { Text("Cancel") }
        Button(
            enabled = faces.values.all { it.corners.size == 4 } && spec.widthM > 0.5f && spec.depthM > 0.5f && spec.wallM > 0.5f,
            onClick = {
                scope.launch {
                    onBusy("Building your house…"); error = null
                    val list = faces.values.toList()
                    runCatching { container.models.buildFromPhotos(spec, list) }.onSuccess { built ->
                        // Front from the house photo: it's lined up already.
                        val f = list.firstOrNull { it.face == Face.FRONT && it.path == map.photoPath }
                        val wall = Shell.frontCorners(spec)
                        val cam = f?.let { Lineup.solve(wall, it.corners, map.aspect) }
                        onBuilt(if (cam != null) built.copy(lineup = cam, lineupModel = wall, lineupPhoto = f.corners) else built)
                    }.onFailure { error = it.message ?: "Couldn't build it." }
                    onBusy(null)
                }
            },
        ) { Text("Build the 3D house") }
    }

    cornersFor?.let { face ->
        val fp = faces[face]
        if (fp == null) { cornersFor = null; return@let }
        CornerPicker(
            fp.path, fp.aspect, "${face.label}: mark the wall's corners", fp.corners.takeIf { it.size == 4 },
            onDone = { faces[face] = fp.copy(corners = it); cornersFor = null },
            onCancel = { cornersFor = null },
        )
    }
}

/** A length in feet or metres, kept in metres. [key] changing resets the text from [metres]. */
@Composable
private fun LengthField(label: String, metres: Float, inches: Boolean, key: Int, modifier: Modifier, onChange: (Float) -> Unit) {
    val unit = if (inches) 0.3048f else 1f
    var text by remember(key, inches) { mutableStateOf("%.1f".format(metres / unit)) }
    OutlinedTextField(
        text,
        { v -> text = v; v.replace(',', '.').toFloatOrNull()?.takeIf { it > 0f }?.let { onChange(it * unit) } },
        label = { Text("$label (${if (inches) "ft" else "m"})") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}
