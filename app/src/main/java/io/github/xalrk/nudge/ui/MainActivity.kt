package io.github.xalrk.nudge.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import android.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.xalrk.nudge.ui.theme.isDark
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.xalrk.nudge.ui.theme.NudgeTheme

class MainActivity : ComponentActivity() {
    private val vm: NudgeViewModel by lazy {
        androidx.lifecycle.ViewModelProvider(this)[NudgeViewModel::class.java]
    }

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        if (savedInstanceState == null) handleImportIntent(intent)
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val dark = isDark(settings.themeMode)
            LaunchedEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            NudgeTheme(settings.themeMode, settings.dynamicColor, settings.accentColor) { NudgeApp(vm) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleImportIntent(intent)
    }

    /** Files opened with or shared to Nudge are imported. */
    private fun handleImportIntent(intent: Intent?) {
        intent ?: return
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM) as? android.net.Uri
            else -> null
        }
        if (uri != null) {
            vm.importFrom(uri)
        } else if (intent.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { vm.importText(it) }
        }
        intent.action = null
    }

    override fun onResume() {
        super.onResume()
        // Catch up on anything missed and make sure the alarm is armed.
        vm.refresh()
    }
}

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    Tab("calendar", "Calendar", Icons.Filled.CalendarMonth),
    Tab("random", "Random", Icons.Filled.Shuffle),
    Tab("settings", "Settings", Icons.Filled.Settings),
)

@Composable
fun NudgeApp(vm: NudgeViewModel = viewModel()) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = tabs.any { it.route == currentRoute }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBar) NavigationBar {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = currentRoute == t.route,
                        onClick = {
                            if (currentRoute != t.route) nav.navigate(t.route) {
                                popUpTo("calendar") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        }
    ) { padding ->
        // consumeWindowInsets stops nested top bars and FABs from re-applying the system bar insets.
        NavHost(nav, startDestination = "calendar", modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            composable("calendar") {
                CalendarScreen(vm, onAdd = { nav.navigate("edit/0?kind=SCHEDULED") }, onOpen = { nav.navigate("edit/$it") })
            }
            composable("random") {
                RandomScreen(vm, onAdd = { nav.navigate("edit/0?kind=RANDOM") }, onOpen = { nav.navigate("edit/$it") })
            }
            composable("settings") { SettingsScreen(vm) }
            composable(
                "edit/{id}?kind={kind}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("kind") { type = NavType.StringType; defaultValue = "SCHEDULED" },
                )
            ) { entry ->
                EditReminderScreen(
                    vm,
                    id = entry.arguments?.getLong("id") ?: 0L,
                    defaultKind = entry.arguments?.getString("kind") ?: "SCHEDULED",
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}
