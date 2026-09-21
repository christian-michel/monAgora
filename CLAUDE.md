# CLAUDE.md — Contexte du projet

## Résumé

**monAgora** : un téléphone sous logiciel libre (base e/OS, elle-même dérivée de LineageOS/AOSP) dont les applications (monPortefeuille, AgoraVote, AgoraVoix, maSanté, GPS citoyen) reposent sur une identité cryptographique personnelle et des **objets signés** échangés en pair-à-pair, sans dépendance à un serveur unique. Premier objectif concret : deux téléphones qui se synchronisent sans serveur (cf. `docs/constitution-technique.md`, section 10).

**Avant toute décision d'architecture ou de dépendance**, vérifie la cohérence avec les documents ci-dessous. Ne dévie pas des choix qui y sont actés sans le signaler explicitement à l'utilisateur — propose plutôt une question ou une alternative que d'improviser silencieusement.

## Documents de référence (à lire en priorité)

- `docs/constitution-technique.md` — principes fondateurs, portée des couches (OS/service/appli/protocole), stack retenue.
- `docs/format-objets-signes.md` — structure exacte des objets, calcul de l'id et de la signature, règles de validation.
- `docs/protocole-synchronisation.md` — messages échangés entre appareils, gestion du curseur de synchronisation, transports local/distant.
- `docs/identite-revocation.md` — clé racine/appareil/révocation, objets `identity_declaration`, `device_authorization`, `device_revocation`, `identity_revocation`, règles de validation.

## Stack v0.1 — ne pas dévier sans discussion explicite

