package org.debs.kalog.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

class IosDatabaseDriverFactory : DatabaseDriverFactory {
    override fun create(): SqlDriver {
        return NativeSqliteDriver(
            schema = KalogDatabase.Schema,
            name = "kalog.db",
        )
    }
}
