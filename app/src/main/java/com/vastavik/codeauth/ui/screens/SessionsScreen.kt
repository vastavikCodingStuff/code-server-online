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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vastavik.codeauth.data.DeviceSession
import com.vastavik.codeauth.data.SecurePrefs
import com.vastavik.codeauth.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * SessionsScreen.kt — Live Device Management & Kill-Switch
 * - Dynamic badge: [TIGER VNC] (Cyan/Green) vs [VS CODE] (Blue/Purple) via targetService/serverDomain
 * - Instant revocation: optimistically remove with animation, POST x-app-secret {tokenId}, snackbar, background refresh
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    securePrefs: SecurePrefs,
    onNavigateToUpdate: () -> Unit = {}
) {
    val factory = remember(securePrefs) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return SessionsViewModel(securePrefs) as T
            }
        }
    }
    val vm: SessionsViewModel = viewModel(factory = factory)
    val uiState by vm.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Collect error + success snackbar
    LaunchedEffect(Unit) {
        vm.snackbarFlow.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }
    LaunchedEffect(uiState.error) {
        uiState.error?.let { msg ->
            // Avoid duplicate with snackbarFlow; show via host if not already from revoke
            if (msg != "Session revoked. Browser disconnected.") {
                snackbarHostState.showSnackbar(msg)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BackgroundDark,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Active Sessions", color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("${uiState.sessions.size} device(s) • Kill-switch", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark),
                actions = {
                    // Distinct green accent Update button — #22C55E pill
                    Button(
                        onClick = onNavigateToUpdate,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E), contentColor = Color.White),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Filled.Update, contentDescription = "Update", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Update", style = MaterialTheme.typography.labelLarge)
                    }
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = PrimaryCyan)
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { vm.refresh(isPull = true) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(BackgroundDark)
        ) {
            when {
                uiState.isLoading && uiState.sessions.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = PrimaryCyan)
                            Spacer(Modifier.height(12.dp))
                            Text("Fetching active sessions…", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                uiState.error != null && uiState.sessions.isEmpty() -> {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.CloudOff, null, tint = ErrorRed, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(uiState.error!!, color = ErrorRed, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { vm.retry() },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan),
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("Retry", color = BackgroundDark) }
                            Spacer(Modifier.height(8.dp))
                            Text("Domain: ${securePrefs.getDomain()}", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                uiState.sessions.isEmpty() -> {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.DevicesOther, null, tint = AccentSlate, modifier = Modifier.size(56.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("No active sessions", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                            Text("Approved browser sessions will appear here.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(16.dp))
                            OutlinedButton(onClick = { vm.refresh() }, shape = RoundedCornerShape(12.dp)) {
                                Text("Refresh", color = PrimaryCyan)
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (uiState.error != null) {
                            item {
                                Card(colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.12f)), shape = RoundedCornerShape(12.dp)) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Warning, null, tint = ErrorRed, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(uiState.error!!, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                        items(uiState.sessions, key = { it.resolvedId() }) { session ->
                            val isRevoking = uiState.revokingId == session.resolvedId()
                            // Instant optimistic removal with animation via animateItem + AnimatedVisibility
                            AnimatedVisibility(
                                visible = true,
                                exit = shrinkVertically(tween(300)) + fadeOut(tween(250))
                            ) {
                                SessionCard(
                                    session = session,
                                    isRevoking = isRevoking,
                                    modifier = Modifier.animateItem(),
                                    onRevoke = { vm.revokeSession(session) }
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
    modifier: Modifier = Modifier,
    onRevoke: () -> Unit
) {
    var showConfirm by remember { mutableStateOf(false) }
    val isTiger = session.isTigerVnc()
    val isVsCode = session.isVsCode()
    val badgeText = session.resolvedBadge()
    val badgeColor = when {
        isTiger -> BadgeVncBg // Cyan/Green
        isVsCode -> BadgeCodeBg // Blue/Purple
        else -> AccentSlate // fallback, not hardcoded VS CODE
    }
    val badgeLabelColor = BackgroundDark

    Card(
        modifier = modifier.fillMaxWidth(),
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
                    Icon(
                        if (isTiger) Icons.Filled.DesktopWindows else Icons.Filled.Laptop,
                        null, tint = badgeColor, modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(badgeColor)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(badgeText, color = badgeLabelColor, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(50))
                                .background(SuccessGreen)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
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
                        Text(session.resolvedIp(), color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = DividerDark, thickness = 0.8.dp)
            Spacer(Modifier.height(12.dp))

            session.resolvedUserAgent()?.let { ua ->
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Computer, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(ua, color = TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Schedule, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Sign-in:", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(6.dp))
                Text(formatTimestamp(session.resolvedTime()), color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Tag, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Token:", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(6.dp))
                Text(session.resolvedId().take(24), color = TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { showConfirm = true },
                enabled = !isRevoking,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = androidx.compose.ui.graphics.Color.White, disabledContainerColor = ErrorRed.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isRevoking) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = androidx.compose.ui.graphics.Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Revoking…")
                } else {
                    Icon(Icons.Filled.Block, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Revoke Device", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            containerColor = SurfaceCard,
            title = { Text("Revoke this device?", color = TextPrimary) },
            text = { Text("This will immediately fail authorization and redirect the browser tab back to the QR screen.", color = TextSecondary) },
            confirmButton = {
                Button(onClick = { showConfirm = false; onRevoke() }, colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)) { Text("Revoke") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }
}

private fun formatTimestamp(raw: String): String {
    if (raw == "—" || raw.isBlank()) return raw
    return try {
        val isoParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val isoParser2 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val isoParser3 = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val date = try { isoParser.parse(raw) } catch (_: Exception) { try { isoParser2.parse(raw) } catch (_: Exception) { isoParser3.parse(raw) } }
        if (date != null) SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(date) else raw
    } catch (_: Exception) { raw }
}
