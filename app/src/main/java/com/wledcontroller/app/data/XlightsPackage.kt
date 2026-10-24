package com.wledcontroller.app.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.wledcontroller.app.house.HouseEngine
import com.wledcontroller.app.house.LightRun
import com.wledcontroller.app.house.PropGroup
import com.wledcontroller.app.house.XlLayout
import com.wledcontroller.app.house.XlModel
import com.wledcontroller.app.house.XlightsModels
import com.wledcontroller.app.house.XlightsPlacement
import com.wledcontroller.app.house.HouseMap
import com.wledcontroller.app.house.allSubmodels
import com.wledcontroller.app.show.SequenceRepository
import com.wledcontroller.app.show.TrackTarget
import com.wledcontroller.app.show.XlightsController
import com.wledcontroller.app.show.XlightsNetworks
import com.wledcontroller.app.show.XsqElement
import com.wledcontroller.app.show.XsqImport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

data class PackageResult(val props: Int, val groups: Int, val shows: Int, val skippedEffects: Int, val unassigned: Int, val missing: List<String>)

/**
 * Opens a zipped xLights show folder (layout, controllers, house picture, sequences, songs,
 * pictures and videos) as a project of its own: the props placed on the picture where they are
 * in xLights, and each sequence as a show with its song.
 */
