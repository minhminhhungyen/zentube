package vn.zenvid

import org.junit.Assert.assertEquals
import org.junit.Test
import vn.zenvid.data.VietnameseText

class VietnameseTextTest {
    private fun n(s: String) = VietnameseText.normalize(s)

    @Test fun normalizes() {
        assertEquals(""""simpleText":"4.2M subscribers"""", n(""""simpleText":"4,2 Tr người đăng ký""""))
        assertEquals(""""x":"7.01M subscribers"""", n(""""x":"7,01 Tr người đăng ký""""))
        assertEquals(""""x":"523K subscribers"""", n(""""x":"523 N người đăng ký""""))
        assertEquals(""""x":"1234 subscribers"""", n(""""x":"1.234 người đăng ký""""))
        assertEquals(""""content":"7.01M subscribers"""", n("\"content\":\"7,01\u00A0Tr người đăng ký\""))
        assertEquals(""""label":"7.01M subscribers"""", n(""""label":"7,01 triệu người đăng ký""""))
        assertEquals(""""simpleText":"3 days ago"""", n(""""simpleText":"3 ngày trước""""))
        assertEquals(""""simpleText":"3 days ago"""", n("\"simpleText\":\"3\u00A0ngày trước\""))
        assertEquals(""""simpleText":"1 year ago"""", n(""""simpleText":"1 năm trước""""))
        assertEquals(""""text":"Streamed 20 hours ago"""", n(""""text":"Phát trực tiếp 20 giờ trước""""))
        // Tiêu đề video có cụm từ tương tự thì giữ nguyên
        val title = """"text":"Kỷ niệm 10 năm trước của tôi""""
        assertEquals(title, n(title))
    }
}