- **Kotlin uniquement** (pas de Rust, pas de FFI/JNI pour l'instant).
- Crypto : Ed25519 (signature) + SHA-256 (empreinte), via Tink ou BouncyCastle.
- Stockage local : SQLite.
- **Pas de CRDT (Automerge)** — les objets sont append-only, révocation par référence (cf. format-objets-signes.md, section 6).
- **Pas de libp2p** — synchro locale via Wi-Fi Direct/Bluetooth (socket + JSON ligne par ligne), synchro distante via HTTPS simple (cf. protocole-synchronisation.md).
- UI : Jetpack Compose.
- Carte : MapLibre + OpenStreetMap. Vidéo : libVLC (ou équivalent libre).

Ces choix sont des simplifications assumées par rapport à une version antérieure du projet, pour rester réalisable en solo/petite équipe. Rust/libp2p/CRDT restent des options futures, seulement si un besoin concret et mesuré apparaît — ne pas les réintroduire par anticipation.

## Conventions de code

- Structure de package suggérée : `core/identity`, `core/objects` (création/vérification des objets signés), `core/sync`, `core/storage`, puis `app/<nom_application>` pour chaque application (ex. `app/gps-citoyen`, `app/mon-portefeuille`).
- Un module = une responsabilité claire, alignée sur la portée définie dans `docs/constitution-technique.md` (section 3).
- Tests obligatoires pour tout ce qui touche à la cryptographie et à la validation des objets : cas valides **et** cas de rejet (signature invalide, id incohérent, champ manquant, type inconnu).
- Pas de secret, clé privée ou donnée personnelle réelle committée dans le dépôt — utiliser des clés de test générées localement pour les tests automatisés.

## Journalisation — détecter les erreurs silencieuses

Le risque principal de ce projet n'est pas le crash visible, c'est l'**erreur silencieuse** : un objet rejeté sans qu'on sache pourquoi, une synchronisation qui échoue à moitié, une signature invalide traitée comme valide par accident. Logger largement, dès la v0.1, sur tout ce qui touche :

- **Validation d'objets** : pour chaque objet reçu ou créé, logger le résultat (accepté/rejeté) avec la raison précise en cas de rejet (id incohérent avec le contenu, signature invalide, champ manquant, type inconnu, version de schéma non supportée). Inclure `id` et `type` de l'objet dans le log, **jamais** la clé privée ni le contenu d'un payload potentiellement sensible en clair (cf. principe 3 de la constitution — confidentialité par défaut).
- **Synchronisation** : logger l'ouverture/fermeture de chaque session de sync (pair, transport utilisé), la requête envoyée (`since`, nombre demandé), la réponse reçue (nombre d'objets, `has_more`), et le nombre d'objets effectivement acceptés vs rejetés vs dédupliqués.
- **Stockage local** : logger les écritures/lectures en erreur (SQLite), jamais les échecs silencieux — une écriture qui échoue doit remonter une erreur visible, pas juste continuer.
- **Cryptographie** : logger chaque échec de vérification de signature avec le contexte (id de l'objet, clé publique attendue), sans jamais logger la clé privée ni les octets signés bruts si le contenu est sensible.
- **Réseau** : logger les échecs de connexion (local et distant), timeouts, et messages malformés reçus (avant de fermer la connexion, cf. `docs/protocole-synchronisation.md` section 6).

Règles pratiques :
- Pas de bloc `catch` vide ou qui avale une exception sans la logger — toute exception attrapée est au minimum loggée avec son contexte avant d'être gérée ou relancée.
- Utiliser des niveaux de log distincts (`DEBUG` pour le détail technique, `INFO` pour les événements normaux comme une sync réussie, `WARN` pour un rejet d'objet, `ERROR` pour un échec inattendu) plutôt qu'un seul niveau indistinct.
- Les logs doivent permettre de reconstituer *a posteriori* le parcours complet d'un objet donné (créé quand, par qui, transmis à qui, accepté ou rejeté où) — privilégier des logs structurés (champs clé-valeur) plutôt que du texte libre, pour pouvoir les filtrer par `id` ou par pair.
- Ne jamais faire dépendre le fonctionnement du programme d'un log (pas d'effet de bord dans un appel de log) — le log est une observation, pas une logique.

## Manière de travailler

- Avancer par petites étapes correspondant à la roadmap de `docs/constitution-technique.md` (section 10) : format d'objet → stockage local → synchronisation à deux → test de bout en bout sans serveur → première application (`GPS citoyen`).
- Une tâche = un commit cohérent et relisible, pas un gros commit qui mélange plusieurs sujets.
- Sur le code cryptographique en particulier (génération/vérification de signature, canonicalisation JSON), expliquer le raisonnement dans le commit ou en commentaire — ce code doit rester lisible et auditable, pas seulement fonctionnel.
- En cas d'ambiguïté entre deux implémentations possibles, préférer celle qui colle le plus littéralement à la spécification (`docs/format-objets-signes.md`, `docs/protocole-synchronisation.md`) plutôt qu'une optimisation ou une généralisation non demandée.

## Statut actuel du projet

*(à mettre à jour au fil de l'avancement)*

- [x] Format d'objet signé : génération + vérification (Kotlin, sans UI) — `core/objects` (id/canonicalisation RFC 8785, signature Ed25519), `core/identity` (clés, encodage b64/b64u)
- [x] Stockage local des objets (SQLite) — `core/storage` (dédup par id, requête par `since`/`types`/`limit`) ; implémentation actuelle via driver JDBC desktop, à remplacer par `android.database.sqlite`/Room quand le module `app` Android existera (voir note dans `SqliteSignedObjectStore.kt`)
- [ ] Synchronisation locale à deux appareils (Wi-Fi Direct/Bluetooth)
- [ ] Test de bout en bout hors-ligne (deux téléphones, aucun serveur)
- [ ] Application `GPS citoyen` (signalement géolocalisé et lieux utiles)
- [ ] Objets d'identité et de révocation (`identity_declaration`, `device_authorization`, `device_revocation`, `identity_revocation`)
- [ ] Mode invité : génération/destruction de clé éphémère, limite anti-abus locale (cf. `docs/constitution-technique.md` section 8)
