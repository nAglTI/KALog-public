package org.debs.kalog.feature.chat.presentation.onboarding

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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import org.debs.kalog.feature.chat.localization.chatLocalized
import org.debs.kalog.feature.chat.presentation.koinLifecycleViewModel

@Composable
fun AccountOnboardingRoute(
    onReady: () -> Unit,
) {
    val viewModel = koinLifecycleViewModel<AccountOnboardingViewModel>()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.isReady) {
        if (state.isReady) onReady()
    }

    AccountOnboardingScreen(
        state = state,
        onCreateNewClick = { viewModel.onEvent(AccountOnboardingEvent.CreateNewAccountClicked) },
        onImportPasswordChange = { viewModel.onEvent(AccountOnboardingEvent.ImportPasswordChanged(it)) },
        onImportClick = { viewModel.onEvent(AccountOnboardingEvent.ImportBackupClicked) },
    )
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
                text = chatLocalized(en = "Mayday Chat account", ru = "Аккаунт Mayday Chat"),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Text(
                text = chatLocalized(
                    en = "Your account is your UUID and encryption keys. Import a backup to continue as the same user, or create a new account for this device.",
                    ru = "Ваш аккаунт - это UUID и ключи шифрования. Импортируйте резервную копию, чтобы продолжить как тот же пользователь, или создайте новый аккаунт для этого устройства.",
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = chatLocalized(
                            en = "Import encrypted backup",
                            ru = "Импорт зашифрованной копии",
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = chatLocalized(
                            en = "Choose a Mayday backup file and enter the password it was protected with. The file cannot be read without that password.",
                            ru = "Выберите файл резервной копии Mayday и введите пароль, которым он был защищён. Без этого пароля файл нельзя прочитать.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = state.importPassword,
                        onValueChange = onImportPasswordChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(chatLocalized(en = "Backup password", ru = "Пароль от резервной копии"))
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(16.dp),
                    )
                    Button(
                        onClick = onImportClick,
                        enabled = !state.isImporting && state.importPassword.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(
                            if (state.isImporting) {
                                chatLocalized(en = "Importing...", ru = "Импорт...")
                            } else {
                                chatLocalized(en = "Choose backup file", ru = "Выбрать файл копии")
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
                    text = chatLocalized(en = "or", ru = "или"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedButton(
                onClick = onCreateNewClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(chatLocalized(en = "Create new account", ru = "Создать новый аккаунт"))
            }

            state.message?.let { message ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            state.error?.let { error ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
