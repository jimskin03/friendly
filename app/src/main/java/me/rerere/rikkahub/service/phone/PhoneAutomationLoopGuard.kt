package me.rerere.rikkahub.service.phone

/**
 * Detects repeated identical phone-tool actions on an unchanged screen and
 * surfaces a warning to the model. Never performs navigation (no auto-BACK).
 *
 * Signature is tool name + normalized target (label / view id / rough position),
 * not node_id (those renumber each inspect). Screen fingerprint is package plus
 * a hash of visible text and view ids. Scrolls that change the fingerprint do
 * not count as stuck. Maestro flow-internal repeats bypass this guard because
 * they do not go through [me.rerere.rikkahub.data.ai.tools.local.withPhoneAutomationTracking].
 */
internal object PhoneAutomationLoopGuard {
    private const val STUCK_THRESHOLD = 3
    const val WARNING =
        "screen unchanged after 3 identical actions; try another element, scroll, or re-inspect"

    private val trackedTools = setOf(
        "phone_click",
        "phone_swipe",
        "phone_type_text",
        "phone_press_key",
        "phone_assert_visible",
        "phone_scroll_until_visible",
        "phone_run_flow",
    )

    private data class ActionKey(val tool: String, val target: String)

    @Volatile
    private var lastFingerprint: String? = null

    @Volatile
    private var lastAction: ActionKey? = null

    @Volatile
    private var identicalCount: Int = 0

    @Synchronized
    fun reset() {
        lastFingerprint = null
        lastAction = null
        identicalCount = 0
    }

    /**
     * Records a completed phone-tool call. Returns [WARNING] when the same
     * action has hit an unchanged fingerprint [STUCK_THRESHOLD] times; else null.
     */
    @Synchronized
    fun observe(
        toolName: String,
        normalizedTarget: String,
        fingerprint: String,
    ): String? {
        if (toolName == "phone_launch_app") {
            reset()
            lastFingerprint = fingerprint.ifBlank { lastFingerprint }
            return null
        }
        if (toolName !in trackedTools) {
            if (fingerprint.isNotBlank() && fingerprint != lastFingerprint) {
                identicalCount = 0
                lastAction = null
                lastFingerprint = fingerprint
            }
            return null
        }
        if (fingerprint.isBlank()) return null

        val action = ActionKey(toolName, normalizedTarget.ifBlank { toolName })
        if (fingerprint != lastFingerprint) {
            identicalCount = 1
            lastAction = action
            lastFingerprint = fingerprint
            return null
        }
        if (action == lastAction) {
            identicalCount++
        } else {
            identicalCount = 1
            lastAction = action
        }
        lastFingerprint = fingerprint
        return if (identicalCount >= STUCK_THRESHOLD) WARNING else null
    }

    fun fingerprint(inspection: ScreenInspectionResult): String {
        val parts = (inspection.interactiveElements + inspection.textElements)
            .asSequence()
            .map { node ->
                "${node.viewId}\u0001${node.text}\u0001${node.description}"
            }
            .sorted()
            .joinToString("\u0002")
        return "${inspection.packageName}#${parts.hashCode()}"
    }
}
