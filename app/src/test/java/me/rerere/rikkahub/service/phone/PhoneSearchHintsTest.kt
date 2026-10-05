package me.rerere.rikkahub.service.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneSearchHintsTest {

    private fun node(
        id: Int,
        text: String = "",
        description: String = "",
        viewId: String = "",
        className: String = "TextView",
        clickable: Boolean = false,
        editable: Boolean = false,
    ): ScreenNodeInfo = ScreenNodeInfo(
        id = id,
        text = text,
        description = description,
        viewId = viewId,
        className = className,
        clickable = clickable,
        editable = editable,
        scrollable = false,
        left = 0,
        top = id * 10,
        right = 100,
        bottom = id * 10 + 8,
        centerX = 50,
        centerY = id * 10 + 4,
    )

    @Test
    fun detectsEditTextSearchField() {
        val field = node(
            id = 1,
            text = "Search",
            className = "EditText",
            editable = true,
        )
        assertTrue(PhoneSearchHints.looksLikeSearch(field))
    }

    @Test
    fun detectsCariAndChineseLabelsOnClickable() {
        assertTrue(
            PhoneSearchHints.looksLikeSearch(
                node(id = 2, description = "Cari di sini", clickable = true),
            ),
        )
        assertTrue(
            PhoneSearchHints.looksLikeSearch(
                node(id = 3, text = "搜索", clickable = true),
            ),
        )
    }

    @Test
    fun detectsViewIdFragment() {
        assertTrue(
            PhoneSearchHints.looksLikeSearch(
                node(
                    id = 4,
                    viewId = "com.example:id/search_bar",
                    className = "EditText",
                    editable = true,
                ),
            ),
        )
    }

    @Test
    fun ignoresNonSearchClickable() {
        assertFalse(
            PhoneSearchHints.looksLikeSearch(
                node(id = 5, text = "Settings", clickable = true),
            ),
        )
    }

    @Test
    fun capsCandidatesAndPrefersEditable() {
        val nodes = listOf(
            node(id = 1, text = "Search icon", clickable = true),
            node(id = 2, text = "Search", className = "EditText", editable = true),
            node(id = 3, viewId = "id/btn_search", clickable = true),
            node(id = 4, description = "cari", clickable = true),
            node(id = 5, text = "搜索商品", clickable = true),
            node(id = 6, viewId = "search_input", editable = true, className = "EditText"),
            node(id = 7, text = "Not related", clickable = true),
        )
        val found = PhoneSearchHints.collect(nodes, limit = 5)
        assertEquals(5, found.size)
        assertTrue(found.first().editable)
        assertTrue(found.all { PhoneSearchHints.looksLikeSearch(it) })
    }
}
