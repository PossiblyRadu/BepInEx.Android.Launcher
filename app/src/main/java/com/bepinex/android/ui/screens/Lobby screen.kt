package com.bepinex.android.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bepinex.android.R
import com.bepinex.android.lobby.Lobby
import com.bepinex.android.lobby.LobbyRegion
import com.bepinex.android.lobby.LobbyRepository
import com.bepinex.android.lobby.RegionResult
import kotlinx.coroutines.delay

/** Lobby browser for modded regions: pick a region (or all) and see codes and player counts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LobbyScreen(gameVersionName: String) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var regions by remember { mutableStateOf<List<LobbyRegion>>(emptyList()) }
    var results by remember { mutableStateOf<Map<String, RegionResult>>(emptyMap()) }
    var selectedRegion by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var versionText by remember { mutableStateOf(gameVersionName) }

    LaunchedEffect(gameVersionName) {
        if (versionText.isBlank()) versionText = gameVersionName
    }

    val encodedVersion = LobbyRepository.encodeVersion(versionText)

    LaunchedEffect(refreshKey, encodedVersion) {
        // Wait briefly so typing in the version field does not fire a request per keystroke.
        delay(500)
        isLoading = true
        if (regions.isEmpty()) {
            regions = LobbyRepository.loadRegions()
        }
        if (encodedVersion != null) {
            results = LobbyRepository.fetchAll(regions, encodedVersion)
        }
        isLoading = false
    }

    val visibleRegions = regions.filter { selectedRegion == null || it.name == selectedRegion }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
            )
            .padding(top = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.lobby_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { refreshKey++ }, enabled = !isLoading) {
                Icon(Icons.Filled.Refresh, stringResource(R.string.lobby_refresh))
            }
        }

        OutlinedTextField(
            value = versionText,
            onValueChange = { versionText = it },
            label = { Text(stringResource(R.string.lobby_game_version)) },
            singleLine = true,
            isError = encodedVersion == null,
            supportingText = {
                if (encodedVersion == null) Text(stringResource(R.string.lobby_version_invalid))
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedRegion == null,
                    onClick = { selectedRegion = null },
                    label = { Text(stringResource(R.string.lobby_all_regions)) }
                )
            }
            items(regions, key = { it.name }) { region ->
                FilterChip(
                    selected = selectedRegion == region.name,
                    onClick = { selectedRegion = region.name },
                    label = { Text(region.name) }
                )
            }
        }

        if (isLoading && results.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                visibleRegions.forEach { region ->
                    val result = results[region.name]
                    item(key = "header_${region.name}") {
                        RegionHeader(region.name, result, isLoading)
                    }
                    when (result) {
                        is RegionResult.Success -> {
                            if (result.lobbies.isEmpty()) {
                                item(key = "empty_${region.name}") {
                                    Text(
                                        text = stringResource(R.string.lobby_none),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                }
                            } else {
                                items(result.lobbies, key = { "${region.name}_${it.code}" }) { lobby ->
                                    LobbyCard(lobby) {
                                        clipboard.setText(AnnotatedString(lobby.code))
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.lobby_code_copied, lobby.code),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        }
                        is RegionResult.Failure -> {
                            item(key = "error_${region.name}") {
                                Text(
                                    text = stringResource(R.string.lobby_error, result.message),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                        null -> Unit
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun RegionHeader(name: String, result: RegionResult?, isLoading: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        when {
            result is RegionResult.Success ->
                Text(
                    text = stringResource(R.string.lobby_count, result.lobbies.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            result == null && isLoading ->
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            else -> Unit
        }
    }
}

@Composable
private fun LobbyCard(lobby: Lobby, onCopy: () -> Unit) {
    Card(modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onCopy)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = lobby.code,
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(
                        R.string.lobby_details,
                        lobby.hostName,
                        lobby.mapName,
                        lobby.impostors
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = stringResource(R.string.lobby_players, lobby.players, lobby.maxPlayers),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            Icon(Icons.Filled.ContentCopy, stringResource(R.string.lobby_copy_code))
        }
    }
}
