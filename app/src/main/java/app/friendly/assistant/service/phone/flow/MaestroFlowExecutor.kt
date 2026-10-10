package app.friendly.assistant.service.phone.flow

import android.content.Context
import kotlinx.coroutines.delay
import app.friendly.assistant.service.phone.ElementSelector
import app.friendly.assistant.service.phone.PhoneAutomationService
import app.friendly.assistant.service.phone.PhoneElementMatcher
import app.friendly.assistant.service.phone.ScreenNodeInfo

class MaestroFlowExecutor(
    private val service: PhoneAutomationService,
    private val context: Context,
) {

    suspend fun execute(
        commands: List<MaestroCommand>,
        onStepProgress: ((stepIndex: Int, total: Int, command: MaestroCommand) -> Unit)? = null,
    ): FlowExecutionResult {
        val startTime = System.currentTimeMillis()
        val stepResults = mutableListOf<FlowStepResult>()

        for ((index, cmd) in commands.withIndex()) {
            onStepProgress?.invoke(index + 1, commands.size, cmd)
            val stepStart = System.currentTimeMillis()
            val result = executeSingleCommand(index, cmd)
            val elapsed = System.currentTimeMillis() - stepStart
            val loggedResult = result.copy(elapsedMs = elapsed)
            stepResults.add(loggedResult)

            if (!loggedResult.success && !cmd.optional) {
                val currentPkg = runCatching { service.inspectScreen().packageName }.getOrDefault("")
                return FlowExecutionResult(
                    success = false,
                    executedSteps = index + 1,
                    totalSteps = commands.size,
                    durationMs = System.currentTimeMillis() - startTime,
                    stepResults = stepResults,
                    failedStepIndex = index + 1,
                    error = "Step ${index + 1} (${loggedResult.commandName}) failed: ${loggedResult.detail}",
                    packageName = currentPkg,
                )
            }
        }

        val finalPkg = runCatching { service.inspectScreen().packageName }.getOrDefault("")
        return FlowExecutionResult(
            success = true,
            executedSteps = commands.size,
            totalSteps = commands.size,
            durationMs = System.currentTimeMillis() - startTime,
            stepResults = stepResults,
            failedStepIndex = null,
            error = null,
            packageName = finalPkg,
        )
    }

    private suspend fun executeSingleCommand(stepIndex: Int, cmd: MaestroCommand): FlowStepResult {
        return when (cmd) {
            is MaestroCommand.LaunchApp -> executeLaunchApp(stepIndex, cmd)
            is MaestroCommand.TapOn -> executeTapOn(stepIndex, cmd)
            is MaestroCommand.InputText -> executeInputText(stepIndex, cmd)
            is MaestroCommand.EraseText -> executeEraseText(stepIndex, cmd)
            is MaestroCommand.PressKey -> executePressKey(stepIndex, cmd)
            is MaestroCommand.Scroll -> executeScroll(stepIndex, cmd)
            is MaestroCommand.ScrollUntilVisible -> executeScrollUntilVisible(stepIndex, cmd)
            is MaestroCommand.AssertVisible -> executeAssertVisible(stepIndex, cmd)
            is MaestroCommand.AssertNotVisible -> executeAssertNotVisible(stepIndex, cmd)
            is MaestroCommand.Sleep -> executeSleep(stepIndex, cmd)
            is MaestroCommand.Repeat -> executeRepeat(stepIndex, cmd)
        }
    }

    private suspend fun executeLaunchApp(stepIndex: Int, cmd: MaestroCommand.LaunchApp): FlowStepResult {
        val (ok, msg) = PhoneAutomationService.launchApp(context, cmd.appName, cmd.phone, cmd.query)
        service.awaitIdle(500L)
        return FlowStepResult(
            stepIndex = stepIndex + 1,
            commandName = "launchApp",
            detail = msg,
            success = ok || cmd.optional,
            elapsedMs = 0,
            optionalSkipped = !ok && cmd.optional,
        )
    }

    private suspend fun executeTapOn(stepIndex: Int, cmd: MaestroCommand.TapOn): FlowStepResult {
        var targetX: Float? = cmd.point?.x
        var targetY: Float? = cmd.point?.y
        var matchedText = ""

        if (targetX == null || targetY == null) {
            val sel = cmd.selector ?: return FlowStepResult(
                stepIndex = stepIndex + 1,
                commandName = "tapOn",
                detail = "No selector or point specified",
                success = cmd.optional,
                elapsedMs = 0,
                optionalSkipped = cmd.optional,
            )

            // Fast check
            var target = resolveElement(sel)
            // If not found, wait briefly (up to 1500ms)
            if (target == null && !cmd.optional) {
                target = service.waitForNode(timeoutMs = 1500L) { node ->
                    PhoneElementMatcher.findBestMatch(listOf(node), sel) != null
                }
            }

            if (target == null) {
                return FlowStepResult(
                    stepIndex = stepIndex + 1,
                    commandName = "tapOn",
                    detail = "Element not found matching: ${describeSelector(sel)}",
                    success = cmd.optional,
                    elapsedMs = 0,
                    optionalSkipped = cmd.optional,
                )
            }

            targetX = target.centerX.toFloat()
            targetY = target.centerY.toFloat()
            matchedText = target.text.ifBlank { target.description }.ifBlank { target.viewId }
        }

        service.refuseIfActingOnFriendly(targetX, targetY)?.let { reason ->
            return FlowStepResult(
                stepIndex = stepIndex + 1,
                commandName = if (cmd.longPress) "longPressOn" else if (cmd.repeat > 1) "doubleTapOn" else "tapOn",
                detail = reason,
                success = cmd.optional,
                elapsedMs = 0,
                optionalSkipped = cmd.optional,
            )
        }

        var clicked = false
        val repeatCount = cmd.repeat.coerceAtLeast(1)
        for (i in 0 until repeatCount) {
            clicked = if (cmd.longPress) {
                // Long press using swipe in-place with 800ms duration
                service.swipe(targetX, targetY, targetX, targetY, 800L)
            } else {
                service.click(targetX, targetY)
            }
            if (repeatCount > 1 && i < repeatCount - 1) {
                delay(150L)
            }
        }
        service.awaitIdle(300L)

        return FlowStepResult(
            stepIndex = stepIndex + 1,
            commandName = if (cmd.longPress) "longPressOn" else if (cmd.repeat > 1) "doubleTapOn" else "tapOn",
            detail = if (matchedText.isNotBlank()) "Tapped \"$matchedText\" at ($targetX, $targetY)" else "Tapped at ($targetX, $targetY)",
            success = clicked || cmd.optional,
            elapsedMs = 0,
            optionalSkipped = !clicked && cmd.optional,
        )
    }

    private suspend fun executeInputText(stepIndex: Int, cmd: MaestroCommand.InputText): FlowStepResult {
        service.refuseIfActingOnFriendly()?.let { reason ->
            return FlowStepResult(
                stepIndex = stepIndex + 1,
                commandName = "inputText",
                detail = reason,
                success = cmd.optional,
                elapsedMs = 0,
                optionalSkipped = cmd.optional,
            )
        }
        val ok = service.typeText(cmd.text, cmd.clearFirst)
        service.awaitIdle(200L)
        return FlowStepResult(
            stepIndex = stepIndex + 1,
            commandName = "inputText",
            detail = if (ok) "Typed \"${cmd.text}\"" else "Failed to type into focused field",
            success = ok || cmd.optional,
            elapsedMs = 0,
            optionalSkipped = !ok && cmd.optional,
        )
    }

    private suspend fun executeEraseText(stepIndex: Int, cmd: MaestroCommand.EraseText): FlowStepResult {
        service.refuseIfActingOnFriendly()?.let { reason ->
            return FlowStepResult(
                stepIndex = stepIndex + 1,
                commandName = "eraseText",
                detail = reason,
                success = cmd.optional,
                elapsedMs = 0,
                optionalSkipped = cmd.optional,
            )
        }
        val ok = service.typeText("", clearFirst = true)
        service.awaitIdle(150L)
        return FlowStepResult(
            stepIndex = stepIndex + 1,
            commandName = "eraseText",
            detail = if (ok) "Cleared input text" else "Failed to clear input",
            success = ok || cmd.optional,
            elapsedMs = 0,
            optionalSkipped = !ok && cmd.optional,
        )
    }

    private suspend fun executePressKey(stepIndex: Int, cmd: MaestroCommand.PressKey): FlowStepResult {
        val ok = service.pressKey(cmd.key)
        service.awaitIdle(350L)
        return FlowStepResult(
            stepIndex = stepIndex + 1,
            commandName = "pressKey",
            detail = "Pressed ${cmd.key}",
            success = ok || cmd.optional,
            elapsedMs = 0,
            optionalSkipped = !ok && cmd.optional,
        )
    }

    private suspend fun executeScroll(stepIndex: Int, cmd: MaestroCommand.Scroll): FlowStepResult {
        val (sx, sy, ex, ey) = computeScrollCoordinates(cmd.direction)
        service.refuseIfActingOnFriendly(sx, sy)?.let { reason ->
            return FlowStepResult(
                stepIndex = stepIndex + 1,
                commandName = "scroll",
                detail = reason,
                success = cmd.optional,
                elapsedMs = 0,
                optionalSkipped = cmd.optional,
            )
        }
        val ok = service.swipe(sx, sy, ex, ey, cmd.durationMs)
        service.awaitIdle(350L)
        return FlowStepResult(
            stepIndex = stepIndex + 1,
            commandName = "scroll",
            detail = "Scrolled ${cmd.direction}",
            success = ok || cmd.optional,
            elapsedMs = 0,
            optionalSkipped = !ok && cmd.optional,
        )
    }

    private suspend fun executeScrollUntilVisible(stepIndex: Int, cmd: MaestroCommand.ScrollUntilVisible): FlowStepResult {
        val maxSwipes = cmd.maxSwipes.coerceIn(1, 15)
        var swipesDone = 0

        for (attempt in 0..maxSwipes) {
            val target = resolveElement(cmd.selector)
            if (target != null) {
                return FlowStepResult(
                    stepIndex = stepIndex + 1,
                    commandName = "scrollUntilVisible",
                    detail = "Found element after $swipesDone scroll(s)",
                    success = true,
                    elapsedMs = 0,
                )
            }
            if (attempt < maxSwipes) {
                val (sx, sy, ex, ey) = computeScrollCoordinates(cmd.direction)
                service.refuseIfActingOnFriendly(sx, sy)?.let { reason ->
                    return FlowStepResult(
                        stepIndex = stepIndex + 1,
                        commandName = "scrollUntilVisible",
                        detail = reason,
                        success = cmd.optional,
                        elapsedMs = 0,
                        optionalSkipped = cmd.optional,
                    )
                }
                service.swipe(sx, sy, ex, ey, 300L)
                swipesDone++
                service.awaitIdle(400L)
            }
        }

        return FlowStepResult(
            stepIndex = stepIndex + 1,
            commandName = "scrollUntilVisible",
            detail = "Element not visible after $swipesDone scroll(s): ${describeSelector(cmd.selector)}",
            success = cmd.optional,
            elapsedMs = 0,
            optionalSkipped = cmd.optional,
        )
    }

    private suspend fun executeAssertVisible(stepIndex: Int, cmd: MaestroCommand.AssertVisible): FlowStepResult {
        val target = service.waitForNode(timeoutMs = cmd.timeoutMs) { node ->
            PhoneElementMatcher.findBestMatch(listOf(node), cmd.selector) != null
        }
        val found = target != null
        return FlowStepResult(
            stepIndex = stepIndex + 1,
            commandName = "assertVisible",
            detail = if (found && target != null) "Visible: \"${target.text.ifBlank { target.description }.ifBlank { describeSelector(cmd.selector)}}\"" else "Not visible: ${describeSelector(cmd.selector)}",
            success = found || cmd.optional,
            elapsedMs = 0,
            optionalSkipped = !found && cmd.optional,
        )
    }

    private suspend fun executeAssertNotVisible(stepIndex: Int, cmd: MaestroCommand.AssertNotVisible): FlowStepResult {
        val disappeared = service.waitForNodeDisappear(timeoutMs = cmd.timeoutMs) { node ->
            PhoneElementMatcher.findBestMatch(listOf(node), cmd.selector) != null
        }
        return FlowStepResult(
            stepIndex = stepIndex + 1,
            commandName = "assertNotVisible",
            detail = if (disappeared) "Element is no longer visible" else "Element remained visible: ${describeSelector(cmd.selector)}",
            success = disappeared || cmd.optional,
            elapsedMs = 0,
            optionalSkipped = !disappeared && cmd.optional,
        )
    }

    private suspend fun executeSleep(stepIndex: Int, cmd: MaestroCommand.Sleep): FlowStepResult {
        delay(cmd.durationMs.coerceIn(10L, 30_000L))
        return FlowStepResult(
            stepIndex = stepIndex + 1,
            commandName = "sleep",
            detail = "Slept ${cmd.durationMs}ms",
            success = true,
            elapsedMs = 0,
        )
    }

    private suspend fun executeRepeat(stepIndex: Int, cmd: MaestroCommand.Repeat): FlowStepResult {
        var totalExecuted = 0
        for (i in 0 until cmd.times) {
            for (subCmd in cmd.commands) {
                val res = executeSingleCommand(stepIndex, subCmd)
                if (!res.success && !subCmd.optional) {
                    return res.copy(detail = "Repeat iteration ${i + 1} failed: ${res.detail}")
                }
                totalExecuted++
            }
        }
        return FlowStepResult(
            stepIndex = stepIndex + 1,
            commandName = "repeat",
            detail = "Repeated ${cmd.times} times ($totalExecuted sub-commands)",
            success = true,
            elapsedMs = 0,
        )
    }

    private fun resolveElement(selector: ElementSelector): ScreenNodeInfo? {
        val inspection = service.inspectScreen()
        val all = inspection.interactiveElements + inspection.textElements
        return PhoneElementMatcher.findBestMatch(all, selector)
    }

    private fun computeScrollCoordinates(direction: String): List<Float> {
        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels.toFloat()
        val height = metrics.heightPixels.toFloat()
        return when (direction.lowercase()) {
            "up" -> listOf(width * 0.5f, height * 0.25f, width * 0.5f, height * 0.75f)
            "left" -> listOf(width * 0.85f, height * 0.5f, width * 0.15f, height * 0.5f)
            "right" -> listOf(width * 0.15f, height * 0.5f, width * 0.85f, height * 0.5f)
            else -> listOf(width * 0.5f, height * 0.75f, width * 0.5f, height * 0.25f) // default down
        }
    }

    private fun describeSelector(sel: ElementSelector): String {
        return sel.text ?: sel.query ?: sel.viewId ?: sel.regex ?: "unspecified"
    }
}
