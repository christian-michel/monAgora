# Spécification — Format des objets signés

*Version 0.1 — complète la constitution technique, section 4*

---

## 1. Principe

Un objet signé est la seule unité d'information que le système échange entre appareils. Il est **créé une fois, jamais modifié** (append-only). Pour corriger ou annuler un objet, on publie un nouvel objet qui y fait référence (voir section 6).

Deux propriétés doivent être vérifiables par n'importe quel appareil, indépendamment de qui le lui a transmis :
- **Intégrité** : le contenu n'a pas été altéré (`id`).
- **Authenticité** : il a bien été créé par le détenteur de la clé privée correspondante (`signature`).

---

## 2. Structure

```json
{
  "type": "waste_report",
  "version": 1,
  "author": "b64u:8fJ3k...==",
  "created_at": "2026-09-11T10:15:00Z",
  "payload": { },
  "id": "3f7a9c1e...(64 caractères hex)",
  "signature": "b64:MEUCIQ...=="
}
```

| Champ | Type | Description |
|---|---|---|
| `type` | string | Identifiant du type d'objet, snake_case (`waste_report`, `vote`, `transaction`, `revocation`…) |
| `version` | int | Version du schéma du `payload` pour ce type (permet de faire évoluer un type sans casser les anciens objets) |
| `author` | string | Clé publique Ed25519 de l'auteur, encodée en base64url, préfixée `b64u:` |
| `created_at` | string | Horodatage UTC au format ISO 8601 (`AAAA-MM-JJTHH:MM:SSZ`) |
| `payload` | object | Contenu spécifique au `type` (voir section 5) |
| `id` | string | Empreinte SHA-256 (hex, 64 caractères) du contenu canonique de l'objet — calculée par l'émetteur, recalculée par le vérificateur |
| `signature` | string | Signature Ed25519 du même contenu canonique, encodée en base64, préfixée `b64:` |

---

## 3. Calcul de l'id et de la signature

Pour éviter toute ambiguïté (un même objet ne doit pas pouvoir donner deux signatures différentes selon l'ordre des champs), on utilise une **sérialisation canonique** : JSON avec clés triées par ordre alphabétique, sans espaces, nombres et chaînes normalisés (on s'appuie sur la RFC 8785 — *JSON Canonicalization Scheme* — plutôt que d'inventer notre propre règle).

**Étapes de création (côté émetteur) :**

1. Construire l'objet **sans** les champs `id` et `signature` :
   `{type, version, author, created_at, payload}`
2. Sérialiser ce sous-ensemble en JSON canonique → `bytes`
3. `id = hex(SHA256(bytes))`
4. `signature = base64(Ed25519_sign(clé_privée, bytes))`
5. Assembler l'objet final avec tous les champs.

**Étapes de vérification (côté receveur) :**

1. Extraire `{type, version, author, created_at, payload}` de l'objet reçu.
2. Sérialiser en JSON canonique → `bytes`
3. Vérifier que `hex(SHA256(bytes)) == id` reçu (intégrité).
4. Vérifier que `Ed25519_verify(author, bytes, signature)` est valide (authenticité).
5. Rejeter l'objet si l'une des deux vérifications échoue — ne jamais l'afficher ni le stocker comme valide.

`id` et `signature` portent sur exactement le même contenu : pas de dépendance circulaire, pas d'ambiguïté sur ce qui est signé.

---

## 4. Choix cryptographiques

- **Signature** : Ed25519 — rapide, clés et signatures compactes (32 et 64 octets), signature déterministe, standard bien supporté (Tink, BouncyCastle en Kotlin/JVM).
- **Empreinte** : SHA-256.
- **Encodage** : base64 standard pour la signature et la clé publique (préfixes `b64:`/`b64u:` pour éviter toute confusion de format en relisant un objet brut).

Pas de chiffrement dans cette spécification : un objet signé est, par défaut, un objet destiné à être **partagé et vérifié**. Une donnée purement privée (ex : une note personnelle jamais partagée) n'a pas besoin de passer par ce format — elle reste dans le stockage local de l'application, chiffrée au repos par les mécanismes standard d'Android. Le chiffrement *sélectif* d'un payload partagé à un destinataire précis (ex : une mesure de santé partagée avec un médecin) sera traité séparément, plus tard, une fois un premier besoin réel identifié.

---

## 5. Payloads par type (registre ouvert)

Chaque `type` définit son propre schéma de `payload`, versionné indépendamment. Exemple pour la première application prévue :

### `waste_report` (v1)

```json
{
  "type": "waste_report",
  "version": 1,
  "author": "b64u:...",
  "created_at": "2026-09-11T10:15:00Z",
  "payload": {
    "lat": 48.8566,
    "lon": 2.3522,
    "category": "overflowing_bin",
    "note": "Poubelle débordante depuis plusieurs jours",
    "photo_hash": "sha256:9f86d0..."
  },
  "id": "...",
  "signature": "..."
}
```

Remarque : la photo elle-même **n'est pas embarquée** dans l'objet (trop volumineux pour circuler efficacement en P2P). L'objet référence son empreinte (`photo_hash`) ; le fichier est transféré séparément, et le receveur vérifie que `SHA256(fichier_reçu) == photo_hash` avant de l'associer à l'objet.

D'autres types (`vote`, `transaction`) seront spécifiés au fur et à mesure qu'on les développera, en suivant ce même modèle — pas besoin de tous les définir maintenant.

---

## 6. Révocation et mise à jour

Comme les objets sont immuables, on ne « modifie » jamais un objet existant. On publie un nouvel objet qui le référence :

```json
{
  "type": "revocation",
  "version": 1,
  "author": "b64u:...",
  "created_at": "...",
  "payload": {
    "target_id": "3f7a9c1e...",
    "reason": "resolved"
  },
  "id": "...",
  "signature": "..."
}
```

Règle de validation applicative : une révocation n'est prise en compte par une application que si son `author` correspond à celui de l'objet ciblé (ou à une identité explicitement autorisée à agir en son nom — cf. délégation, à spécifier plus tard). C'est à chaque application d'interpréter la présence d'une révocation ; le système ne fait que transporter et vérifier les objets, il ne décide pas de leur sens.

---

## 7. Déduplication et synchronisation

- L'`id` sert de clé de déduplication : un appareil qui reçoit un objet dont l'`id` est déjà connu l'ignore silencieusement.
- Le protocole de synchronisation (constitution, section 6) échange les objets par lot : *« donne-moi les objets dont tu disposes, créés ou reçus depuis le timestamp X »*.
- Un objet invalide (signature ou id incorrects) n'est jamais stocké ni retransmis.

---

## 8. Ce qui reste volontairement hors de cette version

- Chiffrement sélectif d'un payload pour un destinataire précis.
- Délégation formelle (une identité autorisant une autre à signer en son nom).
- Schéma de révocation de clé compromise à grande échelle.

Ces points seront traités quand un cas d'usage réel les rendra nécessaires, pour ne pas complexifier la v0.1 par anticipation — conformément au principe 10 de la constitution technique.
