package com.example.adhigam.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.adhigam.data.AppContainer
import com.example.adhigam.data.model.UserRole
import com.example.adhigam.ui.login.LoginScreen
import com.example.adhigam.ui.main.MainScreen

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val authRepository = AppContainer.authRepository
    // Like the web portal: a saved, unexpired login skips the login page.
    val startDestination = remember {
        authRepository.currentSession()?.let { Routes.main(it.role.name) } ?: Routes.LOGIN
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = { role ->
                    navController.navigate(Routes.main(role.name)) {
                        // Back from the main screen should close the app, not return to login.
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = Routes.MAIN,
            arguments = listOf(navArgument(Routes.ARG_ROLE) { type = NavType.StringType })
        ) { entry ->
            val role = entry.arguments?.getString(Routes.ARG_ROLE)
                ?.let { runCatching { UserRole.valueOf(it) }.getOrNull() }
                ?: UserRole.Student
            MainScreen(
                role = role,
                onLogout = {
                    authRepository.logout()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
