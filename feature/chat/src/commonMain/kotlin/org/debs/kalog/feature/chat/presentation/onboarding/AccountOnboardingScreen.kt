package org.debs.kalog.feature.chat.presentation.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.debs.kalog.feature.chat.presentation.koinLifecycleViewModel
import org.debs.kalog.feature.chat.presentation.text.asString
import mayday_chat.feature.chat.generated.resources.Res
import mayday_chat.feature.chat.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun AccountOnboardingRoute(
    onReady: () -> Unit,
) {
    val viewModel = koinLifecycleViewModel<AccountOnboardingViewModel>()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.isReady) {
        if (state.isReady) onReady()
    }

    if (state.isChecking || state.isReady) {
        AccountOnboardingGateScreen()
        return
    }

    AccountOnboardingScreen(
        state = state,
        onCreateNewClick = { viewModel.onEvent(AccountOnboardingEvent.CreateNewAccountClicked) },
        onImportPasswordChange = { viewModel.onEvent(AccountOnboardingEvent.ImportPasswordChanged(it)) },
        onImportClick = { viewModel.onEvent(AccountOnboardingEvent.ImportBackupClicked) },
    )
}

@Composable
private fun AccountOnboardingGateScreen(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun AccountOnboardingScreen(
    state: AccountOnboardingUiState,
    onCreateNewClick: () -> Unit,
    onImportPasswordChange: (String) -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        if (state.isChecking) {
            CircularProgressIndicator()
            return@Box
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.mayday_chat_account),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(Res.string.account_onboarding_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.import_encrypted_backup),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(Res.string.import_encrypted_backup_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = state.importPassword,
                        onValueChange = onImportPasswordChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(stringResource(Res.string.backup_password))
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = MaterialTheme.shapes.large,
                    )
                    Button(
                        onClick = onImportClick,
                        enabled = !state.isImporting && state.importPassword.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = CircleShape,
                    ) {
                        Text(
                            if (state.isImporting) {
                                stringResource(Res.string.importing)
                            } else {
                                stringResource(Res.string.choose_backup_file)
                            },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(Res.string.or_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedButton(
                onClick = onCreateNewClick,
                modifier = Modifier.fillMaxWidth(),
                shape = CircleShape,
            ) {
                Text(stringResource(Res.string.create_new_account))
            }

            state.message?.let { message ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = message.asString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            state.error?.let { error ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = error.asString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
