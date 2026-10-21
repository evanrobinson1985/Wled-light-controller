package com.wledcontroller.app.data

import com.wledcontroller.app.house.HouseMap
import com.wledcontroller.app.house.LightRun
import com.wledcontroller.app.house.MapPoint
import com.wledcontroller.app.house.ModelKind
import com.wledcontroller.app.house.PropModel
import com.wledcontroller.app.house.PropType
import com.wledcontroller.app.house.Submodel
import com.wledcontroller.app.house.XlModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.math.roundToInt

/** A house map kept under a name: its runs, groups and scale, with its photo beside it ([photo], a file in the layouts folder). */
@Serializable
data class SavedLayout(
    val id: String,
    val name: String,
    val savedAt: Long,
    val map: HouseMap,
    val photo: String? = null,
)

/**
 * A light model kept for reuse: the prop's shape and wiring, its parts and real size. Traced
 * and LED-by-LED runs are kept as a custom grid in the shape they had on the house.
 */
@Serializable
data class SavedModel(
    val id: String,
    val name: String,
    val savedAt: Long,
    val prop: PropType,
    val ledCount: Int,
    val model: PropModel,
    val submodels: List<Submodel> = emptyList(),
    val widthCm: Float? = null,
    val heightCm: Float? = null,
    val mirrored: Boolean = false,
    val upsideDown: Boolean = false,
) {
    /** As a prop ready to go on a house map (the same form an xLights model takes). */
    fun toXlModel() = XlModel(name, "Custom", ledCount, model, prop, submodels, null, null, null, null, widthCm, heightCm)

    /** As a run on [deviceId] from [startLed], not yet placed on the photo. */
    fun toRun(id: String, deviceId: String, startLed: Int) = LightRun(
        id, name, deviceId, startLed, ledCount, prop = prop, model = model, submodels = submodels,
        realWidthCm = widthCm, realHeightCm = heightCm, mirrored = mirrored, upsideDown = upsideDown,
    )

    companion object {
        /** [run] as it is on [map] kept as a model, or null when it has no LEDs placed to take a shape from. */
        fun fromRun(run: LightRun, map: HouseMap, name: String = run.name, now: Long = System.currentTimeMillis()): SavedModel? {
            val id = UUID.randomUUID().toString()
            run.model?.let { m ->
                return SavedModel(id, name, now, run.prop, run.count, m, run.submodels, run.realWidthCm ?: boxCm(run.points, map, true),
                    run.realHeightCm ?: boxCm(run.points, map, false), run.mirrored, run.upsideDown)
            }
            val pos = filled(map.positions(run)) ?: return null
            val grid = customGrid(pos, map.aspect)
            val scale = map.cmPerHeight
            val (w, h) = span(pos)
            return SavedModel(
                id, name, now, run.prop, run.count, grid, run.submodels,
                widthCm = scale?.let { (w * map.aspect * it).coerceAtLeast(1f) },
                heightCm = scale?.let { (h * it).coerceAtLeast(1f) },
            )
        }

        private fun boxCm(points: List<MapPoint>, map: HouseMap, across: Boolean): Float? {
            val scale = map.cmPerHeight ?: return null
            if (points.size < 2) return null
            return if (across) abs(points[1].x - points[0].x) * map.aspect * scale else abs(points[1].y - points[0].y) * scale
        }

        /** Every LED's position, unplaced ones taking the one before (or after) them; null when none is placed. */
        internal fun filled(pos: List<MapPoint?>): List<MapPoint>? {
            val first = pos.firstOrNull { it != null } ?: return null
            var last: MapPoint = first
            return pos.map { p -> (p ?: last).also { last = it } }
        }

        private fun span(pos: List<MapPoint>) = (pos.maxOf { it.x } - pos.minOf { it.x }) to (pos.maxOf { it.y } - pos.minOf { it.y })

        /**
         * The LEDs at [pos] (photo coordinates, the photo [aspect] wide) as a custom grid with
         * one LED per cell, keeping their shape. The grid gets finer until no two LEDs share a
         * cell; any still sharing at the finest move to the nearest free cell.
         */
        fun customGrid(pos: List<MapPoint>, aspect: Float): PropModel {
            val minX = pos.minOf { it.x }; val minY = pos.minOf { it.y }
            val w = (pos.maxOf { it.x } - minX) * aspect; val h = pos.maxOf { it.y } - minY
            val long = maxOf(w, h).takeIf { it > 1e-6f } ?: 1f
            var result: PropModel? = null
            for (side in intArrayOf(24, 48, 96, 192, 320)) {
                val gw = if (w <= 1e-6f) 1 else (side * w / long).roundToInt().coerceAtLeast(1)
                val gh = if (h <= 1e-6f) 1 else (side * h / long).roundToInt().coerceAtLeast(1)
                val cells = pos.map { p ->
                    val c = if (gw == 1) 0 else (((p.x - minX) * aspect / w) * (gw - 1)).roundToInt().coerceIn(0, gw - 1)
                    val r = if (gh == 1) 0 else (((p.y - minY) / h) * (gh - 1)).roundToInt().coerceIn(0, gh - 1)
                    r * gw + c
                }
                result = PropModel(ModelKind.CUSTOM, customW = gw, customH = gh, customCells = cells)
                if (cells.toSet().size == cells.size) return result
            }
            return unshare(result!!)
        }

        /** Moves LEDs that share a cell to the nearest free one, growing the grid if it's full. */
        private fun unshare(m: PropModel): PropModel {
            val gw = m.customW; val cells = m.customCells
            // Too few cells for the LEDs (a long straight line): add rows for them to move into.
            val gh = maxOf(m.customH, (cells.size + gw - 1) / gw)
            val used = HashSet<Int>()
            val out = cells.map { c ->
                if (used.add(c)) c
                else {
                    val r0 = c / gw; val c0 = c % gw
                    var found = -1
                    var d = 1
                    while (found < 0 && d <= maxOf(gw, gh)) {
                        loop@ for (dr in -d..d) for (dc in -d..d) {
                            if (maxOf(abs(dr), abs(dc)) != d) continue
                            val r = r0 + dr; val cc = c0 + dc
                            if (r !in 0 until gh || cc !in 0 until gw) continue
                            if (used.add(r * gw + cc)) { found = r * gw + cc; break@loop }
                        }
                        d++
                    }
                    found
                }
            }
            return m.copy(customW = gw, customH = gh, customCells = out)
        }

        /**
         * [model] as an xLights custom model file (.xmodel), so it can be used in xLights or
         * shared. Every kind is written as a custom grid in the shape it has on the house.
         */
        fun toXmodel(model: SavedModel): String {
            val m = model.model
            val grid = if (m.kind == ModelKind.CUSTOM) m else {
                val aspect = m.shapeAspect.takeIf { model.widthCm == null || model.heightCm == null }
                    ?: (model.widthCm!! / model.heightCm!!)
                customGrid(m.layout(MapPoint(0f, 0f), MapPoint(1f, 1f), 1f), aspect)
            }.let { g ->
                // Turned over as it is on the house.
                if (!model.mirrored && !model.upsideDown) g
                else g.copy(customCells = g.customCells.map { c ->
                    if (c < 0) c else {
                        val r = c / g.customW; val col = c % g.customW
                        (if (model.upsideDown) g.customH - 1 - r else r) * g.customW + (if (model.mirrored) g.customW - 1 - col else col)
                    }
                })
            }
            val byCell = HashMap<Int, Int>()
            grid.customCells.forEachIndexed { i, c -> if (c >= 0) byCell.putIfAbsent(c, i + 1) }
            val data = (0 until grid.customH).joinToString(";") { r ->
                (0 until grid.customW).joinToString(",") { c -> byCell[r * grid.customW + c]?.toString() ?: "" }
            }
            fun esc(s: String) = s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
            val size = buildString {
                model.widthCm?.let { append(" widthmm=\"${(it * 10).roundToInt()}\"") }
                model.heightCm?.let { append(" heightmm=\"${(it * 10).roundToInt()}\"") }
            }
            val subs = model.submodels.joinToString("") { s ->
                val line = s.ranges.joinToString(",") { r -> if (r.first == r.last) "${r.first + 1}" else "${r.first + 1}-${r.last + 1}" }
                "\n  <subModel name=\"${esc(s.name)}\" layout=\"horizontal\" type=\"ranges\" line0=\"$line\"/>"
            }
            return """<?xml version="1.0" encoding="UTF-8"?>
<custommodel name="${esc(model.name)}" DisplayAs="Custom" parm1="${grid.customW}" parm2="${grid.customH}" parm3="1" Depth="1" StringType="RGB Nodes" Transparency="0" PixelSize="2" ModelBrightness="" Antialias="1" CustomModel="$data"$size SourceVersion="2024.01">$subs
</custommodel>
"""
        }
    }
}

