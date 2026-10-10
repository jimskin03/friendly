package app.friendly.assistant.ui.pages.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebAppWebSettingsTest {
    @Test
    fun keepsDefaultPhoneWebViewUserAgent() {
        val ua = "Mozilla/5.0 (Linux; Android 15; Pixel 8; wv) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Version/4.0 Chrome/129.0.0.0 Mobile Safari/537.36"
        assertNull(mobileUserAgent(ua))
    }

    @Test
    fun addsMobileTokenToDesktopStyleUserAgent() {
        val ua = "Mozilla/5.0 (Linux; Android 15; SM-X910; wv) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Version/4.0 Chrome/129.0.0.0 Safari/537.36"
        assertEquals(
            "Mozilla/5.0 (Linux; Android 15; SM-X910; wv) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Version/4.0 Chrome/129.0.0.0 Mobile Safari/537.36",
            mobileUserAgent(ua),
        )
    }

    @Test
    fun appendsMobileTokenWhenNoSafariToken() {
        assertEquals("CustomAgent/1.0 Mobile", mobileUserAgent("CustomAgent/1.0"))
    }

    @Test
    fun leavesBlankUserAgentAlone() {
        assertNull(mobileUserAgent(null))
        assertNull(mobileUserAgent(""))
    }
}
