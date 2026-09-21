package org.monagora.core.storage

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.monagora.core.objects.SignedObject
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException

/**
 * Implémentation [SignedObjectStore] sur SQLite, via le driver JDBC portable
 * `org.xerial:sqlite-jdbc`.
 *
 * Note de portabilité (à ne pas perdre de vue) : ce driver embarque des
 * bibliothèques natives desktop (Linux/Mac/Windows x86/arm64) et ne fonctionne
 * pas tel quel sur un appareil Android — ce n'est pas ce qu'utilisent les
 * applications Android en pratique. Il est utilisé ici parce que cet
 * environnement de développement n'a pas le SDK Android installé, et qu'il
 * permet de construire et tester ce module dès maintenant plutôt que d'attendre
 * un outillage Android complet (constitution-technique.md, principe 13 :
 * progressivité). Quand le module `app` Android sera mis en place, il faudra
 * une implémentation de [SignedObjectStore] basée sur `android.database.sqlite`
 * (ou Room) — le contrat de l'interface est conçu pour que ce soit un
 * remplacement mécanique de cette classe, sans toucher aux appelants.
 */
class SqliteSignedObjectStore(path: String) : SignedObjectStore, AutoCloseable {
    private val logger = LoggerFactory.getLogger(SqliteSignedObjectStore::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val connection: Connection = DriverManager.getConnection("jdbc:sqlite:$path")

    init {
        connection.createStatement().use { statement ->
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS signed_objects (
                    id TEXT PRIMARY KEY,
                    type TEXT NOT NULL,
                    version INTEGER NOT NULL,
                    author TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    payload TEXT NOT NULL,
                    signature TEXT NOT NULL
                )
                """.trimIndent(),
            )
            statement.execute("CREATE INDEX IF NOT EXISTS idx_signed_objects_created_at ON signed_objects(created_at)")
            statement.execute("CREATE INDEX IF NOT EXISTS idx_signed_objects_type ON signed_objects(type)")
            statement.execute("CREATE INDEX IF NOT EXISTS idx_signed_objects_author ON signed_objects(author)")
        }
    }

    override fun save(obj: SignedObject): Boolean {
        if (findById(obj.id) != null) {
            logger.debug("object_store_deduplicated id={} type={}", obj.id, obj.type)
            return false
        }
        return try {
            connection.prepareStatement(
                """
                INSERT INTO signed_objects (id, type, version, author, created_at, payload, signature)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, obj.id)
                statement.setString(2, obj.type)
                statement.setInt(3, obj.version)
                statement.setString(4, obj.author)
                statement.setString(5, obj.createdAt)
                statement.setString(6, obj.payload.toString())
                statement.setString(7, obj.signature)
                statement.executeUpdate()
            }
            logger.info("object_stored id={} type={}", obj.id, obj.type)
            true
        } catch (e: SQLException) {
            // Ne jamais avaler l'erreur : une écriture qui échoue doit remonter
            // (CLAUDE.md, section Journalisation), pas continuer comme si de rien n'était.
            logger.error("object_store_write_failed id={} type={} reason=\"{}\"", obj.id, obj.type, e.message)
            throw e
        }
    }

    override fun findById(id: String): SignedObject? = try {
        connection.prepareStatement("SELECT * FROM signed_objects WHERE id = ?").use { statement ->
            statement.setString(1, id)
            statement.executeQuery().use { rs -> if (rs.next()) rs.toSignedObject() else null }
        }
    } catch (e: SQLException) {
        logger.error("object_store_read_failed id={} reason=\"{}\"", id, e.message)
        throw e
    }

    override fun query(since: String?, types: List<String>?, author: String?, limit: Int): List<SignedObject> {
        if (types != null && types.isEmpty()) return emptyList()

        val conditions = mutableListOf<String>()
        val params = mutableListOf<String>()
        if (since != null) {
            conditions.add("created_at > ?")
            params.add(since)
        }
        if (types != null) {
            conditions.add("type IN (${types.joinToString(",") { "?" }})")
            params.addAll(types)
        }
        if (author != null) {
            conditions.add("author = ?")
            params.add(author)
        }
        val whereClause = if (conditions.isEmpty()) "" else "WHERE " + conditions.joinToString(" AND ")
        val sql = "SELECT * FROM signed_objects $whereClause ORDER BY created_at ASC LIMIT ?"

        return try {
            connection.prepareStatement(sql).use { statement ->
                params.forEachIndexed { index, value -> statement.setString(index + 1, value) }
                statement.setInt(params.size + 1, limit)
                statement.executeQuery().use { rs ->
                    val results = mutableListOf<SignedObject>()
                    while (rs.next()) results.add(rs.toSignedObject())
                    results
                }
            }
        } catch (e: SQLException) {
            logger.error(
                "object_store_query_failed since={} types={} author={} reason=\"{}\"",
                since,
                types,
                author,
                e.message,
            )
            throw e
        }
    }

    override fun count(): Long {
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT COUNT(*) FROM signed_objects").use { rs ->
                rs.next()
                return rs.getLong(1)
            }
        }
    }

    override fun close() = connection.close()

    private fun ResultSet.toSignedObject(): SignedObject = SignedObject(
        type = getString("type"),
        version = getInt("version"),
        author = getString("author"),
        createdAt = getString("created_at"),
        payload = json.parseToJsonElement(getString("payload")).jsonObject,
        id = getString("id"),
        signature = getString("signature"),
    )
}
