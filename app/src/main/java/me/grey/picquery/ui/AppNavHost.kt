package me.grey.picquery.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import me.grey.picquery.common.Animation.navigateInAnimation
import me.grey.picquery.common.Animation.navigateUpAnimation
import me.grey.picquery.common.Routes
import me.grey.picquery.ui.display.DisplayScreen
import me.grey.picquery.ui.home.HomeScreen
import me.grey.picquery.ui.indexmgr.IndexMgrScreen
import me.grey.picquery.ui.search.SearchScreen
import me.grey.picquery.ui.setting.SettingScreen

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.Home.name,
) {
    NavHost(
        navController,
        startDestination = startDestination,
        // TODO: Animation when switching screens
        enterTransition = { navigateInAnimation },
        exitTransition = { navigateUpAnimation },
//        popEnterTransition = { navigateInAnimation },
//        popExitTransition = { navigateUpAnimation },
    ) {
        composable(Routes.Home.name) {
            HomeScreen(
                navigateToSearch = { query ->
                    navController.navigate("${Routes.Search.name}/${query}")
                },
                navigateToSetting = { navController.navigate(Routes.Setting.name) },
            )
        }
        composable("${Routes.Search.name}/{query}") {
            val queryText = it.arguments?.getString("query") ?: ""
            SearchScreen(
                initialQuery = queryText,
                onNavigateBack = { navController.popBackStack() },
                onClickPhoto = { photo, index ->
                    // TODO
                    navController.navigate("${Routes.Display.name}/${index}")
                },
            )
        }
        composable(
            "${Routes.Display.name}/{index}",
            arguments = listOf(navArgument("index") { type = NavType.IntType })
        ) {
            val initialIndex: Int = it.arguments?.getInt("index") ?: 0
            DisplayScreen(
                initialPage = initialIndex,
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }
        composable(Routes.Setting.name) {
            SettingScreen(
                onNavigateBack = { navController.popBackStack() },
                navigateToIndexMgr = {
                    navController.navigate(Routes.IndexMgr.name)
                }
            )
        }
        composable(Routes.IndexMgr.name) {
            IndexMgrScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}