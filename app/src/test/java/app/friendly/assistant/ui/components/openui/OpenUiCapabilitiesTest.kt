package app.friendly.assistant.ui.components.openui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenUiCapabilitiesTest {
    @Test
    fun playEditionExcludesPhoneAutomation() {
        val capabilities = OpenUiCapabilities.forEdition(isPlayBuild = true)

        assertEquals("play", capabilities.edition)
        assertFalse(capabilities.phoneAutomation)
        assertTrue(capabilities.desktopControl)
        assertFalse(capabilities.contentReporting)

        val policy = app.friendly.assistant.data.edition.EditionCapabilities.forEdition(isPlayBuild = true)
        assertTrue("Play keeps JavaScript", policy.javascriptExecution)
        assertTrue("Play keeps workspace/terminal and skills", policy.workspaceExecution)
        assertTrue("Play keeps MCP", policy.mcpExecution)
        assertFalse("Play blocks screen time", policy.usageStats)
    }

    @Test
    fun nightlyEditionKeepsPhoneAutomation() {
        val capabilities = OpenUiCapabilities.forEdition(isPlayBuild = false)

        assertEquals("nightly", capabilities.edition)
        assertTrue(capabilities.phoneAutomation)
        assertTrue(capabilities.desktopControl)
    }
}