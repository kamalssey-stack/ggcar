package com.cartunepro.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.cartunepro.app.data.TelemetryViewModel
import com.cartunepro.app.ui.AppRoot
import com.cartunepro.app.ui.CarTuneTheme

class MainActivity : ComponentActivity() {

    private val vm: TelemetryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            CarTuneTheme {
                AppRoot(vm)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        vm.resume(granted)
    }

    override fun onStop() {
        vm.pause()
        super.onStop()
    }
}
