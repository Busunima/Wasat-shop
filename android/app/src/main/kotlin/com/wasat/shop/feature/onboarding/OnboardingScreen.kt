package com.wasat.shop.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wasat.shop.R
import com.wasat.shop.core.designsystem.LocalWindowWidthSizeClass
import com.wasat.shop.core.designsystem.isExpandedLayout

/** Онбординг продавца: форма создания магазина → POST /api/stores/init (ТЗ §4.1 шаг 4). */
@Composable
fun OnboardingScreen(
    onStoreCreated: (slug: String) -> Unit,
    onNavigateToSignIn: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                OnboardingEvent.NavigateToSignIn -> onNavigateToSignIn()
            }
        }
    }
    LaunchedEffect(state.submission) {
        (state.submission as? Submission.Success)?.let { onStoreCreated(it.slug) }
    }

    val submission = state.submission
    val isLoading = submission is Submission.Loading
    // Адаптивность (WindowSizeClass, ТЗ §11.5): на medium/expanded форма не растягивается.
    val widthModifier = if (LocalWindowWidthSizeClass.current.isExpandedLayout) {
        Modifier.widthIn(max = 480.dp)
    } else {
        Modifier.fillMaxWidth()
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = widthModifier
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.headlineSmall,
            )

            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.onboarding_name)) },
                isError = OnboardingField.NAME in state.fieldErrors,
                supportingText = { state.fieldErrors[OnboardingField.NAME]?.let { Text(it) } },
                enabled = !isLoading,
                singleLine = true,
            )

            OutlinedTextField(
                value = state.slug,
                onValueChange = viewModel::onSlugChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.onboarding_slug)) },
                isError = OnboardingField.SLUG in state.fieldErrors,
                supportingText = {
                    Text(
                        state.fieldErrors[OnboardingField.SLUG]
                            ?: stringResource(R.string.onboarding_slug_hint),
                    )
                },
                enabled = !isLoading,
                singleLine = true,
            )

            OutlinedTextField(
                value = state.currency,
                onValueChange = viewModel::onCurrencyChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.onboarding_currency)) },
                isError = OnboardingField.CURRENCY in state.fieldErrors,
                supportingText = { state.fieldErrors[OnboardingField.CURRENCY]?.let { Text(it) } },
                enabled = !isLoading,
                singleLine = true,
            )

            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.onboarding_description)) },
                isError = OnboardingField.DESCRIPTION in state.fieldErrors,
                supportingText = { state.fieldErrors[OnboardingField.DESCRIPTION]?.let { Text(it) } },
                enabled = !isLoading,
                minLines = 3,
            )

            if (submission is Submission.Failed) {
                Card {
                    Text(
                        text = submission.message,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Button(
                onClick = viewModel::submit,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
            ) {
                Text(
                    stringResource(
                        if (isLoading) R.string.onboarding_submitting else R.string.onboarding_submit,
                    ),
                )
            }
        }
    }
}
