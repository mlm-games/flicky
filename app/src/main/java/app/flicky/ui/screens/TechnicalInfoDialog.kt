package app.flicky.ui.screens

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.flicky.R
import app.flicky.data.local.AppVariant
import app.flicky.data.model.FDroidApp
import app.flicky.helper.tvFocusRing
import app.flicky.ui.components.snackbar.SnackbarManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.security.MessageDigest

data class TechnicalPermission(
    val name: String,
    val description: String?,
    val dangerous: Boolean,
)

data class TechnicalInfo(
    val packageName: String,
    val indexVersion: String,
    val installedVersion: String?,
    val signerFingerprint: String?,
    val apkSha256: String?,
    val size: Long,
    val repositoryName: String,
    val permissions: List<TechnicalPermission>?,
)

@Composable
fun TechnicalInfoDialog(
    app: FDroidApp,
    variant: AppVariant?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val snackbarManager: SnackbarManager = koinInject()
    val closeFocus = remember { FocusRequester() }
    val copiedLabel = stringResource(R.string.copied)

    var info by remember { mutableStateOf<TechnicalInfo?>(null) }

    LaunchedEffect(app.packageName, variant?.versionCode, variant?.repositoryUrl) {
        info = loadTechnicalInfo(context, app, variant)
    }

    val current = info

    fun copyValue(value: String) {
        clipboard.nativeClipboard.text = AnnotatedString(value)
        snackbarManager.show(copiedLabel)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = MaterialTheme.shapes.large,
            color = colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    stringResource(R.string.technical_info),
                    style = typography.titleLarge,
                    color = colorScheme.onSurface
                )
                Spacer(Modifier.height(16.dp))

                val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.6f).dp

                Column(
                    modifier = Modifier
                        .heightIn(max = maxHeight)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (current == null) {
                        Text(
                            stringResource(R.string.unknown),
                            style = typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant
                        )
                    } else {
                        TechnicalInfoContent(current, ::copyValue)
                    }
                }

                Spacer(Modifier.height(16.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(closeFocus)
                ) {
                    Text(stringResource(R.string.action_close))
                }
            }
        }
    }
}

@Composable
private fun TechnicalInfoContent(
    info: TechnicalInfo,
    onCopy: (String) -> Unit,
) {
    TechRow(stringResource(R.string.package_name), info.packageName, onCopy = onCopy)
    TechRow(stringResource(R.string.versions), info.indexVersion, onCopy = onCopy)

    info.installedVersion?.let { installed ->
        TechRow(stringResource(R.string.installed), installed, onCopy = onCopy)
    }

    info.signerFingerprint?.let { fingerprint ->
        TechRow(stringResource(R.string.signer_fingerprint), fingerprint, onCopy = onCopy)
    }

    info.apkSha256?.let { sha256 ->
        TechRow(stringResource(R.string.apk_sha256), sha256, onCopy = onCopy)
    }

    if (info.size > 0) {
        TechRow(stringResource(R.string.sort_size), formatBytes(info.size), onCopy = onCopy)
    }

    if (info.repositoryName.isNotBlank()) {
        TechRow(stringResource(R.string.repositories), info.repositoryName, onCopy = onCopy)
    }

    val permissions = info.permissions
    if (!permissions.isNullOrEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.permissions_header),
            style = typography.titleSmall,
            color = colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        permissions.forEach { permission ->
            TechRow(
                label = permission.name,
                value = permission.description ?: permission.name,
                onCopy = onCopy,
                labelColor = if (permission.dangerous) colorScheme.error else colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TechRow(
    label: String,
    value: String,
    onCopy: (String) -> Unit,
    labelColor: Color = colorScheme.onSurfaceVariant,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable { onCopy(value) }
            .tvFocusRing(shape = MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Text(label, style = typography.labelSmall, color = labelColor)
        Text(value, style = typography.bodyMedium, color = colorScheme.onSurface)
    }
}

private suspend fun loadTechnicalInfo(
    context: Context,
    app: FDroidApp,
    variant: AppVariant?,
): TechnicalInfo = withContext(Dispatchers.IO) {
    val pm = context.packageManager

    val packageInfo = installedPackageInfo(pm, app.packageName)
    val installedVersion = packageInfo?.let { pi ->
        val versionName = pi.versionName
        val versionCode = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
        if (versionName.isNullOrBlank()) versionCode.toString() else "$versionName ($versionCode)"
    }

    TechnicalInfo(
        packageName = app.packageName,
        indexVersion = "${app.version} (${app.versionCode})",
        installedVersion = installedVersion,
        signerFingerprint = packageInfo?.let { pi ->
            signerFingerprints(pi).takeIf { it.isNotEmpty() }?.joinToString("\n")
        },
        apkSha256 = variant?.sha256?.takeIf { it.isNotBlank() } ?: app.sha256.takeIf { it.isNotBlank() },
        size = variant?.size?.takeIf { it > 0 } ?: app.size,
        repositoryName = variant?.repositoryName?.takeIf { it.isNotBlank() } ?: app.repository,
        permissions = packageInfo?.let { requestedPermissions(pm, it) }
    )
}

private fun installedPackageInfo(pm: PackageManager, packageName: String): PackageInfo? =
    runCatching { pm.getPackageInfo(packageName, packageInfoFlags()) }.getOrNull()

@Suppress("DEPRECATION")
private fun packageInfoFlags(): Int {
    val signingFlags = if (Build.VERSION.SDK_INT >= 28) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        PackageManager.GET_SIGNATURES
    }
    return signingFlags or PackageManager.GET_PERMISSIONS
}

@Suppress("DEPRECATION")
private fun signerFingerprints(pi: PackageInfo): List<String> {
    val signatures = if (Build.VERSION.SDK_INT >= 28) {
        pi.signingInfo?.apkContentsSigners.orEmpty()
    } else {
        pi.signatures.orEmpty()
    }
    return signatures.map { sha256Fingerprint(it.toByteArray()) }
}

private fun sha256Fingerprint(der: ByteArray): String =
    MessageDigest.getInstance("SHA-256")
        .digest(der)
        .joinToString(":") { "%02x".format(it) }

private fun requestedPermissions(
    pm: PackageManager,
    pi: PackageInfo,
): List<TechnicalPermission> =
    pi.requestedPermissions.orEmpty().map { name ->
        val info = runCatching { pm.getPermissionInfo(name, 0) }.getOrNull()
        TechnicalPermission(
            name = name,
            description = runCatching { info?.loadDescription(pm) }.getOrNull()
                ?.toString()
                ?.takeIf { it.isNotBlank() },
            dangerous = info?.isDangerous() == true
        )
    }

private fun PermissionInfo.isDangerous(): Boolean =
    (protectionLevel and PermissionInfo.PROTECTION_MASK_BASE) == PermissionInfo.PROTECTION_DANGEROUS
