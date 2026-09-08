package com.example.workpilotmini

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.example.workpilotmini.notification.NotificationHelper
import com.example.workpilotmini.ui.navigation.WorkPilotNavGraph
import com.example.workpilotmini.ui.theme.WorkPilotMiniTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationHelper.ensureChannel(this)
        setContent {
            WorkPilotMiniTheme {
                // WorkPilotNavGraph owns its own Scaffold (with the persistent bottom
                // navigation bar), so it just needs to fill the window here.
                RequestStartupPermissions()
                WorkPilotNavGraph(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/** Asks for location + notification permission once when the app first opens. */
@Composable
private fun RequestStartupPermissions() {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* results not required here; features simply no-op without permission */ }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        launcher.launch(permissions.toTypedArray())
    }
}
