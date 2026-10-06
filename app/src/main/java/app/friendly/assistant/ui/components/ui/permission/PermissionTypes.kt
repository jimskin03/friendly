package app.friendly.assistant.ui.components.ui.permission

import android.Manifest
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.friendly.assistant.R


data class PermissionInfo(
    val permission: String,
    val displayName: @Composable () -> Unit,
    val usage: @Composable () -> Unit,
    val required: Boolean = false
)


enum class PermissionStatus {

    NotRequested,

    Granted,

    Denied,

    DeniedPermanently
}


data class PermissionResult(
    val permission: String,
    val status: PermissionStatus,
    val isGranted: Boolean = status == PermissionStatus.Granted
)


data class MultiplePermissionResult(
    val results: Map<String, PermissionResult>,
    val allGranted: Boolean = results.values.all { it.isGranted },
    val allRequiredGranted: Boolean
)

val PermissionCamera = PermissionInfo(
    permission = Manifest.permission.CAMERA,
    displayName = { Text(stringResource(R.string.permission_camera)) },
    usage = { Text(stringResource(R.string.permission_camera_desc)) },
    required = true
)

val PermissionRecordAudio = PermissionInfo(
    permission = Manifest.permission.RECORD_AUDIO,
    displayName = { Text(stringResource(R.string.permission_microphone)) },
    usage = { Text(stringResource(R.string.permission_microphone_desc)) },
    required = true
)

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
val PermissionNotification = PermissionInfo(
    permission = Manifest.permission.POST_NOTIFICATIONS,
    displayName = { Text(stringResource(R.string.permission_notification)) },
    usage = { Text(stringResource(R.string.permission_notification_desc)) },
    required = true
)

@RequiresApi(37)
val PermissionLocalNetwork = PermissionInfo(
    permission = Manifest.permission.ACCESS_LOCAL_NETWORK,
    displayName = { Text(stringResource(R.string.permission_local_network)) },
    usage = { Text(stringResource(R.string.permission_local_network_desc)) },
    required = true
)

val PermissionCallPhone = PermissionInfo(
    permission = Manifest.permission.CALL_PHONE,
    displayName = { Text(stringResource(R.string.permission_call_phone)) },
    usage = { Text(stringResource(R.string.permission_call_phone_desc)) },
    required = true
)

val PermissionReadPhoneState = PermissionInfo(
    permission = Manifest.permission.READ_PHONE_STATE,
    displayName = { Text(stringResource(R.string.permission_read_phone_state)) },
    usage = { Text(stringResource(R.string.permission_read_phone_state_desc)) },
    required = true
)

val PermissionAnswerPhoneCalls = PermissionInfo(
    permission = Manifest.permission.ANSWER_PHONE_CALLS,
    displayName = { Text(stringResource(R.string.permission_answer_phone_calls)) },
    usage = { Text(stringResource(R.string.permission_answer_phone_calls_desc)) },
    required = false
)



