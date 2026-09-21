package org.monagora.app.gpscitoyen

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
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
import org.monagora.core.identity.GuestSession
import org.monagora.core.objects.SignedObjects
import org.monagora.core.objects.wastereport.WasteReportPayload
import org.monagora.core.objects.wastereport.WasteReports
import org.monagora.core.storage.GuestQuota
import org.monagora.core.storage.SignedObjectStore
import org.monagora.core.storage.android.AndroidGuestQuota
import org.monagora.core.storage.android.AndroidSignedObjectStore
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Première application réelle du projet (constitution-technique.md, section 10,
 * étape 5) : GPS citoyen, limité pour l'instant à un seul geste — signaler une
 * poubelle qui débordé — pour valider de bout en bout, sur un appareil réel, la
 * chaîne complète : identité invité -> objet signé -> stockage local Android.
 *
 * Volontairement en mode invité ([GuestSession]) plutôt qu'avec une identité
 * durable : docs/constitution-technique.md, section 8, autorise l'écriture en
 * invité pour GPS citoyen, et l'écran d'onboarding d'identité (clé racine,
 * autorisation d'appareil) reste à construire séparément — pas la peine de
 * bloquer ce premier écran sur cette dépendance (principe 13, progressivité).
 *
 * Limite anti-abus fixée arbitrairement à 20 signalements invité par jour et
 * par appareil ([GUEST_DAILY_LIMIT]) : constitution-technique.md, section 8, ne
 * fixe pas cette valeur elle-même ("choix applicatif") — ajustable librement.
 */
class MainActivity : ComponentActivity() {

    private val logger = LoggerFactory.getLogger(MainActivity::class.java)
    private val guestSession = GuestSession.start()

    private lateinit var store: SignedObjectStore
    private lateinit var quota: GuestQuota
    private lateinit var requestLocationPermission: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = AndroidSignedObjectStore(this)
        quota = AndroidGuestQuota(this)

        val hasPermission = mutableStateOf(hasAnyLocationPermission())
        val statusMessage = mutableStateOf<String?>(null)
        val reports = mutableStateListOf<WasteReportPayload>().apply { addAll(loadReports()) }

        // FINE et COARSE demandées ensemble : depuis Android 12, l'utilisateur
        // peut choisir de n'accorder que la position approximative en réponse
        // à une demande de FINE — ne vérifier que FINE ensuite la traiterait
        // à tort comme un refus complet.
        requestLocationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            val granted = hasAnyLocationPermission()
            hasPermission.value = granted
            if (!granted) statusMessage.value = "Permission de localisation refusée."
        }

        setContent {
            GpsCitoyenScreen(
                hasLocationPermission = hasPermission.value,
                onRequestPermission = {
                    requestLocationPermission.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    )
                },
                onReportOverflowingBin = { note -> reportOverflowingBin(note, statusMessage, reports) },
                reports = reports,
                statusMessage = statusMessage.value,
            )
        }
    }

    override fun onDestroy() {
        // Clé jetable détruite avec la session (docs/identite-revocation.md,
        // section 9) : pas d'objet de révocation à publier, elle cesse
        // simplement d'exister. Les objets déjà publiés restent valides.
        guestSession.close()
        super.onDestroy()
    }

    private fun hasAnyLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun loadReports(): List<WasteReportPayload> =
        store.query(types = listOf(WasteReports.TYPE_WASTE_REPORT))
            .mapNotNull { WasteReports.asWasteReport(it) }

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
