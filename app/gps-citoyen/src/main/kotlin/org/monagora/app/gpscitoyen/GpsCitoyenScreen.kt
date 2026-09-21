package org.monagora.app.gpscitoyen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.monagora.core.objects.wastereport.WasteReportPayload

@Composable
fun GpsCitoyenScreen(
    hasLocationPermission: Boolean,
    onRequestPermission: () -> Unit,
    onReportOverflowingBin: (note: String) -> Unit,
    reports: List<WasteReportPayload>,
    statusMessage: String?,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Scaffold { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("GPS citoyen", style = MaterialTheme.typography.headlineSmall)

                if (!hasLocationPermission) {
                    Text("La localisation est nécessaire pour signaler un problème à cet endroit.")
                    Button(onClick = onRequestPermission) { Text("Autoriser la localisation") }
                } else {
                    var note by remember { mutableStateOf("") }

                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Note (optionnel)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = {
                        onReportOverflowingBin(note)
                        note = ""
                    }) {
                        Text("Signaler une poubelle qui débordé")
                    }
                }

                statusMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

                HorizontalDivider()
                Text("Signalements connus de cet appareil (${reports.size})", style = MaterialTheme.typography.titleMedium)

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(reports) { report ->
                        Text("${report.category} — (${"%.5f".format(report.lat)}, ${"%.5f".format(report.lon)})" +
                            (report.note?.let { " — $it" } ?: ""))
                    }
                }
            }
        }
    }
}
