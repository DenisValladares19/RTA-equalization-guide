package org.rtaguide.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.rtaguide.app.ui.AnalyzerScreen
import org.rtaguide.app.ui.CurvesScreen
import org.rtaguide.app.ui.EqScreen
import org.rtaguide.app.ui.RtaTheme
import org.rtaguide.app.ui.SessionsScreen
import org.rtaguide.app.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    private val viewModel: AnalyzerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RtaTheme { RtaApp(viewModel) } }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) viewModel.onBackground()
    }

    override fun onStart() {
        super.onStart()
        if (viewModel.state.value.hasPermission && !viewModel.state.value.running) viewModel.start()
    }
}

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Analizador", Icons.Default.Home),
    Tab("EQ", Icons.Default.Build),
    Tab("Curvas", Icons.Default.Edit),
    Tab("Sesiones", Icons.Default.List),
    Tab("Ajustes", Icons.Default.Settings),
)

@Composable
fun RtaApp(vm: AnalyzerViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.onPermissionResult(granted)
    }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) vm.onPermissionResult(true) else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    val view = LocalView.current
    val keepOn = state.settings.keepScreenOn && state.running
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        val msg = state.message
        if (msg != null) {
            vm.dismissMessage()
            snackbar.showSnackbar(msg)
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> AnalyzerScreen(state, vm)
                1 -> EqScreen(state, vm)
                2 -> CurvesScreen(state, vm)
                3 -> SessionsScreen(state, vm)
                else -> SettingsScreen(state, vm)
            }
        }
    }
}
