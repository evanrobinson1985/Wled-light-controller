package com.wledcontroller.app.house.model3d

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Saves the house model for other 3D software: binary glTF (.glb, one file with its pictures
 * inside) or Wavefront OBJ (with its .mtl and pictures, in a .zip). Metres, y up — the
 * standard for both, so it opens at its true size the right way up.
 */
object ModelExport {
    /** Where this app keeps its own data (where the lights go) in a .glb's extras. */
    const val KEY = "wledController"
    /** The light markers' object. */
    const val LIGHTS = "Lights"
    /** The same data in an OBJ zip. */
    const val OBJ_DATA = "wled-controller.json"

    /** Small markers where the lights are, in their colours, to go with the model. */
    fun lightMarkers(positions: List<P3?>, colors: IntArray, size: Float = 0.03f): MeshPart? {
        val pts = positions.withIndex().filter { it.value != null }
        if (pts.isEmpty()) return null
        val pos = FloatArray(pts.size * 6 * 3); val col = IntArray(pts.size * 6); val idx = IntArray(pts.size * 24)
        val dirs = listOf(P3(size, 0f, 0f), P3(-size, 0f, 0f), P3(0f, size, 0f), P3(0f, -size, 0f), P3(0f, 0f, size), P3(0f, 0f, -size))
        // An octahedron each: +x, -x, +y, -y, +z, -z corners.
        val faces = intArrayOf(0, 2, 4, 2, 1, 4, 1, 3, 4, 3, 0, 4, 2, 0, 5, 1, 2, 5, 3, 1, 5, 0, 3, 5)
        pts.forEachIndexed { k, (i, p) ->
            dirs.forEachIndexed { d, off -> val q = p!! + off; pos[(k * 6 + d) * 3] = q.x; pos[(k * 6 + d) * 3 + 1] = q.y; pos[(k * 6 + d) * 3 + 2] = q.z; col[k * 6 + d] = colors.getOrElse(i) { -1 } or (0xFF shl 24) }
            for (f in faces.indices) idx[k * 24 + f] = k * 6 + faces[f]
        }
        return MeshPart(pos, null, idx, -1, -1, col)
    }

