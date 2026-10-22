package com.wledcontroller.app.house.model3d

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

/** A model read from a file: the mesh and its pictures (still encoded, as in the file). */
class ModelData(val mesh: Mesh, val images: List<ByteArray>)

/** Reads binary glTF (.glb), what Scaniverse, Polycam and KIRI Engine export. */
object GlbReader {
    fun isGlb(bytes: ByteArray) = bytes.size >= 12 && bytes[0] == 'g'.code.toByte() && bytes[1] == 'l'.code.toByte() &&
        bytes[2] == 'T'.code.toByte() && bytes[3] == 'F'.code.toByte()

    fun read(bytes: ByteArray): ModelData {
        require(isGlb(bytes)) { "That isn't a .glb file." }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var at = 12
        var json: JsonObject? = null
        var bin: ByteBuffer? = null
        while (at + 8 <= bytes.size) {
            val len = buf.getInt(at); val type = buf.getInt(at + 4)
            if (type == 0x4E4F534A) json = Json.parseToJsonElement(String(bytes, at + 8, len, Charsets.UTF_8)).jsonObject
            else if (type == 0x004E4942) bin = ByteBuffer.wrap(bytes, at + 8, len).slice().order(ByteOrder.LITTLE_ENDIAN)
            at += 8 + len
        }
        val doc = json ?: throw IllegalArgumentException("That .glb file has no description.")
        val used = doc["extensionsRequired"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
        if ("KHR_draco_mesh_compression" in used || "EXT_meshopt_compression" in used)
            throw IllegalArgumentException("That model is compressed. Export it again without compression (Draco / meshopt off).")
        return Gltf(doc, bin).read()
    }

    private class Gltf(val doc: JsonObject, val bin: ByteBuffer?) {
        fun arr(k: String) = doc[k]?.jsonArray ?: JsonArray(emptyList())
        val accessors = arr("accessors"); val views = arr("bufferViews"); val meshes = arr("meshes"); val nodes = arr("nodes")
        val materials = arr("materials"); val textures = arr("textures"); val images = arr("images")
        val parts = ArrayList<MeshPart>()
        val imageBytes = ArrayList<ByteArray>()
        val imageIndex = HashMap<Int, Int>()

        fun read(): ModelData {
            val scene = doc["scene"]?.jsonPrimitive?.intOrNull ?: 0
            val roots = arr("scenes").getOrNull(scene)?.jsonObject?.get("nodes")?.jsonArray?.map { it.jsonPrimitive.intOrNull ?: 0 }
                ?: nodes.indices.toList()
            roots.forEach { visit(it, IDENTITY, 0) }
            if (parts.isEmpty()) throw IllegalArgumentException("No shapes in that model.")
            return ModelData(Mesh(parts), imageBytes)
        }

        fun visit(n: Int, parent: FloatArray, depth: Int) {
            if (depth > 64) return
            val node = nodes.getOrNull(n)?.jsonObject ?: return
            val m = Camera.multiply(parent, local(node))
            node["mesh"]?.jsonPrimitive?.intOrNull?.let { addMesh(it, m) }
            node["children"]?.jsonArray?.forEach { c -> c.jsonPrimitive.intOrNull?.let { visit(it, m, depth + 1) } }
        }

        fun floats(o: JsonObject, k: String): FloatArray? = o[k]?.jsonArray?.map { it.jsonPrimitive.floatOrNull ?: 0f }?.toFloatArray()

        fun local(node: JsonObject): FloatArray {
            floats(node, "matrix")?.takeIf { it.size == 16 }?.let { return it }
            val t = floats(node, "translation") ?: floatArrayOf(0f, 0f, 0f)
            val r = floats(node, "rotation") ?: floatArrayOf(0f, 0f, 0f, 1f)
            val s = floats(node, "scale") ?: floatArrayOf(1f, 1f, 1f)
            val (x, y, z, w) = r.toList()
            val rot = floatArrayOf(
                1 - 2 * (y * y + z * z), 2 * (x * y + z * w), 2 * (x * z - y * w), 0f,
                2 * (x * y - z * w), 1 - 2 * (x * x + z * z), 2 * (y * z + x * w), 0f,
                2 * (x * z + y * w), 2 * (y * z - x * w), 1 - 2 * (x * x + y * y), 0f,
                0f, 0f, 0f, 1f,
            )
            for (c in 0 until 3) for (k in 0 until 3) rot[c * 4 + k] *= s[c]
            rot[12] = t[0]; rot[13] = t[1]; rot[14] = t[2]
            return rot
        }

        fun addMesh(i: Int, m: FloatArray) {
            val prims = meshes.getOrNull(i)?.jsonObject?.get("primitives")?.jsonArray ?: return
            for (pe in prims) {
                val p = pe.jsonObject
                if ((p["mode"]?.jsonPrimitive?.intOrNull ?: 4) != 4) continue
                val attrs = p["attributes"]?.jsonObject ?: continue
                val pos = attrs["POSITION"]?.jsonPrimitive?.intOrNull?.let { accessor(it) } ?: continue
                val vcount = pos.size / 3
                val out = FloatArray(pos.size)
                for (v in 0 until vcount) {
                    val x = pos[v * 3]; val y = pos[v * 3 + 1]; val z = pos[v * 3 + 2]
                    out[v * 3] = m[0] * x + m[4] * y + m[8] * z + m[12]
                    out[v * 3 + 1] = m[1] * x + m[5] * y + m[9] * z + m[13]
                    out[v * 3 + 2] = m[2] * x + m[6] * y + m[10] * z + m[14]
                }
                val uv = attrs["TEXCOORD_0"]?.jsonPrimitive?.intOrNull?.let { accessor(it) }
                val idx = p["indices"]?.jsonPrimitive?.intOrNull?.let { a -> accessor(a).let { f -> IntArray(f.size) { f[it].toInt() } } }
                    ?: IntArray(vcount) { it }
                val mat = p["material"]?.jsonPrimitive?.intOrNull?.let { materials.getOrNull(it)?.jsonObject }
                val pbr = mat?.get("pbrMetallicRoughness")?.jsonObject
                val tex = pbr?.get("baseColorTexture")?.jsonObject?.get("index")?.jsonPrimitive?.intOrNull?.let(::texture) ?: -1
                val color = floats(pbr ?: JsonObject(emptyMap()), "baseColorFactor")?.let { c ->
                    (0xFF shl 24) or ((c[0] * 255).toInt().coerceIn(0, 255) shl 16) or ((c[1] * 255).toInt().coerceIn(0, 255) shl 8) or (c[2] * 255).toInt().coerceIn(0, 255)
                } ?: 0xFFB0B4BC.toInt()
                val uvOk = uv?.takeIf { tex >= 0 && it.size == vcount * 2 }
                parts += MeshPart(out, uvOk, idx, if (uvOk != null) tex else -1, color)
            }
        }

        fun texture(t: Int): Int {
            val src = textures.getOrNull(t)?.jsonObject?.let { o ->
                o["source"]?.jsonPrimitive?.intOrNull
                    ?: o["extensions"]?.jsonObject?.values?.firstNotNullOfOrNull { (it as? JsonObject)?.get("source")?.jsonPrimitive?.intOrNull }
            } ?: return -1
            imageIndex[src]?.let { return it }
            val img = images.getOrNull(src)?.jsonObject ?: return -1
            val bytes = img["bufferView"]?.jsonPrimitive?.intOrNull?.let { view(it) }
                ?: img["uri"]?.jsonPrimitive?.content?.takeIf { it.startsWith("data:") }?.let { java.util.Base64.getDecoder().decode(it.substringAfter(",")) }
                ?: return -1
            imageBytes += bytes
            return (imageBytes.size - 1).also { imageIndex[src] = it }
        }

        fun view(v: Int): ByteArray {
            val o = views[v].jsonObject
            val b = bin ?: throw IllegalArgumentException("That model's data is missing.")
            val off = o["byteOffset"]?.jsonPrimitive?.intOrNull ?: 0
            val len = o["byteLength"]!!.jsonPrimitive.intOrNull!!
            return ByteArray(len).also { out -> for (i in 0 until len) out[i] = b.get(off + i) }
        }

        /** An accessor's values as floats (normalised where marked). */
        fun accessor(a: Int): FloatArray {
            val o = accessors[a].jsonObject
            val count = o["count"]!!.jsonPrimitive.intOrNull!!
            val comps = when (o["type"]?.jsonPrimitive?.content) { "SCALAR" -> 1; "VEC2" -> 2; "VEC3" -> 3; "VEC4" -> 4; else -> 1 }
            val ct = o["componentType"]!!.jsonPrimitive.intOrNull!!
            val norm = o["normalized"]?.jsonPrimitive?.booleanOrNull == true
            val size = when (ct) { 5120, 5121 -> 1; 5122, 5123 -> 2; else -> 4 }
            val out = FloatArray(count * comps)
            val viewIdx = o["bufferView"]?.jsonPrimitive?.intOrNull ?: return out
            val view = views[viewIdx].jsonObject
            val b = bin ?: throw IllegalArgumentException("That model's data is missing.")
            val base = (view["byteOffset"]?.jsonPrimitive?.intOrNull ?: 0) + (o["byteOffset"]?.jsonPrimitive?.intOrNull ?: 0)
            val stride = view["byteStride"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 } ?: (size * comps)
            for (i in 0 until count) for (c in 0 until comps) {
                val at = base + i * stride + c * size
                out[i * comps + c] = when (ct) {
                    5126 -> b.getFloat(at)
                    5125 -> (b.getInt(at).toLong() and 0xFFFFFFFFL).toFloat()
                    5123 -> (b.getShort(at).toInt() and 0xFFFF).let { if (norm) it / 65535f else it.toFloat() }
                    5122 -> b.getShort(at).toInt().let { if (norm) max(it / 32767f, -1f) else it.toFloat() }
                    5121 -> (b.get(at).toInt() and 0xFF).let { if (norm) it / 255f else it.toFloat() }
                    else -> b.get(at).toInt().let { if (norm) max(it / 127f, -1f) else it.toFloat() }
                }
            }
            return out
        }
    }

    private val IDENTITY = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
}

/** Reads Wavefront .obj with its .mtl and pictures (usually exported together in a .zip). */
object ObjReader {
    /** [file] gives another file from beside the .obj by name (its .mtl and pictures), or null. */
    fun read(obj: String, file: (String) -> ByteArray?): ModelData {
        val v = ArrayList<Float>(); val vt = ArrayList<Float>()
        class Group(val material: String?) { val pos = ArrayList<Float>(); val uv = ArrayList<Float>(); val idx = ArrayList<Int>(); val seen = HashMap<Long, Int>() }
        val groups = LinkedHashMap<String?, Group>()
        var current: Group = Group(null).also { groups[null] = it }
        val mtlFiles = ArrayList<String>()
        for (raw in obj.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line[0] == '#') continue
            val t = line.split(Regex("\\s+"))
            when (t[0]) {
                "v" -> { v += t[1].toFloat(); v += t[2].toFloat(); v += t[3].toFloat() }
                "vt" -> { vt += t[1].toFloat(); vt += t.getOrNull(2)?.toFloatOrNull() ?: 0f }
                "mtllib" -> mtlFiles += line.substringAfter(' ').trim()
                "usemtl" -> { val name = t.getOrNull(1); current = groups.getOrPut(name) { Group(name) } }
                "f" -> {
                    val corners = t.drop(1).map { c ->
                        val bits = c.split('/')
                        fun ref(s: String?, n: Int) = s?.toIntOrNull()?.let { if (it < 0) n + it else it - 1 }
                        val pi = ref(bits[0], v.size / 3) ?: 0
                        val ti = ref(bits.getOrNull(1), vt.size / 2) ?: -1
                        val key = pi.toLong() shl 32 or (ti.toLong() and 0xFFFFFFFFL)
                        current.seen.getOrPut(key) {
                            current.pos += v[pi * 3]; current.pos += v[pi * 3 + 1]; current.pos += v[pi * 3 + 2]
                            // OBJ pictures run bottom to top.
                            if (ti >= 0) { current.uv += vt[ti * 2]; current.uv += 1f - vt[ti * 2 + 1] } else { current.uv += 0f; current.uv += 0f }
                            current.pos.size / 3 - 1
                        }
                    }
                    for (k in 1 until corners.size - 1) { current.idx += corners[0]; current.idx += corners[k]; current.idx += corners[k + 1] }
                }
            }
        }
        // Materials: their picture or colour.
        val pictures = HashMap<String, String>(); val colors = HashMap<String, Int>()
        for (m in mtlFiles) {
            val text = file(m)?.decodeToString() ?: continue
            var name: String? = null
            for (raw in text.lineSequence()) {
                val t = raw.trim().split(Regex("\\s+"))
                when (t[0]) {
                    "newmtl" -> name = t.getOrNull(1)
                    "map_Kd" -> name?.let { pictures[it] = t.last() }
                    "Kd" -> name?.let { n -> if (t.size >= 4) colors[n] = (0xFF shl 24) or ((t[1].toFloat() * 255).toInt().coerceIn(0, 255) shl 16) or ((t[2].toFloat() * 255).toInt().coerceIn(0, 255) shl 8) or (t[3].toFloat() * 255).toInt().coerceIn(0, 255) }
                }
            }
        }
        val images = ArrayList<ByteArray>(); val imageOf = HashMap<String, Int>()
        val parts = groups.values.filter { it.idx.isNotEmpty() }.map { g ->
            val pic = g.material?.let { pictures[it] }
            val tex = pic?.let { p -> imageOf.getOrPut(p) { file(p)?.let { images += it; images.size - 1 } ?: -1 } } ?: -1
            MeshPart(g.pos.toFloatArray(), g.uv.toFloatArray().takeIf { tex >= 0 }, g.idx.toIntArray(), tex, g.material?.let { colors[it] } ?: 0xFFB0B4BC.toInt())
        }
        require(parts.isNotEmpty()) { "No shapes in that model." }
        return ModelData(Mesh(parts), images)
    }
}

