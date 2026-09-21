# Spécification — Protocole de synchronisation

*Version 0.1 — complète la constitution technique, section 6*

---

## 1. Principe directeur

Le protocole n'a **pas besoin d'authentifier le canal de transport** : la confiance repose entièrement sur la vérification des objets eux-mêmes (id + signature, cf. spécification des objets signés). Un appareil peut donc accepter une connexion de synchronisation d'un pair inconnu sans risque particulier — au pire il reçoit des objets invalides, qu'il rejette silencieusement sans les stocker ni les retransmettre.

Cette simplification est volontaire et cohérente avec le principe 10 de la constitution (simplicité avant généralité) : pas de handshake cryptographique complexe pour la v0.1, la sécurité de fond est déjà assurée à un autre niveau.

Deux liaisons de transport, un seul vocabulaire de messages :
- **Local** (Wi-Fi Direct / Bluetooth) : socket TCP/RFCOMM, messages JSON délimités par des sauts de ligne.
- **Distant** (Internet, pair connu) : HTTP/HTTPS, mêmes messages exprimés en requêtes/réponses REST.

---

## 2. Vocabulaire des messages

### `hello` (transport local uniquement)

Échangé à l'ouverture de la connexion, dans les deux sens :

```json
{
  "msg": "hello",
  "protocol_version": 1,
  "device_pubkey": "b64u:8fJ3k...==",
  "supported_types": ["waste_report", "revocation"]
}
```

Sert uniquement à vérifier la compatibilité de version et à savoir quels types de payloads l'autre appareil sait interpréter — **pas** à établir une confiance quelconque (cf. section 1).

### `sync_request`

```json
{
  "msg": "sync_request",
  "since": "2026-09-11T09:00:00Z",
  "types": null,
  "limit": 200
}
```

- `since` : ne demander que les objets créés après cet horodatage (voir gestion du curseur, section 4).
- `types` : `null` = tous les types ; sinon liste blanche (`["waste_report"]`).
- `limit` : nombre maximal d'objets par réponse (pagination, voir section 5).

### `sync_response`

```json
{
  "msg": "sync_response",
  "objects": [ { "...objet signé..." } ],
  "has_more": true,
  "next_since": "2026-09-11T10:12:00Z"
}
```

`next_since` = `created_at` du dernier objet renvoyé ; à utiliser comme `since` de la requête suivante si `has_more` est vrai.

### `file_request` / `file_response`

Pour récupérer un fichier référencé par son empreinte dans un payload (ex. `photo_hash`) :

```json
{ "msg": "file_request", "hash": "sha256:9f86d0..." }
```

```json
{ "msg": "file_response", "hash": "sha256:9f86d0...", "size": 184320, "data": "<base64>" }
```

*(Pour des fichiers volumineux, prévoir un découpage en plusieurs `file_response` — détail à préciser quand le besoin se présentera concrètement ; pas anticipé davantage pour la v0.1.)*

### `error`

```json
{ "msg": "error", "code": "unsupported_version", "detail": "..." }
```

---

## 3. Liaison locale (Wi-Fi Direct / Bluetooth)

1. Connexion établie via les API Android standard (Wi-Fi Direct ou Bluetooth), en dehors du protocole lui-même.
2. Chaque appareil ouvre un socket et envoie `hello`.
3. Chaque appareil envoie ensuite un `sync_request` (les deux sens sont indépendants et symétriques : A demande à B, B demande à A, dans la même session).
4. Échange de `sync_response`, pagination si nécessaire.
5. Les fichiers référencés mais absents localement sont demandés via `file_request`.
6. Fermeture propre du socket une fois les deux `has_more` à `false`.

Framing : un message JSON par ligne (`\n` comme séparateur), UTF-8.

---

## 4. Liaison distante (HTTP/HTTPS)

Un appareil peut exposer un point d'accès simple (utile pour un pair "toujours allumé" faisant office de relais, ou pour deux appareils qui ne sont pas à portée locale) :

| Requête | Équivalent au message |
|---|---|
| `GET /sync?since=...&types=...&limit=...` | `sync_request` → réponse = corps JSON équivalent à `sync_response` |
| `GET /files/{hash}` | `file_request` → réponse = octets bruts avec en-tête `X-Content-Hash` |
| `POST /objects` | Pousser un lot d'objets vers ce pair (corps = tableau d'objets) |
| `POST /files` | Pousser un fichier (corps brut + en-tête `X-Content-Hash`) |

**HTTPS obligatoire** pour toute synchronisation distante — non pas pour l'authentification (assurée par les signatures) mais pour la confidentialité du contenu en transit face à un observateur passif du réseau.

Le fait d'ajouter un pair distant (son adresse) est **une action manuelle de l'utilisateur** — pas de découverte automatique de pairs inconnus sur Internet en v0.1, conformément au principe 6 de la constitution.

---

## 5. Gestion du curseur de synchronisation

Chaque appareil conserve, **par pair connu** (identifié par sa clé publique d'appareil), un curseur `last_synced_at` :

- Initialement absent → première synchronisation demande tout (`since` omis ou epoch 0).
- Après une synchronisation réussie, `last_synced_at` est mis à jour à `next_since` reçu, **moins une marge de sécurité** (ex. 5 minutes) pour absorber un éventuel décalage d'horloge entre appareils.
- Comme la déduplication se fait par `id` (cf. spécification des objets), redemander par erreur un objet déjà connu n'est jamais un problème — seulement un peu de bande passante gaspillée. Il vaut donc mieux pécher par excès de prudence sur le curseur que risquer de manquer un objet.

---

## 6. Validation et rejet

À la réception de chaque objet dans un `sync_response` :

1. Vérifier `id` et `signature` selon la procédure de la spécification des objets signés.
2. Objet invalide → rejeté silencieusement, jamais stocké, jamais retransmis.
3. Objet valide mais `type` inconnu de l'application locale → stocké quand même (pour permettre une resynchronisation future une fois l'application mise à jour), mais non interprété.

Un message malformé (JSON invalide, champ obligatoire manquant) → réponse `error` et fermeture de la connexion locale, ou code HTTP 400 côté distant.

---

## 7. Hors de portée pour la v0.1

- Protection contre un pair malveillant qui inonderait de requêtes (rate limiting) — à traiter si un cas d'abus réel apparaît.
- Découpage/reprise de transfert de gros fichiers.
- Découverte automatique de pairs inconnus (mDNS, DHT) — cf. constitution, choix assumé de ne pas utiliser libp2p pour l'instant.
- Chiffrement de bout en bout du contenu d'un objet (rappel : HTTPS protège le transport, pas le contenu vis-à-vis d'un relais qui verrait passer l'objet en clair).
