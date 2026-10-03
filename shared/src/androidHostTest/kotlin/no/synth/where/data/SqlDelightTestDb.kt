package no.synth.where.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import no.synth.where.data.db.WhereDatabase

/** Fresh in-memory SQLDelight database (schema created, foreign keys on) for host tests. */
fun freshSqlDelightDb(): WhereDatabase {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    WhereDatabase.Schema.create(driver)
    driver.execute(null, "PRAGMA foreign_keys=ON", 0)
    return WhereDatabase(driver)
}
