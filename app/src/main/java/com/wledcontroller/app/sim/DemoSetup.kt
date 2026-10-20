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
import com.wledcontroller.app.house.ModelKind
import com.wledcontroller.app.house.PropModel
import com.wledcontroller.app.house.StartCorner
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
 * A ready-made virtual house: a night-time picture, six simulated controllers traced on it as
 * props (including icicles along the eave and a dense pixel matrix covering the garage door),
 * groups, light types, presets,
 * a demo show, a playlist and a schedule.
 */
object DemoSetup {

    private const val W = 1600
    private const val H = 1000

    // Everything is laid out from real sizes in feet, all to one scale: the picture is 80 ft
    // wide (and 50 ft high), with the front of the house standing on [GROUND].
    private const val PICTURE_FT = 80f
    private const val GROUND = 0.76f
    /** Feet from the left edge, as a fraction of the picture's width. */
    private fun ftX(ft: Float) = ft / PICTURE_FT
    /** Feet above the ground, as a fraction of the picture's height. */
    private fun ftY(ft: Float) = GROUND - ft * W / H / PICTURE_FT

    // A 36 ft single-storey house: 9 ft walls under an 8/12 gable roof with a 1 ft overhang,
    // a 3 ft × 6 ft 8 in front door in the middle and 4 ft × 3 ft 6 in windows either side.
    private const val HOUSE_LEFT_FT = 14f
    private const val HOUSE_RIGHT_FT = 50f
    private const val HOUSE_MID_FT = (HOUSE_LEFT_FT + HOUSE_RIGHT_FT) / 2f
    private val HOUSE_LEFT = ftX(HOUSE_LEFT_FT)
    private val HOUSE_RIGHT = ftX(HOUSE_RIGHT_FT)
    private val EAVE_Y = ftY(9f)
    private val roof = listOf(
        MapPoint(ftX(HOUSE_LEFT_FT - 1f), ftY(9f - 8f / 12f)),
        MapPoint(ftX(HOUSE_MID_FT), ftY(9f + (HOUSE_MID_FT - HOUSE_LEFT_FT) * 8f / 12f)),
        MapPoint(ftX(HOUSE_RIGHT_FT + 1f), ftY(9f - 8f / 12f)),
    )
    private fun window(midFt: Float) = RectF(ftX(midFt - 2f), ftY(6.67f), ftX(midFt + 2f), ftY(3.17f))
    private val leftWindow = window(HOUSE_LEFT_FT + 7f)
    private val rightWindow = window(HOUSE_RIGHT_FT - 7f)
    private val door = RectF(ftX(HOUSE_MID_FT - 1.5f), ftY(6.67f), ftX(HOUSE_MID_FT + 1.5f), ftY(0f))

    // An 18 ft mega tree, 10 ft across at the base, in the yard left of the house.
    private val TREE_X = ftX(6.5f)
    private val TREE_BASE = 0.80f
    private val TREE_TOP = TREE_BASE - 18f * W / H / PICTURE_FT
    private val TREE_HALF_W = ftX(5f)
    /** The mega tree: 16 strings of 25 pixels round a pole, each fed from the base up. */
    private val treeModel = PropModel(ModelKind.MEGA_TREE, strings = 16, nodes = 25, degrees = 360, fromBottom = true, zigzag = false)
    private val TREE_LEDS = treeModel.ledCount
    /** Icicles along the front eave: 3 strings of 90 pixels 3 in apart, in drops of 3, 5, 4 and 6. */
    private val icicleModel = PropModel(ModelKind.ICICLES, strings = 3, nodes = 90, dropPattern = listOf(3, 5, 4, 6), dropSpacingCm = 7.62f, zigzag = true)
    private val ICICLE_LEDS = icicleModel.ledCount
    // 5 ft × 6 ft 6 in arches in the yard, and 3 ft bushes under the windows.
    private val arches = listOf(0.79f, 0.87f, 0.95f)
    private const val ARCH_BASE = 0.90f
    private val bushes = listOf(MapPoint(leftWindow.centerX(), 0.79f), MapPoint(rightWindow.centerX(), 0.79f))

