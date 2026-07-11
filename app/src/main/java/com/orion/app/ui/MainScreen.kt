package com.orion.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.viewmodel.compose.viewModel
import com.orion.app.viewmodel.OrionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: OrionViewModel = viewModel()
) {
    var showSettings by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Orion") },
                actions = {
                    TextButton(onClick = { showSettings = true }) {
                        Text("Nastavení")
                    }
                }
            )
        }
    ) { padding ->
        if (showSettings) {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { showSettings = false }
            )
        } else {
            HomeScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
fun HomeScreen(
    viewModel: OrionViewModel,
    modifier: Modifier = Modifier
) {
    val listening by viewModel.listening.collectAsState()
    val status by viewModel.status.collectAsState()
    val log by viewModel.log.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // Big status indicator
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (listening) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = if (listening) Icons.Default.Mic else Icons.Default.Stop,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = status,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Start/Stop button
        Button(
            onClick = {
                if (listening) viewModel.stopListening() else viewModel.startListening()
                viewModel.addLog(if (listening) "Zastaveno" else "Spuštěno naslouchání")
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = apiKey.isNotBlank() || true
        ) {
            Icon(
                imageVector = if (listening) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (listening) "Zastavit" else "Spustit Oriona")
        }

        if (apiKey.isBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tip: Bez API klíče bude Orion fungovat jen offline (timer, budík, hudba).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Log
        Card(modifier = Modifier.fillMaxWidth()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .padding(8.dp)
            ) {
                items(log) { entry ->
                    Text(
                        text = "• $entry",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: OrionViewModel,
    onBack: () -> Unit
) {
    val apiKey by viewModel.apiKey.collectAsState()
    val wakeWord by viewModel.wakeWord.collectAsState()
    val aiName by viewModel.aiName.collectAsState()
    val musicFolder by viewModel.musicFolder.collectAsState()

    var localApiKey by remember { mutableStateOf(apiKey) }
    var localWakeWord by remember { mutableStateOf(wakeWord) }
    var localAiName by remember { mutableStateOf(aiName) }
    var localMusicFolder by remember { mutableStateOf(musicFolder) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        TextButton(onClick = onBack) {
            Text("← Zpět")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = localApiKey,
            onValueChange = { localApiKey = it },
            label = { Text("Gemini API klíč") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = localWakeWord,
            onValueChange = { localWakeWord = it },
            label = { Text("Wake word (aktivační slovo)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = localAiName,
            onValueChange = { localAiName = it },
            label = { Text("Jméno asistenta") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = localMusicFolder,
            onValueChange = { localMusicFolder = it },
            label = { Text("Složka s hudbou (volitelné)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("/storage/emulated/0/Music") }
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                viewModel.setApiKey(localApiKey)
                viewModel.setWakeWord(localWakeWord)
                viewModel.setAiName(localAiName)
                viewModel.setMusicFolder(localMusicFolder)
                onBack()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Uložit nastavení")
        }
    }
}
