package org.debs.kalog.core.database

import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals

class JvmDatabaseDriverFactoryTest {
    @Test
    fun migratesLegacyDesktopDatabaseWithoutUserVersion() {
        val tempDirectory = Files.createTempDirectory("kalog-jvm-db")
        val databaseFile = tempDirectory.resolve("kalog.db").toFile()
        val url = "jdbc:sqlite:${databaseFile.absolutePath}"

        DriverManager.getConnection(url).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE TABLE chat_threads (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        subtitle TEXT NOT NULL,
                        avatar_initials TEXT NOT NULL,
                        avatar_accent TEXT NOT NULL,
                        unread_count INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                statement.execute(
                    """
                    CREATE TABLE chat_messages (
                        id TEXT NOT NULL PRIMARY KEY,
                        chat_id TEXT NOT NULL REFERENCES chat_threads(id) ON DELETE CASCADE,
                        sender TEXT,
                        body TEXT NOT NULL,
                        timestamp TEXT NOT NULL,
                        is_service INTEGER NOT NULL,
                        is_mine INTEGER,
                        delivery_status TEXT,
                        position INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        val driver = JvmDatabaseDriverFactory(
            databaseDirectory = tempDirectory.toFile(),
        ).create()

        try {
            DriverManager.getConnection(url).use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("PRAGMA user_version").use { resultSet ->
                        resultSet.next()
                        assertEquals(KalogDatabase.Schema.version, resultSet.getLong(1))
                    }
                }
            }
        } finally {
            driver.close()
            tempDirectory.toFile().deleteRecursively()
        }
    }
}
