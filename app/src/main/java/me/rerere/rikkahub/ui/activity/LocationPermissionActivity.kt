package me.rerere.rikkahub.ui.activity

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import me.rerere.rikkahub.data.ai.tools.LocationGate

/** 透明页：nearby_places 第一次用时弹系统的定位权限框，她点完就关 */
class LocationPermissionActivity : ComponentActivity() {
    private val launcher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        LocationGate.pending?.complete(r.values.any { it })
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    override fun onDestroy() {
        if (isFinishing) LocationGate.pending?.complete(LocationGate.granted(this))
        super.onDestroy()
    }
}
