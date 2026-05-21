package com.routesnap.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.routesnap.app.ui.picker.PhotoPickerScreen
import com.routesnap.app.ui.render.RenderScreen
import com.routesnap.app.ui.share.ShareScreen
import com.routesnap.app.ui.style.StyleScreen
import com.routesnap.app.ui.theme.RouteSnapTheme
import com.routesnap.app.ui.timeline.TimelineScreen
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main Activity - Entry point for the app
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RouteSnapTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    RouteSnapNavGraph(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

/**
 * Navigation graph for the app
 */
@Composable
fun RouteSnapNavGraph(
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "picker",
        modifier = modifier,
    ) {
        composable("picker") {
            PhotoPickerScreen(
                onNavigateToTimeline = { tripId ->
                    navController.navigate("timeline/$tripId")
                },
            )
        }

        composable(
            route = "timeline/{tripId}",
            arguments =
                listOf(
                    navArgument("tripId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
        ) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId")
            TimelineScreen(
                tripId = tripId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToStyle = { navController.navigate("style/$tripId") },
            )
        }

        composable(
            route = "style/{tripId}",
            arguments =
                listOf(
                    navArgument("tripId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
        ) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId")
            StyleScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToRender = { navController.navigate("render/$tripId") },
            )
        }

        composable(
            route = "render/{tripId}",
            arguments =
                listOf(
                    navArgument("tripId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
        ) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId")
            RenderScreen(
                tripId = tripId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToShare = { outputPath ->
                    // Encode path to handle slashes and special characters
                    val encodedPath = java.net.URLEncoder.encode(outputPath, "UTF-8")
                    navController.navigate("share?videoPath=$encodedPath")
                },
            )
        }

        composable(
            route = "share?videoPath={videoPath}",
            arguments =
                listOf(
                    navArgument("videoPath") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
        ) { backStackEntry ->
            val videoPath = backStackEntry.arguments?.getString("videoPath")
            ShareScreen(
                videoPath = videoPath,
                onNavigateHome = {
                    navController.popBackStack("picker", inclusive = false)
                },
            )
        }
    }
}
