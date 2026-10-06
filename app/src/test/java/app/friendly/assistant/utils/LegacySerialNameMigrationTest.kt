package app.friendly.assistant.utils

import app.friendly.assistant.data.model.Avatar
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacySerialNameMigrationTest {
    @Test
    fun legacyAvatarClassNameDecodes() {
        val json = """{"type":"me.rerere.rikkahub.data.model.Avatar.Dummy"}"""
        val avatar = JsonInstant.decodeStored<Avatar>(json)
        assertTrue(avatar is Avatar.Dummy)
    }

    @Test
    fun currentAvatarClassNameDecodes() {
        val json = """{"type":"app.friendly.assistant.data.model.Avatar.Dummy"}"""
        val avatar = JsonInstant.decodeStored<Avatar>(json)
        assertTrue(avatar is Avatar.Dummy)
    }
}
