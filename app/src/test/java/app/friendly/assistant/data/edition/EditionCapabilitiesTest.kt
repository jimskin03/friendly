package app.friendly.assistant.data.edition

import app.friendly.assistant.data.ai.tools.local.LocalToolOption
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class EditionCapabilitiesTest {
    @Test
    fun playExcludesUnreviewedExecutionRoutesEvenWithImportedSettings() {
        val policy = EditionCapabilities.forEdition(isPlayBuild = true)
        assertEquals("play", policy.edition)
        assertFalse(policy.phoneAutomation)
        assertFalse(policy.overlaysAndCalls)
        assertFalse(policy.usageStats)
        assertFalse(policy.desktopExecution)
        assertFalse(policy.javascriptExecution)
        assertFalse(policy.workspaceExecution)
        assertFalse(policy.mcpExecution)
        assertFalse(policy.githubUpdates)
        for (option in listOf(
            LocalToolOption.PhoneAutomation,
            LocalToolOption.ScreenTime,
            LocalToolOption.DesktopControl,
            LocalToolOption.JavascriptEngine,
        )) {
            assertFalse("Play must reject imported $option", policy.allowsLocalTool(option))
        }
        assertTrue(policy.desktopViewer)
        assertTrue(policy.allowsLocalTool(LocalToolOption.TimeInfo))
        assertTrue(policy.allowsLocalTool(LocalToolOption.ChartDisplay))
    }

    @Test
    fun nightlyRetainsSupportedFeatures() {
        val policy = EditionCapabilities.forEdition(isPlayBuild = false)
        assertEquals("nightly", policy.edition)
        assertTrue(policy.phoneAutomation)
        assertTrue(policy.desktopExecution)
        assertTrue(policy.javascriptExecution)
        assertTrue(policy.workspaceExecution)
        assertTrue(policy.mcpExecution)
        assertTrue(policy.githubUpdates)
        assertTrue(policy.allowsLocalTool(LocalToolOption.PhoneAutomation))
        assertTrue(policy.allowsLocalTool(LocalToolOption.DesktopControl))
    }
}
