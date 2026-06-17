package com.wasat.shop.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wasat.shop.R

/** Экран «Профиль» (ТЗ §11.5): аккаунт + свои заказы + избранное. */
@Composable
fun ProfileScreen(
    onOpenOrders: () -> Unit,
    onOpenWishlist: () -> Unit,
    onOpenStockNotifications: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val signedIn by viewModel.signedIn.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.profile_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (signedIn) {
                        viewModel.email ?: stringResource(R.string.profile_signed_in)
                    } else {
                        stringResource(R.string.profile_guest)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (signedIn) {
                    OutlinedButton(onClick = viewModel::signOut) {
                        Text(stringResource(R.string.profile_sign_out))
                    }
                }
            }
        }

        Button(onClick = onOpenOrders, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.profile_my_orders))
        }
        if (signedIn) {
            OutlinedButton(onClick = onOpenWishlist, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.profile_wishlist))
            }
            OutlinedButton(
                onClick = onOpenStockNotifications,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.profile_stock_notifications))
            }
        }
    }
}
