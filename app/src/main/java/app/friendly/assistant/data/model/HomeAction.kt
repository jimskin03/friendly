package app.friendly.assistant.data.model

import kotlinx.serialization.Serializable

const val HOME_ACTION_SLOT_COUNT = 4

@Serializable
enum class HomeActionKind {
    PROMPT,
    LAUNCH_APP,
}

@Serializable
data class HomeAction(
    val kind: HomeActionKind = HomeActionKind.PROMPT,
    val label: String = "",
    val prompt: String = "",
    val appId: String = "",
)

fun defaultHomeActions(): List<HomeAction> = listOf(
    HomeAction(
        kind = HomeActionKind.PROMPT,
        label = "Plan my day",
        prompt = "Help me plan my day with a clear schedule and priorities.",
    ),
    HomeAction(
        kind = HomeActionKind.PROMPT,
        label = "Summarize",
        prompt = "Please summarize the following text or documents:",
    ),
    HomeAction(
        kind = HomeActionKind.PROMPT,
        label = "Look up",
        prompt = "Look up detailed information about ",
    ),
    HomeAction(
        kind = HomeActionKind.PROMPT,
        label = "Brainstorm",
        prompt = "Brainstorm creative and effective ideas for ",
    ),
)

fun normalizeHomeActions(actions: List<HomeAction>): List<HomeAction> {
    val defaults = defaultHomeActions()
    return List(HOME_ACTION_SLOT_COUNT) { index ->
        actions.getOrNull(index) ?: defaults[index]
    }
}
