package me.rerere.rikkahub.ui.pages.extensions.workspace

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.CloudServer
import me.rerere.hugeicons.stroke.Connect
import me.rerere.hugeicons.stroke.FileImport
import me.rerere.hugeicons.stroke.FingerPrint
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.Key01
import me.rerere.hugeicons.stroke.Tick02
import me.rerere.hugeicons.stroke.View
import me.rerere.hugeicons.stroke.ViewOff
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceBackdrop
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceChip
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceDialog
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceDivider
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceField
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceGlass
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceInputRow
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceRoundIcon
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceRow
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceSection
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceTextButton
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceTopBar
import me.rerere.rikkahub.ui.theme.palaceColors
import me.rerere.workspace.ssh.SshAuthType
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.io.ByteArrayOutputStream
import java.io.InputStream

private const val MAX_PRIVATE_KEY_BYTES = 64 * 1024

@Composable
fun WorkspaceSshEditorPage(id: String?) {
    val navController = LocalNavController.current
    val vm: WorkspaceSshEditorVM = koinViewModel(key = "ssh-editor-$id", parameters = { parametersOf(id) })
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val c = palaceColors()
    // 测试连接和保存针对开始时的表单快照, 期间锁定表单
    val editable = !state.busy

    LaunchedEffect(state.saved) {
        if (state.saved) navController.popBackStack()
    }

    val privateKeyPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readLimited(MAX_PRIVATE_KEY_BYTES) }
        }.getOrNull()
        if (text != null) vm.update { it.copy(privateKey = text.trim()) }
    }

    PalaceBackdrop {
        Column(Modifier.fillMaxSize()) {
            PalaceTopBar(
                title = if (vm.isNew) stringResource(R.string.workspace_ssh_editor_title_new) else stringResource(R.string.workspace_ssh_editor_title_edit),
                sub = "SSH WORKSPACE",
                onBack = { navController.popBackStack() },
                rightIcon = HugeIcons.Tick02,
                onRight = { if (!state.busy) vm.save() },
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
            ) {
                PalaceSection("BASIC", stringResource(R.string.workspace_ssh_editor_basic))
                PalaceGlass {
                    PalaceInputRow(
                        label = stringResource(R.string.workspace_ssh_editor_name),
                        value = state.name,
                        onValueChange = { value -> if (editable) vm.update { it.copy(name = value) } },
                        placeholder = stringResource(R.string.workspace_ssh_editor_name_placeholder),
                    )
                }

                PalaceSection("LINK", stringResource(R.string.workspace_ssh_editor_connection))
                PalaceGlass {
                    PalaceInputRow(
                        label = stringResource(R.string.workspace_ssh_editor_host),
                        value = state.host,
                        onValueChange = { value -> if (editable) vm.update { it.copy(host = value.trim()) } },
                        placeholder = stringResource(R.string.workspace_ssh_editor_host_placeholder),
                    )
                    PalaceDivider()
                    PalaceInputRow(
                        label = stringResource(R.string.workspace_ssh_editor_port),
                        value = state.port,
                        onValueChange = { value ->
                            if (editable) vm.update { it.copy(port = value.filter(Char::isDigit).take(5)) }
                        },
                        number = true,
                    )
                    PalaceDivider()
                    PalaceInputRow(
                        label = stringResource(R.string.workspace_ssh_editor_username),
                        value = state.username,
                        onValueChange = { value -> if (editable) vm.update { it.copy(username = value.trim()) } },
                    )
                }

                PalaceSection("AUTH", stringResource(R.string.workspace_ssh_editor_auth))
                PalaceGlass {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(stringResource(R.string.workspace_ssh_editor_auth_method), fontSize = 14.sp, color = c.ink)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PalaceChip(
                                text = stringResource(R.string.workspace_ssh_editor_password),
                                selected = state.authType == SshAuthType.PASSWORD,
                                onClick = { if (editable) vm.update { it.copy(authType = SshAuthType.PASSWORD) } },
                            )
                            PalaceChip(
                                text = stringResource(R.string.workspace_ssh_editor_private_key),
                                selected = state.authType == SshAuthType.PRIVATE_KEY,
                                onClick = { if (editable) vm.update { it.copy(authType = SshAuthType.PRIVATE_KEY) } },
                            )
                        }
                    }
                    PalaceDivider()
                    when (state.authType) {
                        SshAuthType.PASSWORD -> {
                            SecretRow(
                                label = stringResource(R.string.workspace_ssh_editor_password),
                                value = state.password,
                                onValueChange = { value -> if (editable) vm.update { it.copy(password = value) } },
                                placeholder = if (state.hasSavedPassword) stringResource(R.string.workspace_ssh_editor_saved_keep) else "",
                            )
                        }

                        SshAuthType.PRIVATE_KEY -> {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Text(stringResource(R.string.workspace_ssh_editor_private_key), fontSize = 14.sp, color = c.ink)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    stringResource(R.string.workspace_ssh_editor_private_key_desc),
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp,
                                    color = c.mute,
                                )
                                Spacer(Modifier.height(6.dp))
                                PalaceField(
                                    value = state.privateKey,
                                    onValueChange = { value -> if (editable) vm.update { it.copy(privateKey = value) } },
                                    placeholder = if (state.hasSavedPrivateKey) {
                                        stringResource(R.string.workspace_ssh_editor_saved_keep)
                                    } else {
                                        "-----BEGIN OPENSSH PRIVATE KEY-----"
                                    },
                                    minLines = 3,
                                    textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                                )
                            }
                            PalaceDivider()
                            PalaceRow(
                                icon = HugeIcons.FileImport,
                                title = stringResource(R.string.workspace_ssh_editor_import_key),
                                onClick = { if (editable) privateKeyPicker.launch(arrayOf("*/*")) },
                            )
                            PalaceDivider()
                            SecretRow(
                                label = stringResource(R.string.workspace_ssh_editor_passphrase),
                                value = state.passphrase,
                                onValueChange = { value -> if (editable) vm.update { it.copy(passphrase = value) } },
                                placeholder = stringResource(R.string.workspace_ssh_editor_passphrase_placeholder),
                            )
                        }
                    }
                }

                PalaceSection("DIR", stringResource(R.string.workspace_ssh_editor_working_dir))
                PalaceGlass {
                    PalaceInputRow(
                        label = stringResource(R.string.workspace_ssh_editor_remote_dir),
                        supporting = stringResource(R.string.workspace_ssh_editor_remote_dir_desc),
                        value = state.remoteDir,
                        onValueChange = { value -> if (editable) vm.update { it.copy(remoteDir = value.trim()) } },
                        placeholder = "/home/user/project",
                    )
                }

                PalaceSection("SAFE", stringResource(R.string.workspace_ssh_editor_security))
                PalaceGlass {
                    val hostKey = state.hostKey
                    InfoRow(
                        icon = HugeIcons.FingerPrint,
                        title = stringResource(R.string.workspace_ssh_editor_host_key),
                        value = if (hostKey == null) {
                            stringResource(R.string.workspace_ssh_editor_host_key_unconfirmed)
                        } else {
                            "${hostKey.type}\n${hostKey.fingerprint}"
                        },
                        mono = hostKey != null,
                    )
                }

                Spacer(Modifier.height(16.dp))
                ProbeSection(
                    state = state,
                    onTest = vm::testConnection,
                )
            }
        }
    }

    state.pendingHostKey?.let { pending ->
        HostKeyDialog(
            pending = pending,
            address = "${pending.draft.host}:${pending.draft.port}",
            onTrust = vm::trustHostKey,
            onDismiss = vm::dismissHostKey,
        )
    }

    state.error?.let { error ->
        val message = when (error) {
            is SshEditorError.Resource -> stringResource(error.id)
            is SshEditorError.Message -> error.text
        }
        PalaceDialog(
            title = if (state.canSaveAnyway) {
                stringResource(R.string.workspace_ssh_editor_connect_failed)
            } else {
                stringResource(R.string.workspace_ssh_editor_error)
            },
            onDismiss = vm::dismissError,
            confirmText = if (state.canSaveAnyway) stringResource(R.string.workspace_ssh_editor_save_anyway) else null,
            onConfirm = vm::saveAnyway,
            dismissText = if (state.canSaveAnyway) stringResource(R.string.cancel) else stringResource(R.string.confirm),
        ) {
            Text(
                if (state.canSaveAnyway) stringResource(R.string.workspace_ssh_editor_save_anyway_message, message) else message,
                fontSize = 14.sp,
                color = c.ink,
            )
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, value: String, mono: Boolean = false) {
    val c = palaceColors()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = c.ink2)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = c.ink)
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                color = c.mute,
                fontFamily = if (mono) FontFamily.Monospace else null,
            )
        }
    }
}

