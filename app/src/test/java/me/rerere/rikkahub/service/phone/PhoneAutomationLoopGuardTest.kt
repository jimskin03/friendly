package me.rerere.rikkahub.service.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PhoneAutomationLoopGuardTest {

    @Before
    fun setUp() {
        PhoneAutomationLoopGuard.reset()
    }

    @Test
    fun warnsAfterThreeIdenticalActionsOnUnchangedScreen() {
        val fp = "com.example.app#123"
        assertNull(PhoneAutomationLoopGuard.observe("phone_click", "label:search", fp))
        assertNull(PhoneAutomationLoopGuard.observe("phone_click", "label:search", fp))
        assertEquals(
            PhoneAutomationLoopGuard.WARNING,
            PhoneAutomationLoopGuard.observe("phone_click", "label:search", fp),
        )
    }

    @Test
    fun doesNotWarnWhenFingerprintChanges() {
        assertNull(PhoneAutomationLoopGuard.observe("phone_swipe", "dir:up", "pkg#1"))
        assertNull(PhoneAutomationLoopGuard.observe("phone_swipe", "dir:up", "pkg#1"))
        // Scroll changed the screen
        assertNull(PhoneAutomationLoopGuard.observe("phone_swipe", "dir:up", "pkg#2"))
        assertNull(PhoneAutomationLoopGuard.observe("phone_swipe", "dir:up", "pkg#2"))
        assertEquals(
            PhoneAutomationLoopGuard.WARNING,
            PhoneAutomationLoopGuard.observe("phone_swipe", "dir:up", "pkg#2"),
        )
    }

    @Test
    fun launchAppResetsCounter() {
        val fp = "com.example.app#9"
        assertNull(PhoneAutomationLoopGuard.observe("phone_click", "query:ok", fp))
        assertNull(PhoneAutomationLoopGuard.observe("phone_click", "query:ok", fp))
        assertNull(PhoneAutomationLoopGuard.observe("phone_launch_app", "flow:x", fp))
        assertNull(PhoneAutomationLoopGuard.observe("phone_click", "query:ok", fp))
        assertNull(PhoneAutomationLoopGuard.observe("phone_click", "query:ok", fp))
        assertEquals(
            PhoneAutomationLoopGuard.WARNING,
            PhoneAutomationLoopGuard.observe("phone_click", "query:ok", fp),
        )
    }


    @Test
    fun bringAppToFrontResetsCounter() {
        val fp = "com.example.app#9"
        assertNull(PhoneAutomationLoopGuard.observe("phone_click", "query:ok", fp))
        assertNull(PhoneAutomationLoopGuard.observe("phone_click", "query:ok", fp))
        assertNull(PhoneAutomationLoopGuard.observe("phone_bring_app_to_front", "pkg", fp))
        assertNull(PhoneAutomationLoopGuard.observe("phone_click", "query:ok", fp))
        assertNull(PhoneAutomationLoopGuard.observe("phone_click", "query:ok", fp))
        assertEquals(
            PhoneAutomationLoopGuard.WARNING,
            PhoneAutomationLoopGuard.observe("phone_click", "query:ok", fp),
        )
    }

    @Test
    fun inspectDoesNotCountTowardStuck() {
        val fp = "com.example.app#5"
        assertNull(PhoneAutomationLoopGuard.observe("phone_inspect_screen", "inspect", fp))
        assertNull(PhoneAutomationLoopGuard.observe("phone_inspect_screen", "inspect", fp))
        assertNull(PhoneAutomationLoopGuard.observe("phone_inspect_screen", "inspect", fp))
        assertNull(PhoneAutomationLoopGuard.observe("phone_click", "label:a", fp))
        assertNull(PhoneAutomationLoopGuard.observe("phone_click", "label:a", fp))
        assertEquals(
            PhoneAutomationLoopGuard.WARNING,
            PhoneAutomationLoopGuard.observe("phone_click", "label:a", fp),
        )
    }

    @Test
    fun fingerprintUsesPackageAndVisibleContent() {
        val a = ScreenInspectionResult(
            packageName = "com.a",
            windowTitle = "",
            interactiveElements = listOf(
                ScreenNodeInfo(
                    id = 0, text = "Hello", description = "", viewId = "btn",
                    className = "Button", clickable = true, editable = false, scrollable = false,
                    left = 0, top = 0, right = 10, bottom = 10, centerX = 5, centerY = 5,
                ),
            ),
            textElements = emptyList(),
        )
        val b = a.copy(
            interactiveElements = a.interactiveElements.map { it.copy(text = "World") },
        )
        assert(PhoneAutomationLoopGuard.fingerprint(a) != PhoneAutomationLoopGuard.fingerprint(b))
        assert(PhoneAutomationLoopGuard.fingerprint(a).startsWith("com.a#"))
    }
}
