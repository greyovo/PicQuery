@file:OptIn(ExperimentalPermissionsApi::class)

package me.grey.picquery.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import me.grey.picquery.ui.Animation.navigateInAnimation
import me.grey.picquery.ui.Animation.navigateUpAnimation
import me.grey.picquery.ui.display.DisplayScreen
import me.grey.picquery.ui.home.HomeScreen
import me.grey.picquery.ui.search.SearchScreen

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
                navigateToSetting = { /* TODO */ },
            )
        }
        composable("${Routes.Search.name}/{query}") {
            val queryText = it.arguments?.getString("query") ?: ""
            SearchScreen(
                initialQuery = queryText,
                onBack = { navController.popBackStack() },
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
            DisplayScreen(initialPage = initialIndex)
        }
    }
}

enum class Routes {
    Home,
    Search,
    Display
}

object Animation {
    /**
     * Value in ms
     */
    private const val DEFAULT_LOW_VELOCITY_SWIPE_DURATION = 150

    private const val DEFAULT_NAVIGATION_ANIMATION_DURATION = 300

    val navigateInAnimation = fadeIn(tween(DEFAULT_NAVIGATION_ANIMATION_DURATION))
    val navigateUpAnimation = fadeOut(tween(DEFAULT_NAVIGATION_ANIMATION_DURATION))

    fun enterAnimation(durationMillis: Int): EnterTransition =
        fadeIn(tween(durationMillis))

    fun exitAnimation(durationMillis: Int): ExitTransition =
        fadeOut(tween(durationMillis))
}