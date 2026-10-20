package com.nahabit.tracker

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nahabit.tracker.core.backup.AutoBackupWorker
import com.nahabit.tracker.core.datastore.UserPreferences
import com.nahabit.tracker.core.datastore.UserPreferencesRepository
import com.nahabit.tracker.core.navigation.NahabitScaffold
import com.nahabit.tracker.core.notifications.NahabitNotificationHelper
import com.nahabit.tracker.core.ui.theme.NahabitCyan
import com.nahabit.tracker.core.ui.theme.NahabitTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var preferencesRepository: UserPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        NahabitNotificationHelper.createNotificationChannels(this)

        val shortcutDest = intent?.getStringExtra("extra_shortcut_dest")

        setContent {
            val prefs by preferencesRepository.preferencesFlow.collectAsStateWithLifecycle(
                initialValue = UserPreferences()
            )

            LaunchedEffect(prefs.hideInRecents) {
                if (prefs.hideInRecents) {
                    window.setFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE
                    )
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            LaunchedEffect(prefs.autoBackupEnabled) {
                AutoBackupWorker.syncSchedule(applicationContext, prefs.autoBackupEnabled)
            }

            NahabitTheme(
                themeMode = prefs.themeMode,
                dynamicColor = prefs.useDynamicColors,
                isCompactMode = prefs.isCompactMode
            ) {
                var isSessionUnlocked by rememberSaveable { mutableStateOf(false) }

                if (prefs.appLockPinCode.isNotEmpty() && !isSessionUnlocked) {
                    AppLockGateScreen(
                        expectedPin = prefs.appLockPinCode,
                        onUnlocked = { isSessionUnlocked = true }
                    )
                } else {
                    NahabitScaffold(shortcutDestination = shortcutDest)
                }
            }
        }
    }
}

@Composable
private fun AppLockGateScreen(
    expectedPin: String,
    onUnlocked: () -> Unit
) {
    var pinInput by rememberSaveable { mutableStateOf("") }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = NahabitCyan)
                    Text(
                        text = stringResource(R.string.privacy_app_locked_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it.filter(Char::isDigit).take(4) },
                        label = { Text(stringResource(R.string.journal_pin_enter)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            if (pinInput == expectedPin) {
                                pinInput = ""
                                onUnlocked()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text(stringResource(R.string.journal_pin_unlock))
                    }
                }
            }
        }
    }
}
