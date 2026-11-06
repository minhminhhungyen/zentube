package vn.zenvid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.zenvid.support.Updates

class UpdatesTest {
    @Test fun versionCompare() {
        assertTrue(Updates.isNewer("1.10.0", "1.9.9"))
        assertTrue(Updates.isNewer("2.0", "1.7.0"))
        assertTrue(Updates.isNewer("1.7.1", "1.7.0"))
        assertFalse(Updates.isNewer("1.7.0", "1.7.0"))
        assertFalse(Updates.isNewer("1.6.9", "1.7.0"))
        assertFalse(Updates.isNewer("1.7.0-beta", "1.7.0"))
    }

    @Test fun parsesGithubRelease() {
        val json = """{"tag_name":"v1.8.0","name":"1.8.0","body":"Sửa lỗi phát video","html_url":"https://github.com/a/b/releases/tag/v1.8.0",
            "draft":false,"prerelease":false,"assets":[{"browser_download_url":"https://github.com/a/b/releases/download/v1.8.0/zenvid-1.8.0.apk"}]}"""
        val u = Updates.parse(json, "1.7.0")!!
        assertEquals("1.8.0", u.version)
        assertEquals("https://github.com/a/b/releases/download/v1.8.0/zenvid-1.8.0.apk", u.apkUrl)
        assertNull(Updates.parse(json, "1.8.0"))
        assertNull(Updates.parse(json.replace("\"prerelease\":false", "\"prerelease\":true"), "1.7.0"))
    }
}
