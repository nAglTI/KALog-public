package org.debs.kalog.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.sqldelight.db.SqlDriver
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

class JvmDatabaseDriverFactory(
    databaseName: String = "kalog.db",
    databaseDirectory: File = File(System.getProperty("user.home"), ".kalog"),
) : DatabaseDriverFactory {
    private val databaseFile = File(databaseDirectory, databaseName)

    override fun create(): SqlDriver {
        databaseFile.parentFile?.mkdirs()
        val url = "jdbc:sqlite:${databaseFile.absolutePath}"
        normalizeLegacyDesktopSchema(url)
        return JdbcSqliteDriver(
            url = url,
            schema = KalogDatabase.Schema,
        )
    }

    private fun normalizeLegacyDesktopSchema(url: String) {
        DriverManager.getConnection(url).use { connection ->
            val currentVersion = connection.readUserVersion()
            if (currentVersion != 0L || !connection.tableExists(CHAT_THREADS_TABLE)) {
                return
            }

            val normalizedVersion = if (
                connection.columnExists(CHAT_THREADS_TABLE, CHAT_TYPE_COLUMN) &&
                connection.columnExists(CHAT_MESSAGES_TABLE, MESSAGE_TYPE_COLUMN) &&
                connection.columnExists(CHAT_MESSAGES_TABLE, FROM_USER_ID_COLUMN) &&
                connection.columnExists(CHAT_MESSAGES_TABLE, TO_USER_ID_COLUMN)
            ) {
                KalogDatabase.Schema.version
            } else {
                LEGACY_SCHEMA_VERSION
            }

            connection.createStatement().use { statement ->
                statement.execute("PRAGMA user_version = $normalizedVersion")
            }
        }
    }

    private fun Connection.readUserVersion(): Long {
        createStatement().use { statement ->
            statement.executeQuery("PRAGMA user_version").use { resultSet ->
                if (resultSet.next()) {
                    return resultSet.getLong(1)
                }
            }
        }
        return 0L
    }

    private fun Connection.tableExists(tableName: String): Boolean {
        prepareStatement(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1",
        ).use { statement ->
            statement.setString(1, tableName)
            statement.executeQuery().use { resultSet ->
                return resultSet.next()
            }
        }
    }

    private fun Connection.columnExists(tableName: String, columnName: String): Boolean {
        createStatement().use { statement ->
            statement.executeQuery("PRAGMA table_info($tableName)").use { resultSet ->
                while (resultSet.next()) {
                    if (resultSet.getString("name") == columnName) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private companion object {
        private const val LEGACY_SCHEMA_VERSION = 1L
        private const val CHAT_THREADS_TABLE = "chat_threads"
        private const val CHAT_MESSAGES_TABLE = "chat_messages"
        private const val CHAT_TYPE_COLUMN = "chat_type"
        private const val MESSAGE_TYPE_COLUMN = "message_type"
        private const val FROM_USER_ID_COLUMN = "from_user_id"
        private const val TO_USER_ID_COLUMN = "to_user_id"
    }
}
