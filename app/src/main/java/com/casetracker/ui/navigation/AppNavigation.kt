package com.casetracker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.casetracker.ui.detail.CaseDetailScreen
import com.casetracker.ui.edit.CaseEditScreen
import com.casetracker.ui.main.MainScaffold

object Routes {
    const val MAIN = "main"
    const val DETAIL = "detail/{caseId}"
    const val EDIT = "edit?caseId={caseId}"
    const val CREATE = "edit"

    fun detail(caseId: Long) = "detail/$caseId"
    fun edit(caseId: Long?) = if (caseId != null) "edit?caseId=$caseId" else "edit"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.MAIN) {
        composable(Routes.MAIN) {
            MainScaffold(
                onOpenCase = { id -> navController.navigate(Routes.detail(id)) },
                onCreateCase = { navController.navigate(Routes.CREATE) },
                onEditCase = { id -> navController.navigate(Routes.edit(id)) }
            )
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("caseId") { type = NavType.LongType })
        ) { backStackEntry ->
            val caseId = backStackEntry.arguments?.getLong("caseId") ?: 0L
            CaseDetailScreen(
                caseId = caseId,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Routes.edit(caseId)) }
            )
        }
        composable(
            route = Routes.EDIT,
            arguments = listOf(navArgument("caseId") {
                type = NavType.LongType
                defaultValue = 0L
            })
        ) { backStackEntry ->
            val caseId = backStackEntry.arguments?.getLong("caseId") ?: 0L
            CaseEditScreen(
                caseId = caseId.takeIf { it > 0 },
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }
    }
}
