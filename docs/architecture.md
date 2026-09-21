# Carte du projet — architecture et navigation

*Document de navigation, pas une spécification : en cas de contradiction avec
`constitution-technique.md`, `format-objets-signes.md`,
`protocole-synchronisation.md` ou `identite-revocation.md`, ce sont ces
derniers qui font foi. Ce document explique **où vit chaque chose dans le
code**, pas les règles elles-mêmes.*

**À tenir à jour** : toute création/suppression/déplacement de module, ou
tout changement de dépendance entre modules, doit se refléter ici dans le
même commit (cf. CLAUDE.md, section "Manière de travailler").

---

## 1. Vue d'ensemble

monAgora n'a pour l'instant qu'une seule application réelle, **GPS citoyen**
(`app/gps-citoyen`), posée sur une pile de modules `core/*` qui portent les
capacités communes à toute future application (identité, objets signés,
stockage, synchronisation, confiance) — cf. `constitution-technique.md`,
section 3, tableau des couches. Un module `core/*` ne connaît jamais une
application par son nom ; c'est toujours l'application qui assemble les
briques `core/*` dont elle a besoin.

Chaque module `core/*` existe en une ou deux variantes :
- une variante **Kotlin/JVM pur** (aucune API `android.*`), utilisable en
  test/dev sans SDK Android ni appareil — c'est là que vit l'essentiel de la
  logique et des tests ;
- éventuellement une variante **`-android`**, qui n'ajoute qu'une
  implémentation liée à une API Android (SQLite, Bluetooth, `android.util.Log`)
  derrière la même interface que la variante JVM, pensée comme un
  remplacement mécanique côté appelant (voir section 4).

## 2. Carte des modules

| Module | Type | Dépend de | Rôle | Fichiers clés |
|---|---|---|---|---|
| `core/identity` | Kotlin/JVM | — | Clés Ed25519 (génération/signature/vérification), encodages `b64:`/`b64u:`, session invité | `Ed25519Keys`, `Ed25519KeyPair`, `Base64Std`, `Base64Url`, `GuestSession` |
| `core/objects` | Kotlin/JVM | `core/identity` | Format d'objet signé : id canonique (RFC 8785), création, vérification ; payloads `identity`/`wastereport` | `SignedObject`, `SignedObjects`, `CanonicalEnvelope`, `SignedObjectCodec`, `Sha256`, `identity/IdentityObjects`, `wastereport/WasteReports` |
| `core/storage` | Kotlin/JVM | `core/objects` | Persistance SQLite (driver JDBC desktop) des objets signés + quota invité | `SignedObjectStore` (interface), `SqliteSignedObjectStore`, `GuestQuota` (interface), `SqliteGuestQuota` |
| `core/storage-android` | Android lib | `core/storage`, `core/objects` | Mêmes interfaces que `core/storage`, implémentées via `android.database.sqlite` | `DbOpenHelper`, `AndroidSignedObjectStore`, `AndroidGuestQuota` |
| `core/sync` | Kotlin/JVM | `core/objects`, `core/storage` | Protocole de synchronisation : messages, codec JSON, curseur par pair, ingestion (validation+dédup), session complète sur `InputStream`/`OutputStream` génériques | `SyncMessage` (+ sous-types), `SyncMessageCodec`, `SyncCursor`, `SyncCursorStore` (interface), `SqliteSyncCursorStore`, `SyncIngest`, `SyncSession` |
| `core/sync-android` | Android lib | `core/sync`, `core/storage-android` | Transport Bluetooth RFCOMM (serveur + client) au-dessus de `SyncSession` ; curseur Android | `BluetoothSyncTransport`, `BluetoothSyncServer`, `BluetoothSyncClient`, `AndroidSyncCursorStore` |
| `core/trust` | Kotlin/JVM | `core/objects`, `core/storage` | Résolution de confiance appareil/identité (`identite-revocation.md`, section 4) | `TrustResolver`, `DeviceTrust` |
| `core/logging-android` | Android lib | — (SLF4J API seulement) | Binding SLF4J → `android.util.Log`, seuil de log réglable dynamiquement (mode debug) | `AndroidServiceProvider`, `AndroidLoggerFactory`, `AndroidLogger`, `AndroidLogging` |
| `app/gps-citoyen` | Android app | tous les `core/*` ci-dessus | Première application réelle : signalement géolocalisé + synchro Bluetooth | `MainActivity`, `GpsCitoyenScreen`, `MonAgoraApplication` |

## 3. Graphe de dépendances

```
core/identity
   └── core/objects
          ├── core/storage
          │      └── core/storage-android
          ├── core/sync ── (utilise core/storage)
          │      └── core/sync-android ── (utilise core/storage-android)
          └── core/trust ── (utilise core/storage)

core/logging-android            (indépendant — juste un binding SLF4J)

app/gps-citoyen
   └── dépend de tous les modules ci-dessus
```

Règle Gradle à connaître (source de deux bugs réels rencontrés) : un module
dont l'API publique expose un type venant d'une dépendance doit déclarer
cette dépendance en `api`, pas `implementation`, sinon les modules qui en
dépendent ne voient pas ce type. Exemple : `core/objects` et `core/storage`
déclarent `kotlinx-serialization-json` en `api` parce que `SignedObject.payload`
est un `JsonObject` public.

## 4. Deux variantes, une seule interface : le motif JDBC ↔ Android

`core/storage`, `core/sync` (curseur) et `core/storage-android`/`core/sync-android`
illustrent le même motif partout dans ce projet :

1. Une interface Kotlin pure définit le contrat (`SignedObjectStore`, `GuestQuota`,
   `SyncCursorStore`).
