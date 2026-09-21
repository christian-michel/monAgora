# Constitution technique — monAgora

*Version 0.1 — document de référence, à faire évoluer au fil du projet*

---

## 1. Vision

Créer un environnement numérique personnel, sous licence libre, permettant à un individu de gérer son identité, ses échanges, ses données et sa participation collective (vote, signalement, monnaie) **sans dépendre d'une autorité technique unique**, tout en restant utilisable au quotidien dès la v0.1.

Ce document sert de filtre de décision : à chaque choix technique, on doit pouvoir répondre à *« est-ce que ça respecte ces principes ? »*. Il sera donné en contexte au début de chaque nouvelle session de travail (avec moi ou avec Claude Code) pour garder la continuité du projet.

Le projet est nommé **monAgora** : un outil citoyen et personnel. Les applications prévues, chacune une application au sens de la section 3 :

- **monPortefeuille** — monnaie libre, fork du projet libre Gecko.
- **AgoraVote** — consultation et vote citoyen.
- **AgoraVoix** — expression publique et débat.
- **maSanté** — suivi de santé personnel.
- **GPS citoyen** — cartographie participative (transports en commun, poubelles, restaurants, médecins, pharmacies, commerces, associations, mairies…).

Chacune s'appuie sur les mêmes services système (identité, objets signés, synchronisation, capacités) — aucune n'a de mécanisme de confiance ou de stockage qui lui soit propre.

---

## 2. Principes fondateurs

1. **Logiciel libre** — tout composant développé pour le projet est publié sous licence libre (à définir : GPL, MIT, AGPL selon les couches).
2. **Souveraineté des données** — les données créées par l'utilisateur lui appartiennent ; aucune application ne doit être seule dépositaire d'une donnée essentielle.
3. **Confidentialité par défaut** — une donnée reste privée et locale tant qu'elle n'est pas explicitement partagée.
4. **Identité contrôlée par l'utilisateur** — l'identité est cryptographique (clé privée détenue par l'utilisateur), pas administrative (compte chez un tiers).
5. **Fonctionnement hors-ligne** — le téléphone doit rester utilisable sans réseau ; la synchronisation est une opportunité, pas une dépendance.
6. **Aucun point de défaillance unique** — aucun service essentiel (identité, données, réseau) ne doit dépendre d'un seul serveur pour fonctionner.
7. **Vérifiabilité** — toute donnée partagée doit pouvoir être authentifiée (signature) indépendamment de sa source de transmission.
8. **Interopérabilité** — les formats de données sont documentés et ouverts ; une autre application (même non écrite par nous) doit pouvoir les lire.
9. **Portabilité** — l'utilisateur peut exporter l'intégralité de ses données dans un format exploitable ailleurs.
10. **Simplicité avant généralité** — on résout le problème concret posé par une application avant de construire une abstraction générique pour tous les cas futurs imaginables.
11. **Réutilisation avant réinvention** — on s'appuie sur des briques existantes, matures et éprouvées (Android/e/OS, SQLite, bibliothèques crypto standard, OpenStreetMap, moteur vidéo existant) partout où c'est possible.
12. **Neutralité politique de l'outil** — le système fournit des capacités (voter, signer, publier), pas un modèle de société imposé ; les règles précises (mode de scrutin, gouvernance d'un commun) sont un choix de l'application ou de la communauté qui l'utilise, pas du système.
13. **Progressivité** — chaque étape doit produire quelque chose d'utilisable, pas seulement une fondation invisible pour la suivante.

---

## 3. Portée : qui fait quoi

Question centrale à trancher pour chaque brique : **est-ce une capacité universelle (→ système) ou un choix d'usage (→ application) ?**

| Couche | Rôle | Exemples |
|---|---|---|
| **OS existant** (réutilisé tel quel) | Fournit le matériel, les capteurs, la sécurité de base | e/OS (base LineageOS/AOSP), Android runtime, permissions Android |
| **Services système** (développés par nous) | Fournissent des capacités communes à toutes les applications | Identité, Objets signés, Stockage local, Synchronisation |
| **Bibliothèques externes** (réutilisées) | Fonctions techniques génériques | SQLite, bibliothèque crypto (Tink/BouncyCastle), MapLibre, OpenStreetMap, moteur vidéo (libVLC) |
| **Applications** (développées par nous) | Traduisent la vision en usages concrets | monPortefeuille, AgoraVote, AgoraVoix, maSanté, GPS citoyen |
| **Protocole** (spécification, pas du code) | Définit comment deux appareils/applications se comprennent | Format des objets, format de synchronisation |
| **Hors du système** | Volontairement laissé à l'écosystème existant | Décodage vidéo bas niveau, moteur de rendu cartographique, gestion du modem |

