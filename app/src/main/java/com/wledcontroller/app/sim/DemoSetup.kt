package com.wledcontroller.app.sim

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.wledcontroller.app.data.AppStore
import com.wledcontroller.app.data.DeviceGroup
import com.wledcontroller.app.data.LightProfile
import com.wledcontroller.app.data.SavedDevice
import com.wledcontroller.app.house.HouseEngine
import com.wledcontroller.app.house.HouseMap
import com.wledcontroller.app.house.LightRun
import com.wledcontroller.app.house.MapPoint
import com.wledcontroller.app.house.PropGroup
import com.wledcontroller.app.house.PropType
import com.wledcontroller.app.show.Clip
import com.wledcontroller.app.show.Playlist
import com.wledcontroller.app.show.ScheduleEntry
import com.wledcontroller.app.show.SeqEffect
import com.wledcontroller.app.show.Sequence
import com.wledcontroller.app.show.SequenceRepository
import com.wledcontroller.app.show.Track
import com.wledcontroller.app.show.TrackTarget
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * A ready-made virtual house: a night-time picture, four simulated controllers traced on it as
 * props, groups, light types, presets, a demo show, a playlist and a schedule.
 */
object DemoSetup {

    private const val W = 1600
    private const val H = 1000

    // House geometry, as fractions of the picture.
    private const val EAVE_Y = 0.42f
    private val roof = listOf(MapPoint(0.23f, EAVE_Y), MapPoint(0.48f, 0.17f), MapPoint(0.73f, EAVE_Y))
    private val leftWindow = RectF(0.31f, 0.50f, 0.39f, 0.62f)
    private val rightWindow = RectF(0.57f, 0.50f, 0.65f, 0.62f)
    private val door = RectF(0.45f, 0.56f, 0.51f, 0.75f)
    private const val TREE_X = 0.12f
    private const val TREE_TOP = 0.30f
    private const val TREE_BASE = 0.85f
    private val arches = listOf(0.79f, 0.87f, 0.95f)
    private const val ARCH_BASE = 0.90f
    private val bushes = listOf(MapPoint(0.33f, 0.79f), MapPoint(0.63f, 0.79f))

    /** Creates the demo. Simulated controllers are written to [simFile] for [SimHub.load]. */
    suspend fun build(store: AppStore, house: HouseEngine, sequences: SequenceRepository, simFile: java.io.File) {
        val (photo, aspect) = house.importBitmap(drawHouse())

        val roofDev = SavedDevice("sim-roof", "Roofline", "127.77.0.1")
        val windowDev = SavedDevice("sim-windows", "Windows & door", "127.77.0.2")
        val treeDev = SavedDevice("sim-tree", "Mega tree", "127.77.0.3")
        val yardDev = SavedDevice("sim-yard", "Yard props", "127.77.0.4")

        val runs = listOf(
            LightRun("run-roof", "Roofline", roofDev.id, 0, 150, roof, prop = PropType.LINE),
            LightRun("run-lwin", "Left window", windowDev.id, 0, 32, outline(leftWindow), prop = PropType.WINDOW),
            LightRun("run-rwin", "Right window", windowDev.id, 32, 32, outline(rightWindow), prop = PropType.WINDOW),
            LightRun(
                "run-door", "Door", windowDev.id, 64, 36,
                listOf(MapPoint(door.left, door.bottom), MapPoint(door.left, door.top), MapPoint(door.right, door.top), MapPoint(door.right, door.bottom)),
                prop = PropType.WINDOW,
            ),
            LightRun("run-tree", "Mega tree", treeDev.id, 0, 160, spiral(), prop = PropType.TREE),
            LightRun("run-arch1", "Arch 1", yardDev.id, 0, 25, arch(arches[0]), prop = PropType.ARCH),
            LightRun("run-arch2", "Arch 2", yardDev.id, 25, 25, arch(arches[1]), prop = PropType.ARCH),
            LightRun("run-arch3", "Arch 3", yardDev.id, 50, 25, arch(arches[2]), prop = PropType.ARCH),
            LightRun("run-bush1", "Left bush", yardDev.id, 75, 28, bush(bushes[0]), prop = PropType.BUSH),
            LightRun("run-bush2", "Right bush", yardDev.id, 103, 27, bush(bushes[1]), prop = PropType.BUSH),
        )
        val groups = listOf(
            PropGroup("grp-arches", "All arches", listOf("run-arch1", "run-arch2", "run-arch3")),
            PropGroup("grp-windows", "Windows & door", listOf("run-lwin", "run-rwin", "run-door")),
            PropGroup("grp-bushes", "Bushes", listOf("run-bush1", "run-bush2")),
        )

        val now = System.currentTimeMillis()
        val red = 0xFF1010; val green = 0x10C020; val gold = 0xFFB040; val white = 0xFFFFFF; val warm = 0xFFB45A
        fun data(d: SavedDevice, leds: Int, seed: Int, seg: JsonObject) = SimDeviceData(
            host = d.host, name = d.name, seed = seed, createdAt = now,
            state = SimDevice.defaultState(leds, seg).let { JsonObject(it + ("bri" to kotlinx.serialization.json.JsonPrimitive(170))) }.toString(),
            cfg = SimDevice.defaultCfg(d.name, leds).toString(),
            presets = presets(leds).toString(),
        )
        val sims = listOf(
            data(roofDev, 150, 11, segment(150, 67, listOf(red, green, gold), pal = 4, sx = 90)),
            data(windowDev, 100, 23, segment(100, 0, listOf(warm, 0, 0))),
            data(treeDev, 160, 37, segment(160, 80, listOf(green, red, gold), pal = 4, sx = 110, ix = 170)),
            data(yardDev, 130, 41, segment(130, 54, listOf(red, white, green), sx = 70, ix = 60)),
        )
        simFile.parentFile?.mkdirs()
        simFile.writeText(kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(SimDeviceData.serializer()), sims))

