package com.adb.controller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.adb.controller.ui.ControlScreen
import com.adb.controller.ui.ControlViewModel
import com.adb.controller.ui.MainScreen
import com.adb.controller.ui.MainViewModel
import com.adb.controller.ui.theme.AdbControllerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AdbControllerTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "main"
    ) {
        // 主页面 - 设备列表
        composable("main") {
            val viewModel: MainViewModel = viewModel()
            MainScreen(
                viewModel = viewModel,
                onDeviceClick = { device ->
                    navController.navigate("control/${device.host}/${device.port}")
                }
            )
        }

        // 远程控制页面
        composable(
            route = "control/{host}/{port}",
            arguments = listOf(
                navArgument("host") { type = NavType.StringType },
                navArgument("port") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val host = backStackEntry.arguments?.getString("host") ?: ""
            val port = backStackEntry.arguments?.getInt("port") ?: 5555
            val viewModel: ControlViewModel = viewModel()
            ControlScreen(
                viewModel = viewModel,
                host = host,
                port = port,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