class XlightsPackage(
    private val context: Context,
    private val store: AppStore,
    private val house: HouseEngine,
    private val sequences: SequenceRepository,
    private val projects: Projects,
) {
    /** What's in a package, for asking before opening it. */
    data class Peek(val name: String, val props: Int, val shows: List<String>)

    private val work get() = File(context.cacheDir, "xlights_package")

    /** Unpacks [uri] and says what's in it. */
    suspend fun peek(uri: Uri): Peek = withContext(Dispatchers.IO) {
        val dir = unpack(uri)
        val rgb = find(dir, "xlights_rgbeffects.xml") ?: throw IllegalArgumentException("That zip has no xLights layout (xlights_rgbeffects.xml) in it")
        val layout = rgb.inputStream().use { XlightsModels.parseLayout(it) }
        val shows = dir.walkTopDown().filter { it.isFile && it.extension.equals("xsq", true) }.map { it.nameWithoutExtension }.toList()
        val name = shows.firstOrNull() ?: rgb.parentFile?.name ?: "xLights show"
        Peek(name, layout.models.size, shows)
    }

    /**
     * Opens the package [peek] unpacked as a new project called [name] (the open one kept as it
     * is). [progress] gets a short line on what it's doing.
     */
    suspend fun open(name: String, progress: (String) -> Unit = {}): PackageResult {
        val dir = work
        val rgb = find(dir, "xlights_rgbeffects.xml") ?: throw IllegalArgumentException("Open the package again")
        var result: PackageResult? = null
        projects.create(name, note = "From xLights") {
            result = fill(dir, rgb, progress)
        }
        withContext(Dispatchers.IO) { dir.deleteRecursively() }
        return result!!
    }

    private suspend fun fill(dir: File, rgb: File, progress: (String) -> Unit): PackageResult = withContext(Dispatchers.IO) {
        progress("Reading the layout")
        val layout = rgb.inputStream().use { XlightsModels.parseLayout(it) }
        val networks = find(dir, "xlights_networks.xml")?.let { f -> runCatching { f.inputStream().use { XlightsNetworks.parse(it) } }.getOrNull() } ?: emptyList()
        val missing = ArrayList<String>()

        progress("Adding the house picture")
        val photo = layout.preview?.image?.let { find(dir, it) ?: run { missing += it; null } }
        var imgW = 0; var imgH = 0
        val saved = photo?.let { f ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, bounds)
            imgW = bounds.outWidth; imgH = bounds.outHeight
            var sample = 1
            while (maxOf(imgW, imgH) / sample > 4200) sample *= 2
            BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })?.let { house.importBitmap(it) }
        }
        val area = layout.preview?.let { XlightsPlacement.photoArea(it, imgW, imgH) } ?: boundsOf(layout)

        progress("Placing ${layout.models.size} props")
        val devices = store.devices.first()
        fun deviceFor(ctrl: String?): String? {
            if (ctrl == null) return null
            val ip = networks.firstOrNull { it.name.equals(ctrl, true) }?.ip
            return devices.firstOrNull { it.name.trim().equals(ctrl.trim(), true) || (ip != null && ip.isNotBlank() && it.host == ip) }?.id
        }
        val models = onControllers(layout.models, networks)
        val starts = XlightsModels.resolveStarts(models, null)
        var unassigned = 0
        val runs = models.map { m ->
            val (ctrl, first) = starts[m.name] ?: (null to 0)
            val device = deviceFor(ctrl) ?: run { unassigned++; UNASSIGNED + (ctrl ?: "") }
            LightRun(
                id = UUID.randomUUID().toString(),
                name = m.name, deviceId = device, startLed = first, count = m.ledCount,
                points = XlightsPlacement.points(m, area) ?: emptyList(),
                prop = m.prop, model = m.model, submodels = m.submodels,
                realWidthCm = m.widthCm, realHeightCm = m.heightCm,
            )
        }
        val groups = layout.groups.mapNotNull { (gName, members) ->
            val ids = members.mapNotNull { n -> runs.firstOrNull { it.name.equals(n, true) }?.id }
            if (ids.isEmpty()) null else PropGroup(UUID.randomUUID().toString(), gName, ids)
        }
        val map = HouseMap(
            photoPath = saved?.first,
            aspect = saved?.second ?: (area.first / area.second),
            runs = runs, groups = groups,
        )
        store.updateHouseMap { map }

        // Pictures and videos are brought in once each, however many effects use them.
        val xsqs = dir.walkTopDown().filter { it.isFile && it.extension.equals("xsq", true) }.toList()
            .mapNotNull { f -> runCatching { f to f.inputStream().use { XsqImport.parse(it) } }.getOrNull() }
        fun baseOf(path: String) = path.substringAfterLast('\\').substringAfterLast('/').lowercase()
        val wanted = xsqs.flatMap { (_, x) -> x.elements.flatMap { el -> el.layers.flatten() } }
            .flatMap { e -> MEDIA_KEYS.mapNotNull { e.settings[it] } }
            .map(::baseOf).filter { it.isNotBlank() }.distinct()
        val brought = HashMap<String, String>()
        for (base in wanted) {
            val f = find(dir, base) ?: run { missing += base; null } ?: continue
            val copy = runCatching {
                if (f.extension.lowercase() in VIDEO) {
                    progress("Bringing in the video ${f.nameWithoutExtension}")
                    sequences.importVideo(context, Uri.fromFile(f))
                } else sequences.importPicture(context, Uri.fromFile(f))
            }.getOrNull()
            if (copy != null) brought[base] = copy
        }
        fun media(path: String): String? = brought[baseOf(path)]

        var shows = 0; var skipped = 0
        for ((xsqFile, xsq) in xsqs) {
            progress("Bringing in ${xsqFile.nameWithoutExtension}")
            val targets = xsq.elements.associate { it.label to targetFor(it, map) }
            val (seq, s) = XsqImport.toSequence(xsq, xsqFile.nameWithoutExtension, targets, ::media)
            skipped += s
            val song = xsq.mediaFile?.let { m ->
                val base = m.substringAfterLast('\\').substringAfterLast('/')
                find(dir, base)?.let { copySong(it) } ?: run { missing += base; null }
            }
            sequences.save(if (song != null) seq.copy(audioFile = song.first, audioTitle = song.second) else seq)
            shows++
        }
        PackageResult(runs.size, groups.size, shows, skipped, unassigned, missing.distinct())
    }

    /** Models on an absolute channel go on the controller whose channels hold it. */
    private fun onControllers(models: List<XlModel>, networks: List<XlightsController>): List<XlModel> = models.map { m ->
        val abs = m.absoluteChannel
        if (m.controller != null || m.chainedAfter != null || abs == null) return@map m
        val c = networks.firstOrNull { abs >= it.startChannel && abs < it.startChannel + it.channels } ?: return@map m
        m.copy(controller = c.name, startChannel = abs - c.startChannel + 1, absoluteChannel = null)
    }

    private fun targetFor(el: XsqElement, map: HouseMap): TrackTarget? {
        fun same(a: String, b: String) = a.trim().equals(b.trim(), ignoreCase = true)
        val run = map.runs.firstOrNull { same(it.name, el.name) }
        if (el.submodel != null) {
            val sub = run?.allSubmodels()?.firstOrNull { same(it.name, el.submodel) } ?: return null
            return TrackTarget.submodel(run.id, sub.name)
        }
        if (run != null) return TrackTarget(TrackTarget.Kind.PROP, run.id)
        return map.groups.firstOrNull { same(it.name, el.name) }?.let { TrackTarget(TrackTarget.Kind.GROUP, it.id) }
    }

    /** Copies a song into app storage. Returns (path, title). */
    private fun copySong(f: File): Pair<String, String> {
        val audioDir = File(context.filesDir, "audio").apply { mkdirs() }
        val out = File(audioDir, "${UUID.randomUUID()}.${f.extension.ifBlank { "audio" }}")
        f.copyTo(out, overwrite = true)
        return out.absolutePath to f.nameWithoutExtension
    }

    /** With no layout view in the file, the props' own extent. */
    private fun boundsOf(layout: XlLayout): Pair<Float, Float> {
        val maxX = layout.models.mapNotNull { it.place?.x }.maxOrNull() ?: 1000f
        val maxY = layout.models.mapNotNull { it.place?.y }.maxOrNull() ?: 1000f
        return maxOf(maxX * 1.1f, 1f) to maxOf(maxY * 1.1f, 1f)
    }

    private suspend fun unpack(uri: Uri): File = withContext(Dispatchers.IO) {
        val dir = work.apply { deleteRecursively(); mkdirs() }
        val input = context.contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("Couldn't open that file")
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (!e.isDirectory) {
                    val target = File(dir, e.name)
                    // Refuse entries that would escape the folder.
                    if (!target.canonicalPath.startsWith(dir.canonicalPath + File.separator)) throw SecurityException("Bad entry in that zip")
                    target.parentFile?.mkdirs()
                    target.outputStream().use { zip.copyTo(it) }
                }
                zip.closeEntry()
            }
        }
        dir
    }

    /** The file called [name] anywhere in the package (ignoring case). */
    private fun find(dir: File, name: String): File? =
        dir.walkTopDown().firstOrNull { it.isFile && it.name.equals(name, ignoreCase = true) }

    companion object {
        /** The start of the controller id on props whose controller isn't one of yours yet. */
        const val UNASSIGNED = "xlights:"
        private val MEDIA_KEYS = listOf("E_FILEPICKER_Pictures_Filename", "E_FILEPICKERCTRL_Video_Filename")
        private val VIDEO = setOf("mp4", "mov", "avi", "mkv", "webm", "m4v", "3gp")
    }
}
