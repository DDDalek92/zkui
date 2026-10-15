# Database readiness and audit failure policy

Local H2 file-format preflight now runs before every DAO connection and before Flyway datasource construction/dev clean. A sibling incompatible format prevents startup instead of silently creating a parallel empty database; see [H2_FILE_FORMAT_GUARD.md](H2_FILE_FORMAT_GUARD.md). This is not the full H2/Flyway runtime upgrade.

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

Validated and merged in Fork PR #23, exact head `748ad0c86bf3b2721ffdd32414afe90400aea1ab`.
[CI run 38736989054](https://github.com/DDDalek92/zkui/actions/runs/38736989054): all four required jobs passed; Linux, macOS and Windows each ran 16 unit + 89 integration = 105 tests, zero failures/errors/skips. Linux Chrome passed 3/3 with no skips; the container build/non-root/login/environment/prefixed-assets smoke passed.
An independent detached checkout of this exact head also passed local OpenJDK17 `clean verify` with 105 tests. The initial host DNS limitation is historical, not the final verification state. No local browser/container success is claimed.

Source review confirmed all production `insertHistory` callers are covered by Home/Import's sanitized failure handling; redirects follow successful history writes, and failure clears success flashes. Startup initialization occurs before constructing/binding Jetty. V1 blob remains `fadfd7e9462b348131965f855949529ac6ceaa39`; no driver/runtime dependency update or automatic metadata repair was introduced.

Separate PR #24 was closed unmerged after the concurrent overlap was detected; its branch was retained and its local 104 tests do not stand in for this head's 105 tests.
This closes part of D01-C's failure-policy gap; cross-version application/Flyway migration, the remaining failure matrix and real MySQL remain pending.
