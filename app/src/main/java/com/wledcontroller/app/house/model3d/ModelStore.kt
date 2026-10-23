package com.wledcontroller.app.house.model3d

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.provider.OpenableColumns
import com.wledcontroller.app.house.MapPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

/** The 3D house's files: the mesh and its pictures, under the app's storage. */
class ModelStore(private val context: Context) {
    private val root get() = File(context.filesDir, "house3d").apply { mkdirs() }
    fun dir(m: House3D) = File(root, m.dir)

    @Volatile private var cached: Pair<String, Mesh>? = null

    suspend fun mesh(m: House3D): Mesh = withContext(Dispatchers.IO) {
        cached?.takeIf { it.first == m.dir }?.second
            ?: File(dir(m), MESH).inputStream().use { MeshFile.read(it) }.also { cached = m.dir to it }
    }

    /** The model's pictures, in texture order. */
    fun textures(m: House3D): List<File> =
        dir(m).listFiles { f -> f.name.startsWith("tex") }.orEmpty().sortedBy { it.name.removePrefix("tex").substringBefore('.').toIntOrNull() ?: 0 }

    fun delete(m: House3D) { dir(m).deleteRecursively(); if (cached?.first == m.dir) cached = null }

    /** A photo of one side of the house, kept for building (upright, not too big). Returns (path, aspect). */
    suspend fun keepPhoto(uri: Uri): Pair<String, Float> = withContext(Dispatchers.IO) {
        val bmp = decode(uri, 2400) ?: throw IllegalArgumentException("Couldn't read that photo.")
        val dir = File(root, "photos").apply { mkdirs() }
        val f = File(dir, "side_${System.currentTimeMillis()}.jpg")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        val aspect = bmp.width.toFloat() / bmp.height
        bmp.recycle()
        f.absolutePath to aspect
    }

