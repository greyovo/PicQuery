package me.grey.picquery.ui

import android.net.Uri
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
import me.grey.picquery.ui.photoDetail.PhotoDetailScreen
import me.grey.picquery.ui.indexmgr.IndexMgrScreen
import me.grey.picquery.ui.search.SearchScreen
import me.grey.picquery.ui.setting.SettingScreen
import me.grey.picquery.ui.simlilar.SimilarPhotosScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.Saver
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.grey.picquery.data.model.Photo
import me.grey.picquery.data.model.PhotoItem
import me.grey.picquery.ui.simlilar.LocalSimilarityConfig
import me.grey.picquery.ui.simlilar.SimilarityConfiguration
import timber.log.Timber

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.Home.name,
) {
    val similarityConfigSaver = Saver<SimilarityConfiguration, List<Float>>(
        save = { listOf(
            it.searchImageSimilarityThreshold,
            it.similarityGroupDelta,
            it.minSimilarityGroupSize.toFloat()
        )},
        restore = { saved ->
            SimilarityConfiguration(
                searchImageSimilarityThreshold = saved[0],
                similarityGroupDelta = saved[1],
                minSimilarityGroupSize = saved[2].toInt()
            )
        }
    )

    var similarityConfig by rememberSaveable(
        stateSaver = similarityConfigSaver
    ) {
        mutableStateOf(
            SimilarityConfiguration(
                searchImageSimilarityThreshold = 0.96f,
                similarityGroupDelta = 0.04f,
                minSimilarityGroupSize = 2
            )
        )
    }

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
                navigateToSimilar = { navController.navigate(Routes.Similar.name) },
            )
        }
        composable("${Routes.Search.name}/{query}") {
            val queryText = it.arguments?.getString("query") ?: ""
            SearchScreen(
                initialQuery = queryText,
                onNavigateBack = { navController.popBackStack() },
                onClickPhoto = { _, index ->
                    Timber.tag("AppNavHost").d("onClickPhoto: $index")
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
        composable(Routes.IndexMgr.name) {
            IndexMgrScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(Routes.Similar.name) {
            CompositionLocalProvider(LocalSimilarityConfig provides similarityConfig) {
                SimilarPhotosScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onPhotoClick = { groupIndex,_, photos ->
                        navController.navigate("${Routes.PhotoDetail.name}/${groupIndex}")
                    },
                    onConfigUpdate = { newSearchThreshold, newSimilarityDelta, newMinGroupSize ->
                        similarityConfig = SimilarityConfiguration(
                            searchImageSimilarityThreshold = newSearchThreshold,
                            similarityGroupDelta = newSimilarityDelta,
                            minSimilarityGroupSize = newMinGroupSize
                        )
                    }
                )
            }
        }
        composable(
            Routes.Setting.name,
            enterTransition = { popInAnimation },
            popEnterTransition = { popInAnimation },
            exitTransition = { navigateUpAnimation },
        ) {
            SettingScreen(
                onNavigateBack = { navController.popBackStack() },
                navigateToIndexMgr = {
                    navController.navigate(Routes.IndexMgr.name)
                }
            )
        }

        composable(Routes.PhotoDetail.name + "/{groupIndex}") { backStackEntry ->
            val groupIndex = backStackEntry.arguments?.getString("groupIndex")?.toIntOrNull() ?: 0

            PhotoDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                initialPage = groupIndex,
                groupIndex = groupIndex,
            )
        }

    }
}