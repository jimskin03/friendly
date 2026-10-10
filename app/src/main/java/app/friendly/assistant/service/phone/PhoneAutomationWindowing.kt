package app.friendly.assistant.service.phone

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.util.Log
import app.friendly.assistant.data.datastore.PhoneAutomationWindowMode
import app.friendly.assistant.data.datastore.SettingsStore
import org.koin.core.context.GlobalContext
import kotlin.math.min

private const val TAG = "PhoneAutomationWindowing"

/** Hidden [android.app.WindowConfiguration.WINDOWING_MODE_SPLIT_SCREEN_SECONDARY]. */
private const val WINDOWING_MODE_SPLIT_SCREEN_SECONDARY = 4

/** Hidden [android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM]. */
private const val WINDOWING_MODE_FREEFORM = 5

internal fun currentPhoneAutomationWindowMode(): PhoneAutomationWindowMode {
    return runCatching {
        GlobalContext.get().get<SettingsStore>().settingsFlow.value.displaySetting.phoneAutomationWindowMode
    }.getOrDefault(PhoneAutomationWindowMode.OFF)
}

/**
 * Single startActivity choke point for Phone Automation app launches
 * (name/package matcher and [DirectAppIntents]).
 * Off keeps a normal full-screen [Intent.FLAG_ACTIVITY_NEW_TASK] launch.
 * Split and Popup request that window mode and fall back to public flags
 * if the hidden windowing API cannot be used. A failed windowed start is
 * retried as a normal launch so the call does not crash.
 */
internal fun Context.startPhoneAutomationActivity(intent: Intent) {
    val mode = currentPhoneAutomationWindowMode()
    if (mode == PhoneAutomationWindowMode.OFF) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        return
    }
    val baseFlags = intent.flags
    try {
        when (mode) {
            PhoneAutomationWindowMode.SPLIT -> startSplit(intent)
            PhoneAutomationWindowMode.POPUP -> startPopup(intent, baseFlags)
            PhoneAutomationWindowMode.OFF -> {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        }
    } catch (e: ActivityNotFoundException) {
        throw e
    } catch (t: Throwable) {
        Log.w(TAG, "Windowed launch failed; falling back to a normal launch", t)
        intent.flags = baseFlags or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }
}

private fun Context.startSplit(intent: Intent) {
    intent.addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or
            Intent.FLAG_ACTIVITY_MULTIPLE_TASK,
    )
    val options = ActivityOptions.makeBasic()
    if (!options.trySetLaunchWindowingMode(WINDOWING_MODE_SPLIT_SCREEN_SECONDARY)) {
        Log.w(TAG, "Split windowing mode unavailable; launching with public flags")
        startActivity(intent)
        return
    }
    startActivity(intent, options.toBundle())
}

private fun Context.startPopup(intent: Intent, baseFlags: Int) {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val options = ActivityOptions.makeBasic()
    options.setLaunchBounds(phonePopupLaunchBounds(this))
    if (!options.trySetLaunchWindowingMode(WINDOWING_MODE_FREEFORM)) {
        Log.w(TAG, "Freeform windowing mode unavailable; launching normally")
        intent.flags = baseFlags or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        return
    }
    startActivity(intent, options.toBundle())
}

/**
 * [ActivityOptions.setLaunchWindowingMode] is a hidden API.
 * Returns false when reflection fails so the caller can use public flags.
 */
private fun ActivityOptions.trySetLaunchWindowingMode(windowingMode: Int): Boolean {
    return try {
        val method = ActivityOptions::class.java.declaredMethods.firstOrNull { candidate ->
            candidate.name == "setLaunchWindowingMode" && candidate.parameterTypes.size == 1
        } ?: return false
        method.isAccessible = true
        method.invoke(this, windowingMode)
        true
    } catch (t: Throwable) {
        Log.w(TAG, "setLaunchWindowingMode($windowingMode) reflection failed", t)
        false
    }
}

/** A phone-sized rect, inset from the screen edges so it is not fullscreen. */
private fun phonePopupLaunchBounds(context: Context): Rect {
    val metrics = context.resources.displayMetrics
    val density = metrics.density
    val margin = (24 * density).toInt()
    val width = min((360 * density).toInt(), metrics.widthPixels - margin * 2).coerceAtLeast(1)
    val height = min((640 * density).toInt(), (metrics.heightPixels * 0.72f).toInt()).coerceAtLeast(1)
    val left = ((metrics.widthPixels - width) / 2).coerceAtLeast(0)
    val top = margin.coerceAtMost((metrics.heightPixels - height).coerceAtLeast(0))
    return Rect(left, top, left + width, top + height)
}
