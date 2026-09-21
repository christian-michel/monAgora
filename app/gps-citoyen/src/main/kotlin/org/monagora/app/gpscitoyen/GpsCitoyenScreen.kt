package org.monagora.app.gpscitoyen

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.monagora.core.objects.wastereport.WasteReportPayload

/**
 * Écran unique de l'application : uniquement de l'UI Jetpack Compose, sans
 * logique métier ni état de session. C'est [MainActivity] qui porte tout
 * l'état réel (permissions accordées, position GPS, signalements chargés
 * depuis le stockage local, appareils Bluetooth appairés, résultat d'une
 * synchronisation en cours) et le fait redescendre ici en paramètres ; en
 * retour, chaque interaction utilisateur (clic, saisie) remonte vers
 * [MainActivity] via une lambda `onXxx` fournie par l'appelant plutôt que
 * d'être traitée ici (state hoisting — le patron standard Compose pour
 * séparer UI et logique). Concrètement : ce composable ne lit jamais de
 * permission Android, ne crée ni ne signe jamais d'objet `waste_report`, et
 * n'ouvre jamais lui-même de connexion Bluetooth — voir [MainActivity] pour
 * tout ça.
 *
 * Seule exception, volontaire : `note`, l'état du champ de texte du
 * signalement en cours de saisie (voir plus bas), reste local à cet écran —
 * un état d'UI purement transitoire (le texte tapé avant validation), sans
 * intérêt à exister en dehors de la durée de vie de ce composable ; il est
 * remis à vide juste après avoir été remonté via [onReportOverflowingBin].
 *
 * @param hasLocationPermission vrai si la permission de localisation
 *   (fine ou approximative) est accordée, tel que déterminé par
 *   [MainActivity] ; ce composable ne peut pas relire cette permission
 *   lui-même (il n'a pas de `Context` Android), et ne doit de toute façon
 *   pas dupliquer une logique de permission déjà centralisée côté activité —
 *   conditionne l'affichage du bouton de demande ou du formulaire de
 *   signalement.
 * @param onRequestLocationPermission déclenche la demande système de
 *   permission de localisation (ACCESS_FINE_LOCATION + ACCESS_COARSE_LOCATION).
 * @param onReportOverflowingBin signale une poubelle qui débordé à la
 *   position GPS courante, avec la note optionnelle saisie dans le champ de
 *   texte ; création et signature de l'objet `waste_report` entièrement
 *   déléguées à l'appelant.
 * @param reports signalements déjà connus de cet appareil (relus depuis le
 *   stockage local par [MainActivity]), du plus récent au plus ancien ;
 *   affichés dans l'ordre où la liste est fournie par l'appelant — cet écran
 *   ne trie rien lui-même, il affiche la liste telle qu'elle lui est passée
 *   (voir `MainActivity.loadReports`).
 * @param hasBluetoothPermission même rôle que [hasLocationPermission], pour
 *   les permissions Bluetooth (BLUETOOTH_CONNECT/BLUETOOTH_ADVERTISE) —
 *   même raison de ne pas la lire directement ici.
 * @param onRequestBluetoothPermission déclenche la demande système de
 *   permission Bluetooth.
 * @param pairedDevices appareils Bluetooth déjà appairés au niveau système
 *   (réglages Android, pas de découverte active depuis cette application) ;
 *   un bouton de synchronisation est proposé par appareil de la liste.
 * @param onSyncWithDevice déclenche une synchronisation Bluetooth avec
 *   l'appareil choisi (opération réseau, effectuée par l'appelant hors du
 *   thread UI).
 * @param statusMessage message d'état ponctuel à afficher sous le formulaire
 *   (résultat d'une action récente : signalement enregistré, synchronisation
 *   en cours/terminée/échouée, permission refusée...), ou `null` si rien à
 *   afficher.
 */
@Composable
fun GpsCitoyenScreen(
    hasLocationPermission: Boolean,
    onRequestLocationPermission: () -> Unit,
    onReportOverflowingBin: (note: String) -> Unit,
    reports: List<WasteReportPayload>,
    hasBluetoothPermission: Boolean,
    onRequestBluetoothPermission: () -> Unit,
    pairedDevices: List<BluetoothDevice>,
    onSyncWithDevice: (BluetoothDevice) -> Unit,
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
                    Button(onClick = onRequestLocationPermission) { Text("Autoriser la localisation") }
                } else {
                    // État purement local à cet écran (cf. KDoc de fonction ci-dessus) :
                    // le texte de la note tapée avant validation du formulaire.
                    // `rememberSaveable` (pas `remember`) : survit à une recréation
                    // d'activité (ex. rotation de l'écran) — sans quoi la note tapée
                    // serait perdue.
                    var note by rememberSaveable { mutableStateOf("") }

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
                Text("Synchronisation (Bluetooth)", style = MaterialTheme.typography.titleMedium)

                if (!hasBluetoothPermission) {
                    Text("Le Bluetooth est nécessaire pour synchroniser avec un autre appareil, sans serveur.")
                    Button(onClick = onRequestBluetoothPermission) { Text("Autoriser le Bluetooth") }
                } else if (pairedDevices.isEmpty()) {
                    Text("Aucun appareil Bluetooth appairé — appaire d'abord l'autre téléphone depuis les réglages système.")
                } else {
                    pairedDevices.forEach { device ->
                        OutlinedButton(onClick = { onSyncWithDevice(device) }) {
                            Text("Synchroniser avec ${deviceLabel(device)}")
                        }
                    }
                }

                HorizontalDivider()
                Text("Signalements connus de cet appareil (${reports.size})", style = MaterialTheme.typography.titleMedium)

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(reports) { report ->
                        // 5 décimales ≈ 1,1 m de précision au niveau de l'équateur — largement
                        // suffisant pour un affichage lisible à l'utilisateur (par opposition
                        // aux ~15-17 décimales d'un Double brut, qui n'apportent aucune
                        // précision réelle en plus, juste du bruit de calcul flottant).
                        Text("${report.category} — (${"%.5f".format(report.lat)}, ${"%.5f".format(report.lon)})" +
                            (report.note?.let { " — $it" } ?: ""))
                    }
                }
            }
        }
    }
}

// device.name lève MissingPermission sans BLUETOOTH_CONNECT (API 31+) — cette
// fonction n'est appelée que dans la branche pairedDevices.forEach ci-dessus,
// déjà gardée par hasBluetoothPermission ; @SuppressLint documente cette
// garantie pour le linter plutôt que de la contourner silencieusement.
@SuppressLint("MissingPermission") // n'est appelée que quand hasBluetoothPermission est vrai
private fun deviceLabel(device: BluetoothDevice): String = device.name ?: device.address