/**
 * Saved house layouts and light models, kept in app storage (in backups, and the same in and
 * out of the simulator). Layouts and models can also go to and come from files to share.
 */
class Library(private val filesDir: File) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val layoutsDir get() = File(filesDir, LAYOUTS).apply { mkdirs() }
    private val modelsDir get() = File(filesDir, MODELS).apply { mkdirs() }

    private val _layouts = MutableStateFlow<List<SavedLayout>>(emptyList())
    /** Saved layouts, newest first. */
    val layouts: StateFlow<List<SavedLayout>> = _layouts.asStateFlow()

    private val _models = MutableStateFlow<List<SavedModel>>(emptyList())
    /** Saved models, by name. */
    val models: StateFlow<List<SavedModel>> = _models.asStateFlow()

    init { reload() }

    /** Reads the library again (after a restore). */
    fun reload() {
        _layouts.value = readAll<SavedLayout>(layoutsDir).sortedByDescending { it.savedAt }
        _models.value = readAll<SavedModel>(modelsDir).sortedBy { it.name.lowercase() }
    }

    private inline fun <reified T> readAll(dir: File): List<T> =
        dir.listFiles { f -> f.extension == "json" }?.mapNotNull { f -> runCatching { json.decodeFromString<T>(f.readText()) }.getOrNull() } ?: emptyList()

    private fun write(file: File, text: String) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    // ---- Layouts ----

    /**
     * Saves [map] (with a copy of its photo) as [name]. Saving under the name of a layout already
     * kept replaces it.
     */
    suspend fun saveLayout(name: String, map: HouseMap, now: Long = System.currentTimeMillis()): SavedLayout = withContext(Dispatchers.IO) {
        val existing = _layouts.value.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
        val id = existing?.id ?: UUID.randomUUID().toString()
        existing?.photo?.let { File(layoutsDir, it).delete() }
        val photo = map.photoPath?.let(::File)?.takeIf { it.isFile }?.let { src ->
            val dest = File(layoutsDir, "$id-$now.jpg")
            src.copyTo(dest, overwrite = true)
            dest.name
        }
        val layout = SavedLayout(id, name.trim().ifEmpty { "House layout" }, now, map.copy(photoPath = null), photo)
        write(File(layoutsDir, "$id.json"), json.encodeToString(layout))
        reload()
        layout
    }

    /**
     * [layout] ready to become the house map: its photo copied to where the house photo lives
     * (as `house_….jpg`, replacing the old one) and the map pointed at it.
     */
    suspend fun openLayout(layout: SavedLayout): HouseMap = withContext(Dispatchers.IO) {
        val src = layout.photo?.let { File(layoutsDir, it) }?.takeIf { it.isFile }
        val photoPath = src?.let {
            val dest = File(filesDir, "house_${System.currentTimeMillis()}.jpg")
            it.copyTo(dest, overwrite = true)
            filesDir.listFiles { f -> f.name.startsWith("house_") && f != dest }?.forEach { old -> old.delete() }
            dest.absolutePath
        }
        layout.map.copy(photoPath = photoPath)
    }

    suspend fun renameLayout(id: String, name: String) = withContext(Dispatchers.IO) {
        val l = _layouts.value.firstOrNull { it.id == id } ?: return@withContext
        write(File(layoutsDir, "$id.json"), json.encodeToString(l.copy(name = name.trim().ifEmpty { l.name })))
        reload()
    }

    suspend fun deleteLayout(id: String) = withContext(Dispatchers.IO) {
        _layouts.value.firstOrNull { it.id == id }?.photo?.let { File(layoutsDir, it).delete() }
        File(layoutsDir, "$id.json").delete()
        reload()
    }

    /** [layout] as one file (a zip of the layout and its photo) to keep elsewhere or share. */
    suspend fun exportLayout(layout: SavedLayout, out: OutputStream) = withContext(Dispatchers.IO) {
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(LAYOUT_ENTRY)); zip.write(json.encodeToString(layout.copy(photo = null)).toByteArray()); zip.closeEntry()
            layout.photo?.let { File(layoutsDir, it) }?.takeIf { it.isFile }?.let { f ->
                zip.putNextEntry(ZipEntry(PHOTO_ENTRY)); f.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            }
        }
    }

    /** Adds a layout from a file made by [exportLayout]. A layout with the same name gets "(2)" added. */
    suspend fun importLayout(input: InputStream, now: Long = System.currentTimeMillis()): SavedLayout = withContext(Dispatchers.IO) {
        var layout: SavedLayout? = null
        var photo: ByteArray? = null
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                when (e.name) {
                    LAYOUT_ENTRY -> layout = json.decodeFromString<SavedLayout>(zip.readBytes().decodeToString())
                    PHOTO_ENTRY -> photo = zip.readBytes()
                }
                zip.closeEntry()
            }
        }
        val l = layout ?: throw IllegalArgumentException("That isn't a house layout file.")
        val id = UUID.randomUUID().toString()
        val photoName = photo?.let { bytes -> "$id-$now.jpg".also { File(layoutsDir, it).writeBytes(bytes) } }
        val saved = l.copy(id = id, name = uniqueName(l.name, _layouts.value.map { it.name }), photo = photoName, map = l.map.copy(photoPath = null))
        write(File(layoutsDir, "$id.json"), json.encodeToString(saved))
        reload()
        saved
    }

    // ---- Models ----

    suspend fun saveModel(model: SavedModel) = withContext(Dispatchers.IO) {
        val m = model.copy(name = uniqueName(model.name, _models.value.filter { it.id != model.id }.map { it.name }))
        write(File(modelsDir, "${m.id}.json"), json.encodeToString(m))
        reload()
        m
    }

    suspend fun deleteModel(id: String) = withContext(Dispatchers.IO) {
        File(modelsDir, "$id.json").delete()
        reload()
    }

    /** [model] as a file: this app's own form, which keeps it exactly. */
    fun modelFile(model: SavedModel): String = json.encodeToString(model)

    /** A model from a file: this app's own form, or an xLights model (.xmodel). */
    fun parseModelFile(text: String, now: Long = System.currentTimeMillis()): SavedModel {
        val t = text.trimStart()
        if (t.startsWith("{")) {
            return json.decodeFromString<SavedModel>(t).copy(id = UUID.randomUUID().toString(), savedAt = now)
        }
        val x = com.wledcontroller.app.house.XlightsModels.parseXmodel(t.byteInputStream())
        val model = x.model ?: PropModel(ModelKind.MATRIX, strings = 1, nodes = x.ledCount, vertical = false, zigzag = false)
        return SavedModel(UUID.randomUUID().toString(), x.name, now, x.prop, x.ledCount, model, x.submodels, x.widthCm, x.heightCm)
    }

    companion object {
        const val LAYOUTS = "layouts"
        const val MODELS = "models"
        private const val LAYOUT_ENTRY = "layout.json"
        private const val PHOTO_ENTRY = "photo.jpg"

        internal fun uniqueName(name: String, taken: List<String>): String {
            val base = name.trim().ifEmpty { "Untitled" }
            if (taken.none { it.equals(base, true) }) return base
            var n = 2
            while (taken.any { it.equals("$base ($n)", true) }) n++
            return "$base ($n)"
        }
    }
}
