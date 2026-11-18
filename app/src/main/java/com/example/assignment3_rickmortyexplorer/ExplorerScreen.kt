package com.example.assignment3_rickmortyexplorer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import java.util.Locale

// Escolhe o ecrã: câmara (ecrã novo do TP5) ou o ecrã principal
@Composable
fun ExplorerScreen(viewModel: ExplorerViewModel = viewModel()) {
    val state = viewModel.uiState
    val current = state.current

    if (state.showCamera && current != null) {
        CameraScreen(
            locationName = current.name,
            onPhotoReady = viewModel::onPhotoCaptured,
            onClose = viewModel::closeCamera
        )
    } else {
        ExplorerContent(viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerContent(viewModel: ExplorerViewModel) {
    val state = viewModel.uiState

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rick and Morty Explorer") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ----- Seleção da categoria -----
            Text("Select Category", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Category.entries.forEach { c ->
                    FilterChip(
                        selected = state.category == c,
                        onClick = { viewModel.onCategorySelected(c) },
                        label = { Text(c.label) }
                    )
                }
            }

            // ----- Filtro por nome -----
            OutlinedTextField(
                value = state.filter,
                onValueChange = viewModel::onFilterChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Filter ${state.category.label} by name...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )

            // ----- Estatísticas (Firebase + API) -----
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Firebase & API Stats", fontWeight = FontWeight.Bold)
                    Text("Total Rolls (Synced): ${state.totalRolls}", fontWeight = FontWeight.Bold)
                    Text("Total ${state.category.plural} in API: ${state.total}")
                    Text("Retrieved in current view: ${state.items.size} items")
                }
            }

            // ----- Item atual -----
            val current = state.current
            when {
                state.isLoading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                state.error != null -> Text(
                    text = state.error,
                    color = MaterialTheme.colorScheme.error
                )

                current == null -> Text("No results found")

                else -> ItemCard(current)
            }

            // ----- TP5: foto + coordenadas da localização (só na categoria Location) -----
            if (!state.isLoading && state.category == Category.LOCATION && current != null) {
                LocationPhotoCard(
                    locationName = current.name,
                    photo = state.photo,
                    onTakePhoto = viewModel::openCamera
                )
            }

            // ----- Botão Roll -----
            Button(
                onClick = viewModel::roll,
                enabled = state.items.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Roll Random Item")
            }

            // ----- Save / Load -----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = viewModel::saveCurrent,
                    enabled = state.current != null,
                    modifier = Modifier.weight(1f)
                ) { Text("Save Item") }

                OutlinedButton(
                    onClick = viewModel::loadLast,
                    modifier = Modifier.weight(1f)
                ) { Text("Load Last") }
            }

            // ----- Mensagem de feedback -----
            state.message?.let {
                Text(it, color = MaterialTheme.colorScheme.primary)
            }

            // ----- Histórico guardado -----
            HistoryCard(state.history)
        }
    }
}

@Composable
fun LocationPhotoCard(
    locationName: String,
    photo: LocationPhoto?,
    onTakePhoto: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Location Photo", fontWeight = FontWeight.Bold)

            if (photo == null) {
                Text("No photo yet for $locationName")
            } else {
                Base64Image(
                    base64 = photo.imageBase64,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                val lat = photo.latitude
                val lng = photo.longitude
                if (lat != null && lng != null) {
                    Text("Latitude: ${"%.5f".format(Locale.US, lat)}")
                    Text("Longitude: ${"%.5f".format(Locale.US, lng)}")
                } else {
                    Text("Coordinates unavailable")
                }
            }

            Button(onClick = onTakePhoto, modifier = Modifier.fillMaxWidth()) {
                Text(if (photo == null) "Take Photo" else "Update Photo")
            }
        }
    }
}

@Composable
fun HistoryCard(history: List<SavedItem>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Saved History (${history.size})", fontWeight = FontWeight.Bold)
            if (history.isEmpty()) {
                Text("Nothing saved yet")
            } else {
                history.take(10).forEach { Text("• ${it.name} (${it.category})") }
                if (history.size > 10) Text("... and ${history.size - 10} more")
            }
        }
    }
}

@Composable
fun ItemCard(item: DisplayItem) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (item.imageUrl != null) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
            } else {
                Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                    Text("No image")
                }
            }

            Text(
                text = item.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(item.label1, style = MaterialTheme.typography.labelSmall)
                    Text(item.value1, fontWeight = FontWeight.SemiBold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(item.label2, style = MaterialTheme.typography.labelSmall)
                    Text(item.value2, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}