package com.vastavik.codeauth.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vastavik.codeauth.ui.theme.*

/**
 * UpdateScreen.kt — In-App GitHub Release Updater
 * Obsidian #080C14 background, #0E1526 cards, #22C55E green accent
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(
    onBack: () -> Unit = {},
    viewModel: UpdateViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            TopAppBar(
                title = { Text("App Update", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Update, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark),
                actions = {
                    IconButton(onClick = { viewModel.checkForUpdate() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Check", tint = PrimaryCyan)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(BackgroundDark)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header card
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = ColorGreen, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Vastavik Authenticator", color = TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Keep your security key up to date", color = TextSecondary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                }
            }

            when (val state = uiState) {
                is UpdateUiState.Checking -> {
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = ColorGreen, modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text("Checking for updates…", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                                Text("Contacting GitHub releases", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                is UpdateUiState.UpToDate -> {
                    UpToDateCard(state)
                    ReleaseCard(release = state.latest, viewModel = viewModel, showDownload = false, context = context, state = state)
                }
                is UpdateUiState.UpdateAvailable -> {
                    UpdateAvailableBanner(state)
                    ReleaseCard(release = state.latest, viewModel = viewModel, showDownload = true, context = context, state = state)
                }
                is UpdateUiState.Downloading -> {
                    DownloadProgressCard(state)
                    // Also show release info underneath
                    (viewModel.uiState.value as? UpdateUiState.UpdateAvailable)?.let {
                        ReleaseCard(release = it.latest, viewModel = viewModel, showDownload = false, context = context, state = it)
                    }
                }
                is UpdateUiState.Downloaded -> {
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = SuccessGreen.copy(alpha = 0.12f)), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Download complete", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text("Tap below to install", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.installApk(context, state.file) },
                                colors = ButtonDefaults.buttonColors(containerColor = ColorGreen, contentColor = BackgroundDark),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Download, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Install Now")
                            }
                        }
                    }
                }
                is UpdateUiState.Error -> {
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.12f)), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Error", color = ErrorRed, fontWeight = FontWeight.SemiBold)
                            Text(state.message, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.checkForUpdate() },
                                colors = ButtonDefaults.buttonColors(containerColor = ColorGreen),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("Retry", color = BackgroundDark) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpToDateCard(state: UpdateUiState.UpToDate) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = SuccessGreen.copy(alpha = 0.14f)), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Up to date ✓", color = TextPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Text("You're on the latest version", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun UpdateAvailableBanner(state: UpdateUiState.UpdateAvailable) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = ColorGreen.copy(alpha = 0.14f)), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.SystemUpdate, null, tint = ColorGreen, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Update available!", color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("${state.current} → ${state.latest.displayVersion()}", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun DownloadProgressCard(state: UpdateUiState.Downloading) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = SurfaceCard), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Download, null, tint = ColorGreen, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Downloading… ${(state.progress * 100).toInt()}%", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("${state.bytesDownloaded / 1024 / 1024} MB", color = TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(progress = { state.progress }, color = ColorGreen, trackColor = SurfaceVariantDark, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)))
            if (state.totalBytes > 0) {
                Spacer(Modifier.height(6.dp))
                Text("${state.bytesDownloaded} / ${state.totalBytes} bytes", color = TextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ReleaseCard(
    release: com.vastavik.codeauth.data.GitHubRelease,
    viewModel: UpdateViewModel,
    showDownload: Boolean,
    context: Context,
    state: UpdateUiState
) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = SurfaceCard), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Release Notes", color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            // Versions
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Current", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    val currentVersion = remember(context) {
                        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0" } catch (_: Exception) { "1.0.0" }
                    }
                    Text(currentVersion, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Latest", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    Text(release.displayVersion(), color = ColorGreen, fontWeight = FontWeight.Bold)
                }
            }
            HorizontalDivider(color = DividerDark, thickness = 0.8.dp)
            // Published date
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, null, tint = TextMuted, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Published: ${viewModel.formatDate(release.publishedAt)}", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            // Body / changelog
            if (!release.body.isNullOrBlank()) {
                Text(release.body, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            } else {
                Text("No release notes provided.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            // APK info
            release.apkAsset()?.let { asset ->
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Download, null, tint = ColorGreen, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(asset.name, color = TextPrimary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            Text("${asset.size / 1024 / 1024} MB • APK", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (showDownload) {
                Button(
                    onClick = { viewModel.downloadAndInstall(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorGreen, contentColor = androidx.compose.ui.graphics.Color.White),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Icon(Icons.Filled.Download, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Download & Install Update", fontWeight = FontWeight.Bold)
                }
                Text("Allow install from this app if prompted (Android 8.0+).", color = TextMuted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

// Local green accent alias to match spec #22C55E
private val ColorGreen = androidx.compose.ui.graphics.Color(0xFF22C55E)
