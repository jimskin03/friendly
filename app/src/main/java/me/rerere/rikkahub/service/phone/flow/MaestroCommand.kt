package me.rerere.rikkahub.service.phone.flow

import me.rerere.rikkahub.service.phone.ElementSelector

sealed interface MaestroCommand {
    val optional: Boolean get() = false

    data class LaunchApp(
        val appName: String,
        val phone: String? = null,
        val query: String? = null,
        override val optional: Boolean = false,
    ) : MaestroCommand

    data class TapOn(
        val selector: ElementSelector? = null,
        val point: Point? = null,
        val repeat: Int = 1,
        val longPress: Boolean = false,
        override val optional: Boolean = false,
    ) : MaestroCommand

    data class InputText(
        val text: String,
        val clearFirst: Boolean = false,
        override val optional: Boolean = false,
    ) : MaestroCommand

    data class EraseText(
        val characters: Int = 50,
        override val optional: Boolean = false,
    ) : MaestroCommand

    data class PressKey(
        val key: String,
        override val optional: Boolean = false,
    ) : MaestroCommand

    data class Scroll(
        val direction: String = "down",
        val durationMs: Long = 300L,
        override val optional: Boolean = false,
    ) : MaestroCommand

    data class ScrollUntilVisible(
        val selector: ElementSelector,
        val direction: String = "down",
        val maxSwipes: Int = 5,
        override val optional: Boolean = false,
    ) : MaestroCommand

    data class AssertVisible(
        val selector: ElementSelector,
        val timeoutMs: Long = 3000L,
        override val optional: Boolean = false,
    ) : MaestroCommand

    data class AssertNotVisible(
        val selector: ElementSelector,
        val timeoutMs: Long = 3000L,
        override val optional: Boolean = false,
    ) : MaestroCommand

    data class Sleep(
        val durationMs: Long,
        override val optional: Boolean = false,
    ) : MaestroCommand

    data class Repeat(
        val times: Int,
        val commands: List<MaestroCommand>,
        override val optional: Boolean = false,
    ) : MaestroCommand

    data class Point(val x: Float, val y: Float)
}

data class FlowStepResult(
    val stepIndex: Int,
    val commandName: String,
    val detail: String,
    val success: Boolean,
    val elapsedMs: Long,
    val optionalSkipped: Boolean = false,
)

data class FlowExecutionResult(
    val success: Boolean,
    val executedSteps: Int,
    val totalSteps: Int,
    val durationMs: Long,
    val stepResults: List<FlowStepResult>,
    val failedStepIndex: Int? = null,
    val error: String? = null,
    val packageName: String = "",
)
