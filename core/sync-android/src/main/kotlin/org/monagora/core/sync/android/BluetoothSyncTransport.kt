package org.monagora.core.sync.android

import java.util.UUID

/**
 * Constantes du service RFCOMM utilisé pour la synchronisation locale
 * (docs/protocole-synchronisation.md, section 3).
 */
object BluetoothSyncTransport {
    /**
     * UUID de service RFCOMM propre à monAgora — généré une fois pour toutes
     * ([java.util.UUID.randomUUID]) et figé ici : deux appareils qui se sont
     * déjà appairés doivent continuer à retrouver le même service à chaque
     * connexion. Ne jamais le régénérer.
     */
    val SERVICE_UUID: UUID = UUID.fromString("6bd50977-0fe6-4370-9c1c-9f8b7c1ce005")

    /** Nom de service annoncé via SDP — informatif, affiché par certains outils système. */
    const val SERVICE_NAME = "monagora-sync"
}
