package org.debs.kalog

import android.app.AlertDialog
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import org.debs.kalog.feature.chat.localization.chatLocalized
import org.debs.kalog.feature.chat.presentation.platform.AndroidChatPlatformBridge

class MainActivity : FragmentActivity() {
    private lateinit var deviceCredentialLauncher: ActivityResultLauncher<Intent>
    private var contentShown = false
    private var protectedDeviceContentShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                scrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.light(
                scrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.argb(0x80, 0x1B, 0x1B, 0x1B),
            ),
        )
        super.onCreate(savedInstanceState)
        AndroidChatPlatformBridge.register(this)

        deviceCredentialLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                showAppContent(protectedDeviceLockAvailable = true)
            } else {
                finish()
            }
        }

        requestStartupUnlock()
    }

    override fun onResume() {
        super.onResume()
        if (contentShown && !protectedDeviceContentShown && isDeviceSecure()) {
            contentShown = false
            requestStartupUnlock()
        }
    }

    private fun requestStartupUnlock() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requestBiometricOrDeviceCredentialUnlock()
        } else {
            requestLegacyDeviceCredentialUnlock()
        }
    }

    private fun requestBiometricOrDeviceCredentialUnlock() {
        if (!isDeviceSecure()) {
            showAppContent(
                protectedDeviceLockAvailable = false,
                showReducedProtectionWarning = true,
            )
            return
        }

        val authenticators = BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        val biometricManager = BiometricManager.from(this)
        if (biometricManager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            requestLegacyDeviceCredentialUnlock()
            return
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(appLockTitle())
            .setSubtitle(appLockSubtitle())
            .setAllowedAuthenticators(authenticators)
            .build()
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    showAppContent(protectedDeviceLockAvailable = true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (!contentShown) finish()
                }
            },
        )
        prompt.authenticate(promptInfo)
    }

    private fun requestLegacyDeviceCredentialUnlock() {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!keyguardManager.isDeviceSecure) {
            showAppContent(
                protectedDeviceLockAvailable = false,
                showReducedProtectionWarning = true,
            )
            return
        }
        val intent = keyguardManager.createConfirmDeviceCredentialIntent(
            appLockTitle(),
            appLockSubtitle(),
        )
        if (intent == null) {
            Toast.makeText(this, appLockUnavailableMessage(), Toast.LENGTH_LONG).show()
            finish()
            return
        }
        deviceCredentialLauncher.launch(intent)
    }

    private fun showAppContent(
        protectedDeviceLockAvailable: Boolean,
        showReducedProtectionWarning: Boolean = false,
    ) {
        if (contentShown) return
        contentShown = true
        protectedDeviceContentShown = protectedDeviceLockAvailable
        setContent {
            App(protectedDeviceLockAvailable = protectedDeviceLockAvailable)
        }
        if (showReducedProtectionWarning) {
            window.decorView.post {
                if (!isFinishing && !isDestroyed) {
                    AlertDialog.Builder(this)
                        .setTitle(reducedProtectionTitle())
                        .setMessage(reducedProtectionMessage())
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        }
    }

    private fun isDeviceSecure(): Boolean {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return keyguardManager.isDeviceSecure
    }

    private companion object {
        private fun appLockTitle(): String = chatLocalized(
            en = "Unlock Mayday Chat",
            ru = "Разблокируйте Mayday Chat",
        )

        private fun appLockSubtitle(): String = chatLocalized(
            en = "Use fingerprint, face, PIN, pattern, or password.",
            ru = "Используйте отпечаток, лицо, PIN-код, графический ключ или пароль.",
        )

        private fun appLockUnavailableMessage(): String = chatLocalized(
            en = "Set up a secure screen lock to open Mayday Chat.",
            ru = "Настройте безопасную блокировку экрана, чтобы открыть Mayday Chat.",
        )

        private fun reducedProtectionTitle(): String = chatLocalized(
            en = "Device lock is disabled",
            ru = "Блокировка устройства отключена",
        )

        private fun reducedProtectionMessage(): String = chatLocalized(
            en = "Mayday Chat opened without system authentication because this device has no secure screen lock. Account access, backup import, UUID registration, and key generation are disabled until you set up a PIN, pattern, password, or biometric unlock in system settings.",
            ru = "Mayday Chat открыт без системной аутентификации, потому что на устройстве нет безопасной блокировки экрана. Доступ к аккаунту, импорт резервных копий, регистрация UUID и генерация ключей отключены, пока вы не настроите PIN-код, графический ключ, пароль или биометрическую разблокировку в системных настройках.",
        )
    }
}