    /** Writes [mesh] (its pictures as JPEGs, by texture number) as .glb, with [lights] as an object of its own. */
    fun writeGlb(mesh: Mesh, images: List<ByteArray>, out: OutputStream, lights: MeshPart? = null, appData: JsonObject? = null) {
        val bin = ByteArrayOutputStream()
        val views = ArrayList<JsonObject>(); val accessors = ArrayList<JsonObject>()
        fun view(bytes: ByteArray, target: Int?): Int {
            while (bin.size() % 4 != 0) bin.write(0)
            val off = bin.size(); bin.write(bytes)
            views += buildJsonObject { put("buffer", 0); put("byteOffset", off); put("byteLength", bytes.size); target?.let { put("target", it) } }
            return views.size - 1
        }
        fun floats(a: FloatArray): ByteArray = ByteBuffer.allocate(a.size * 4).order(ByteOrder.LITTLE_ENDIAN).also { b -> a.forEach { b.putFloat(it) } }.array()
        fun accessor(a: FloatArray, type: String, comps: Int, bounds: Boolean = false): Int {
            val v = view(floats(a), 34962)
            accessors += buildJsonObject {
                put("bufferView", v); put("componentType", 5126); put("count", a.size / comps); put("type", type)
                if (bounds) {
                    putJsonArray("min") { for (c in 0 until comps) add(JsonPrimitive((c until a.size step comps).minOf { a[it] })) }
                    putJsonArray("max") { for (c in 0 until comps) add(JsonPrimitive((c until a.size step comps).maxOf { a[it] })) }
                }
            }
            return accessors.size - 1
        }
        fun indices(a: IntArray): Int {
            val v = view(ByteBuffer.allocate(a.size * 4).order(ByteOrder.LITTLE_ENDIAN).also { b -> a.forEach { b.putInt(it) } }.array(), 34963)
            accessors += buildJsonObject { put("bufferView", v); put("componentType", 5125); put("count", a.size); put("type", "SCALAR") }
            return accessors.size - 1
        }
        val imageViews = images.map { view(it, null) }
        val materials = ArrayList<JsonObject>()
        fun primitive(p: MeshPart): JsonObject {
            val textured = p.uvs != null && p.texture in images.indices
            val vc = p.vertexColors?.takeIf { it.size == p.vertexCount }
            val material = buildJsonObject {
                putJsonObject("pbrMetallicRoughness") {
                    if (textured) putJsonObject("baseColorTexture") { put("index", p.texture) }
                    else if (vc == null) putJsonArray("baseColorFactor") {
                        add(JsonPrimitive(((p.color shr 16) and 0xFF) / 255f)); add(JsonPrimitive(((p.color shr 8) and 0xFF) / 255f)); add(JsonPrimitive((p.color and 0xFF) / 255f)); add(JsonPrimitive(1f))
                    }
                    put("metallicFactor", 0f); put("roughnessFactor", 1f)
                }
                put("doubleSided", true)
            }
            materials += material
            return buildJsonObject {
                putJsonObject("attributes") {
                    put("POSITION", accessor(p.positions, "VEC3", 3, bounds = true))
                    if (textured) put("TEXCOORD_0", accessor(p.uvs!!, "VEC2", 2))
                    if (vc != null) put("COLOR_0", accessor(FloatArray(vc.size * 3) { k -> ((vc[k / 3] shr (16 - 8 * (k % 3))) and 0xFF) / 255f }, "VEC3", 3))
                }
                put("indices", indices(p.indices)); put("material", materials.size - 1); put("mode", 4)
            }
        }
        val house = mesh.parts.filter { it.triangleCount > 0 }.map(::primitive)
        val lightPrim = lights?.takeIf { it.triangleCount > 0 }?.let(::primitive)
        val json = buildJsonObject {
            putJsonObject("asset") {
                put("version", "2.0"); put("generator", "WLED Controller")
                appData?.let { putJsonObject("extras") { put(KEY, it) } }
            }
            put("scene", 0)
            putJsonArray("scenes") {
                addJsonObject {
                    putJsonArray("nodes") { add(JsonPrimitive(0)); if (lightPrim != null) add(JsonPrimitive(1)) }
                    // Also here: 3D software that keeps custom properties keeps the scene's.
                    appData?.let { putJsonObject("extras") { put(KEY, it) } }
                }
            }
            putJsonArray("nodes") {
                addJsonObject { put("name", "House"); put("mesh", 0) }
                if (lightPrim != null) addJsonObject { put("name", LIGHTS); put("mesh", 1) }
            }
            putJsonArray("meshes") {
                addJsonObject { put("name", "House"); put("primitives", JsonArray(house)) }
                if (lightPrim != null) addJsonObject { put("name", "Lights"); put("primitives", JsonArray(listOf(lightPrim))) }
            }
            put("materials", JsonArray(materials))
            if (images.isNotEmpty()) {
                putJsonArray("samplers") { addJsonObject { put("magFilter", 9729); put("minFilter", 9987); put("wrapS", 33071); put("wrapT", 33071) } }
                putJsonArray("images") { imageViews.forEach { v -> addJsonObject { put("bufferView", v); put("mimeType", "image/jpeg") } } }
                putJsonArray("textures") { images.indices.forEach { i -> addJsonObject { put("sampler", 0); put("source", i) } } }
            }
            put("accessors", JsonArray(accessors))
            put("bufferViews", JsonArray(views))
            while (bin.size() % 4 != 0) bin.write(0)
            putJsonArray("buffers") { addJsonObject { put("byteLength", bin.size()) } }
        }
        var jb = json.toString().toByteArray(Charsets.UTF_8)
        if (jb.size % 4 != 0) jb += ByteArray(4 - jb.size % 4) { ' '.code.toByte() }
        val binBytes = bin.toByteArray()
        val total = 12 + 8 + jb.size + 8 + binBytes.size
        val head = ByteBuffer.allocate(12 + 8).order(ByteOrder.LITTLE_ENDIAN)
        head.putInt(0x46546C67); head.putInt(2); head.putInt(total); head.putInt(jb.size); head.putInt(0x4E4F534A)
        out.write(head.array()); out.write(jb)
        out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(binBytes.size).putInt(0x004E4942).array())
        out.write(binBytes)
        out.flush()
    }

    /** Writes [mesh] as house.obj + house.mtl + its pictures, zipped; colour at each corner as "v x y z r g b". */
    fun writeObjZip(mesh: Mesh, images: List<ByteArray>, out: OutputStream, lights: MeshPart? = null, appData: JsonObject? = null) {
        val obj = StringBuilder("# WLED Controller house model — metres, y up\nmtllib house.mtl\n")
        val mtl = StringBuilder()
        var base = 1; var uvBase = 1
        val parts = mesh.parts.map { "house" to it } + listOfNotNull(lights?.let { "lights" to it })
        parts.forEachIndexed { k, (name, p) ->
            val textured = p.uvs != null && p.texture in images.indices
            val vc = p.vertexColors?.takeIf { it.size == p.vertexCount }
            obj.append("o ").append(name).append('_').append(k).append('\n')
            for (i in 0 until p.vertexCount) {
                obj.append("v ").append(f(p.positions[i * 3])).append(' ').append(f(p.positions[i * 3 + 1])).append(' ').append(f(p.positions[i * 3 + 2]))
                if (vc != null) obj.append(' ').append(f(((vc[i] shr 16) and 0xFF) / 255f)).append(' ').append(f(((vc[i] shr 8) and 0xFF) / 255f)).append(' ').append(f((vc[i] and 0xFF) / 255f))
                obj.append('\n')
            }
            if (textured) for (i in 0 until p.vertexCount) obj.append("vt ").append(f(p.uvs!![i * 2])).append(' ').append(f(1f - p.uvs[i * 2 + 1])).append('\n')
            val m = "m$k"
            mtl.append("newmtl ").append(m).append('\n')
            val c = if (vc != null || textured) -1 else p.color
            mtl.append("Kd ").append(f(((c shr 16) and 0xFF) / 255f)).append(' ').append(f(((c shr 8) and 0xFF) / 255f)).append(' ').append(f((c and 0xFF) / 255f)).append('\n')
            if (textured) mtl.append("map_Kd tex").append(p.texture).append(".jpg\n")
            mtl.append('\n')
            obj.append("usemtl ").append(m).append('\n')
            for (t in 0 until p.triangleCount) {
                obj.append('f')
                for (j in 0 until 3) {
                    val v = p.indices[t * 3 + j] + base
                    obj.append(' ').append(v); if (textured) obj.append('/').append(p.indices[t * 3 + j] + uvBase)
                }
                obj.append('\n')
            }
            base += p.vertexCount
            if (textured) uvBase += p.vertexCount
        }
        val z = ZipOutputStream(out)
        z.putNextEntry(ZipEntry("house.obj")); z.write(obj.toString().toByteArray()); z.closeEntry()
        z.putNextEntry(ZipEntry("house.mtl")); z.write(mtl.toString().toByteArray()); z.closeEntry()
        images.forEachIndexed { i, b -> z.putNextEntry(ZipEntry("tex$i.jpg")); z.write(b); z.closeEntry() }
        appData?.let { z.putNextEntry(ZipEntry(OBJ_DATA)); z.write(it.toString().toByteArray()); z.closeEntry() }
        z.finish(); z.flush()
    }

    private fun f(v: Float): String {
        val s = "%.5f".format(java.util.Locale.ROOT, v).trimEnd('0')
        return if (s.endsWith('.')) s.dropLast(1) else s
    }
}
