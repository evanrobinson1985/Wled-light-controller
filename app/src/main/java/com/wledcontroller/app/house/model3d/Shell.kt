package com.wledcontroller.app.house.model3d

import com.wledcontroller.app.house.MapPoint
import kotlinx.serialization.Serializable

@Serializable
enum class Roof(val label: String) { GABLE("Gable"), HIP("Hip"), FLAT("Flat") }

/** The sides of a house built from photos, as seen standing in front of it. */
@Serializable
enum class Face(val label: String) { FRONT("Front"), RIGHT("Right side"), BACK("Back"), LEFT("Left side") }

/**
 * A simple house: a box [widthM] wide, [depthM] deep with walls [wallM] high, and a roof
 * [roofM] high on top. A gable's ridge runs left to right ([ridgeAcross]) or front to back.
 */
@Serializable
data class ShellSpec(
    val widthM: Float = 12f,
    val depthM: Float = 9f,
    val wallM: Float = 3f,
    val roof: Roof = Roof.GABLE,
    val roofM: Float = 2.5f,
    val ridgeAcross: Boolean = true,
)

/** A photo of one side, with the wall's corners marked on it: top-left, top-right, bottom-right, bottom-left. */
@Serializable
data class FacePhoto(val face: Face, val path: String, val corners: List<MapPoint>, val aspect: Float)

/**
 * Builds the house from a [ShellSpec]: one part per wall (textured by its photo when there is
 * one) and one for the roof. The front wall is at z = 0, the house sits on y = 0, centred
 * left to right. Each wall's texture covers its full height, peak included: the wall's
 * rectangle is the bottom [wallShare] of it.
 */
object Shell {
    private const val ROOF_COLOR = 0xFF3A3D44.toInt()
    private const val WALL_COLOR = 0xFF9AA0AA.toInt()

    /** The front wall's corners: top-left, top-right, bottom-right, bottom-left. */
    fun frontCorners(s: ShellSpec) = listOf(
        P3(-s.widthM / 2, s.wallM, 0f), P3(s.widthM / 2, s.wallM, 0f), P3(s.widthM / 2, 0f, 0f), P3(-s.widthM / 2, 0f, 0f),
    )

    /** Whether [face] has a triangle of wall up to the roof's peak. */
    fun hasGable(s: ShellSpec, face: Face) = s.roof == Roof.GABLE &&
        if (s.ridgeAcross) face == Face.LEFT || face == Face.RIGHT else face == Face.FRONT || face == Face.BACK

    fun faceWidth(s: ShellSpec, face: Face) = if (face == Face.FRONT || face == Face.BACK) s.widthM else s.depthM
    fun faceHeight(s: ShellSpec, face: Face) = s.wallM + if (hasGable(s, face)) s.roofM else 0f
    fun wallShare(s: ShellSpec, face: Face) = s.wallM / faceHeight(s, face)

    /** [textures] says which texture each side's photo is (missing: plain wall colour). */
    fun build(s: ShellSpec, textures: Map<Face, Int> = emptyMap()): Mesh {
        val w = s.widthM; val d = s.depthM; val h = s.wallM; val r = s.roofM
        val parts = ArrayList<MeshPart>()
        for (face in Face.entries) {
            // Bottom-left corner as seen from outside, and the direction to the right.
            val (origin, along) = when (face) {
                Face.FRONT -> P3(-w / 2, 0f, 0f) to P3(1f, 0f, 0f)
                Face.RIGHT -> P3(w / 2, 0f, 0f) to P3(0f, 0f, -1f)
                Face.BACK -> P3(w / 2, 0f, -d) to P3(-1f, 0f, 0f)
                Face.LEFT -> P3(-w / 2, 0f, -d) to P3(0f, 0f, 1f)
            }
            val fw = faceWidth(s, face)
            val outline = if (hasGable(s, face)) listOf(0f to 0f, fw to 0f, fw to h, fw / 2 to h + r, 0f to h)
            else listOf(0f to 0f, fw to 0f, fw to h, 0f to h)
            val fh = faceHeight(s, face)
            val pos = FloatArray(outline.size * 3); val uv = FloatArray(outline.size * 2)
            outline.forEachIndexed { i, (u, y) ->
                val p = origin + along * u + P3.UP * y
                pos[i * 3] = p.x; pos[i * 3 + 1] = p.y; pos[i * 3 + 2] = p.z
                uv[i * 2] = u / fw; uv[i * 2 + 1] = 1f - y / fh
            }
            val tex = textures[face] ?: -1
            parts += MeshPart(pos, uv.takeIf { tex >= 0 }, fan(outline.size), tex, WALL_COLOR)
        }
        parts += roof(s)
        return Mesh(parts)
    }

    private fun roof(s: ShellSpec): MeshPart {
        val w = s.widthM; val d = s.depthM; val h = s.wallM; val top = h + s.roofM
        val fl = P3(-w / 2, h, 0f); val fr = P3(w / 2, h, 0f); val br = P3(w / 2, h, -d); val bl = P3(-w / 2, h, -d)
        val polys: List<List<P3>> = when (s.roof) {
            Roof.FLAT -> listOf(listOf(fl, fr, br, bl))
            Roof.GABLE -> if (s.ridgeAcross) {
                val a = P3(-w / 2, top, -d / 2); val b = P3(w / 2, top, -d / 2)
                listOf(listOf(fl, fr, b, a), listOf(br, bl, a, b))
            } else {
                val a = P3(0f, top, 0f); val b = P3(0f, top, -d)
                listOf(listOf(fr, br, b, a), listOf(bl, fl, a, b))
            }
            Roof.HIP -> if (w >= d) {
                val a = P3(-(w - d) / 2, top, -d / 2); val b = P3((w - d) / 2, top, -d / 2)
                listOf(listOf(fl, fr, b, a), listOf(br, bl, a, b), listOf(fr, br, b), listOf(bl, fl, a))
            } else {
                // Deeper than wide: the ridge runs front to back.
                val a = P3(0f, top, -w / 2); val b = P3(0f, top, -d + w / 2)
                listOf(listOf(fl, fr, a), listOf(fr, br, b, a), listOf(br, bl, b), listOf(bl, fl, a, b))
            }
        }
        val pts = polys.flatten()
        val pos = FloatArray(pts.size * 3)
        pts.forEachIndexed { i, p -> pos[i * 3] = p.x; pos[i * 3 + 1] = p.y; pos[i * 3 + 2] = p.z }
        val idx = ArrayList<Int>()
        var base = 0
        for (poly in polys) { fan(poly.size).forEach { idx += base + it }; base += poly.size }
        return MeshPart(pos, null, idx.toIntArray(), -1, ROOF_COLOR)
    }

    private fun fan(n: Int): IntArray = IntArray((n - 2) * 3) { i -> val t = i / 3; when (i % 3) { 0 -> 0; 1 -> t + 1; else -> t + 2 } }
}
