package io.muun.apollo.domain.errors

class DbMigrationError(targetVersion: Int, sql: String, cause: Throwable): MuunError(cause) {

    init {
        metadata["targetVersion"] = targetVersion
        metadata["sql"] = sql
    }

}