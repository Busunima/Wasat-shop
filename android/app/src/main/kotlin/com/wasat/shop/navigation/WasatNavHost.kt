package com.wasat.shop.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.wasat.shop.feature.admin.InventoryScreen
import com.wasat.shop.feature.analytics.AnalyticsScreen
import com.wasat.shop.feature.admin.MyProductsScreen
import com.wasat.shop.feature.admin.ProductEditScreen
import com.wasat.shop.feature.admin.CategoriesScreen
import com.wasat.shop.feature.admin.PromocodesScreen
import com.wasat.shop.feature.admin.BroadcastScreen
import com.wasat.shop.feature.admin.StaffScreen
import com.wasat.shop.feature.admin.StoreSettingsScreen
import com.wasat.shop.feature.auth.AuthRepository
import com.wasat.shop.feature.auth.SignInScreen
import com.wasat.shop.feature.cart.CartScreen
import com.wasat.shop.feature.catalog.CatalogScreen
import com.wasat.shop.feature.catalog.ProductDetailScreen
import com.wasat.shop.feature.home.HomeScreen
import com.wasat.shop.feature.notifications.NotificationCenterScreen
import com.wasat.shop.feature.onboarding.OnboardingScreen
import com.wasat.shop.feature.orders.CheckoutScreen
import com.wasat.shop.feature.orders.MyOrdersScreen
import com.wasat.shop.feature.orders.RequestReturnScreen
import com.wasat.shop.feature.orders.StoreOrdersScreen
import com.wasat.shop.feature.orders.StoreReturnsScreen
import com.wasat.shop.feature.orders.WriteReviewScreen
import com.wasat.shop.feature.storefront.StoreResolverScreen
import com.wasat.shop.feature.wishlist.WishlistScreen

object Routes {
    const val AUTH = "auth"
    const val ONBOARDING = "onboarding"
    const val HOME = "home?slug={slug}"
    const val CATALOG = "catalog/{storeId}?currency={currency}"
    const val PRODUCT = "product/{storeId}/{productId}?currency={currency}"
    const val CART = "cart/{storeId}?currency={currency}"
    const val CHECKOUT = "checkout/{storeId}?currency={currency}"
    const val MY_ORDERS = "myorders/{storeId}?currency={currency}"
    const val STORE_ORDERS = "storeorders/{storeId}?currency={currency}"
    const val WRITE_REVIEW = "review/{storeId}/{productId}/{orderId}"
    const val REQUEST_RETURN = "return/{storeId}/{orderId}?currency={currency}"
    const val STORE_RETURNS = "storereturns/{storeId}?currency={currency}"
    const val MY_PRODUCTS = "myproducts/{storeId}?currency={currency}"
    const val PRODUCT_EDIT = "productedit/{storeId}?currency={currency}&productId={productId}"
    const val STORE_SETTINGS = "storesettings/{storeId}?currency={currency}"
    const val INVENTORY = "inventory/{storeId}"
    const val CATEGORIES = "categories/{storeId}"
    const val PROMOCODES = "promocodes/{storeId}?currency={currency}"
    const val STAFF = "staff/{storeId}"
    const val BROADCAST = "broadcast/{storeId}"
    const val ANALYTICS = "analytics/{storeId}?currency={currency}"
    const val WISHLIST = "wishlist/{storeId}?currency={currency}"
    const val STORE_BY_SLUG = "store/{slug}"
    const val NOTIFICATIONS = "notifications"