    // A 24 ft two-car garage joined on to the right of the house: 9 ft walls, a 6/12 gable roof
    // and a 16 ft × 7 ft double door.
    private const val GARAGE_LEFT_FT = HOUSE_RIGHT_FT
    private const val GARAGE_RIGHT_FT = GARAGE_LEFT_FT + 24f
    private const val GARAGE_MID_FT = (GARAGE_LEFT_FT + GARAGE_RIGHT_FT) / 2f
    private val GARAGE_LEFT = ftX(GARAGE_LEFT_FT)
    private val GARAGE_RIGHT = ftX(GARAGE_RIGHT_FT)
    private val GARAGE_EAVE = ftY(9f)
    private val GARAGE_PEAK = ftY(9f + 12f * 6f / 12f)
    private val garageDoor = RectF(ftX(GARAGE_MID_FT - 8f), ftY(7f), ftX(GARAGE_MID_FT + 8f), ftY(0f))
    /** The matrix on the door: 96 × 42 pixels, about 2 in apart. */
    private const val MATRIX_W = 96
    private const val MATRIX_H = 42
    private const val MATRIX_LEDS = MATRIX_W * MATRIX_H

    /** Creates the demo. Simulated controllers are written to [simFile] for [SimHub.load]. */
    suspend fun build(store: AppStore, house: HouseEngine, sequences: SequenceRepository, simFile: java.io.File) {
        val (photo, aspect) = house.importBitmap(drawHouse())

        val roofDev = SavedDevice("sim-roof", "Roofline", "127.77.0.1")
        val windowDev = SavedDevice("sim-windows", "Windows & door", "127.77.0.2")
        val treeDev = SavedDevice("sim-tree", "Mega tree", "127.77.0.3")
        val yardDev = SavedDevice("sim-yard", "Yard props", "127.77.0.4")
        val garageDev = SavedDevice("sim-garage", "Garage matrix", "127.77.0.5")
        val icicleDev = SavedDevice("sim-icicles", "Icicles", "127.77.0.6")

        val runs = listOf(
            LightRun("run-roof", "Roofline", roofDev.id, 0, 150, roof, prop = PropType.LINE),
            LightRun("run-lwin", "Left window", windowDev.id, 0, 32, outline(leftWindow), prop = PropType.WINDOW),
            LightRun("run-rwin", "Right window", windowDev.id, 32, 32, outline(rightWindow), prop = PropType.WINDOW),
            LightRun(
                "run-door", "Door", windowDev.id, 64, 36,
                listOf(MapPoint(door.left, door.bottom), MapPoint(door.left, door.top), MapPoint(door.right, door.top), MapPoint(door.right, door.bottom)),
                prop = PropType.WINDOW,
            ),
            LightRun(
                "run-tree", "Mega tree", treeDev.id, 0, TREE_LEDS,
                listOf(MapPoint(TREE_X - TREE_HALF_W, TREE_TOP), MapPoint(TREE_X + TREE_HALF_W, TREE_BASE)),
                prop = PropType.TREE, model = treeModel,
            ),
            // Hung from the eave, wired from the left.
            LightRun(
                "run-icicles", "Icicles", icicleDev.id, 0, ICICLE_LEDS,
                listOf(MapPoint(HOUSE_LEFT + 0.004f, EAVE_Y + 0.004f), MapPoint(HOUSE_RIGHT - 0.004f, EAVE_Y + 0.004f)),
                prop = PropType.ICICLES, model = icicleModel,
            ),
            LightRun("run-arch1", "Arch 1", yardDev.id, 0, 25, arch(arches[0]), prop = PropType.ARCH),
            LightRun("run-arch2", "Arch 2", yardDev.id, 25, 25, arch(arches[1]), prop = PropType.ARCH),
            LightRun("run-arch3", "Arch 3", yardDev.id, 50, 25, arch(arches[2]), prop = PropType.ARCH),
            LightRun("run-bush1", "Left bush", yardDev.id, 75, 28, bush(bushes[0]), prop = PropType.BUSH),
            LightRun("run-bush2", "Right bush", yardDev.id, 103, 27, bush(bushes[1]), prop = PropType.BUSH),
            // A dense pixel matrix over the whole garage door, wired in rows from the top left,
            // snaking back and forth.
            LightRun(
                "run-garage", "Garage door matrix", garageDev.id, 0, MATRIX_LEDS,
                listOf(MapPoint(garageDoor.left, garageDoor.top), MapPoint(garageDoor.right, garageDoor.bottom)),
                prop = PropType.MATRIX,
                model = PropModel(ModelKind.MATRIX, strings = MATRIX_H, nodes = MATRIX_W, start = StartCorner.TOP_LEFT, vertical = false, zigzag = true),
                realWidthCm = 16 * 30.48f, realHeightCm = 7 * 30.48f,
            ),
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
            data(treeDev, TREE_LEDS, 37, segment(TREE_LEDS, 80, listOf(green, red, gold), pal = 4, sx = 110, ix = 170)),
            data(icicleDev, ICICLE_LEDS, 61, segment(ICICLE_LEDS, 51, listOf(0xDCEBFF, 0x8CC8FF, white), sx = 60, ix = 140)),
            data(yardDev, 130, 41, segment(130, 54, listOf(red, white, green), sx = 70, ix = 60)),
            // Set up as a 2D panel, so its own effects sweep across the door rather than along the wiring.
            data(garageDev, MATRIX_LEDS, 53, segment(MATRIX_LEDS, 9, listOf(red, green, gold), pal = 4, sx = 70))
                .copy(ledmap = matrixLedmap().toString()),
        )
        simFile.parentFile?.mkdirs()
        simFile.writeText(kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(SimDeviceData.serializer()), sims))

