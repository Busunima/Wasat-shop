package com.wasat.shop.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wasat.shop.feature.admin.MyProductsScreen
import com.wasat.shop.feature.admin.ProductEditScreen
import com.wasat.shop.feature.auth.AuthRepository
import com.wasat.shop.feature.auth.SignInScreen
import com.wasat.shop.feature.cart.CartScreen
import com.wasat.shop.feature.catalog.CatalogScreen
import com.wasat.shop.feature.catalog.ProductDetailScreen
import com.wasat.shop.feature.home.HomeScreen
import com.wasat.shop.feature.onboarding.OnboardingScreen

object Routes {
    const val AUTH = "auth"
    const val ONBOARDING = "onboarding"
    const val HOME = "home?slug={slug}"
    const val CATALOG = "catalog/{storeId}?currency={currency}"
    const val PRODUCT = "product/{storeId}/{productId}?currency={currency}"
    const val CART = "cart/{storeId}?currency={currency}"
    const val MY_PRODUCTS = "myproducts/{storeId}?currency={currency}"
    const val PRODUCT_EDIT = "productedit/{storeId}?currency={currency}&productId={productId}"

    fun home(slug: String?): String = if (slug != null) "home?slug=$slug" else "home"
    fun catalog(storeId: String, currency: String): String = "catalog/$storeId?currency=$currency"
    fun product(storeId: String, productId: String, currency: String): String =
        "product/$storeId/$productId?currency=$currency"
    fun cart(storeId: String, currency: String): String = "cart/$storeId?currency=$currency"
    fun myProducts(storeId: String, currency: String): String =
        "myproducts/$storeId?currency=$currency"
    fun productEdit(storeId: String, currency: String, productId: String?): String =
        "productedit/$storeId?currency=$currency" +
            (productId?.let { "&productId=$it" } ?: "")
}

private val currencyArg = navArgument("currency") {
    type = NavType.StringType
    defaultValue = "USD"
}

/**
 * Граф навигации: auth → onboarding → home → catalog → product.
 * Маршрутизация по custom claims (owner → админ-режим) — уточнение Фазы 2.
 */
@Composable
fun WasatNavHost(authRepository: AuthRepository) {
    val navController = rememberNavController()
    val startDestination =
        if (authRepository.currentUser() == null) Routes.AUTH else Routes.ONBOARDING

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.AUTH) {
            SignInScreen(
                onSignedIn = {
                    navController.navigate(Routes.ONBOARDING) {
                        popUpTo(Routes.AUTH) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onStoreCreated = { slug ->
                    navController.navigate(Routes.home(slug)) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
                onNavigateToSignIn = {
                    navController.navigate(Routes.AUTH) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = Routes.HOME,
            arguments = listOf(
                navArgument("slug") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            HomeScreen(
                slug = backStackEntry.arguments?.getString("slug"),
                onOpenCatalog = { storeId, currency ->
                    navController.navigate(Routes.catalog(storeId, currency))
                },
                onOpenMyProducts = { storeId, currency ->
                    navController.navigate(Routes.myProducts(storeId, currency))
                },
            )
        }

        composable(
            route = Routes.CATALOG,
            arguments = listOf(currencyArg),
        ) { backStackEntry ->
            val storeId = backStackEntry.arguments?.getString("storeId").orEmpty()
            val currency = backStackEntry.arguments?.getString("currency") ?: "USD"
            CatalogScreen(
                currency = currency,
                onProductClick = { productId ->
                    navController.navigate(Routes.product(storeId, productId, currency))
                },
                onOpenCart = {
                    navController.navigate(Routes.cart(storeId, currency))
                },
            )
        }

        composable(
            route = Routes.PRODUCT,
            arguments = listOf(currencyArg),
        ) { backStackEntry ->
            val currency = backStackEntry.arguments?.getString("currency") ?: "USD"
            ProductDetailScreen(currency = currency)
        }

        composable(
            route = Routes.CART,
            arguments = listOf(currencyArg),
        ) { backStackEntry ->
            val currency = backStackEntry.arguments?.getString("currency") ?: "USD"
            CartScreen(currency = currency)
        }

        composable(
            route = Routes.MY_PRODUCTS,
            arguments = listOf(currencyArg),
        ) { backStackEntry ->
            val storeId = backStackEntry.arguments?.getString("storeId").orEmpty()
            val currency = backStackEntry.arguments?.getString("currency") ?: "USD"
            MyProductsScreen(
                currency = currency,
                onAddProduct = {
                    navController.navigate(Routes.productEdit(storeId, currency, null))
                },
                onEditProduct = { productId ->
                    navController.navigate(Routes.productEdit(storeId, currency, productId))
                },
            )
        }

        composable(
            route = Routes.PRODUCT_EDIT,
            arguments = listOf(
                currencyArg,
                navArgument("productId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            ProductEditScreen(onSaved = { navController.popBackStack() })
        }
    }
}
