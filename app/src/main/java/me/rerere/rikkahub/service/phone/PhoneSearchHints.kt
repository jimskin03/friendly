package me.rerere.rikkahub.service.phone

/**
 * Search-first hints for phone inspect output. Detects likely search fields /
 * affordances from accessibility labels (no English keyword gate on tools).
 * Does not click or type — hints only.
 */
internal object PhoneSearchHints {
    const val MAX_CANDIDATES = 5

    /**
     * Tokens that often appear in search UI labels across locales, plus common
     * view-id fragments (e.g. search_bar, iv_search).
     */
    private val SEARCH_PATTERN = Regex(
        pattern = "search|cari|搜索|尋找|查找|pencarian|busca|suche|recherche",
        option = RegexOption.IGNORE_CASE,
    )

    /**
     * True when [node] is an EditText/editable or clickable control whose
     * text, content description, or view id looks like search.
     */
    fun looksLikeSearch(node: ScreenNodeInfo): Boolean {
        if (!hasSearchSignal(node)) return false
        val isEdit = node.editable ||
            node.className.contains("EditText", ignoreCase = true) ||
            node.className.contains("AutoCompleteTextView", ignoreCase = true) ||
            node.className.contains("SearchView", ignoreCase = true)
        return isEdit || node.clickable
    }

    fun collect(
        nodes: List<ScreenNodeInfo>,
        limit: Int = MAX_CANDIDATES,
    ): List<ScreenNodeInfo> {
        if (limit <= 0) return emptyList()
        return nodes.asSequence()
            .filter { looksLikeSearch(it) }
            .distinctBy { node ->
                "${node.viewId}\u0001${node.text}\u0001${node.description}\u0001${node.left},${node.top},${node.right},${node.bottom}"
            }
            .sortedWith(
                compareByDescending<ScreenNodeInfo> { it.editable }
                    .thenByDescending { it.clickable }
                    .thenBy { it.id },
            )
            .take(limit)
            .toList()
    }

    private fun hasSearchSignal(node: ScreenNodeInfo): Boolean {
        val shortViewId = node.viewId.substringAfter(":id/", node.viewId)
        return SEARCH_PATTERN.containsMatchIn(node.text) ||
            SEARCH_PATTERN.containsMatchIn(node.description) ||
            SEARCH_PATTERN.containsMatchIn(node.viewId) ||
            SEARCH_PATTERN.containsMatchIn(shortViewId)
    }
}