    /**
     * Builds the house from [spec] and the photos of its sides: each photo is straightened out
     * by its marked corners and wrapped onto its wall.
     */
    suspend fun buildFromPhotos(spec: ShellSpec, faces: List<FacePhoto>, name: String = "My house"): House3D = withContext(Dispatchers.IO) {
        val m = House3D(UUID.randomUUID().toString(), House3D.Kind.PHOTOS, name, shell = spec, faces = faces)
        val dir = dir(m).apply { mkdirs() }
        val textures = HashMap<Face, Int>()
        for (fp in faces) {
            val photo = BitmapFactory.decodeFile(fp.path) ?: continue
            val tex = straighten(photo, fp.corners, spec, fp.face)
            photo.recycle()
            val i = textures.size
            File(dir, "tex$i.jpg").outputStream().use { tex.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            tex.recycle()
            textures[fp.face] = i
        }
        val mesh = Shell.build(spec, textures)
        File(dir, MESH).outputStream().use { MeshFile.write(mesh, it) }
        cached = m.dir to mesh
        m.copy(triangles = mesh.triangleCount)
    }

    /** The wall in [photo] between its [corners], squared up into a picture of the whole side (gable included). */
    private fun straighten(photo: Bitmap, corners: List<MapPoint>, spec: ShellSpec, face: Face): Bitmap {
        val fw = Shell.faceWidth(spec, face); val fh = Shell.faceHeight(spec, face)
        val pxPerM = minOf(MAX_TEX / fw, MAX_TEX / fh)
        val tw = (fw * pxPerM).toInt().coerceAtLeast(16); val th = (fh * pxPerM).toInt().coerceAtLeast(16)
        val wallTop = th * (1f - Shell.wallShare(spec, face))
        val src = FloatArray(8) { i -> val c = corners[i / 2]; if (i % 2 == 0) c.x * photo.width else c.y * photo.height }
        val dst = floatArrayOf(0f, wallTop, tw.toFloat(), wallTop, tw.toFloat(), th.toFloat(), 0f, th.toFloat())
        val matrix = Matrix().apply { setPolyToPoly(src, 0, dst, 0, 4) }
        val out = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(0xFF6B6F78.toInt())
            drawBitmap(photo, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        }
        return out
    }

    /**
     * Loads a 3D model: .glb, .obj, or a .zip with an .obj, its .mtl and pictures. Also gives
     * where the lights go on it, when this app saved it.
     */
    suspend fun importScan(uri: Uri): Pair<House3D, ModelLights?> = withContext(Dispatchers.IO) {
        val name = displayName(uri)
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalArgumentException("Couldn't open that file.")
        val data = when {
            GlbReader.isGlb(bytes) -> GlbReader.read(bytes)
            bytes.size > 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() -> {
                val files = HashMap<String, ByteArray>()
                ZipInputStream(bytes.inputStream()).use { z ->
                    while (true) {
                        val e = z.nextEntry ?: break
                        if (!e.isDirectory) files[e.name.substringAfterLast('/').lowercase()] = z.readBytes()
                    }
                }
                files.entries.firstOrNull { it.key.endsWith(".glb") }?.let { GlbReader.read(it.value) }
                    ?: files.entries.firstOrNull { it.key.endsWith(".obj") }?.let { obj ->
                        val d = ObjReader.read(obj.value.decodeToString()) { files[it.substringAfterLast('/').substringAfterLast('\\').lowercase()] }
                        ModelData(d.mesh, d.images, files[ModelExport.OBJ_DATA]?.decodeToString())
                    }
                    ?: throw IllegalArgumentException("No .obj or .glb in that zip.")
            }
            name.endsWith(".gltf", true) -> throw IllegalArgumentException("Export as .glb (one file) rather than .gltf.")
            else -> ObjReader.read(bytes.decodeToString()) { null }
        }
        if (data.mesh.triangleCount > MAX_TRIANGLES)
            throw IllegalArgumentException("That scan is very detailed (${data.mesh.triangleCount / 1000}k triangles). Export it again at a lower detail (under ${MAX_TRIANGLES / 1_000_000} million).")
        val m = House3D(UUID.randomUUID().toString(), House3D.Kind.SCAN, name.substringBeforeLast('.'))
        val dir = dir(m).apply { mkdirs() }
        // Pictures: decoded, made a sensible size, saved; a part whose picture won't read goes plain.
        val maxSide = if (data.images.size > 2) 2048 else 4096
        val ok = data.images.mapIndexed { i, b ->
            val bmp = decode(b, maxSide) ?: return@mapIndexed false
            File(dir, "tex$i.jpg").outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bmp.recycle(); true
        }
        var mesh = Mesh(data.mesh.parts.map { p -> if (p.texture >= 0 && ok.getOrElse(p.texture) { false }) p else MeshPart(p.positions, null, p.indices, -1, p.color, p.vertexColors) })
        val lights = data.lights?.let { runCatching { lightsJson.decodeFromString<ModelLights>(it) }.getOrNull() }
        // Stood on the ground, centred — unless it's one of this app's, already placed just so.
        if (lights == null) {
            val (lo, hi) = mesh.bounds
            val shift = P3(-(lo.x + hi.x) / 2, -lo.y, -(lo.z + hi.z) / 2)
            mesh = mesh.transformed { it + shift }
        }
        File(dir, MESH).outputStream().use { MeshFile.write(mesh, it) }
        cached = m.dir to mesh
        m.copy(triangles = mesh.triangleCount) to lights
    }

    /** Where the phone's scanner keeps its photos until the scan is saved. */
    fun scanPhotoDir() = File(root, "scan_photos")

    /** Keeps a scan made with the phone, with the photos it took. */
    suspend fun saveScanned(scanned: Mesh, name: String = "Phone scan"): House3D = withContext(Dispatchers.IO) {
        require(scanned.triangleCount > 0) { "Nothing was scanned — scan more of the house before finishing." }
        val m = House3D(UUID.randomUUID().toString(), House3D.Kind.SCAN, name)
        val dir = dir(m).apply { mkdirs() }
        val (lo, hi) = scanned.bounds
        val shift = P3(-(lo.x + hi.x) / 2, -lo.y, -(lo.z + hi.z) / 2)
        val mesh = scanned.transformed { it + shift }
        File(dir, MESH).outputStream().use { MeshFile.write(mesh, it) }
        cached = m.dir to mesh
        // The photos, moved the same way as the model.
        val photos = File(dir, PHOTOS)
        var count = 0
        if (scanPhotoDir().renameTo(photos)) {
            photos.listFiles { f -> f.name.endsWith(".bin") }.orEmpty().forEach { f ->
                val frame = f.inputStream().use { ScanFrame.read(it) }
                val pose = frame.pose.copyOf().also { it[12] += shift.x; it[13] += shift.y; it[14] += shift.z }
                f.outputStream().use { ScanFrame(pose, frame.fx, frame.fy, frame.cx, frame.cy, frame.width, frame.height, frame.depthW, frame.depthH, frame.depth).write(it) }
                count++
            }
        }
        m.copy(triangles = mesh.triangleCount, photos = count)
    }

    /** How much room the scan's photos take, bytes. */
    fun photoBytes(m: House3D): Long = File(dir(m), PHOTOS).listFiles().orEmpty().sumOf { it.length() }

    /** Deletes the scan's photos (the colours stay as they are). */
    fun dropPhotos(m: House3D): House3D { File(dir(m), PHOTOS).deleteRecursively(); return m.copy(photos = 0) }

    /**
     * Paints the scan from its photos: much sharper colours. The scan's own colours are kept,
     * to go back to. [onProgress] gets 0..1.
     */
    suspend fun sharpen(m: House3D, onProgress: (Float) -> Unit): House3D = withContext(Dispatchers.IO) {
        val dir = dir(m)
        val plainFile = File(dir, PLAIN)
        if (!plainFile.exists()) File(dir, MESH).copyTo(plainFile)
        val plain = plainFile.inputStream().use { MeshFile.read(it) }
        val part = plain.parts.firstOrNull() ?: throw IllegalStateException("The model is empty.")
        val photos = File(dir, PHOTOS)
        val ids = photos.listFiles { f -> f.name.endsWith(".bin") }.orEmpty()
            .mapNotNull { it.name.removePrefix("photo").removeSuffix(".bin").toIntOrNull() }.sorted()
            .filter { File(photos, "photo$it.jpg").exists() }
        require(ids.isNotEmpty()) { "The scan's photos have been deleted." }
        val frames = ids.map { i -> File(photos, "photo$i.bin").inputStream().use { ScanFrame.read(it) } }
        textures(m).forEach { it.delete() }
        val page = 2048
        val mesh = PhotoTexturer.texture(
            part, frames,
            image = { k ->
                BitmapFactory.decodeFile(File(photos, "photo${ids[k]}.jpg").absolutePath)?.let { b ->
                    IntArray(b.width * b.height).also { px -> b.getPixels(px, 0, b.width, 0, 0, b.width, b.height); b.recycle() }
                        .takeIf { b.width == frames[k].width && b.height == frames[k].height }
                }
            },
            pageSize = page,
            onPage = { i, px ->
                val bmp = Bitmap.createBitmap(px, page, page, Bitmap.Config.ARGB_8888)
                File(dir, "tex$i.jpg").outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                bmp.recycle()
            },
            onProgress = onProgress,
        )
        File(dir, MESH).outputStream().use { MeshFile.write(mesh, it) }
        cached = m.dir to mesh
        m.copy(sharpened = true)
    }

    /** Back to the scan's own colours. */
    suspend fun plainColours(m: House3D): House3D = withContext(Dispatchers.IO) {
        val dir = dir(m)
        val plainFile = File(dir, PLAIN)
        if (plainFile.exists()) {
            plainFile.copyTo(File(dir, MESH), overwrite = true)
            textures(m).forEach { it.delete() }
            cached = null
        }
        m.copy(sharpened = false)
    }

    /** Saves the model to [uri] for other 3D software: .glb, or .obj in a .zip; [lights] as markers. */
    suspend fun export(m: House3D, uri: Uri, glb: Boolean, lights: MeshPart?, placement: ModelLights?) = withContext(Dispatchers.IO) {
        val appData = placement?.let { lightsJson.encodeToJsonElement(ModelLights.serializer(), it) as kotlinx.serialization.json.JsonObject }
        val mesh = mesh(m)
        val count = mesh.parts.maxOfOrNull { it.texture }?.plus(1) ?: 0
        val images = (0 until count).map { i -> File(dir(m), "tex$i.jpg").takeIf { it.exists() }?.readBytes() ?: ByteArray(0) }
        context.contentResolver.openOutputStream(uri)?.use { out ->
            if (glb) ModelExport.writeGlb(mesh, images, out, lights, appData) else ModelExport.writeObjZip(mesh, images, out, lights, appData)
        } ?: throw IllegalStateException("Couldn't write there.")
    }

    /** Turns the model a quarter turn about x (on its back ↔ upright) or y; any line-up is lost. */
    suspend fun turn(m: House3D, aboutX: Boolean): House3D = withContext(Dispatchers.IO) {
        val rot: (P3) -> P3 = { p -> if (aboutX) P3(p.x, -p.z, p.y) else P3(p.z, p.y, -p.x) }
        var mesh = mesh(m).transformed(rot)
        val (lo, hi) = mesh.bounds
        val shift = P3(-(lo.x + hi.x) / 2, -lo.y, -(lo.z + hi.z) / 2)
        mesh = mesh.transformed { it + shift }
        File(dir(m), MESH).outputStream().use { MeshFile.write(mesh, it) }
        cached = m.dir to mesh
        // The scan's own colours and its photos turn with it.
        File(dir(m), PLAIN).takeIf { it.exists() }?.let { f ->
            val plain = f.inputStream().use { MeshFile.read(it) }.transformed { rot(it) + shift }
            f.outputStream().use { MeshFile.write(plain, it) }
        }
        File(dir(m), PHOTOS).listFiles { f -> f.name.endsWith(".bin") }.orEmpty().forEach { f ->
            val fr = f.inputStream().use { ScanFrame.read(it) }
            val p = fr.pose.copyOf()
            for (c in 0 until 3) { val v = rot(P3(p[c * 4], p[c * 4 + 1], p[c * 4 + 2])); p[c * 4] = v.x; p[c * 4 + 1] = v.y; p[c * 4 + 2] = v.z }
            val e = rot(P3(p[12], p[13], p[14])) + shift; p[12] = e.x; p[13] = e.y; p[14] = e.z
            f.outputStream().use { ScanFrame(p, fr.fx, fr.fy, fr.cx, fr.cy, fr.width, fr.height, fr.depthW, fr.depthH, fr.depth).write(it) }
        }
        m.copy(lineup = null, lineupModel = null, lineupPhoto = null)
    }

    private fun displayName(uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull() ?: uri.lastPathSegment ?: "Scan"

    private fun decode(uri: Uri, maxSide: Int): Bitmap? {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val bmp = decode(bytes, maxSide) ?: return null
        // Photos straight from the camera may be stored sideways.
        val turn = runCatching {
            when (android.media.ExifInterface(bytes.inputStream()).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        if (turn == 0f) return bmp
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(turn) }, true).also { if (it != bmp) bmp.recycle() }
    }

    private fun decode(bytes: ByteArray, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    companion object {
        private val lightsJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = false }
        private const val MESH = "mesh.bin"
        private const val PLAIN = "mesh_plain.bin"
        private const val PHOTOS = "photos"
        private const val MAX_TEX = 2048f
        const val MAX_TRIANGLES = 3_000_000
    }
}
