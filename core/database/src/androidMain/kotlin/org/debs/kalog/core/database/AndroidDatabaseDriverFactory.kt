package org.debs.kalog.core.database

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

class AndroidDatabaseDriverFactory(
    private val context: Context,
) : DatabaseDriverFactory {
    override fun create() = AndroidSqliteDriver(
        schema = KalogDatabase.Schema,
        context = context,
        name = "kalog.db",
    )
}
