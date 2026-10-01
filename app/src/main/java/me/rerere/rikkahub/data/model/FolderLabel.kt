package me.rerere.rikkahub.data.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Airplane01
import me.rerere.hugeicons.stroke.Book02
import me.rerere.hugeicons.stroke.Briefcase01
import me.rerere.hugeicons.stroke.Code
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.Heart
import me.rerere.hugeicons.stroke.Idea01
import me.rerere.hugeicons.stroke.Message01
import me.rerere.hugeicons.stroke.MoneyBag02
import me.rerere.hugeicons.stroke.Sparkles

enum class FolderLabel(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val color: Color,
) {
    PLANNING(
        id = "planning",
        title = "Planning",
        icon = HugeIcons.File02,
        color = Color(0xFF8B5CF6),
    ),
    RESEARCH(
        id = "research",
        title = "Research",
        icon = HugeIcons.Idea01,
        color = Color(0xFF10B981),
    ),
    CODE(
        id = "code",
        title = "Code",
        icon = HugeIcons.Code,
        color = Color(0xFF3B82F6),
    ),
    TRAVEL(
        id = "travel",
        title = "Travel",
        icon = HugeIcons.Airplane01,
        color = Color(0xFFEC4899),
    ),
    HEALTH(
        id = "health",
        title = "Health",
        icon = HugeIcons.Heart,
        color = Color(0xFFF97316),
    ),
    PROJECT(
        id = "project",
        title = "Ideas",
        icon = HugeIcons.Sparkles,
        color = Color(0xFFA855F7),
    ),
    WORK(
        id = "work",
        title = "Work",
        icon = HugeIcons.Briefcase01,
        color = Color(0xFFF59E0B),
    ),
    EDUCATION(
        id = "education",
        title = "Education",
        icon = HugeIcons.Book02,
        color = Color(0xFF06B6D4),
    ),
    CHAT(
        id = "chat",
        title = "Chat",
        icon = HugeIcons.Message01,
        color = Color(0xFF6366F1),
    ),
    FINANCE(
        id = "finance",
        title = "Finance",
        icon = HugeIcons.MoneyBag02,
        color = Color(0xFF14B8A6),
    );

    companion object {
        fun fromId(id: String?): FolderLabel {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: PLANNING
        }
    }
}
