package org.monagora.app.gpscitoyen

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.core.content.ContextCompat
import org.monagora.core.identity.Base64Url
import org.monagora.core.identity.GuestSession
import org.monagora.core.objects.SignedObjects
import org.monagora.core.objects.wastereport.WasteReportPayload
import org.monagora.core.objects.wastereport.WasteReports
import org.monagora.core.storage.GuestQuota
import org.monagora.core.storage.SignedObjectStore
import org.monagora.core.storage.android.AndroidGuestQuota
import org.monagora.core.storage.android.AndroidSignedObjectStore
import org.monagora.core.sync.SyncCursorStore
import org.monagora.core.sync.android.AndroidSyncCursorStore
import org.monagora.core.sync.android.BluetoothSyncClient
import org.monagora.core.sync.android.BluetoothSyncServer
import org.slf4j.LoggerFactory
import java.io.IOException
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.concurrent.thread

/**
 * Première application réelle du projet (constitution-technique.md, section 10,
 * étape 5) : GPS citoyen. Signale une poubelle qui débordé, et — depuis cet
 * ajout — synchronise ces signalements avec un autre appareil à portée
 * Bluetooth, sans serveur (docs/protocole-synchronisation.md, section 3) :
 * l'« objectif concret » énoncé en tête de CLAUDE.md.
 *
 * Volontairement en mode invité ([GuestSession]) plutôt qu'avec une identité
 * durable : docs/constitution-technique.md, section 8, autorise l'écriture en
 * invité pour GPS citoyen, et l'écran d'onboarding d'identité (clé racine,
 * autorisation d'appareil) reste à construire séparément — pas la peine de
 * bloquer ce premier écran sur cette dépendance (principe 13, progressivité).
 *
 * **Limite connue, assumée** : faute d'identité durable, la clé de session
 * invité sert aussi de `device_pubkey` pour le protocole de synchronisation
 * (hello, curseur par pair). Elle change à chaque relance de l'appli : le
 * curseur ne s'accumule donc pas d'une session à l'autre, chaque
 * synchronisation redemande plus d'objets que strictement nécessaire. Ça ne
 * casse rien (la déduplication par `id` protège toujours de tout doublon,
 * cf. format-objets-signes.md section 7) — c'est juste moins efficace tant
 * qu'une identité d'appareil durable n'existe pas.
 *
 * Limite anti-abus fixée arbitrairement à 20 signalements invité par jour et
 * par appareil ([GUEST_DAILY_LIMIT]) : constitution-technique.md, section 8, ne
 * fixe pas cette valeur elle-même ("choix applicatif") — ajustable librement.
 */
class MainActivity : ComponentActivity() {

    private val logger = LoggerFactory.getLogger(MainActivity::class.java)
    private val guestSession = GuestSession.start()
    private val localDevicePubkey = Base64Url.encode(guestSession.publicKey)

    private lateinit var store: SignedObjectStore
    private lateinit var quota: GuestQuota
    private lateinit var syncCursors: SyncCursorStore
    private lateinit var requestLocationPermission: ActivityResultLauncher<Array<String>>
    private lateinit var requestBluetoothPermission: ActivityResultLauncher<Array<String>>

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothServer: BluetoothSyncServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = AndroidSignedObjectStore(this)
        quota = AndroidGuestQuota(this)
        syncCursors = AndroidSyncCursorStore(this)
        bluetoothAdapter = getSystemService(BluetoothManager::class.java)?.adapter

        val hasLocationPermission = mutableStateOf(hasAnyLocationPermission())
        val hasBluetoothPermission = mutableStateOf(hasBluetoothPermission())
        val statusMessage = mutableStateOf<String?>(null)
        // loadReports() affiche du plus récent au plus ancien (voir sa définition :
        // elle inverse l'ordre croissant par created_at de store.query, cf.
        // AndroidSignedObjectStore.query) — cohérent avec reportOverflowingBin() qui
        // insère un nouveau signalement en tête de liste (add(0, ...)).
        val reports = mutableStateListOf<WasteReportPayload>().apply { addAll(loadReports()) }
        val pairedDevices = mutableStateListOf<BluetoothDevice>()

        requestLocationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            // FINE et COARSE demandées ensemble : depuis Android 12, l'utilisateur
            // peut choisir de n'accorder que la position approximative en réponse
            // à une demande de FINE — ne vérifier que FINE ensuite la traiterait
            // à tort comme un refus complet.
            val granted = hasAnyLocationPermission()
            hasLocationPermission.value = granted
            if (!granted) statusMessage.value = "Permission de localisation refusée."
        }

        requestBluetoothPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            val granted = hasBluetoothPermission()
            hasBluetoothPermission.value = granted
            if (granted) {
                startBluetoothServer()
                pairedDevices.setAll(pairedDevices())
            } else {
                statusMessage.value = "Permission Bluetooth refusée."
            }
        }

        if (hasBluetoothPermission.value) {
            startBluetoothServer()
            pairedDevices.setAll(pairedDevices())
        }

        setContent {
            GpsCitoyenScreen(
                hasLocationPermission = hasLocationPermission.value,
                onRequestLocationPermission = {
                    requestLocationPermission.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    )
                },
                onReportOverflowingBin = { note -> reportOverflowingBin(note, statusMessage, reports) },
                reports = reports,
                hasBluetoothPermission = hasBluetoothPermission.value,
                onRequestBluetoothPermission = {
                    requestBluetoothPermission.launch(
                        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE),
                    )
                },
                pairedDevices = pairedDevices,
                onSyncWithDevice = { device -> syncWithDevice(device, statusMessage, reports) },
                statusMessage = statusMessage.value,
            )
        }
    }

    override fun onDestroy() {
        bluetoothServer?.stop()
        // Clé jetable détruite avec la session (docs/identite-revocation.md,
        // section 9) : pas d'objet de révocation à publier, elle cesse
        // simplement d'exister. Les objets déjà publiés restent valides.
        guestSession.close()
        super.onDestroy()
    }

    private fun hasAnyLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun hasBluetoothPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true // permissions "normales" pré-Android 12, accordées à l'installation (cf. AndroidManifest.xml de core:sync-android)
        }

    @SuppressLint("MissingPermission") // vérifié par hasBluetoothPermission() avant tout appel
    private fun pairedDevices(): List<BluetoothDevice> = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()

    private fun startBluetoothServer() {
        val adapter = bluetoothAdapter ?: return
        val server = BluetoothSyncServer(adapter, localDevicePubkey, listOf(WasteReports.TYPE_WASTE_REPORT), store, syncCursors)
        bluetoothServer = server
        thread(name = "sync-bt-server") {
            try {
                server.start()
            } catch (e: IOException) {
                logger.error("bluetooth_sync_server_thread_failed reason=\"{}\"", e.message)
            }
        }
    }

    @SuppressLint("MissingPermission") // vérifié par hasBluetoothPermission() avant que l'UI ne propose de synchroniser
    private fun syncWithDevice(
        device: BluetoothDevice,
        statusMessage: MutableState<String?>,
        reports: SnapshotStateList<WasteReportPayload>,
    ) {
        val adapter = bluetoothAdapter ?: return
        statusMessage.value = "Synchronisation avec ${device.name ?: device.address} en cours…"
        thread(name = "sync-ui-trigger") {
            try {
                BluetoothSyncClient(adapter, localDevicePubkey, listOf(WasteReports.TYPE_WASTE_REPORT), store, syncCursors)
                    .syncWith(device)
                runOnUiThread {
                    statusMessage.value = "Synchronisation terminée."
                    reports.setAll(loadReports())
                }
            } catch (e: IOException) {
                logger.error("bluetooth_sync_ui_trigger_failed remote={} reason=\"{}\"", device.address, e.message)
                runOnUiThread { statusMessage.value = "Échec de la synchronisation : ${e.message}" }
            }
        }
    }

    /**
     * Du plus récent au plus ancien : [SignedObjectStore.query] trie par `created_at`
     * croissant (nécessaire à la pagination du protocole de synchronisation, cf.
     * `SyncCursor`) — inversé ici uniquement pour l'affichage, cohérent avec
     * [reportOverflowingBin] qui insère un nouveau signalement en tête de liste.
     */
    private fun loadReports(): List<WasteReportPayload> =
        store.query(types = listOf(WasteReports.TYPE_WASTE_REPORT))
            .mapNotNull { WasteReports.asWasteReport(it) }
            .asReversed()

    private fun reportOverflowingBin(
        note: String,
        statusMessage: MutableState<String?>,
        reports: SnapshotStateList<WasteReportPayload>,
    ) {
        val location = lastKnownLocation()
        if (location == null) {
            statusMessage.value = "Pas de position GPS disponible pour l'instant — réessaie dans un instant."
            logger.warn("waste_report_creation_failed reason=\"aucune position connue\"")
            return
        }

        if (!quota.tryConsume(GUEST_DAILY_LIMIT)) {
            statusMessage.value = "Limite de signalements en mode invité atteinte pour aujourd'hui."
            return
        }

        val obj = WasteReports.createAsGuest(
            session = guestSession,
            createdAt = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),
            lat = location.latitude,
            lon = location.longitude,
            category = "overflowing_bin",
            note = note.ifBlank { null },
        )

        check(SignedObjects.verify(obj)) { "un objet qu'on vient de créer et signer doit se vérifier lui-même" }
        store.save(obj)

        // Insertion en tête (pas en fin, contrairement à l'ordre de loadReports() —
        // voir la note dans onCreate) : le signalement qu'on vient de créer soi-même
        // doit rester visible en haut sans avoir à faire défiler la liste.
        reports.add(0, WasteReports.asWasteReport(obj)!!)
        statusMessage.value = "Signalement enregistré."
        logger.info("waste_report_created_from_ui id={}", obj.id)
    }

    @SuppressLint("MissingPermission") // le seul appelant vérifie hasLocationPermission avant d'arriver ici
    private fun lastKnownLocation(): Location? {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        return locationManager.allProviders
            .mapNotNull { provider -> locationManager.getLastKnownLocation(provider) }
            .maxByOrNull { it.time }
    }

    companion object {
        private const val GUEST_DAILY_LIMIT = 20
    }
}

private fun <T> SnapshotStateList<T>.setAll(items: List<T>) {
    clear()
    addAll(items)
}