        listOf(roofDev, windowDev, treeDev, yardDev, garageDev, icicleDev).forEach { store.upsertDevice(it) }
        store.upsertGroup(DeviceGroup("sim-all", "Whole yard", listOf(roofDev.id, windowDev.id, treeDev.id, yardDev.id, garageDev.id, icicleDev.id)))
        store.setLightProfile(LightProfile.deviceKey(roofDev.id), LightProfile.STRIP)
        store.setLightProfile(LightProfile.deviceKey(windowDev.id), LightProfile.BULLETS)
        store.setLightProfile(LightProfile.deviceKey(treeDev.id), LightProfile.STRIP)
        store.setLightProfile(LightProfile.deviceKey(yardDev.id), LightProfile.BULLETS)
        store.setLightProfile(LightProfile.deviceKey(garageDev.id), LightProfile.MATRIX)
        store.setLightProfile(LightProfile.deviceKey(icicleDev.id), LightProfile.BULLETS)
        // The front wall is 36 ft wide, which gives the map its scale.
        store.updateHouseMap {
            HouseMap(
                photoPath = photo, aspect = aspect, runs = runs, groups = groups,
                scaleA = MapPoint(HOUSE_LEFT, GROUND), scaleB = MapPoint(HOUSE_RIGHT, GROUND), scaleCm = (HOUSE_RIGHT_FT - HOUSE_LEFT_FT) * 30.48f,
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
            Track("demo-t7", "Garage door", prop("run-garage"), listOf(
                clip(11000, 13000, SeqEffect.TEXT, c(0xFF2020, 0x20C040), speed = 0.45f).copy(text = "Merry Christmas!"),
                clip(32000, 8000, SeqEffect.PLASMA, c(0xFF1010, 0x10C020, 0xFFB040), speed = 0.5f),
            )),
            Track("demo-t8", "Icicles", prop("run-icicles"), listOf(
                clip(11000, 13000, SeqEffect.SNOW, c(0xDCEBFF, 0x8CC8FF)),
                clip(32000, 8000, SeqEffect.TWINKLE, c(0xFFFFFF, 0x8CC8FF)),
            )),
        )
        return Sequence(
            id = "sim-demo-show", name = "Demo show", durationMs = 48_000, tracks = tracks,
            marks = (0L until 48_000L step 500L).toList(), bpm = 120f,
        )
    }

