# Database readiness and audit failure policy

The HTTP server must not start if Flyway initialization fails or the exact histories columns cannot be queried.
Migration errors stop startup with a generic message; driver exceptions are not chained into the uncaught exception because they can contain JDBC URLs, credentials or SQL data.
This does not upgrade H2/Flyway, repair migration metadata or change V1.

Runtime history writes now throw a dedicated, sanitized exception. Home and import return HTTP 503 and clear any success flash message.
ZooKeeper and JDBC do not share a transaction: the node mutation/import may already be applied when the audit write fails.
The response explicitly asks the operator to inspect current state before retrying; it does not claim rollback.
This is an explicit degraded response, not an atomic audit guarantee or a persistent write-disable latch.

Regression coverage:
- Applied V1 metadata with a missing histories table prevents readiness.
- A changed V1 checksum prevents readiness without repairing the checksum or losing synthetic history.
- An invalid database causes the assembled JAR to exit nonzero before opening HTTP, without printing a synthetic credential.
- Home and import mutations with a dropped history table return 503, preserve the actual ZK result, and show no success flash.
- JDBC connections are released after a failed write; recovery on a healthy separate database still works.

Validation pending: local dependency resolution is unavailable on this host (GitHub DNS fails).
The exact PR head must pass all required remote CI jobs and source review before merge.
This closes part of D01-C's failure-policy gap; cross-version application/Flyway migration, the remaining failure matrix and real MySQL remain pending.
