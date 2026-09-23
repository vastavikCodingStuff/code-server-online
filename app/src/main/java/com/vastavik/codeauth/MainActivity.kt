package com.vastavik.codeauth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vastavik.codeauth.data.SecurePrefs
import com.vastavik.codeauth.ui.screens.ScannerScreen
import com.vastavik.codeauth.ui.screens.SessionsScreen
import com.vastavik.codeauth.ui.screens.SettingsScreen
import com.vastavik.codeauth.ui.screens.UpdateScreen
import com.vastavik.codeauth.ui.theme.BackgroundDark
import com.vastavik.codeauth.ui.theme.CodeAuthTheme
import com.vastavik.codeauth.ui.theme.PrimaryCyan
import com.vastavik.codeauth.ui.theme.SurfaceDark

/**
 * MainActivity.kt — Vastavik Authenticator
 * Bottom nav: [Scan] [Sessions] [Settings] + Top-Right Update button (#22C55E) → UpdateScreen
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val securePrefs = SecurePrefs.getInstance(this)
        setContent {
            CodeAuthTheme(darkTheme = true, dynamicColor = false) {
                CodeAuthNavHost(securePrefs)
            }
        }
    }
}

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Scanner : Screen("scanner", "Scan", Icons.Filled.QrCodeScanner)
    data object Sessions : Screen("sessions", "Sessions", Icons.Filled.Dashboard)
    data object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
    data object Update : Screen("update", "Update", Icons.Filled.Update)
}

@Composable
fun CodeAuthNavHost(securePrefs: SecurePrefs) {
    val navController = rememberNavController()
    val bottomItems = listOf(Screen.Scanner, Screen.Sessions, Screen.Settings)

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = currentRoute != Screen.Update.route

    Scaffold(
        containerColor = BackgroundDark,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = SurfaceDark) {
                    bottomItems.forEach { screen ->
                        val selected = currentRoute == screen.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = PrimaryCyan,
                                selectedTextColor = PrimaryCyan,
                                indicatorColor = PrimaryCyan.copy(alpha = 0.18f),
                                unselectedIconColor = androidx.compose.ui.graphics.Color(0xFF94A3B8),
                                unselectedTextColor = androidx.compose.ui.graphics.Color(0xFF94A3B8)
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Scanner.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            composable(Screen.Scanner.route) {
                ScannerScreen(
                    securePrefs = securePrefs,
                    onNavigateToUpdate = { navController.navigate(Screen.Update.route) }
                )
            }
            composable(Screen.Sessions.route) {
                SessionsScreen(
                    securePrefs = securePrefs,
                    onNavigateToUpdate = { navController.navigate(Screen.Update.route) }
                )
            }
            composable(Screen.Settings.route) { SettingsScreen(securePrefs) }
            composable(Screen.Update.route) {
                UpdateScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

/**
 * Reusable green pill Update button for TopAppBar — #22C55E with white icon/text
 */
@Composable
fun UpdateActionButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = androidx.compose.ui.graphics.Color(0xFF22C55E),
            contentColor = androidx.compose.ui.graphics.Color.White
        ),
        shape = RoundedCornerShape(50),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
        modifier = modifier.height(36.dp)
    ) {
        Icon(Icons.Filled.Update, contentDescription = "Update", modifier = Modifier.padding(end = 6.dp))
        Text("Update", style = MaterialTheme.typography.labelLarge)
    }
}
