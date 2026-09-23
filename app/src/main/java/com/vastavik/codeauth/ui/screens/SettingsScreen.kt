package com.vastavik.codeauth.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.vastavik.codeauth.data.SecurePrefs
import com.vastavik.codeauth.ui.theme.*
import kotlinx.coroutines.launch

/**
 * SettingsScreen.kt — Secure Config Storage UI
 * - VPS Domain + Secret Key with EncryptedSharedPreferences
 * - Eye toggle for secret, save with snackbar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    securePrefs: SecurePrefs
) {
    val config by securePrefs.configFlow.collectAsState()
    var domainInput by remember(config.vpsDomain) { mutableStateOf(config.vpsDomain) }
    var secretInput by remember(config.secretKey) { mutableStateOf(config.secretKey) }
    var secretVisible by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()

    val isDirty = domainInput.trim() != config.vpsDomain || secretInput.trim() != config.secretKey
    val isValidDomain = domainInput.trim().startsWith("http://") || domainInput.trim().startsWith("https://")
    val isValidSecret = secretInput.trim().length >= 8

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BackgroundDark,
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(BackgroundDark)
                .verticalScroll(scroll)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header card
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Security, null, tint = PrimaryIndigoLight, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Secure Config", color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Encrypted with Jetpack Security • AES256-GCM", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // VPS Domain
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Language, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("VPS Domain", color = TextSecondary, style = MaterialTheme.typography.labelLarge)
                }
                OutlinedTextField(
                    value = domainInput,
                    onValueChange = { domainInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://code.vastaviklearning.online", color = TextMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    isError = domainInput.isNotBlank() && !isValidDomain,
                    supportingText = {
                        if (domainInput.isNotBlank() && !isValidDomain) Text("Must start with https://", color = ErrorRed)
                        else Text("No trailing slash needed", color = TextMuted)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryIndigo,
                        unfocusedBorderColor = DividerDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = PrimaryIndigo,
                        focusedContainerColor = SurfaceVariantDark,
                        unfocusedContainerColor = SurfaceVariantDark
                    ),
                    shape = RoundedCornerShape(14.dp),
                    leadingIcon = { Icon(Icons.Filled.Link, null, tint = TextMuted) }
                )
            }

            // Secret Key
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.VpnKey, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Secret Key (x-app-secret)", color = TextSecondary, style = MaterialTheme.typography.labelLarge)
                }
                OutlinedTextField(
                    value = secretInput,
                    onValueChange = { secretInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("ChangeThisToASecretHighEntropyKey123!", color = TextMuted) },
                    singleLine = true,
                    visualTransformation = if (secretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = secretInput.isNotBlank() && !isValidSecret,
                    supportingText = {
                        if (secretInput.isNotBlank() && !isValidSecret) Text("At least 8 characters", color = ErrorRed)
                        else Text("High-entropy header value • stored encrypted", color = TextMuted)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryIndigo,
                        unfocusedBorderColor = DividerDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = PrimaryIndigo,
                        focusedContainerColor = SurfaceVariantDark,
                        unfocusedContainerColor = SurfaceVariantDark
                    ),
                    shape = RoundedCornerShape(14.dp),
                    leadingIcon = { Icon(Icons.Filled.Lock, null, tint = TextMuted) },
                    trailingIcon = {
                        IconButton(onClick = { secretVisible = !secretVisible }) {
                            Icon(
                                if (secretVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (secretVisible) "Hide" else "Show",
                                tint = TextMuted
                            )
                        }
                    }
                )
            }

            // Save button
            Button(
                onClick = {
                    if (!isValidDomain) {
                        scope.launch { snackbarHostState.showSnackbar("Invalid domain — must start with https://") }
                        return@Button
                    }
                    if (!isValidSecret) {
                        scope.launch { snackbarHostState.showSnackbar("Secret too short") }
                        return@Button
                    }
                    isSaving = true
                    securePrefs.saveConfig(domainInput, secretInput)
                    scope.launch {
                        snackbarHostState.showSnackbar("✓ Saved securely (EncryptedSharedPreferences)")
                        isSaving = false
                    }
                },
                enabled = isDirty && isValidDomain && isValidSecret && !isSaving,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo, disabledContainerColor = SurfaceVariantDark),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                if (isSaving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = TextPrimary)
                else Icon(Icons.Filled.Save, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (isDirty) "Save Encrypted" else "Saved ✓", fontWeight = FontWeight.SemiBold)
            }

            // Reset defaults
            OutlinedButton(
                onClick = {
                    domainInput = SecurePrefs.DEFAULT_DOMAIN
                    secretInput = SecurePrefs.DEFAULT_SECRET
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
            ) {
                Icon(Icons.Filled.RestartAlt, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Reset to defaults")
            }

            // Info
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = PrimaryIndigo.copy(alpha = 0.10f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Info, null, tint = PrimaryIndigoLight, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("How it works", color = TextPrimary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "• Settings are encrypted at rest with AndroidX Security (MasterKey AES256_GCM).\n" +
                                    "• Domain + secret are used for /approve and /devices calls.\n" +
                                    "• Change the default secret on your VPS and here to match.",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
