package com.kishan.preptracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kishan.preptracker.ui.AppViewModel
import com.kishan.preptracker.ui.components.RestTimerBar
import com.kishan.preptracker.ui.screens.*
import com.kishan.preptracker.ui.theme.GritOrange
import com.kishan.preptracker.ui.theme.PrepTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { PrepTheme { App(vm) } }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshPermissionState()
        if (vm.profile.value.hcAutoSync) vm.syncNow()
    }
}

@Composable
fun App(vm: AppViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: "today"
    val timerLeft by vm.timerLeft.collectAsStateWithLifecycle()
    val timerTotal by vm.timerTotal.collectAsStateWithLifecycle()
    val restFlash by vm.restFlashNow.collectAsStateWithLifecycle()

    val permLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        vm.refreshPermissionState()
        if (granted.containsAll(vm.hc.permissions)) vm.syncNow(daysBack = 30)
    }
    LaunchedEffect(Unit) { vm.refreshPermissionState() }

    val tabs = listOf(
        Triple("today", "Today", Icons.Default.Today),
        Triple("train", "Train", Icons.Default.FitnessCenter),
        Triple("trends", "Trends", Icons.AutoMirrored.Filled.ShowChart),
        Triple("review", "Review", Icons.Default.Checklist),
        Triple("plan", "Plan", Icons.Default.Settings),
    )

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                tabs.forEach { (r, label, icon) ->
                    NavigationBarItem(
                        selected = route == r,
                        onClick = { nav.navigate(r) { launchSingleTop = true; popUpTo("today") } },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            NavHost(nav, startDestination = "today") {
                composable("today") { TodayScreen(vm) }
                composable("train") { TrainScreen(vm) }
                composable("trends") { TrendsScreen(vm) }
                composable("review") { ReviewScreen(vm) }
                composable("plan") { PlanScreen(vm, onRequestPermissions = { permLauncher.launch(vm.hc.permissions) }) }
            }
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)) {
                RestTimerBar(timerLeft, timerTotal, onStop = { vm.stopTimer() }, onPlus = { vm.addTimer(30) })
            }
        }
    }
    if (restFlash) {
        Box(Modifier.fillMaxSize().background(GritOrange.copy(alpha = 0.42f)))
    }
    }
}
