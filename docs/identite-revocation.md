# Spécification — Révocation d'appareil et d'identité

*Version 0.1 — complète la constitution technique, section 5*

---

## 1. Principe

Pouvoir bloquer un appareil perdu ou volé, ou une identité compromise, **sans autorité centrale qui décide ou stocke qui est révoqué**. Comme pour la synchronisation et le format des objets, la révocation est elle-même un objet signé, qui circule par le protocole déjà spécifié (`docs/protocole-synchronisation.md`) et que chaque application vérifie localement.

**À ne pas confondre avec l'objet `revocation` de `docs/format-objets-signes.md` (section 6)** : celui-là annule un *contenu* (un vote, un signalement) et est signé par le même auteur que l'objet visé. Les objets définis ici annulent une **clé** (un appareil ou une identité entière) — un problème différent, avec des règles de confiance différentes, d'où des types dédiés.

---

## 2. Les trois clés d'une identité

| Clé | Rôle | Où elle vit |
|---|---|---|
| **Clé racine** | Identifie la personne ; autorise les appareils et identités dérivées ; signe les révocations d'appareil | Sur un appareil de confiance, à l'usage courant |
| **Clé d'appareil** | Signe les objets créés depuis cet appareil (votes, signalements, transactions…) | Sur l'appareil concerné uniquement |
| **Clé de révocation** | Seul pouvoir : révoquer l'identité entière, y compris si la clé racine est compromise | Gardée à part, hors-ligne, générée une fois à la création de l'identité |

L'identité d'une personne est représentée par sa **clé racine publique** (`root_pubkey`) — c'est elle qui sert de repère stable pour tout le reste, exactement comme l'`author` sert de repère pour un objet ordinaire.

**Cas particulier — mode invité** : une clé jetable générée pour une session invité (cf. constitution, section 8) n'a ni `identity_declaration` ni clé de révocation associée. Elle n'entre jamais dans ce graphe et n'a donc rien à révoquer : sa fin de session (destruction locale de la clé privée) joue exactement ce rôle.

---

## 3. Objets introduits

### `identity_declaration` — publié une fois, à la création de l'identité

Déclare quelle clé de révocation fait autorité pour cette identité. Signé par la clé racine elle-même.

```json
{
  "type": "identity_declaration",
  "version": 1,
  "author": "b64u:<root_pubkey>",
  "created_at": "2026-09-21T09:00:00Z",
  "payload": {
    "revocation_pubkey": "b64u:<revocation_pubkey>"
  },
  "id": "...",
  "signature": "..."
}
```

### `device_authorization` — publié à chaque nouvel appareil

Signé par la clé racine.

```json
{
  "type": "device_authorization",
  "version": 1,
  "author": "b64u:<root_pubkey>",
  "created_at": "...",
  "payload": {
    "device_pubkey": "b64u:<device_pubkey>"
  },
  "id": "...",
  "signature": "..."
}
```

### `device_revocation` — un appareil perdu ou volé, l'identité reste valide

Signé par la **clé racine** (cas normal : on a encore accès à son identité, par exemple depuis un autre appareil déjà autorisé).

```json
{
  "type": "device_revocation",
  "version": 1,
  "author": "b64u:<root_pubkey>",
  "created_at": "...",
  "payload": {
    "device_pubkey": "b64u:<device_pubkey_à_révoquer>",
    "reason": "lost"
  },
  "id": "...",
  "signature": "..."
}
```

`reason` : `lost` / `stolen` / `replaced` / `other` — informatif, n'affecte pas la validation.

### `identity_revocation` — la clé racine elle-même est compromise, ou sortie volontaire

Signé par la **clé de révocation**, jamais par la clé racine — c'est tout l'intérêt d'avoir une clé séparée : elle reste utilisable même si la clé racine ne l'est plus.

```json
{
  "type": "identity_revocation",
  "version": 1,
  "author": "b64u:<revocation_pubkey>",
  "created_at": "...",
  "payload": {
    "root_pubkey": "b64u:<root_pubkey_de_l'identité_révoquée>",
    "reason": "compromised"
  },
  "id": "...",
  "signature": "..."
}
```

---

## 4. Règles de validation

Avant de faire confiance à un objet quelconque signé par une clé d'appareil `D` au nom d'une identité `R` (clé racine) :