    // ------------------------------------------------------------------ geometry

    /**
     * WLED's ledmap.json for the garage matrix: the panel's pixels in rows from the top left
     * (the map gives, for each of them, which LED on the snaking wiring it is).
     */
    internal fun matrixLedmap() = buildJsonObject {
        put("width", MATRIX_W); put("height", MATRIX_H)
        putJsonArray("map") {
            for (row in 0 until MATRIX_H) for (col in 0 until MATRIX_W) {
                add(row * MATRIX_W + if (row % 2 == 0) col else MATRIX_W - 1 - col)
            }
        }
    }

    private fun outline(r: RectF) = listOf(
        MapPoint(r.left, r.top), MapPoint(r.right, r.top), MapPoint(r.right, r.bottom), MapPoint(r.left, r.bottom), MapPoint(r.left, r.top),
    )

    private fun arch(cx: Float) = List(13) { i ->
        val a = PI.toFloat() * i / 12f
        MapPoint(cx - 0.032f * cos(a), ARCH_BASE - 0.13f * sin(a))
    }

    private fun bush(c: MapPoint) = List(7) { j ->
        MapPoint(c.x - 0.0225f + j * 0.0075f, c.y + if (j % 2 == 0) 0.012f else -0.012f)
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

        // The garage, joined on to the right of the house: wall, gable roof and its big door.
        p.color = 0xFF30303A.toInt(); cv.drawRect(x(GARAGE_LEFT), y(GARAGE_EAVE), x(GARAGE_RIGHT), y(GROUND), p)
        p.color = 0xFF221C26.toInt()
        cv.drawPath(Path().apply {
            moveTo(x(GARAGE_LEFT - ftX(1f)), y(GARAGE_EAVE) + 4); lineTo(x((GARAGE_LEFT + GARAGE_RIGHT) / 2f), y(GARAGE_PEAK))
            lineTo(x(GARAGE_RIGHT + ftX(1f)), y(GARAGE_EAVE) + 4); close()
        }, p)
        p.color = 0xFFDCE3F2.toInt(); p.alpha = 60
        cv.drawRect(x(GARAGE_LEFT - ftX(1f)), y(GARAGE_EAVE) - 3, x(GARAGE_RIGHT + ftX(1f)), y(GARAGE_EAVE) + 5, p)
        p.alpha = 255
        p.color = 0xFF1C1E26.toInt(); cv.drawRect(x(garageDoor.left), y(garageDoor.top), x(garageDoor.right), y(garageDoor.bottom), p)
        // The door's panels, faint under the matrix.
        p.color = 0xFF2A2D38.toInt(); p.strokeWidth = 3f
        for (i in 1..3) {
            val yy = garageDoor.top + (garageDoor.bottom - garageDoor.top) * i / 4f
            cv.drawLine(x(garageDoor.left), y(yy), x(garageDoor.right), y(yy), p)
        }
        // Driveway.
        p.color = 0xFF2C3346.toInt()
        cv.drawPath(Path().apply {
            moveTo(x(garageDoor.left), y(GROUND)); lineTo(x(garageDoor.right), y(GROUND)); lineTo(x(garageDoor.right + 0.06f), y(1f)); lineTo(x(garageDoor.left - 0.03f), y(1f)); close()
        }, p)

        // House.
        p.color = 0xFF34343E.toInt(); cv.drawRect(x(HOUSE_LEFT), y(EAVE_Y), x(HOUSE_RIGHT), y(GROUND), p)
        p.color = 0xFF2A2330.toInt()
        cv.drawRect(x(ftX(HOUSE_RIGHT_FT - 8f)), y(ftY(19f)), x(ftX(HOUSE_RIGHT_FT - 6f)), y(ftY(12f)), p) // chimney
        val roofPath = Path().apply {
            moveTo(x(roof[0].x), y(roof[0].y) + 4); lineTo(x(roof[1].x), y(roof[1].y)); lineTo(x(roof[2].x), y(roof[2].y) + 4); close()
        }
        p.color = 0xFF221C26.toInt(); cv.drawPath(roofPath, p)
        p.color = 0xFFDCE3F2.toInt(); p.alpha = 70
        // Snow along the roof's edges.
        val (rl, peak, rr) = roof
        cv.drawPath(Path().apply {
            moveTo(x(rl.x + 0.005f), y(rl.y)); lineTo(x(peak.x), y(peak.y + 0.015f)); lineTo(x(rr.x - 0.005f), y(rr.y))
            lineTo(x(rr.x - 0.04f), y(rr.y)); lineTo(x(peak.x), y(peak.y + 0.045f)); lineTo(x(rl.x + 0.04f), y(rl.y)); close()
        }, p)
        p.alpha = 255
        for (w in listOf(leftWindow, rightWindow)) {
            p.color = 0xFF5A4628.toInt(); cv.drawRect(x(w.left), y(w.top), x(w.right), y(w.bottom), p)
            p.color = 0xFF2A2128.toInt(); p.strokeWidth = 5f
            cv.drawLine(x(w.centerX()), y(w.top), x(w.centerX()), y(w.bottom), p)
            cv.drawLine(x(w.left), y(w.centerY()), x(w.right), y(w.centerY()), p)
        }
        p.color = 0xFF4A2C22.toInt(); cv.drawRect(x(door.left), y(door.top), x(door.right), y(door.bottom), p)
        p.color = 0xFFC8A050.toInt(); cv.drawCircle(x(door.right - 0.008f), y(door.centerY() + 0.02f), 5f, p)

        // Mega tree: a pole with its strings pulled out from the top to a ring of stakes at the
        // base (the same layout the lights use), then bushes and arch frames.
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f; p.color = 0xFF2A3038.toInt()
        val wires = treeModel.copy(nodes = 2, fromBottom = false).layout(
            MapPoint(TREE_X - TREE_HALF_W, TREE_TOP), MapPoint(TREE_X + TREE_HALF_W, TREE_BASE), W / H.toFloat(),
        )
        for (k in 0 until treeModel.strings) {
            val (top, base) = wires[2 * k] to wires[2 * k + 1]
            cv.drawLine(x(top.x), y(top.y), x(base.x), y(base.y), p)
        }
        cv.drawOval(
            x(TREE_X - TREE_HALF_W), y(TREE_BASE - (TREE_BASE - TREE_TOP) * PropModel.TREE_TILT),
            x(TREE_X + TREE_HALF_W), y(TREE_BASE), p,
        )
        p.style = Paint.Style.FILL; p.color = 0xFF4A505A.toInt()
        cv.drawRect(x(TREE_X - 0.003f), y(TREE_TOP - 0.025f), x(TREE_X + 0.003f), y(TREE_BASE - 0.03f), p)
        p.color = 0xFF1A3324.toInt()
        for (b in bushes) cv.drawOval(x(b.x - ftX(2.5f)), y(b.y - 0.03f), x(b.x + ftX(2.5f)), y(b.y + 0.025f), p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 4f; p.color = 0xFF5A6070.toInt()
        for (a in arches) cv.drawArc(x(a - 0.032f), y(ARCH_BASE - 0.13f), x(a + 0.032f), y(ARCH_BASE + 0.13f), 180f, 180f, false, p)
        p.style = Paint.Style.FILL
        return bmp
    }
}
