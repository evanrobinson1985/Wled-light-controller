package com.wledcontroller.app.data

import com.wledcontroller.app.house.HouseMap
import com.wledcontroller.app.house.LightRun
import com.wledcontroller.app.house.MapPoint
import com.wledcontroller.app.house.ModelKind
import com.wledcontroller.app.house.PropModel
import com.wledcontroller.app.house.PropType
import com.wledcontroller.app.house.Submodel
import com.wledcontroller.app.house.XlightsModels
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

class LibraryTest {

    private fun tempDir(): File = Files.createTempDirectory("lib").toFile()

    private val roof = LightRun("r1", "Roof", "dev1", 0, 50, points = listOf(MapPoint(0.1f, 0.3f), MapPoint(0.5f, 0.2f), MapPoint(0.9f, 0.3f)))
    private val matrix = LightRun(
        "m1", "Panel", "dev2", 0, 16 * 20,
        points = listOf(MapPoint(0.2f, 0.5f), MapPoint(0.4f, 0.8f)),
        prop = PropType.MATRIX, model = PropModel(ModelKind.MATRIX, strings = 16, nodes = 20),
        submodels = listOf(Submodel("Left half", listOf(0 until 160))), mirrored = true,
    )

    private fun mapWith(dir: File): HouseMap {
        val photo = File(dir, "house_1.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        return HouseMap(photoPath = photo.absolutePath, aspect = 1.5f, runs = listOf(roof, matrix), scaleA = MapPoint(0f, 0.9f), scaleB = MapPoint(1f, 0.9f), scaleCm = 1500f)
    }

    @Test
    fun layoutSavesAndOpensWithItsPhoto() = runBlocking {
        val dir = tempDir()
        val lib = Library(dir)
        val map = mapWith(dir)
        val saved = lib.saveLayout("Our house", map)
        assertEquals(1, lib.layouts.value.size)
        assertNull("the kept map doesn't point at the live photo", saved.map.photoPath)

        // The live map moves on (a new photo); opening the saved one brings everything back.
        File(map.photoPath!!).delete()
        val opened = lib.openLayout(lib.layouts.value.single())
        assertEquals(map.runs, opened.runs)
        assertEquals(map.scaleCm, opened.scaleCm)
        val photo = File(opened.photoPath!!)
        assertTrue(photo.name.startsWith("house_"))
        assertEquals(listOf<Byte>(1, 2, 3, 4), photo.readBytes().toList())

        // Saving under the same name updates it rather than adding another.
        lib.saveLayout("our house", opened.copy(runs = listOf(roof)))
        assertEquals(1, lib.layouts.value.size)
        assertEquals(1, lib.layouts.value.single().map.runs.size)

        // A new Library on the same folder (the app restarting, or after an update) finds it.
        assertEquals(1, Library(dir).layouts.value.size)
    }

    @Test
    fun layoutGoesToAFileAndBack() = runBlocking {
        val a = tempDir()
        val libA = Library(a)
        val saved = libA.saveLayout("Display 2026", mapWith(a))
        val out = ByteArrayOutputStream()
        libA.exportLayout(saved, out)

        val b = tempDir()
        val libB = Library(b)
        val imported = libB.importLayout(out.toByteArray().inputStream())
        assertEquals("Display 2026", imported.name)
        assertEquals(saved.map.runs, imported.map.runs)
        val opened = libB.openLayout(imported)
        assertEquals(4, File(opened.photoPath!!).length())
        // Importing it again keeps both.
        libB.importLayout(out.toByteArray().inputStream())
        assertEquals(listOf("Display 2026", "Display 2026 (2)"), libB.layouts.value.map { it.name }.sorted())
    }

    @Test
    fun deletingALayoutRemovesItsPhotoToo() = runBlocking {
        val dir = tempDir()
        val lib = Library(dir)
        val saved = lib.saveLayout("Test", mapWith(dir))
        lib.deleteLayout(saved.id)
        assertTrue(lib.layouts.value.isEmpty())
        assertTrue(File(dir, Library.LAYOUTS).listFiles()!!.isEmpty())
    }

    @Test
    fun boxedModelIsKeptExactly() {
        val map = HouseMap(aspect = 1.5f, runs = listOf(matrix))
        val m = SavedModel.fromRun(matrix, map)!!
        assertEquals(matrix.model, m.model)
        assertEquals(320, m.ledCount)
        assertTrue(m.mirrored)
        val run = m.toRun("new", "devX", 100)
        assertEquals(matrix.model, run.model)
        assertEquals(matrix.submodels, run.submodels)
        assertEquals(100, run.startLed)
        assertTrue(run.points.isEmpty())
    }

    @Test
    fun tracedRunBecomesACustomGridInTheSameShape() {
        val map = mapWith(tempDir())
        val m = SavedModel.fromRun(roof, map)!!
        assertEquals(ModelKind.CUSTOM, m.model.kind)
        assertEquals(50, m.ledCount)
        assertEquals("one LED per cell", 50, m.model.customCells.toSet().size)
        assertTrue(m.model.customCells.all { it >= 0 })
        // Real size from the map's scale: the roof spans 0.8 of the 1500 cm wide picture.
        assertEquals(1200f, m.widthCm!!, 30f)
        // Laid out again, it runs left to right and peaks in the middle, like the roofline.
        val pos = m.model.layout(MapPoint(0f, 0f), MapPoint(1f, 1f), 1f)
        assertTrue(pos.first().x < 0.05f && pos.last().x > 0.95f)
        assertTrue(pos[25].y < pos.first().y && pos[25].y < pos.last().y)
    }

    @Test
    fun unplacedRunHasNoShapeToKeep() {
        val run = LightRun("x", "Bush", "dev", 0, 10)
        assertNull(SavedModel.fromRun(run, HouseMap()))
    }

    @Test
    fun crowdedLedsStillGetACellEach() {
        // 400 LEDs bunched on a short straight line.
        val pts = List(400) { MapPoint(0.5f + it * 1e-5f, 0.5f) }
        val g = SavedModel.customGrid(pts, 1.5f)
        assertEquals(400, g.customCells.toSet().size)
        assertTrue(g.customCells.all { it in 0 until g.customW * g.customH })
    }

    @Test
    fun modelExportsToXlightsAndReadsBack() {
        val map = mapWith(tempDir())
        val m = SavedModel.fromRun(roof, map)!!.copy(submodels = listOf(Submodel("Left", listOf(0 until 10, 20 until 25))))
        val xml = SavedModel.toXmodel(m)
        val x = XlightsModels.parseXmodel(xml.byteInputStream())
        assertEquals(50, x.ledCount)
        assertEquals(m.model.customCells, x.model!!.customCells)
        assertEquals(listOf(Submodel("Left", listOf(0..9, 20..24))), x.submodels)
        assertEquals(m.widthCm!!, x.widthCm!!, 0.1f)

        // A matrix (mirrored) goes out as a grid with every LED on it.
        val mm = SavedModel.fromRun(matrix, HouseMap(aspect = 1.5f, runs = listOf(matrix)))!!
        val xm = XlightsModels.parseXmodel(SavedModel.toXmodel(mm).byteInputStream())
        assertEquals(320, xm.ledCount)
        assertEquals(320, xm.model!!.customCells.toSet().size)
    }

    @Test
    fun modelFilesRoundTrip() = runBlocking {
        val lib = Library(tempDir())
        val m = SavedModel.fromRun(matrix, HouseMap(runs = listOf(matrix)))!!
        val back = lib.parseModelFile(lib.modelFile(m))
        assertEquals(m.model, back.model)
        assertEquals(m.submodels, back.submodels)
        assertEquals(m.name, back.name)
        // Saving two with one name keeps both apart.
        lib.saveModel(back); lib.saveModel(back.copy(id = "other"))
        assertEquals(listOf("Panel", "Panel (2)"), lib.models.value.map { it.name })
        // An xLights file comes in too.
        val fromX = lib.parseModelFile(SavedModel.toXmodel(m))
        assertNotNull(fromX.model)
        assertEquals(320, fromX.ledCount)
    }
}