/** Saves and loads a mesh in the app's own compact form. */
object MeshFile {
    fun write(mesh: Mesh, out: java.io.OutputStream) {
        val d = java.io.DataOutputStream(java.io.BufferedOutputStream(out, 1 shl 16))
        d.writeInt(MAGIC); d.writeInt(mesh.parts.size)
        for (p in mesh.parts) {
            d.writeInt(p.texture); d.writeInt(p.color)
            d.writeInt(p.positions.size); p.positions.forEach(d::writeFloat)
            val uv = p.uvs
            d.writeInt(uv?.size ?: 0); uv?.forEach(d::writeFloat)
            d.writeInt(p.indices.size); p.indices.forEach(d::writeInt)
        }
        d.flush()
    }

    fun read(input: java.io.InputStream): Mesh {
        val d = java.io.DataInputStream(java.io.BufferedInputStream(input, 1 shl 16))
        require(d.readInt() == MAGIC) { "Not a house model." }
        val n = d.readInt()
        return Mesh(List(n) {
            val tex = d.readInt(); val color = d.readInt()
            val pos = FloatArray(d.readInt()) { d.readFloat() }
            val uv = d.readInt().let { k -> if (k == 0) null else FloatArray(k) { d.readFloat() } }
            val idx = IntArray(d.readInt()) { d.readInt() }
            MeshPart(pos, uv, idx, tex, color)
        })
    }

    private const val MAGIC = 0x48334431 // "H3D1"
}