2. `core/storage`/`core/sync` fournissent une implémentation JDBC desktop
   (`org.xerial:sqlite-jdbc`) — **utilisable uniquement en JVM pur, pas sur un
   appareil Android réel** (bibliothèques natives desktop). Elle existe pour
   pouvoir développer et tester ce module avant même d'avoir un environnement
   Android complet.
3. `core/storage-android`/`core/sync-android` fournissent l'implémentation
   réelle, via `android.database.sqlite`/`android.bluetooth`, avec le même
   schéma SQL et le même contrat — un remplacement mécanique côté appelant,
   jamais un second protocole.

`app/gps-citoyen` n'utilise que les variantes `-android` ; les variantes JVM
pures ne sont là que pour le développement/test de la logique commune.

## 5. Où trouver quoi (spec → code)

| Concept de la spec | Document | Code |
|---|---|---|
| Structure d'un objet signé, calcul `id`/`signature` | `format-objets-signes.md` §2-3 | `SignedObject`, `CanonicalEnvelope`, `SignedObjects.create`/`verify` |
| Payload `waste_report` v1 | `format-objets-signes.md` §5 | `wastereport/WasteReportPayload`, `wastereport/WasteReports` |
| Objets d'identité/révocation (4 types) | `identite-revocation.md` §3 | `identity/IdentityObjects`, `identity/IdentityPayloads` |
| Règles de résolution de confiance (les 3 règles) | `identite-revocation.md` §4 | `TrustResolver.resolve` |
| Mode invité (clé jetable, quota) | `constitution-technique.md` §8 | `GuestSession` (`core/identity`), `SignedObjects.createAsGuest`, `GuestQuota`/`SqliteGuestQuota`/`AndroidGuestQuota` |
| Vocabulaire des messages de sync (`hello`, `sync_request`, ...) | `protocole-synchronisation.md` §2 | `SyncMessage` et ses sous-types, `SyncMessageCodec` |
| Curseur de synchronisation, marge de sécurité | `protocole-synchronisation.md` §5 | `SyncCursor`, `SyncCursorStore`/`SqliteSyncCursorStore`/`AndroidSyncCursorStore` |
| Déroulé d'une session locale (hello → requêtes symétriques → fermeture) | `protocole-synchronisation.md` §3 | `SyncSession.run` |
| Validation/rejet à l'ingestion d'une réponse de sync | `protocole-synchronisation.md` §6 | `SyncIngest.ingest` |
| Transport local réel (Bluetooth) | `protocole-synchronisation.md` §3 (établissement de connexion, hors protocole) | `BluetoothSyncServer`, `BluetoothSyncClient`, `BluetoothSyncTransport` |
| Journalisation des erreurs silencieuses | `CLAUDE.md`, section Journalisation | tous les `catch` du projet ; binding Android : `core/logging-android`, activé par `MonAgoraApplication` |

## 6. Parcours de bout en bout d'un `waste_report`

Sert de fil conducteur pour comprendre comment les modules s'articulent,
depuis l'écran jusqu'à l'appareil pair :

1. **Création** (`MainActivity.reportOverflowingBin`) : position GPS lue via
   `android.location.LocationManager`, quota invité vérifié
   (`GuestQuota.tryConsume`), objet assemblé et signé par
   `WasteReports.createAsGuest` → `SignedObjects.createAsGuest` → id canonique
   (`CanonicalEnvelope` + `Sha256`) et signature Ed25519 par la clé de session
   (`GuestSession.sign`).
2. **Stockage local** : `AndroidSignedObjectStore.save` (SQLite, dédup par `id`).
3. **Synchronisation** : au clic sur "Synchroniser avec ...", `BluetoothSyncClient`
   ouvre un socket RFCOMM vers l'appareil appairé et délègue à `SyncSession.run`,
   qui échange `hello`, demande les objets nouveaux depuis le curseur connu pour
   ce pair (`AndroidSyncCursorStore`), reçoit une `sync_response`.
4. **Réception côté pair** : `SyncIngest.ingest` revérifie chaque objet reçu
   (`SignedObjects.verify` — jamais fait confiance à la seule provenance réseau),
   le stocke s'il est nouveau et valide, l'ignore silencieusement s'il est déjà
   connu (dédup par `id`), le rejette (loggé) s'il est invalide.
5. **Affichage** : l'écran recharge la liste depuis le store local
   (`MainActivity.loadReports`) — le pair voit désormais le signalement, sans
   qu'aucun serveur n'ait été impliqué.

La résolution de confiance (`TrustResolver`) et les objets d'identité
(`IdentityObjects`) ne sont pas encore branchés dans ce parcours concret :
`GPS citoyen` fonctionne aujourd'hui entièrement en mode invité (voir la note
dans `MainActivity.kt` et le statut dans `CLAUDE.md`).

## 7. Construire et tester

```
./gradlew test                 # tous les modules Kotlin/JVM purs (identity, objects, storage, sync, trust)
./gradlew :core:objects:test   # un seul module
./gradlew assembleDebug        # APK debug de app/gps-citoyen (nécessite le SDK Android, cf. CLAUDE.md)
```

`scripts/install-android-sdk.sh` installe le SDK si besoin (environnement de
dev éphémère, à relancer à chaque nouveau conteneur).

## 8. Statut d'avancement

Le détail au fil de l'eau (ce qui est fait, en cours, manquant) vit dans
`CLAUDE.md`, section "Statut actuel du projet" — pas dupliqué ici, pour
n'avoir qu'un seul endroit à tenir à jour.