@Composable
private fun SecretRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    val c = palaceColors()
    var visible by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(label, fontSize = 14.sp, color = c.ink)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PalaceField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = placeholder,
                singleLine = true,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            )
            Spacer(Modifier.width(8.dp))
            PalaceRoundIcon(
                icon = if (visible) HugeIcons.ViewOff else HugeIcons.View,
                size = 36.dp,
                onClick = { visible = !visible },
            )
        }
    }
}

@Composable
private fun ProbeSection(
    state: WorkspaceSshEditorState,
    onTest: () -> Unit,
) {
    val c = palaceColors()
    PalaceGlass {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (state.testing) {
                LoadingIndicator(modifier = Modifier.size(20.dp), color = c.ink2)
            } else {
                Icon(HugeIcons.Connect, null, Modifier.size(16.dp), tint = c.ink2)
            }
            Spacer(Modifier.width(6.dp))
            PalaceTextButton(
                text = if (state.testing) {
                    stringResource(R.string.workspace_ssh_editor_connecting)
                } else {
                    stringResource(R.string.workspace_ssh_editor_test)
                },
                onClick = onTest,
                enabled = !state.busy,
            )
        }
    }
    state.probeResult?.let { result ->
        PalaceSection("PROBE", stringResource(R.string.workspace_ssh_editor_connected))
        PalaceGlass {
            InfoRow(
                icon = HugeIcons.CloudServer,
                title = stringResource(R.string.workspace_ssh_editor_shell),
                value = result.shell,
            )
            PalaceDivider()
            InfoRow(
                icon = HugeIcons.Folder01,
                title = stringResource(R.string.workspace_ssh_editor_working_dir),
                value = when {
                    state.remoteDir.isBlank() || state.remoteDir == result.homeDir ->
                        stringResource(R.string.workspace_ssh_editor_home_dir, result.homeDir)
                    result.remoteDirExists -> state.remoteDir
                    else -> stringResource(R.string.workspace_ssh_editor_dir_missing, state.remoteDir)
                },
            )
        }
    }
}

