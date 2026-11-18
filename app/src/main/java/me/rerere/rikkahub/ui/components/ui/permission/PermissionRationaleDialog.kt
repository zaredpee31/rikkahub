package me.rerere.rikkahub.ui.components.ui.permission

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AlertCircle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceDialog
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceGlass
import me.rerere.rikkahub.ui.theme.palaceColors

/**
 * 权限请求说明对话框
 */
@Composable
internal fun PermissionRationaleDialog(
    permissions: List<PermissionInfo>,
    permanentlyDeniedPermissions: List<PermissionInfo>,
    onProceed: () -> Unit,
    onCancel: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = palaceColors()
    val hasPermanentlyDenied = permanentlyDeniedPermissions.isNotEmpty()
    // 有永久拒绝的权限时，确认按钮跳转到设置（onProceed 里处理），否则是正常授权
    PalaceDialog(
        title = stringResource(R.string.permission_diaog_title),
        onDismiss = onCancel,
        confirmText = if (hasPermanentlyDenied) {
            stringResource(R.string.permission_go_to_settings)
        } else {
            stringResource(R.string.confirm)
        },
        onConfirm = onProceed,
        dismissText = stringResource(R.string.cancel),
    ) {
        // 标题图标
        Icon(
            imageVector = HugeIcons.AlertCircle,
            contentDescription = null,
            modifier = Modifier
                .size(32.dp)
                .align(Alignment.CenterHorizontally),
            tint = c.ink2
        )

        // 说明文字
        Text(
            text = if (hasPermanentlyDenied) {
                stringResource(R.string.permission_desc_goto_setting)
            } else {
                stringResource(R.string.permission_desc_require_permission)
            },
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            color = c.mute,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        // 权限列表
        permissions.forEach { permissionInfo ->
            PermissionItem(
                permissionInfo = permissionInfo,
                isPermanentlyDenied = permanentlyDeniedPermissions.contains(permissionInfo),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 单个权限项组件
 */
@Composable
private fun PermissionItem(
    permissionInfo: PermissionInfo,
    modifier: Modifier = Modifier,
    isPermanentlyDenied: Boolean = false,
) {
    val c = palaceColors()
    PalaceGlass(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // 权限名称
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    ProvideTextStyle(value = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = c.ink)) {
                        permissionInfo.displayName()
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (permissionInfo.required) {
                        Text(
                            text = stringResource(R.string.permission_required),
                            fontSize = 10.sp,
                            color = c.error,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(c.error.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (isPermanentlyDenied) {
                        Text(
                            text = stringResource(R.string.permission_permanently_denied),
                            fontSize = 10.sp,
                            color = Color.White,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(c.error)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 权限使用说明
            ProvideTextStyle(value = TextStyle(fontSize = 11.5.sp, lineHeight = 16.sp, color = c.mute)) {
                permissionInfo.usage()
            }
        }
    }
}
