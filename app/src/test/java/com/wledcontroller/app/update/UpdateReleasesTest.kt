package com.wledcontroller.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateReleasesTest {
    private fun release(tag: String, vararg assets: String, draft: Boolean = false) = """
        {"tag_name":"$tag","name":"Build","draft":$draft,"assets":[${assets.joinToString(",") { """{"name":"$it","size":123,"browser_download_url":"https://example.com/$tag/$it"}""" }}]}
    """.trimIndent()

    @Test fun readsTheBuildNumberFromTheTag() {
        assertEquals(123, UpdateReleases.buildOf("v1.0.123"))
        assertEquals(7, UpdateReleases.buildOf("1.0.7"))
        assertNull(UpdateReleases.buildOf("nightly"))
    }

    @Test fun picksTheNewestBuildWithThisAppsApk() {
        val json = "[" + listOf(
            release("v1.0.130", "WLED-Watch.apk"),
            release("v1.0.128", "WLED-Controller.apk", "WLED-Watch.apk"),
            release("v1.0.131", "WLED-Controller.apk", draft = true),
            release("v1.0.125", "WLED-Controller.apk"),
        ).joinToString(",") + "]"
        val phone = UpdateReleases.newest(json, "WLED-Controller.apk", installed = 122)!!
        assertEquals(128, phone.build)
        assertEquals("https://example.com/v1.0.128/WLED-Controller.apk", phone.apkUrl)
        assertEquals(130, UpdateReleases.newest(json, "WLED-Watch.apk", installed = 122)!!.build)
        // Nothing newer than what's installed.
        assertNull(UpdateReleases.newest(json, "WLED-Controller.apk", installed = 128))
        assertNull(UpdateReleases.newest("not json", "WLED-Controller.apk", installed = 1))
    }
}
