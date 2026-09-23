package com.vastavik.codeauth.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vastavik.codeauth.data.DeviceSession
import com.vastavik.codeauth.data.NetworkClient
import com.vastavik.codeauth.data.SecurePrefs
import com.vastavik.codeauth.ui.theme.*
import kotlinx.coroutines.launch

/**
 * DashboardScreen.kt — Active Sessions / Devices Kill-Switch
 * GET /api/app/devices with x-app-secret header
 * POST /api/app/revoke with animation on removal
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    securePrefs: SecurePrefs
) {
    val scope = rememberCoroutineScope()
    val config by securePrefs.configFlow.collectAsState()

    var sessions by remember { mutableStateOf<List<DeviceSession>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var revokingId by remember { mutableStateOf<String?>(null) }
    var revokedIds by remember { mutableStateOf(setOf<String>()) }

    val snackbarHostState = remember { SnackbarHostState() }

    suspend fun loadSessions() {
        loading = true
        error = null
        val result = NetworkClient.fetchDevices(config.vpsDomain, config.secretKey)
        result.onSuccess { list ->
            sessions = list
            if (list.isEmpty()) error = null
        }.onFailure { ex ->
            error = ex.message ?: "Failed to fetch sessions. Check secret/domain."
        }
        loading = false
    }

    LaunchedEffect(config.vpsDomain, config.secretKey) {
        loadSessions()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BackgroundDark,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Active Sessions", color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("${sessions.size} device(s) • Kill-switch", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark),
                actions = {
                    IconButton(onClick = { scope.launch { loadSessions() } }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = PrimaryIndigoLight)
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(BackgroundDark)
        ) {
            when {
                loading && sessions.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = PrimaryIndigo)
                        Spacer(Modifier.height(12.dp))
                        Text("Fetching active sessions…", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                error != null && sessions.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Filled.CloudOff, null, tint = ErrorRed, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(error!!, color = ErrorRed, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp))
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { scope.launch { loadSessions() } },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Retry") }
                        Spacer(Modifier.height(8.dp))
                        Text("Domain: ${config.vpsDomain}", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                sessions.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Filled.DevicesOther, null, tint = AccentSlate, modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("No active sessions", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                        Text("Approved browser sessions will appear here.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = { scope.launch { loadSessions() } }, shape = RoundedCornerShape(12.dp)) { Text("Refresh", color = PrimaryIndigoLight) }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (error != null) {
                            item {
                                Card(colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.12f)), shape = RoundedCornerShape(12.dp)) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Warning, null, tint = ErrorRed, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(error!!, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                        if (loading) {
                            item {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = PrimaryIndigo, trackColor = SurfaceVariantDark)
                            }
                        }
                        items(sessions, key = { it.resolvedId() }) { session ->
                            val id = session.resolvedId()
                            val isRevoking = revokingId == id
                            val isRemoved = revokedIds.contains(id)

                            AnimatedVisibility(
                                visible = !isRemoved,
                                exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(tween(250))
                            ) {
                                SessionCard(
                                    session = session,
                                    isRevoking = isRevoking,
                                    onRevoke = {
                                        scope.launch {
                                            revokingId = id
                                            val result = NetworkClient.revokeSession(config.vpsDomain, config.secretKey, id)
                                            result.onSuccess { resp ->
                                                val ok = resp.success != false && resp.error == null
                                                if (ok) {
                                                    revokedIds = revokedIds + id
                                                    // remove after animation
                                                    kotlinx.coroutines.delay(350)
                                                    sessions = sessions.filterNot { it.resolvedId() == id }
                                                    snackbarHostState.showSnackbar("Session revoked")
                                                } else {
                                                    snackbarHostState.showSnackbar(resp.error ?: resp.message ?: "Revoke failed")
                                                }
                                            }.onFailure { ex ->
                                                snackbarHostState.showSnackbar(ex.message ?: "Network error revoking")
                                            }
                                            revokingId = null
                                        }
                                    }
                                )
                            }
                        }
                        item { Spacer(Modifier.height(72.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionCard(
    session: DeviceSession,
    isRevoking: Boolean,
    onRevoke: () -> Unit
) {
    var showConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceVariantDark),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Laptop, null, tint = PrimaryIndigoLight, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        session.resolvedDeviceName(),
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Language, null, tint = TextMuted, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            session.resolvedIp(),
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1
                        )
                    }
                }
                // status dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(SuccessGreen)
                )
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = DividerDark, thickness = 0.8.dp)

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Schedule, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Login:", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(6.dp))
                Text(session.resolvedTime(), color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (session.id.isNotBlank() || session.tokenId != null) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Tag, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Token:", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(6.dp))
                    Text(session.resolvedId().take(24), color = TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            // user agent
            val ua = session.userAgent ?: session.browserAgent
            if (!ua.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(ua, color = TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { showConfirm = true },
                enabled = !isRevoking,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White, disabledContainerColor = ErrorRed.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isRevoking) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Revoking…")
                } else {
                    Icon(Icons.Filled.Block, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Revoke Session", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            containerColor = SurfaceCard,
            title = { Text("Revoke this session?", color = TextPrimary) },
            text = { Text("This will immediately log out that browser. This cannot be undone.", color = TextSecondary) },
            confirmButton = {
                Button(onClick = { showConfirm = false; onRevoke() }, colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)) { Text("Revoke") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }
}
