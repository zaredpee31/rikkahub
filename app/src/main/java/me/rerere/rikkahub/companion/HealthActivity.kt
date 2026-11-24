package me.rerere.rikkahub.companion

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController

/** 从通知点进来就弹 Health Connect 授权；在 Health Connect 里点这个 app 的隐私说明也落到这里。 */
class HealthActivity : ComponentActivity() {
    private val ask = registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        val ok = HomeHealth.READS.any { it in granted }
        Toast.makeText(this, if (ok) "授权好了，阿辞一会儿就去读" else "没有授权", Toast.LENGTH_SHORT).show()
        if (ok) HomeHealth.syncSoon()
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val act = intent?.action
        val rationale = act == "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" || act == "android.intent.action.VIEW_PERMISSION_USAGE"
        if (rationale || HealthConnectClient.getSdkStatus(this) != HealthConnectClient.SDK_AVAILABLE) {
            setContentView(TextView(this).apply {
                setPadding(48, 160, 48, 48)
                textSize = 16f
                text = if (rationale) HomeHealth.RATIONALE else "这台手机上的 Health Connect 现在用不了。"
            })
            return
        }
        if (savedInstanceState == null) ask.launch(HomeHealth.PERMS)
    }
}
