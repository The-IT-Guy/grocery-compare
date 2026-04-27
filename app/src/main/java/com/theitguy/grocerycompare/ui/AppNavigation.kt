package com.theitguy.grocerycompare.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.theitguy.grocerycompare.data.models.Store
import com.theitguy.grocerycompare.ui.screens.*
import com.theitguy.grocerycompare.viewmodel.CompareViewModel

object Routes {
    const val HOME = "home"
    const val RESULTS = "results"
    const val SCANNER = "scanner"
    const val SETTINGS = "settings"
    const val WEBVIEW = "webview/{storeName}/{encodedUrl}"

    fun webView(storeName: String, url: String): String {
        val encoded = java.net.URLEncoder.encode(url, "UTF-8")
        return "webview/$storeName/$encoded"
    }
}

@Composable
fun AppNavigation(
    viewModel: CompareViewModel = viewModel(),
    navController: NavHostController = rememberNavController()
) {
    val uiState by viewModel.uiState.collectAsState()
    val userLocation by viewModel.userLocation.collectAsState()
    val context = LocalContext.current

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            viewModel.updateLocation(context)
        }
    }

    LaunchedEffect(Unit) {
        if (viewModel.hasLocationPermission(context)) {
            viewModel.updateLocation(context)
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = { fadeIn(animationSpec = tween(200)) + slideInHorizontally(initialOffsetX = { 100 }) },
        exitTransition = { fadeOut(animationSpec = tween(200)) },
        popEnterTransition = { fadeIn(animationSpec = tween(200)) + slideInHorizontally(initialOffsetX = { -100 }) },
        popExitTransition = { fadeOut(animationSpec = tween(200)) + slideOutHorizontally(targetOffsetX = { 100 }) }
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                uiState = uiState,
                userLocation = userLocation,
                onQueryChange = { viewModel.updateQuery(it) },
                onSearch = {
                    viewModel.searchByUpc(uiState.searchQuery)
                    navController.navigate(Routes.RESULTS)
                },
                onScanClick = { navController.navigate(Routes.SCANNER) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                onHistoryItemClick = { upc ->
                    viewModel.updateQuery(upc)
                    viewModel.searchByUpc(upc)
                    navController.navigate(Routes.RESULTS)
                },
                onClearHistory = { viewModel.clearHistory() },
                onUpdateLocation = {
                    if (viewModel.hasLocationPermission(context)) {
                        viewModel.updateLocation(context)
                    } else {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                }
            )
        }

        composable(Routes.RESULTS) {
            ResultsScreen(
                uiState = uiState,
                sortedResults = viewModel.getSortedResults(),
                onQueryChange = { viewModel.updateQuery(it) },
                onSearch = { viewModel.searchByUpc(uiState.searchQuery) },
                onScanClick = { navController.navigate(Routes.SCANNER) },
                onSortChange = { viewModel.setSortOption(it) },
                onBackClick = {
                    viewModel.clearResults()
                    navController.popBackStack()
                },
                onOpenWebView = { store, url ->
                    navController.navigate(Routes.webView(store.name, url))
                }
            )
        }

        composable(Routes.SCANNER) {
            ScannerScreen(
                onBarcodeScanned = { barcode ->
                    viewModel.onBarcodeScan(barcode)
                    try {
                        navController.popBackStack()
                        navController.navigate(Routes.RESULTS) { launchSingleTop = true }
                    } catch (_: Exception) { }
                },
                onClose = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                enabledStores = uiState.enabledStores,
                onToggleStore = { viewModel.toggleStore(it) },
                onBackClick = { navController.popBackStack() },
                onOpenMembershipLogin = { store ->
                    val loginUrl = when (store) {
                        Store.SAMS_CLUB -> "https://www.samsclub.com/login"
                        Store.COSTCO -> "https://www.costco.com/LogonForm"
                        else -> store.baseUrl
                    }
                    navController.navigate(Routes.webView(store.name, loginUrl))
                }
            )
        }

        composable(
            route = Routes.WEBVIEW,
            arguments = listOf(
                navArgument("storeName") { type = NavType.StringType },
                navArgument("encodedUrl") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val storeName = backStackEntry.arguments?.getString("storeName") ?: ""
            val encodedUrl = backStackEntry.arguments?.getString("encodedUrl") ?: ""
            val url = java.net.URLDecoder.decode(encodedUrl, "UTF-8")
            val store = try { Store.valueOf(storeName) } catch (_: Exception) { Store.WALMART }

            StoreWebViewScreen(
                store = store,
                initialUrl = url,
                onClose = { navController.popBackStack() }
            )
        }
    }
}
