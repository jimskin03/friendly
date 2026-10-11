package me.rerere.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WorkspaceFileSystemSafetyTest {
    @get:Rule val temp = TemporaryFolder()
    private val fs = WorkspaceFileSystem()

    @Test fun rootAliasesCannotDeleteOrReplaceTheWorkspace() {
        val root = temp.newFolder("workspace")
        File(root, "keep.txt").writeText("keep")
        for (alias in listOf(".", "/", "a/..", "")) {
            assertThrows(IllegalArgumentException::class.java) { fs.delete(root, alias, true) }
            assertThrows(IllegalArgumentException::class.java) { fs.move(root, "keep.txt", alias, true) }
            assertEquals("keep", File(root, "keep.txt").readText())
        }
        assertTrue(root.isDirectory)
    }

    @Test fun movingToSelfDoesNotDeleteTheFile() {
        val root = temp.newFolder("workspace")
        val original = File(root, "keep.txt").apply { writeText("keep") }
        fs.move(root, "keep.txt", "./keep.txt", true)
        assertEquals("keep", original.readText())
    }

    @Test fun movingIntoSelfOrReplacingAnAncestorLeavesBothIntact() {
        val root = temp.newFolder("workspace")
        val directory = File(root, "parent").apply { mkdir() }
        val original = File(directory, "keep.txt").apply { writeText("keep") }
        assertThrows(IllegalArgumentException::class.java) {
            fs.move(root, "parent", "parent/child", true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            fs.move(root, "parent/keep.txt", "parent", true)
        }
        assertEquals("keep", original.readText())
    }

    @Test fun overwriteReplacesExistingTargetAfterSourceIsValidated() {
        val root = temp.newFolder("workspace")
        File(root, "from.txt").writeText("new")
        File(root, "to.txt").writeText("old")
        fs.move(root, "from.txt", "to.txt", true)
        assertEquals("new", File(root, "to.txt").readText())
        assertTrue(!File(root, "from.txt").exists())
    }
}