1. Il doit exister un `device_authorization` valide, `author = R`, `payload.device_pubkey = D`.
2. Il ne doit exister **aucun** `device_revocation`, `author = R`, `payload.device_pubkey = D` — s'il en existe un, tout objet signé par `D` est rejeté, sans tenir compte de l'horodatage (règle volontairement simple pour la v0.1, cf. section 6).
3. Il ne doit exister **aucun** `identity_revocation` valide pour `R` — c'est-à-dire signé par la clé déclarée dans le `identity_declaration` de `R`. Si l'identité entière est révoquée, tous ses appareils le sont avec elle, y compris ceux jamais explicitement listés dans un `device_revocation`.

Un `identity_revocation` n'est accepté comme valide que si sa signature correspond à `revocation_pubkey` tel que déclaré dans le `identity_declaration` de ce `root_pubkey` — sans quoi n'importe qui pourrait prétendre révoquer l'identité de n'importe qui d'autre.

---

## 5. Propagation

Aucun mécanisme nouveau : ces objets circulent exactement comme n'importe quel autre objet signé, via le protocole de synchronisation déjà spécifié. Un appareil qui reçoit un `device_revocation` ou `identity_revocation` le stocke et le retransmet comme les autres — il n'y a pas de distinction de traitement réseau entre un objet de révocation et un signalement de poubelle.

---

## 6. Limite assumée

Sans autorité centrale, une révocation met le temps que met sa propagation à devenir effective partout — un appareil qui n'a pas encore reçu le `device_revocation` continuera, un temps, à faire confiance à la clé révoquée. C'est la même limite que sur la Ğ1 (la révocation y dépend aussi de la finalité des blocs et de la propagation réseau), pas une régression introduite ici. Aucune solution ne supprime ce délai sans réintroduire un serveur central faisant autorité — ce qui irait contre le principe 6 de la constitution. On l'assume explicitement plutôt que de le cacher.

---

## 7. Garde de la clé de révocation

C'est le point le plus sensible pratiquement : si elle est perdue *en même temps* que la clé racine, plus aucune révocation n'est possible (point encore ouvert, noté dans la constitution, section 5).

**Pour la v0.1** : auto-conservation simple.
- Générée une fois, à la création de l'identité, sur l'appareil.
- Exportée immédiatement (QR code à imprimer, fichier à mettre en lieu sûr) — l'application guide l'utilisateur dans ce geste au moment de la création de l'identité, comme le fait Ğecko avec son document de révocation.
- Recommandé (pas forcé techniquement en v0.1) : supprimer la clé privée de révocation de l'appareil une fois l'export confirmé, pour qu'un vol du téléphone seul ne l'expose jamais.

**Hors de portée pour la v0.1** : partage de la clé de révocation entre plusieurs proches (« gardiens », via un partage de secret façon Shamir), qui apporterait de la résilience sans dépendre d'un seul support physique — évolution possible plus tard, une fois le besoin confirmé à l'usage (principe 10 de la constitution).

---

## 8. Cas d'usage

- **Téléphone volé, identité intacte** → depuis un autre appareil déjà autorisé (ou en recréant un accès à la clé racine), publier un `device_revocation` visant l'appareil volé. Les autres appareils/applications rejettent désormais tout ce qui en provient.
- **Compromission de la clé racine elle-même** (ex. l'appareil qui la portait est compromis au niveau système, pas juste volé) → récupérer la clé de révocation gardée à part, publier un `identity_revocation`. Toute l'identité — tous ses appareils, présents et futurs — devient invalide pour le reste du réseau. La personne doit alors créer une nouvelle identité (nouveau `root_pubkey`, nouvelle `identity_declaration`, nouvelle clé de révocation) — il n'y a volontairement pas de mécanisme de « migration » automatique d'une identité révoquée vers une nouvelle, pour ne pas donner à un attaquant en possession de la clé compromise un moyen de suivre la transition.

---

## 9. Identités éphémères (mode invité)

Une identité invité (clé jetable, générée localement pour une session, détruite à la fermeture) **n'entre jamais dans ce modèle** : pas de `identity_declaration`, pas de `device_authorization`, et donc rien à révoquer — la fin de session équivaut déjà à une révocation immédiate et totale, sans qu'aucun objet ne circule pour l'annoncer.

Une application distingue une identité invité d'une identité durable par une simple absence : aucun `device_authorization` connu ne correspond à la clé qui a signé l'objet. Elle reste libre d'afficher ce contenu différemment (ex. « contribution non vérifiée ») sans que cela relève du mécanisme de révocation décrit dans ce document. Portée exacte de ce qu'un invité peut faire, application par application : `docs/constitution-technique.md`, section 8.