        listOf(roofDev, windowDev, treeDev, yardDev).forEach { store.upsertDevice(it) }
        store.upsertGroup(DeviceGroup("sim-all", "Whole yard", listOf(roofDev.id, windowDev.id, treeDev.id, yardDev.id)))
        store.setLightProfile(LightProfile.deviceKey(roofDev.id), LightProfile.STRIP)
        store.setLightProfile(LightProfile.deviceKey(windowDev.id), LightProfile.BULLETS)
        store.setLightProfile(LightProfile.deviceKey(treeDev.id), LightProfile.STRIP)
        store.setLightProfile(LightProfile.deviceKey(yardDev.id), LightProfile.BULLETS)
        // The front wall is 40 ft wide, which gives the map its scale.
        store.updateHouseMap {
            HouseMap(
                photoPath = photo, aspect = aspect, runs = runs, groups = groups,
                scaleA = MapPoint(0.26f, 0.76f), scaleB = MapPoint(0.70f, 0.76f), scaleCm = 40 * 30.48f,
            )
        }

        val show = demoShow()
        sequences.save(show)
        store.upsertPlaylist(Playlist("sim-playlist", "Demo playlist", listOf(show.id)))
        store.upsertSchedule(ScheduleEntry("sim-schedule", "sim-playlist", startMin = 17 * 60, endMin = 22 * 60, lightsOffAtEnd = true))
    }

    private fun segment(len: Int, fx: Int, colors: List<Int>, pal: Int = 0, sx: Int = 128, ix: Int = 128): JsonObject {
        val base = SimDevice.defaultSegment(len, fx = fx, pal = pal)
        return JsonObject(base + mapOf(
            "col" to JsonArray(colors.map { c -> buildJsonArray { add((c shr 16) and 0xFF); add((c shr 8) and 0xFF); add(c and 0xFF) } }),
            "sx" to kotlinx.serialization.json.JsonPrimitive(sx),
            "ix" to kotlinx.serialization.json.JsonPrimitive(ix),
        ))
    }

    private fun presets(len: Int) = buildJsonObject {
        putJsonObject("0") {}
        fun preset(id: Int, name: String, fx: Int, colors: List<Int>, pal: Int = 0, sx: Int = 128) = putJsonObject(id.toString()) {
            put("n", name); put("on", true); put("bri", 170)
            put("seg", JsonArray(listOf(segment(len, fx, colors, pal, sx))))
        }
        preset(1, "Warm white", 0, listOf(0xFFB45A, 0, 0))
        preset(2, "Rainbow", 9, listOf(0xFF0000, 0, 0), sx = 100)
        preset(3, "Candy cane", 54, listOf(0xFF0000, 0xFFFFFF, 0xFF0000), sx = 80)
        preset(4, "Snowfall", 87, listOf(0xA0C8FF, 0x101830, 0), pal = 3)
        putJsonObject("5") {
            put("n", "Cycle looks")
            putJsonObject("playlist") {
                putJsonArray("ps") { add(1); add(2); add(3); add(4) }
                putJsonArray("dur") { repeat(4) { add(100) } }
                putJsonArray("transition") { repeat(4) { add(7) } }
                put("repeat", 0); put("end", 0)
            }
        }
    }

    private fun demoShow(): Sequence {
        fun c(vararg rgb: Int) = rgb.map { it or (0xFF shl 24) }
        var n = 0
        fun clip(start: Long, dur: Long, fx: SeqEffect, colors: List<Int>, speed: Float = 0.5f, size: Float = 0.5f, fadeIn: Long = 0, fadeOut: Long = 0) =
            Clip("demo-c${n++}", start, dur, fx, colors, speed = speed, size = size, fadeInMs = fadeIn, fadeOutMs = fadeOut)
        val house = TrackTarget(TrackTarget.Kind.HOUSE)
        fun prop(id: String) = TrackTarget(TrackTarget.Kind.PROP, id)
        fun group(id: String) = TrackTarget(TrackTarget.Kind.GROUP, id)
        val tracks = listOf(
            Track("demo-t1", "Whole house", house, listOf(
                clip(0, 3000, SeqEffect.WIPE, c(0xFF0000, 0x00C000)),
                clip(3000, 8000, SeqEffect.CHASE, c(0xFF0000, 0xFFFFFF), speed = 0.4f),
                clip(24000, 8000, SeqEffect.RAINBOW, c(0xFFFFFF), speed = 0.6f),
                clip(40000, 8000, SeqEffect.FIREWORKS, c(0xFFD27A, 0xFFFFFF, 0xC0C8FF), fadeOut = 1500),
            )),
            Track("demo-t2", "Roofline", prop("run-roof"), listOf(
                clip(11000, 8000, SeqEffect.COMET, c(0xFFFFFF), speed = 0.6f),
                clip(32000, 8000, SeqEffect.COLOR_WAVE, c(0xFF1010, 0x10C020, 0xFFB040)),
            )),
            Track("demo-t3", "Arches", group("grp-arches"), listOf(
                clip(11000, 8000, SeqEffect.CHASE, c(0xFF0000, 0x00C000), speed = 0.7f),
                clip(19000, 5000, SeqEffect.PULSE, c(0xFFB040)),
            )),
            Track("demo-t4", "Mega tree", prop("run-tree"), listOf(
                clip(11000, 13000, SeqEffect.TWINKLE, c(0x10C020, 0xFFB040, 0xFF1010)),
                clip(32000, 8000, SeqEffect.SPARKLE, c(0xFFFFFF, 0x10C020)),
            )),
            Track("demo-t5", "Windows", group("grp-windows"), listOf(
                clip(11000, 13000, SeqEffect.PULSE, c(0xFFB45A), speed = 0.3f),
                clip(32000, 8000, SeqEffect.ON, c(0xFFB45A), fadeIn = 2000, fadeOut = 2000),
            )),
            Track("demo-t6", "Bushes", group("grp-bushes"), listOf(
                clip(11000, 13000, SeqEffect.SHIMMER, c(0x10C020, 0x40FF60)),
                clip(32000, 8000, SeqEffect.SNOW, c(0xFFFFFF)),
            )),
        )
        return Sequence(
            id = "sim-demo-show", name = "Demo show", durationMs = 48_000, tracks = tracks,
            marks = (0L until 48_000L step 500L).toList(), bpm = 120f,
        )
    }

    // ------------------------------------------------------------------ geometry

    private fun outline(r: RectF) = listOf(
        MapPoint(r.left, r.top), MapPoint(r.right, r.top), MapPoint(r.right, r.bottom), MapPoint(r.left, r.bottom), MapPoint(r.left, r.top),
    )

    private fun treeHalfWidth(y: Float) = 0.075f * (y - TREE_TOP) / (TREE_BASE - TREE_TOP)

    /** Lights wound round the tree from the bottom up. */
    private fun spiral(): List<MapPoint> = List(64) { i ->
        val f = i / 63f
        val y = TREE_BASE - 0.02f - f * (TREE_BASE - TREE_TOP - 0.03f)
        MapPoint(TREE_X + treeHalfWidth(y) * sin(f * 7f * 2f * PI.toFloat()), y)
    }

    private fun arch(cx: Float) = List(13) { i ->
        val a = PI.toFloat() * i / 12f
        MapPoint(cx - 0.032f * cos(a), ARCH_BASE - 0.13f * sin(a))
    }

    private fun bush(c: MapPoint) = List(7) { j ->
        MapPoint(c.x - 0.045f + j * 0.015f, c.y + if (j % 2 == 0) 0.02f else -0.02f)
    }

    // ------------------------------------------------------------------ picture

    private fun drawHouse(): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        fun x(f: Float) = f * W
        fun y(f: Float) = f * H

        // Night sky, stars and moon.
        p.shader = LinearGradient(0f, 0f, 0f, y(0.75f), 0xFF070B1C.toInt(), 0xFF1A2448.toInt(), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, x(1f), y(0.76f), p)
        p.shader = null
        val rnd = Random(7)
        p.color = 0xFFDDE4FF.toInt()
        repeat(140) { cv.drawCircle(rnd.nextFloat() * W, rnd.nextFloat() * y(0.6f), 0.6f + rnd.nextFloat() * 1.6f, p) }
        p.color = 0xFFF2EBD0.toInt(); cv.drawCircle(x(0.86f), y(0.12f), y(0.045f), p)
        p.color = 0xFF0E1530.toInt(); cv.drawCircle(x(0.875f), y(0.11f), y(0.04f), p)

        // Snowy ground.
        p.shader = LinearGradient(0f, y(0.74f), 0f, y(1f), 0xFF3C4763.toInt(), 0xFF262E44.toInt(), Shader.TileMode.CLAMP)
        cv.drawRect(0f, y(0.74f), x(1f), y(1f), p)
        p.shader = null

        // House.
        p.color = 0xFF34343E.toInt(); cv.drawRect(x(0.26f), y(EAVE_Y), x(0.70f), y(0.76f), p)
        p.color = 0xFF2A2330.toInt()
        cv.drawRect(x(0.58f), y(0.20f), x(0.62f), y(0.32f), p) // chimney
        val roofPath = Path().apply {
            moveTo(x(roof[0].x), y(roof[0].y) + 4); lineTo(x(roof[1].x), y(roof[1].y)); lineTo(x(roof[2].x), y(roof[2].y) + 4); close()
        }
        p.color = 0xFF221C26.toInt(); cv.drawPath(roofPath, p)
        p.color = 0xFFDCE3F2.toInt(); p.alpha = 70
        cv.drawPath(Path().apply { moveTo(x(0.25f), y(EAVE_Y)); lineTo(x(0.48f), y(0.19f)); lineTo(x(0.71f), y(EAVE_Y)); lineTo(x(0.66f), y(EAVE_Y)); lineTo(x(0.48f), y(0.23f)); lineTo(x(0.30f), y(EAVE_Y)); close() }, p)
        p.alpha = 255
        for (w in listOf(leftWindow, rightWindow)) {
            p.color = 0xFF5A4628.toInt(); cv.drawRect(x(w.left), y(w.top), x(w.right), y(w.bottom), p)
            p.color = 0xFF2A2128.toInt(); p.strokeWidth = 5f
            cv.drawLine(x(w.centerX()), y(w.top), x(w.centerX()), y(w.bottom), p)
            cv.drawLine(x(w.left), y(w.centerY()), x(w.right), y(w.centerY()), p)
        }
        p.color = 0xFF4A2C22.toInt(); cv.drawRect(x(door.left), y(door.top), x(door.right), y(door.bottom), p)
        p.color = 0xFFC8A050.toInt(); cv.drawCircle(x(door.right - 0.008f), y(door.centerY() + 0.02f), 5f, p)

        // Tree, bushes and arch frames.
        p.color = 0xFF16281E.toInt()
        cv.drawPath(Path().apply {
            moveTo(x(TREE_X), y(TREE_TOP)); lineTo(x(TREE_X + 0.08f), y(TREE_BASE)); lineTo(x(TREE_X - 0.08f), y(TREE_BASE)); close()
        }, p)
        p.color = 0xFF3A2A20.toInt(); cv.drawRect(x(TREE_X - 0.008f), y(TREE_BASE), x(TREE_X + 0.008f), y(TREE_BASE + 0.04f), p)
        p.color = 0xFF1A3324.toInt()
        for (b in bushes) cv.drawOval(x(b.x - 0.06f), y(b.y - 0.045f), x(b.x + 0.06f), y(b.y + 0.04f), p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 4f; p.color = 0xFF5A6070.toInt()
        for (a in arches) cv.drawArc(x(a - 0.032f), y(ARCH_BASE - 0.13f), x(a + 0.032f), y(ARCH_BASE + 0.13f), 180f, 180f, false, p)
        p.style = Paint.Style.FILL
        return bmp
    }
}
