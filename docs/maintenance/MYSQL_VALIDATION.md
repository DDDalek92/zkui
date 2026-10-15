# MySQL dialect and Connector/J maintenance

Candidate D01-D work. Runtime Connector/J moves from mysql:mysql-connector-java8.0.16 to com.mysql:mysql-connector-j26.7.0. X DevAPI is unused; exclude its protobuf dependency. The application support floor for MySQL becomes8.4. MySQL5.6 exists only as an isolated legacy-data generator; existing5.x services must be migrated separately rather than passed to the new application.

Original db/migration/V1__Core.sql is unchanged. Fresh MySQL uses the parallel db/mysql-migration location with NOT NULL AUTO_INCREMENT PRIMARY KEY. On an existing version table, choose only the matching known original/dialect V1 checksum and require a successful original script record. Flyway validation remains enabled. Unknown, unsuccessful or tampered metadata fails startup; no repair, baseline or deletion of version records is performed. Unknown nonempty schemas still fail. H2 keeps its original location/driver.

CI generates an original-format database with Flyway2.2.1/original V1/Connector8.0.16 on test-owned MySQL5.6.51, including null/empty/Unicode/whitespace/date/large-ID rows. It dumps that closed-to-writes fixture and imports into a fresh owned MySQL8.4 service. Modern DAO startup twice must preserve every original history and metadata field, continue generated IDs, create/reopen the fresh dialect and reject wrong credentials/tampered checksums/unknown schemas without repair. The actual packaged app must log in, render restored MySQL history for ADMIN and refuse USER.

Modern connections use sslMode=VERIFY_IDENTITY, a two-day CI CA/server certificate with localhost/IP SAN, an owned truststore and UTC. The test checks a nonempty TLS cipher. The old offline generator uses loopback-only plaintext and is never a production example. No caller URLs, user databases or production services are accepted by the smoke helper. Container digests are recorded for pinning.

Validation pending exact-head CI including the additional MySQL job. This is not an in-place server-upgrade procedure, universal historical-database proof, H2/Flyway runtime upgrade, complete recovery/delta-preservation matrix or final vulnerability/launch clearance.

Official version/coordinates/compatibility basis:
- https://dev.mysql.com/doc/relnotes/connector-j/en/news-26-7-0.html
- https://dev.mysql.com/doc/connector-j/en/connector-j-versions.html
- https://dev.mysql.com/doc/connector-j/en/connector-j-installing-maven.html
