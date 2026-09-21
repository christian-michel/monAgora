package org.monagora.core.sync

import org.monagora.core.storage.SignedObjectStore
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

private const val PROTOCOL_VERSION = 1

/**
 * Fait vivre une session de synchronisation locale de bout en bout, sur un
 * transport déjà connecté — un socket Bluetooth RFCOMM ou Wi-Fi Direct, peu
 * importe ici : cette classe ne connaît que des [InputStream]/[OutputStream].
 * L'établissement de la connexion elle-même (étape 1 de
 * docs/protocole-synchronisation.md, section 3) est hors de sa responsabilité —
 * c'est justement ce découpage qui rend cette classe testable en JVM pur, sans
 * matériel Bluetooth, via de simples flux en mémoire (voir SyncSessionTest).
 *
 * Implémente les étapes 2 à 6 de cette même section : `hello` dans les deux
 * sens, `sync_request`/`sync_response` symétriques et indépendants (chaque
 * camp peut demander pendant que l'autre répond — géré ici par une seule
 * boucle de lecture qui réagit au type de chaque message reçu, plutôt que par
 * un ordre de tour figé), fermeture une fois les deux `has_more` à `false`.
 *
 * Le fichier (`file_request`/`file_response`) n'est pas traité ici : aucune
 * application du projet n'échange encore de fichier réel (pas de capture
 * photo dans `app/gps-citoyen` à ce jour) — pas anticipé avant qu'un besoin
 * concret n'existe (constitution-technique.md, principe 10).
 */
class SyncSession(
    private val input: InputStream,
    private val output: OutputStream,
    private val localDevicePubkey: String,
    private val supportedTypes: List<String>,
    private val store: SignedObjectStore,
    private val cursors: SyncCursorStore,
    private val transportName: String,
    private val requestLimit: Int = 200,
) {
    private val logger = LoggerFactory.getLogger(SyncSession::class.java)
    private val writer = OutputStreamWriter(output, StandardCharsets.UTF_8)

    /**
     * Bloque jusqu'à ce que la session se termine (les deux sens synchronisés,
     * connexion fermée par le pair, ou message malformé/version incompatible).
     * Ne lance jamais d'exception sur un problème *protocolaire* (c'est loggé
     * et la session se termine proprement) — seules les erreurs d'E/S sur le
     * flux lui-même remontent, comme toute écriture qui échoue doit remonter
     * (CLAUDE.md, section Journalisation).
     */
    fun run() {
        logger.info("sync_session_opened transport={}", transportName)
        try {
            send(Hello(protocolVersion = PROTOCOL_VERSION, devicePubkey = localDevicePubkey, supportedTypes = supportedTypes))

            val reader = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8))
            var peerDevicePubkey: String? = null
            var sentInitialRequest = false
            var ourPullDone = false
            var theirPullDone = false
            var lastNextSince: String? = null

            while (!(ourPullDone && theirPullDone)) {
                val line = reader.readLine() ?: break // pair a fermé la connexion

                when (val message = SyncMessageCodec.decode(line)) {
                    null -> {
                        send(SyncError(code = "malformed_message", detail = "ligne JSON invalide ou champ obligatoire manquant"))
                        break
                    }

                    is Hello -> {
                        if (message.protocolVersion != PROTOCOL_VERSION) {
                            send(SyncError(code = "unsupported_version", detail = "version $PROTOCOL_VERSION attendue"))
                            break
                        }
                        peerDevicePubkey = message.devicePubkey
                        logger.info("sync_hello_received transport={} peer={}", transportName, peerDevicePubkey)
                        if (!sentInitialRequest) {
                            val since = cursors.getCursor(peerDevicePubkey)
                            send(SyncRequest(since = since, types = null, limit = requestLimit))
                            logger.info(
                                "sync_request_sent transport={} peer={} since={} limit={}",
                                transportName,
                                peerDevicePubkey,
                                since,
                                requestLimit,
                            )
                            sentInitialRequest = true
                        }
                    }

                    is SyncRequest -> {
                        val effectiveLimit = minOf(message.limit, requestLimit)
                        // +1 pour détecter s'il y en a davantage, sans dépasser effectiveLimit dans la réponse.
                        val batch = store.query(since = message.since, types = message.types, limit = effectiveLimit + 1)
                        val hasMore = batch.size > effectiveLimit
                        val objects = if (hasMore) batch.subList(0, effectiveLimit) else batch
                        send(SyncResponse(objects = objects, hasMore = hasMore, nextSince = objects.lastOrNull()?.createdAt))
                        logger.info(
                            "sync_response_sent transport={} peer={} count={} has_more={}",
                            transportName,
                            peerDevicePubkey,
                            objects.size,
                            hasMore,
                        )
                        if (!hasMore) theirPullDone = true
                    }

                    is SyncResponse -> {
                        val peer = peerDevicePubkey
                        if (peer == null) {
                            logger.warn("sync_response_before_hello transport={}", transportName)
                        } else {
                            val result = SyncIngest.ingest(message, store)
                            logger.info(
                                "sync_response_received transport={} peer={} accepted={} rejected={} deduplicated={} has_more={}",
                                transportName,
                                peer,
                                result.accepted,
                                result.rejected,
                                result.deduplicated,
                                message.hasMore,
                            )
                            if (message.hasMore && message.nextSince != null) {
                                send(SyncRequest(since = message.nextSince, types = null, limit = requestLimit))
                            } else {
                                lastNextSince = message.nextSince
                                ourPullDone = true
                            }
                        }
                    }

                    is SyncError -> {
                        logger.warn("sync_error_received transport={} code={} detail={}", transportName, message.code, message.detail)
                        break
                    }

                    is FileRequest, is FileResponse -> {
                        logger.debug("sync_file_message_ignored transport={}", transportName)
                    }
                }
            }

            val peer = peerDevicePubkey
            val cursorValue = lastNextSince
            if (peer != null && cursorValue != null) {
                cursors.setCursor(peer, SyncCursor.withSafetyMargin(cursorValue))
            }
        } catch (e: IOException) {
            logger.error("sync_session_io_error transport={} reason=\"{}\"", transportName, e.message)
            throw e
        } finally {
            logger.info("sync_session_closed transport={}", transportName)
        }
    }

    private fun send(message: SyncMessage) {
        writer.write(SyncMessageCodec.encode(message))
        writer.write("\n")
        writer.flush()
    }
}
