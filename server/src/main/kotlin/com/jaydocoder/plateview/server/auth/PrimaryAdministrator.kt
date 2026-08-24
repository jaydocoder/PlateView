package com.jaydocoder.plateview.server.auth

import java.sql.Connection

internal fun Connection.isPrimaryAdministrator(actorId: Long): Boolean = prepareStatement(
    "SELECT 1 FROM users WHERE id = ? AND username = 'admin' AND role = 'ADMIN' AND status = 'ACTIVE'",
).use { statement ->
    statement.setLong(1, actorId)
    statement.executeQuery().use { it.next() }
}
