package app.friendly.assistant.service.phone

data class ElementSelector(
    val query: String? = null,
    val viewId: String? = null,
    val text: String? = null,
    val containsText: String? = null,
    val regex: String? = null,
    val nodeId: Int? = null,
    val index: Int = 0,
    val clickableOnly: Boolean = false,
    val enabledOnly: Boolean = false,
)

object PhoneElementMatcher {

    /**
     * Finds all matching elements from [nodes] scored and sorted by relevance.
     */
    fun findMatches(
        nodes: List<ScreenNodeInfo>,
        selector: ElementSelector,
    ): List<ScreenNodeInfo> {
        if (selector.nodeId != null) {
            val direct = nodes.filter { it.id == selector.nodeId }
            if (direct.isNotEmpty()) return direct
        }

        val candidates = nodes.asSequence()
            .filter { node ->
                if (selector.clickableOnly && !node.clickable) return@filter false
                if (selector.enabledOnly && !node.enabled) return@filter false
                true
            }
            .mapNotNull { node ->
                val score = computeMatchScore(node, selector)
                if (score > 0) node to score else null
            }
            .sortedWith(
                compareByDescending<Pair<ScreenNodeInfo, Int>> { it.second }
                    .thenBy { it.first.id }
            )
            .map { it.first }
            .toList()

        return candidates
    }

    /**
     * Resolves the best matching element for [selector], respecting [ElementSelector.index].
     */
    fun findBestMatch(
        nodes: List<ScreenNodeInfo>,
        selector: ElementSelector,
    ): ScreenNodeInfo? {
        val matches = findMatches(nodes, selector)
        if (matches.isEmpty()) return null
        val targetIndex = selector.index.coerceAtLeast(0)
        return if (targetIndex < matches.size) matches[targetIndex] else null
    }

    /**
     * Matches a single element by a simple query string or view ID.
     */
    fun findByQueryOrId(
        nodes: List<ScreenNodeInfo>,
        query: String?,
        viewId: String? = null,
        nodeId: Int? = null,
        index: Int = 0,
    ): ScreenNodeInfo? {
        val selector = ElementSelector(
            query = query,
            viewId = viewId,
            nodeId = nodeId,
            index = index,
        )
        return findBestMatch(nodes, selector)
    }

    private fun computeMatchScore(node: ScreenNodeInfo, selector: ElementSelector): Int {
        var maxScore = 0

        // 1. View ID match
        val targetViewId = selector.viewId ?: selector.query
        if (!targetViewId.isNullOrBlank()) {
            val nodeViewId = node.viewId
            if (nodeViewId.isNotBlank()) {
                val shortViewId = nodeViewId.substringAfter(":id/")
                val targetShort = targetViewId.substringAfter(":id/")

                if (nodeViewId.equals(targetViewId, ignoreCase = true) || shortViewId.equals(targetShort, ignoreCase = true)) {
                    maxScore = maxOf(maxScore, 95)
                } else if (shortViewId.contains(targetShort, ignoreCase = true)) {
                    maxScore = maxOf(maxScore, 65)
                }
            }
        }

        // 2. Exact or substring text / description
        val targetText = selector.text ?: selector.query
        if (!targetText.isNullOrBlank()) {
            val nodeText = node.text
            val nodeDesc = node.description

            // Exact match
            if (nodeText == targetText || nodeDesc == targetText) {
                maxScore = maxOf(maxScore, 100)
            } else if (nodeText.equals(targetText, ignoreCase = true) || nodeDesc.equals(targetText, ignoreCase = true)) {
                maxScore = maxOf(maxScore, 90)
            } else if (nodeText.startsWith(targetText, ignoreCase = true) || nodeDesc.startsWith(targetText, ignoreCase = true)) {
                maxScore = maxOf(maxScore, 80)
            } else if (nodeText.contains(targetText, ignoreCase = true) || nodeDesc.contains(targetText, ignoreCase = true)) {
                maxScore = maxOf(maxScore, 70)
            }
        }

        // 3. Explicit containsText
        if (!selector.containsText.isNullOrBlank()) {
            val q = selector.containsText
            if (node.text.contains(q, ignoreCase = true) || node.description.contains(q, ignoreCase = true)) {
                maxScore = maxOf(maxScore, 75)
            }
        }

        // 4. Regex
        if (!selector.regex.isNullOrBlank()) {
            runCatching {
                val r = Regex(selector.regex, RegexOption.IGNORE_CASE)
                if (r.containsMatchIn(node.text) || r.containsMatchIn(node.description)) {
                    maxScore = maxOf(maxScore, 85)
                }
            }
        }

        return maxScore
    }
}
