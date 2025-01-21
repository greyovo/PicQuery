package me.grey.picquery.ui

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import me.grey.picquery.common.Animation.navigateInAnimation
import me.grey.picquery.common.Animation.navigateUpAnimation
import me.grey.picquery.common.Animation.popInAnimation
import me.grey.picquery.common.Routes
import me.grey.picquery.ui.display.DisplayScreen
import me.grey.picquery.ui.home.HomeScreen
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
        enterTransition = { navigateInAnimation },
        exitTransition = { navigateUpAnimation },
    ) {
        composable(Routes.Home.name) {
            HomeScreen(
                modifier = modifier,
                navigateToSearch = { query ->
                    navController.navigate("${Routes.Search.name}/${query}")
                },
                navigateToSearchWitImage = {
                    val query = Uri.encode(it.toString())
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
                onClickPhoto = { _, index ->
                    Log.d("AppNavHost", "onClickPhoto: $index")
                    navController.navigate("${Routes.Display.name}/${index}")
                },
            )
        }
        composable(
            "${Routes.Display.name}/{index}",
        ) {
            val initialIndex: Int = it.arguments?.getString("index")?.toInt() ?: 0
            DisplayScreen(
                initialPage = initialIndex,
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }
        composable(
            Routes.Setting.name,
            enterTransition = { popInAnimation },
            popEnterTransition = { popInAnimation },
            exitTransition = { navigateUpAnimation },
        ) {
            SettingScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}