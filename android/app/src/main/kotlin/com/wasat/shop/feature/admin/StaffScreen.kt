package com.wasat.shop.feature.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wasat.shop.R
import com.wasat.shop.core.network.dto.StaffMemberDto

private val STAFF_ROLES = listOf("manager", "staff")

/** Сотрудники магазина (FR-A09): приглашение по email, роли, удаление. */
@Composable
fun StaffScreen(viewModel: StaffViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var pendingRemove by remember { mutableStateOf<StaffMemberDto?>(null) }

    pendingRemove?.let { member ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text(stringResource(R.string.staff_remove_title)) },
            text = { Text(stringResource(R.string.staff_remove_confirm, member.email)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.remove(member.uid)
                    pendingRemove = null
                }) { Text(stringResource(R.string.staff_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) {
                    Text(stringResource(R.string.catalog_retry))
                }
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.staff_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { InviteForm(state = state, viewModel = viewModel) }

            state.error?.let { msg ->
                item {
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            when {
                state.loading -> item {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.members.isEmpty() -> item {
                    Text(
                        text = stringResource(R.string.staff_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(8.dp),
                    )
                }
                else -> items(state.members, key = { it.uid }) { member ->
                    StaffRow(
                        member = member,
                        busy = state.busy,
                        onChangeRole = { viewModel.changeRole(member.uid, it) },
                        onRemove = { pendingRemove = member },
                    )
                }
            }
        }
    }
}

@Composable
private fun InviteForm(state: StaffUiState, viewModel: StaffViewModel) {
    Card {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.staff_invite_title),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = state.email,
                onValueChange = viewModel::onEmail,
                label = { Text(stringResource(R.string.staff_email)) },
                isError = state.emailError != null,
                supportingText = { state.emailError?.let { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            RoleSelector(selected = state.role, onSelect = viewModel::onRole)
            Button(
                onClick = viewModel::invite,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.staff_invite))
            }
        }
    }
}

@Composable
private fun RoleSelector(selected: String, onSelect: (String) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        STAFF_ROLES.forEachIndexed { index, role ->
            SegmentedButton(
                selected = selected == role,
                onClick = { onSelect(role) },
                shape = SegmentedButtonDefaults.itemShape(index, STAFF_ROLES.size),
            ) {
                Text(stringResource(if (role == "manager") R.string.staff_role_manager else R.string.staff_role_staff))
            }
        }
    }
}

@Composable
private fun StaffRow(
    member: StaffMemberDto,
    busy: Boolean,
    onChangeRole: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Card {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = member.email,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRemove, enabled = !busy) {
                    Text(stringResource(R.string.staff_remove))
                }
            }
            RoleSelector(selected = member.role, onSelect = onChangeRole)
        }
    }
}
