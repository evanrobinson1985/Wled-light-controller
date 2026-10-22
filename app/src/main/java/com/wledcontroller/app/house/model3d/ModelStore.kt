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

    /** Imports a 3D scan: .glb, .obj, or a .zip with an .obj, its .mtl and pictures. */
    suspend fun importScan(uri: Uri): House3D = withContext(Dispatchers.IO) {
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
                    ?: files.entries.firstOrNull { it.key.endsWith(".obj") }?.let { obj -> ObjReader.read(obj.value.decodeToString()) { files[it.substringAfterLast('/').substringAfterLast('\\').lowercase()] } }
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
        // Stood on the ground, centred.
        val (lo, hi) = mesh.bounds
        val shift = P3(-(lo.x + hi.x) / 2, -lo.y, -(lo.z + hi.z) / 2)
        mesh = mesh.transformed { it + shift }
        File(dir, MESH).outputStream().use { MeshFile.write(mesh, it) }
        cached = m.dir to mesh
        m.copy(triangles = mesh.triangleCount)
    }

    /** Keeps a scan made with the phone. */
    suspend fun saveScanned(scanned: Mesh, name: String = "Phone scan"): House3D = withContext(Dispatchers.IO) {
        require(scanned.triangleCount > 0) { "Nothing was scanned — scan more of the house before finishing." }
        val m = House3D(UUID.randomUUID().toString(), House3D.Kind.SCAN, name)
        val dir = dir(m).apply { mkdirs() }
        val (lo, hi) = scanned.bounds
        val shift = P3(-(lo.x + hi.x) / 2, -lo.y, -(lo.z + hi.z) / 2)
        val mesh = scanned.transformed { it + shift }
        File(dir, MESH).outputStream().use { MeshFile.write(mesh, it) }
        cached = m.dir to mesh
        m.copy(triangles = mesh.triangleCount)
    }

    /** Turns the model a quarter turn about x (on its back ↔ upright) or y; any line-up is lost. */
    suspend fun turn(m: House3D, aboutX: Boolean): House3D = withContext(Dispatchers.IO) {
        var mesh = mesh(m).transformed { p -> if (aboutX) P3(p.x, -p.z, p.y) else P3(p.z, p.y, -p.x) }
        val (lo, hi) = mesh.bounds
        val shift = P3(-(lo.x + hi.x) / 2, -lo.y, -(lo.z + hi.z) / 2)
        mesh = mesh.transformed { it + shift }
        File(dir(m), MESH).outputStream().use { MeshFile.write(mesh, it) }
        cached = m.dir to mesh
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
        private const val MESH = "mesh.bin"
        private const val MAX_TEX = 2048f
        const val MAX_TRIANGLES = 3_000_000
    }
}
