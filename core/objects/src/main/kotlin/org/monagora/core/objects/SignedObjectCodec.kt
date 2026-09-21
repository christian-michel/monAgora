package org.monagora.core.objects

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Désérialisation d'un objet reçu en JSON brut (typiquement un élément de
 * `sync_response.objects`, docs/protocole-synchronisation.md, section 2).
 *
 * Distincte de [SignedObjects.verify] à dessein : ici on valide la *structure*
 * (JSON syntaxiquement correct, tous les champs obligatoires présents et du bon
 * type) ; [SignedObjects.verify] valide ensuite le *contenu* (id, signature).
 * Le protocole de synchronisation traite d'ailleurs ces deux échecs séparément
 * (section 6 : JSON malformé → `error` + fermeture de connexion ; objet
 * structurellement valide mais id/signature invalides → rejet silencieux).
 *
 * `ignoreUnknownKeys` est activé délibérément : un champ ajouté par une version
 * plus récente de l'application ne doit pas faire rejeter l'objet par un
 * appareil qui n'a pas encore été mis à jour (constitution-technique.md,
 * principe 8 — interopérabilité).
 */
object SignedObjectCodec {
    private val logger = LoggerFactory.getLogger(SignedObjectCodec::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * @return l'objet décodé, ou `null` si le JSON est malformé ou qu'un champ
     *   obligatoire manque — jamais d'exception, la raison est loggée en WARN.
     */
    fun parse(rawJson: String): SignedObject? = try {
        json.decodeFromString(SignedObject.serializer(), rawJson)
    } catch (e: SerializationException) {
        logger.warn("object_rejected reason=\"JSON malformé ou champ obligatoire manquant : {}\"", e.message)
        null
    } catch (e: IllegalArgumentException) {
        logger.warn("object_rejected reason=\"JSON invalide : {}\"", e.message)
        null
    }
}
