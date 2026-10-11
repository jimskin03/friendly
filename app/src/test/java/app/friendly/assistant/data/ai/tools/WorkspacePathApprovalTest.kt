package app.friendly.assistant.data.ai.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspacePathApprovalTest {
    @Test fun traversalCannotBeAutoApproved() {
        assertTrue(rootfsWriteNeedsApproval("/workspace/../etc/hostname") { false })
        assertTrue(rootfsWriteNeedsApproval("/workspace/a/../../etc/hostname") { false })
        assertTrue(rootfsWriteNeedsApproval("/skills/../tmp/link") { false })
    }

    @Test fun symlinkedPathNeedsApprovalWhileRegularWritablePathDoesNot() {
        assertTrue(rootfsWriteNeedsApproval("/workspace/link/secret") { true })
        assertFalse(rootfsWriteNeedsApproval("/workspace/notes.txt") { false })
        assertFalse(rootfsWriteNeedsApproval("/tmp/notes.txt") { false })
        assertTrue(rootfsWriteNeedsApproval("/etc/hostname") { false })
    }
}
