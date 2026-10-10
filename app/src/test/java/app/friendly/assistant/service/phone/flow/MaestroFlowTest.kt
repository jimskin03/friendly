package app.friendly.assistant.service.phone.flow

import app.friendly.assistant.service.phone.ElementSelector
import app.friendly.assistant.service.phone.PhoneElementMatcher
import app.friendly.assistant.service.phone.ScreenNodeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class MaestroFlowTest {

    @Test
    fun testParseSimpleYamlFlow() {
        val yaml = """
            appId: com.google.android.apps.maps
            ---
            - launchApp: "Google Maps"
            - tapOn: "Search here"
            - inputText: "Coffee"
            - back
            - sleep: 500
        """.trimIndent()

        val commands = MaestroFlowParser.parse(yaml)
        assertEquals(5, commands.size)

        assertTrue(commands[0] is MaestroCommand.LaunchApp)
        assertEquals("Google Maps", (commands[0] as MaestroCommand.LaunchApp).appName)

        assertTrue(commands[1] is MaestroCommand.TapOn)
        assertEquals("Search here", (commands[1] as MaestroCommand.TapOn).selector?.query)

        assertTrue(commands[2] is MaestroCommand.InputText)
        assertEquals("Coffee", (commands[2] as MaestroCommand.InputText).text)

        assertTrue(commands[3] is MaestroCommand.PressKey)
        assertEquals("back", (commands[3] as MaestroCommand.PressKey).key)

        assertTrue(commands[4] is MaestroCommand.Sleep)
        assertEquals(500L, (commands[4] as MaestroCommand.Sleep).durationMs)
    }

    @Test
    fun testParseAdvancedCommandsAndOptions() {
        val yaml = """
            - launchApp:
                appName: "com.grabtaxi.passenger"
                optional: true
            - tapOn:
                id: "com.grab:id/btn_book"
                index: 1
                optional: false
            - scrollUntilVisible:
                element: "Confirm Pickup"
                direction: "down"
                maxSwipes: 8
            - assertVisible:
                text: "Ride details"
                timeout: 4000
            - assertNotVisible:
                text: "Loading..."
            - repeat:
                times: 2
                commands:
                  - scroll: "down"
                  - sleep: 300
        """.trimIndent()

        val commands = MaestroFlowParser.parse(yaml)
        assertEquals(6, commands.size)

        val launch = commands[0] as MaestroCommand.LaunchApp
        assertEquals("com.grabtaxi.passenger", launch.appName)
        assertTrue(launch.optional)

        val tap = commands[1] as MaestroCommand.TapOn
        assertEquals("com.grab:id/btn_book", tap.selector?.viewId)
        assertEquals(1, tap.selector?.index)
        assertFalse(tap.optional)

        val scrollUntil = commands[2] as MaestroCommand.ScrollUntilVisible
        assertEquals("Confirm Pickup", scrollUntil.selector.query)
        assertEquals("down", scrollUntil.direction)
        assertEquals(8, scrollUntil.maxSwipes)

        val assertVis = commands[3] as MaestroCommand.AssertVisible
        assertEquals("Ride details", assertVis.selector.query)
        assertEquals(4000L, assertVis.timeoutMs)

        val assertNotVis = commands[4] as MaestroCommand.AssertNotVisible
        assertEquals("Loading...", assertNotVis.selector.query)

        val repeatCmd = commands[5] as MaestroCommand.Repeat
        assertEquals(2, repeatCmd.times)
        assertEquals(2, repeatCmd.commands.size)
        assertTrue(repeatCmd.commands[0] is MaestroCommand.Scroll)
        assertTrue(repeatCmd.commands[1] is MaestroCommand.Sleep)
    }

    @Test
    fun testVariableInterpolation() {
        val yaml = """
            - launchApp: "${'$'}{APP_NAME}"
            - tapOn: "${'$'}{TARGET_BUTTON}"
            - inputText: "${'$'}{SEARCH_TERM}"
        """.trimIndent()

        val params = mapOf(
            "APP_NAME" to "Maps",
            "TARGET_BUTTON" to "Search Here",
            "SEARCH_TERM" to "Starbucks Coffee",
        )

        val commands = MaestroFlowParser.parse(yaml, params)
        assertEquals(3, commands.size)

        assertEquals("Maps", (commands[0] as MaestroCommand.LaunchApp).appName)
        assertEquals("Search Here", (commands[1] as MaestroCommand.TapOn).selector?.query)
        assertEquals("Starbucks Coffee", (commands[2] as MaestroCommand.InputText).text)
    }

    @Test
    fun testParseJsonFlow() {
        val json = """
            [
                { "launchApp": "Settings" },
                { "tapOn": { "text": "Network & internet", "optional": true } },
                { "inputText": { "text": "Wi-Fi", "clearFirst": true } }
            ]
        """.trimIndent()

        val commands = MaestroFlowParser.parse(json)
        assertEquals(3, commands.size)
        assertEquals("Settings", (commands[0] as MaestroCommand.LaunchApp).appName)

        val tap = commands[1] as MaestroCommand.TapOn
        assertEquals("Network & internet", tap.selector?.query)
        assertTrue(tap.optional)

        val input = commands[2] as MaestroCommand.InputText
        assertEquals("Wi-Fi", input.text)
        assertTrue(input.clearFirst)
    }

    @Test
    fun testPhoneElementMatcherExactVsContains() {
        val node1 = createMockNode(id = 1, text = "Search", viewId = "btn_search")
        val node2 = createMockNode(id = 2, text = "Search here for restaurants", viewId = "input_search")

        val selector = ElementSelector(query = "Search")
        val match = PhoneElementMatcher.findBestMatch(listOf(node2, node1), selector)

        // Exact match (node1) should score higher than substring match (node2)
        assertNotNull(match)
        assertEquals(1, match?.id)
    }

    @Test
    fun testPhoneElementMatcherResourceIdMatch() {
        val node1 = createMockNode(id = 1, text = "", viewId = "com.google.android.apps.maps:id/search_box")
        val node2 = createMockNode(id = 2, text = "Other", viewId = "com.google.android.apps.maps:id/other_btn")

        // Matches by short viewId
        val match = PhoneElementMatcher.findBestMatch(listOf(node2, node1), ElementSelector(viewId = "search_box"))
        assertNotNull(match)
        assertEquals(1, match?.id)

        // Matches by query matching short viewId
        val match2 = PhoneElementMatcher.findBestMatch(listOf(node2, node1), ElementSelector(query = "search_box"))
        assertNotNull(match2)
        assertEquals(1, match2?.id)
    }

    @Test
    fun testPhoneElementMatcherIndex() {
        val node1 = createMockNode(id = 1, text = "Add to cart")
        val node2 = createMockNode(id = 2, text = "Add to cart")

        val first = PhoneElementMatcher.findBestMatch(listOf(node1, node2), ElementSelector(query = "Add to cart", index = 0))
        val second = PhoneElementMatcher.findBestMatch(listOf(node1, node2), ElementSelector(query = "Add to cart", index = 1))

        assertEquals(1, first?.id)
        assertEquals(2, second?.id)
    }

    @Test
    fun testPhoneElementMatcherClickableAndEnabledFilter() {
        val disabledNode = createMockNode(id = 1, text = "Submit", enabled = false, clickable = true)
        val enabledNode = createMockNode(id = 2, text = "Submit", enabled = true, clickable = true)

        val match = PhoneElementMatcher.findBestMatch(
            listOf(disabledNode, enabledNode),
            ElementSelector(query = "Submit", enabledOnly = true)
        )

        assertEquals(2, match?.id)
    }

    @Test
    fun testRepositoryOperations() = kotlinx.coroutines.runBlocking {
        val tempDir = Files.createTempDirectory("maestro_flows_test").toFile()
        try {
            val repository = MaestroFlowRepository(baseDir = tempDir)
            val initialList = repository.listFlows()
            assertEquals(0, initialList.size)

            val yamlContent = """
                - launchApp: "Settings"
                - tapOn: "Wi-Fi"
            """.trimIndent()

            val saved = repository.saveFlow("wifi_toggle", yamlContent, "Toggles Wi-Fi")
            assertTrue(saved)

            val flows = repository.listFlows()
            assertEquals(1, flows.size)
            assertEquals("wifi_toggle", flows[0].name)
            assertEquals("Toggles Wi-Fi", flows[0].description)
            assertEquals(2, flows[0].stepCount)

            val loaded = repository.getFlow("wifi_toggle")
            assertNotNull(loaded)
            assertTrue(loaded?.content?.contains("launchApp: \"Settings\"") == true)

            val deleted = repository.deleteFlow("wifi_toggle")
            assertTrue(deleted)
            assertNull(repository.getFlow("wifi_toggle"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun createMockNode(
        id: Int,
        text: String = "",
        description: String = "",
        viewId: String = "",
        className: String = "View",
        clickable: Boolean = true,
        editable: Boolean = false,
        scrollable: Boolean = false,
        enabled: Boolean = true,
        checked: Boolean = false,
        focused: Boolean = false,
        selected: Boolean = false,
    ): ScreenNodeInfo {
        return ScreenNodeInfo(
            id = id,
            text = text,
            description = description,
            viewId = viewId,
            className = className,
            clickable = clickable,
            editable = editable,
            scrollable = scrollable,
            left = 10,
            top = 10 + (id * 50),
            right = 100,
            bottom = 50 + (id * 50),
            centerX = 55,
            centerY = 30 + (id * 50),
            enabled = enabled,
            checked = checked,
            focused = focused,
            selected = selected,
        )
    }
}