**Règle de décision pratique** : si une fonctionnalité doit être partagée par au moins deux applications prévues (ex : signature, identité, stockage), elle va en service système. Si elle ne sert qu'à un seul usage précis, elle reste dans l'application.

---

## 4. Le concept central : l'objet signé

Toute donnée qui a vocation à être vérifiée ou partagée (un vote, une transaction, un signalement) suit le même format de base :

```
Object
├── id            (identifiant unique)
├── type          (ex: "waste_report", "vote", "transaction")
├── author        (clé publique de l'auteur)
├── created_at    (horodatage)
├── payload       (contenu spécifique au type)
└── signature     (signature de author sur les champs ci-dessus)
```

Ce format unique permet au système de gérer génériquement : authentification, horodatage, intégrité, et réplication — sans que chaque application ait à réinventer ces mécanismes. Le détail complet (calcul de l'id, de la signature, règles de validation) est spécifié dans `docs/format-objets-signes.md`.

**Choix assumé (simplification par rapport à la première proposition) : pas de CRDT (type Automerge) pour la v0.1.** Les objets sont *append-only* (créés une fois, jamais réécrits) : un vote, une transaction, un signalement ne sont pas modifiés après coup, ils sont créés, éventuellement suivis d'un nouvel objet qui les révoque ou les complète. Cela évite d'introduire la complexité de la fusion automatique de documents concurrents — utile pour de l'édition collaborative de texte, pas nécessaire ici.

---

## 5. Identité

- Chaque personne possède une **clé racine** (root key), utilisée pour autoriser les appareils et identités dérivées, à l'usage courant.
- Chaque appareil possède sa propre **clé d'appareil**, autorisée par la clé racine.
- Une personne possède aussi une **clé de révocation**, distincte de la clé racine, générée à la création de l'identité et gardée à part (hors-ligne). Son seul pouvoir : signer une révocation totale de l'identité, y compris si la clé racine elle-même est compromise. Inspirée du champ `revocation_key` de Duniter (Ğ1), qui suit le même principe.
- Une personne peut avoir **plusieurs identités dérivées** (pseudonymes) pour cloisonner ses activités (santé, citoyenne, professionnelle) sans lien automatique entre elles.
- La révocation (d'un appareil ou de l'identité entière) est possible sans dépendre d'une autorité centrale — mécanisme détaillé dans `docs/identite-revocation.md`.

*(Point encore ouvert, distinct de la révocation : mécanisme de récupération en cas de perte simultanée de la clé racine et de la clé de révocation — trade-off sécurité/facilité d'usage à trancher explicitement, pas en cours de route.)*

---

## 6. Réseau et synchronisation

**Choix assumé (simplification) : pas de libp2p en v0.1.**

- **Transport local** : Wi-Fi Direct ou Bluetooth pour la synchronisation entre appareils physiquement proches, via les API Android standard.
- **Transport distant** : simple client/serveur (HTTPS/WebSocket) vers un ou plusieurs pairs connus (adresse IP ou nom de domaine) — pas de découverte automatique de pairs inconnus pour l'instant.
- **Protocole de synchronisation** : un appareil demande à un autre *« donne-moi les objets que tu as reçus/créés depuis le timestamp X »* ; l'autre répond avec la liste des objets signés correspondants. Chaque appareil stocke localement les objets déjà connus (par leur `id`) pour éviter les doublons.
- Les serveurs, s'il y en a, sont des **relais optionnels** (ils accélèrent la découverte et la disponibilité) mais jamais une source de vérité unique : un objet reste valide et vérifiable indépendamment du serveur qui l'a transmis.

Cette approche couvre le scénario cible du prototype (deux téléphones qui se rencontrent et se synchronisent sans serveur) sans la complexité d'un stack P2P à grande échelle. On pourra introduire quelque chose comme libp2p plus tard si un vrai besoin de découverte de pairs inconnus à grande échelle apparaît.

---

## 7. Stockage

- **SQLite** pour toutes les données locales : index, cache, et table des objets signés reçus/créés.
- **Système de fichiers** pour les fichiers volumineux (photos, vidéos), référencés depuis un objet plutôt que stockés dedans.
- Pas de couche de stockage distribuée dédiée pour la v0.1 — chaque appareil a sa copie locale complète des objets qui le concernent.

---

## 8. Permissions et capacités

**Choix assumé (simplification)** : pas de système de capacités générique développé de zéro pour la v0.1.

- On s'appuie sur le **modèle de permissions Android existant** pour l'accès au matériel (GPS, caméra, etc.).
- Le service Identité gère en plus des **autorisations applicatives scopées** : une application obtient un jeton lui donnant le droit de signer/lire certains types d'objets au nom d'une identité donnée (ex : l'application Santé peut lire les objets de type `health_measurement` mais pas `vote`).
- Un modèle de capacités plus fin et générique (à la façon d'un OS à capacités) pourra être envisagé plus tard, une fois que les besoins réels se seront révélés à l'usage — pas avant.

### Mode invité — identité éphémère

- À l'activation, génération locale d'une paire de clés jetable, sans aucun objet `identity_declaration` ni `device_authorization` associé — elle n'entre jamais dans le graphe d'identités durables (cf. `docs/identite-revocation.md`).
- Les objets créés en mode invité sont signés normalement (même format), mais une application les reconnaît comme provenant d'une identité non enregistrée par l'absence de `device_authorization` correspondant.
- À la fermeture de session, la clé privée est détruite localement — pas d'objet de révocation nécessaire, la clé cesse simplement d'exister. Les objets déjà publiés restent disponibles (cohérent avec le modèle append-only, section 4).
- **Anti-abus** : pas de résistance Sybil possible pour une clé jetable par nature — la limite se pose au niveau de l'appareil (nombre d'objets créés en mode invité par appareil et par jour), pas de l'identité.
- **Portée par défaut, application par application** (choix éditorial, ajustable librement — pas une contrainte technique) :

| Application | Lecture en invité | Écriture en invité |
|---|---|---|
| GPS citoyen | Oui | Oui — signalement utile même sans identité durable |
| AgoraVoix | Oui | Non |
| AgoraVote | Oui (consultation) | **Non** — un vote suppose une identité rattachée à la toile de confiance |
| monPortefeuille | — | Non applicable |
| maSanté | — | Non applicable |

---

## 9. Stack technique retenue pour la v0.1

| Domaine | Choix |
|---|---|
| OS | /e/OS (base LineageOS/AOSP, community pour `beryllium`) |
| Langage | **Kotlin uniquement** (pas de Rust/FFI pour l'instant) |
| UI | Jetpack Compose |
| Crypto | Bibliothèque standard JVM (ex : Google Tink) |
| Stockage | SQLite |
| Réseau local | Wi-Fi Direct / Bluetooth (API Android) |
| Réseau distant | HTTPS / WebSocket vers pairs connus |
| Carte | MapLibre + OpenStreetMap |
| Vidéo | libVLC (ou équivalent libre existant) |
| Modèle de données | Objets signés, append-only, format JSON documenté |

Rust, libp2p et un système de CRDT restent des options pour une **version ultérieure**, seulement si un besoin concret et mesuré (perf, portabilité multi-plateforme, édition collaborative réelle) apparaît — pas par anticipation.

---

## 10. Étapes vers le prototype

1. **Format d'objet + signature** : définir précisément le JSON, écrire génération/vérification de signature en Kotlin (sans UI).
2. **Stockage local** : persister/relire des objets signés dans SQLite.
3. **Synchronisation à deux** : deux appareils (ou un appareil + un simulateur) échangent leurs objets via Wi-Fi Direct ou sur le même réseau local, sans serveur.
4. **Test de validation** : couper Internet, créer un objet sur le téléphone A, rapprocher le téléphone B, vérifier que B reçoit et vérifie l'objet.
5. **Première application réelle** : *Terrain*, avec GPS + photo + création d'objet `waste_report`, branchée sur les briques ci-dessus.

Ce document sera mis à jour à mesure que des décisions sont prises ou révisées — toute déviation par rapport aux principes de la section 2 doit être justifiée explicitement plutôt que silencieuse.
