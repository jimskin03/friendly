package app.friendly.assistant.data.ai.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class McpToolSanitizationTest {

    @Test
    fun standardServerAndToolNameFormattedCorrectly() {
        val name = sanitizeMcpToolName("local", "read_file")
        assertEquals("mcp__local__read_file", name)
    }

    @Test
    fun hyphensAndSpacesAreSanitizedToValidIdentifiers() {
        val name = sanitizeMcpToolName("chatgpt-local-coder", "execute command")
        assertEquals("mcp__chatgpt_local_coder__execute_command", name)
        assertTrue(name.matches(Regex("^[a-zA-Z_][a-zA-Z0-9_]*$")))
    }

    @Test
    fun specialCharactersInServerOrToolNameAreCleaned() {
        val name = sanitizeMcpToolName("My Server @ Home!", "tool.v2#run")
        assertTrue(name.matches(Regex("^[a-zA-Z_][a-zA-Z0-9_]*$")))
    }

    @Test
    fun excessivelyLongToolNameIsTruncatedTo64CharsOrFewer() {
        val longServer = "a".repeat(40)
        val longTool = "b".repeat(40)
        val name = sanitizeMcpToolName(longServer, longTool)
        assertTrue("Name length was ${name.length}, expected <= 64", name.length <= 64)
        assertTrue(name.matches(Regex("^[a-zA-Z_][a-zA-Z0-9_]*$")))
    }
}