@Composable
private fun HostKeyDialog(
    pending: PendingHostKey,
    address: String,
    onTrust: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = palaceColors()
    val changed = pending.expected != null
    PalaceDialog(
        title = if (changed) {
            stringResource(R.string.workspace_ssh_editor_host_key_changed)
        } else {
            stringResource(R.string.workspace_ssh_editor_host_key_confirm)
        },
        onDismiss = onDismiss,
        confirmText = if (changed) {
            stringResource(R.string.workspace_ssh_editor_trust_new_key)
        } else {
            stringResource(R.string.workspace_ssh_editor_trust_continue)
        },
        onConfirm = onTrust,
        dismissText = stringResource(R.string.cancel),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(HugeIcons.Key01, null, Modifier.size(16.dp), tint = if (changed) c.error else c.ink2)
            Spacer(Modifier.width(8.dp))
            Text(
                if (changed) {
                    stringResource(R.string.workspace_ssh_editor_host_key_changed_message, address)
                } else {
                    val keyFile = pending.actual.type.removePrefix("ssh-").substringBefore('-')
                    stringResource(
                        R.string.workspace_ssh_editor_host_key_first_message,
                        address,
                        "ssh-keygen -lf /etc/ssh/ssh_host_${keyFile}_key.pub",
                    )
                },
                fontSize = 14.sp,
                color = if (changed) c.error else c.ink,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = "${pending.actual.type}\n${pending.actual.fingerprint}",
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            color = c.mute,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }
}

// 私钥文件很小, 超过上限的多半是选错了文件, 不整个读进内存
private fun InputStream.readLimited(limit: Int): String? {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        if (output.size() + read > limit) return null
        output.write(buffer, 0, read)
    }
    return output.toString(Charsets.UTF_8.name())
}