    fun home(slug: String?): String = if (slug != null) "home?slug=$slug" else "home"
    fun catalog(storeId: String, currency: String): String = "catalog/$storeId?currency=$currency"
    fun product(storeId: String, productId: String, currency: String): String =
        "product/$storeId/$productId?currency=$currency"
    fun cart(storeId: String, currency: String): String = "cart/$storeId?currency=$currency"
    fun checkout(storeId: String, currency: String): String =
        "checkout/$storeId?currency=$currency"
    fun myOrders(storeId: String, currency: String): String =
        "myorders/$storeId?currency=$currency"
    fun storeOrders(storeId: String, currency: String): String =
        "storeorders/$storeId?currency=$currency"
    fun writeReview(storeId: String, productId: String, orderId: String): String =
        "review/$storeId/$productId/$orderId"
    fun requestReturn(storeId: String, orderId: String, currency: String): String =
        "return/$storeId/$orderId?currency=$currency"
    fun storeReturns(storeId: String, currency: String): String =
        "storereturns/$storeId?currency=$currency"
    fun myProducts(storeId: String, currency: String): String =
        "myproducts/$storeId?currency=$currency"
    fun productEdit(storeId: String, currency: String, productId: String?): String =
        "productedit/$storeId?currency=$currency" +
            (productId?.let { "&productId=$it" } ?: "")
    fun storeSettings(storeId: String, currency: String): String =
        "storesettings/$storeId?currency=$currency"
    fun inventory(storeId: String): String = "inventory/$storeId"
    fun categories(storeId: String): String = "categories/$storeId"
    fun promocodes(storeId: String, currency: String): String =
        "promocodes/$storeId?currency=$currency"
    fun staff(storeId: String): String = "staff/$storeId"
    fun broadcast(storeId: String): String = "broadcast/$storeId"
    fun analytics(storeId: String, currency: String): String =
        "analytics/$storeId?currency=$currency"
    fun wishlist(storeId: String, currency: String): String =
        "wishlist/$storeId?currency=$currency"
    fun storeBySlug(slug: String): String = "store/$slug"
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
                onOpenOrders = { storeId, currency ->
                    navController.navigate(Routes.storeOrders(storeId, currency))
                },
                onOpenReturns = { storeId, currency ->
                    navController.navigate(Routes.storeReturns(storeId, currency))
                },
                onOpenSettings = { storeId, currency ->
                    navController.navigate(Routes.storeSettings(storeId, currency))
                },
                onOpenInventory = { storeId ->
                    navController.navigate(Routes.inventory(storeId))
                },
                onOpenCategories = { storeId ->
                    navController.navigate(Routes.categories(storeId))
                },
                onOpenPromocodes = { storeId, currency ->
                    navController.navigate(Routes.promocodes(storeId, currency))
                },
                onOpenStaff = { storeId ->
                    navController.navigate(Routes.staff(storeId))
                },
                onOpenBroadcast = { storeId ->
                    navController.navigate(Routes.broadcast(storeId))
                },
                onOpenAnalytics = { storeId, currency ->
                    navController.navigate(Routes.analytics(storeId, currency))
                },
                onOpenStore = { slug ->
                    navController.navigate(Routes.storeBySlug(slug))
                },
                onOpenNotifications = {
                    navController.navigate(Routes.NOTIFICATIONS)
                },
            )
        }

        composable(Routes.NOTIFICATIONS) { NotificationCenterScreen() }

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
                onOpenWishlist = {
                    navController.navigate(Routes.wishlist(storeId, currency))
                },
            )
        }

        composable(
            route = Routes.PRODUCT,
            arguments = listOf(currencyArg),
        ) { backStackEntry ->
            val storeId = backStackEntry.arguments?.getString("storeId").orEmpty()
            val currency = backStackEntry.arguments?.getString("currency") ?: "USD"
            ProductDetailScreen(
                currency = currency,
                // FR-B12: переход на похожий товар
                onProductClick = { productId ->
                    navController.navigate(Routes.product(storeId, productId, currency))
                },
            )
        }

        composable(
            route = Routes.CART,
            arguments = listOf(currencyArg),
        ) { backStackEntry ->
            val storeId = backStackEntry.arguments?.getString("storeId").orEmpty()
            val currency = backStackEntry.arguments?.getString("currency") ?: "USD"
            CartScreen(
                currency = currency,
                onCheckout = { navController.navigate(Routes.checkout(storeId, currency)) },
                onMyOrders = { navController.navigate(Routes.myOrders(storeId, currency)) },
            )
        }

        // FR-B05: оформление заказа из корзины
        composable(
            route = Routes.CHECKOUT,
            arguments = listOf(currencyArg),
        ) { backStackEntry ->
            val storeId = backStackEntry.arguments?.getString("storeId").orEmpty()
            val currency = backStackEntry.arguments?.getString("currency") ?: "USD"
            CheckoutScreen(
                onPlaced = {
                    navController.navigate(Routes.myOrders(storeId, currency)) {
                        popUpTo(Routes.CHECKOUT) { inclusive = true }
                    }
                },
            )
        }

        // FR-B06: история заказов покупателя
        composable(
            route = Routes.MY_ORDERS,
            arguments = listOf(currencyArg),
        ) { backStackEntry ->
            val storeId = backStackEntry.arguments?.getString("storeId").orEmpty()
            val currency = backStackEntry.arguments?.getString("currency") ?: "USD"
            MyOrdersScreen(
                onWriteReview = { productId, orderId ->
                    navController.navigate(Routes.writeReview(storeId, productId, orderId))
                },
                onRequestReturn = { orderId ->
                    navController.navigate(Routes.requestReturn(storeId, orderId, currency))
                },
                onOpenCart = {
                    navController.navigate(Routes.cart(storeId, currency))
                },
            )
        }

        // FR-A04: заказы магазина (владелец/сотрудник)
        composable(
            route = Routes.STORE_ORDERS,
            arguments = listOf(currencyArg),
        ) {
            StoreOrdersScreen()
        }

        // FR-B08: форма отзыва о товаре из полученного заказа
        composable(route = Routes.WRITE_REVIEW) {
            WriteReviewScreen(onDone = { navController.popBackStack() })
        }

        // FR-B09: заявка на возврат покупателем
        composable(
            route = Routes.REQUEST_RETURN,
            arguments = listOf(currencyArg),
        ) {
            RequestReturnScreen(onDone = { navController.popBackStack() })
        }

        // FR-A11: очередь возвратов магазина (владелец/сотрудник)
        composable(
            route = Routes.STORE_RETURNS,
            arguments = listOf(currencyArg),
        ) {
            StoreReturnsScreen()
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

        // FR-A01: настройки магазина (владелец)
        composable(
            route = Routes.STORE_SETTINGS,
            arguments = listOf(currencyArg),
        ) {
            StoreSettingsScreen(onSaved = { navController.popBackStack() })
        }

        // FR-A03: инвентарь (владелец)
        composable(route = Routes.INVENTORY) {
            InventoryScreen()
        }

        // FR-A01: категории магазина (владелец)
        composable(route = Routes.CATEGORIES) {
            CategoriesScreen()
        }

        // FR-A06: промокоды (владелец)
        composable(
            route = Routes.PROMOCODES,
            arguments = listOf(currencyArg),
        ) {
            PromocodesScreen()
        }

        // FR-A09: сотрудники и роли (владелец)
        composable(route = Routes.STAFF) {
            StaffScreen()
        }

        // FR-A07: broadcast-рассылка покупателям (владелец)
        composable(route = Routes.BROADCAST) {
            BroadcastScreen()
        }

        // FR-A05: дашборд аналитики (владелец)
        composable(
            route = Routes.ANALYTICS,
            arguments = listOf(currencyArg),
        ) { backStackEntry ->
            val currency = backStackEntry.arguments?.getString("currency") ?: "USD"
            AnalyticsScreen(currency = currency)
        }

        // FR-B07: вишлист покупателя
        composable(
            route = Routes.WISHLIST,
            arguments = listOf(currencyArg),
        ) { backStackEntry ->
            val storeId = backStackEntry.arguments?.getString("storeId").orEmpty()
            val currency = backStackEntry.arguments?.getString("currency") ?: "USD"
            WishlistScreen(
                currency = currency,
                onProductClick = { productId ->
                    navController.navigate(Routes.product(storeId, productId, currency))
                },
            )
        }

        // FR-B01: открытие чужой витрины по slug — deep link (myapp://store/{slug},
        // https://app.example.com/s/{slug}) и QR/кэш через навигацию.
        composable(
            route = Routes.STORE_BY_SLUG,
            arguments = listOf(navArgument("slug") { type = NavType.StringType }),
            deepLinks = listOf(
                navDeepLink { uriPattern = "myapp://store/{slug}" },
                navDeepLink { uriPattern = "https://app.example.com/s/{slug}" },
            ),
        ) {
            StoreResolverScreen(
                onResolved = { storeId, currency ->
                    navController.navigate(Routes.catalog(storeId, currency)) {
                        popUpTo(Routes.STORE_BY_SLUG) { inclusive = true }
                    }
                },
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Routes.home(null))
                    }
                },
            )
        }
    }
}
