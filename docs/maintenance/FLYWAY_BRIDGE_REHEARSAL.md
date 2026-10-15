# Isolated Flyway metadata bridge rehearsal

This test-only candidate uses Flyway 4.2.0's own metadata-upgrade code on a disposable old-H2 working copy, then exports it and imports into a fresh H2 2.5.252 LEGACY target. Flyway 13.10.0 must migrate/validate twice without repair, baseline or rewriting V1. Original sealed files, original export, history fields and every retained metadata field are compared; the removed version_rank column is the expected supported format change. The original old-driver recovery path remains tested.

Official source basis:
- https://github.com/flyway/flyway/blob/flyway-4.2.0/flyway-core/src/main/java/org/flywaydb/core/internal/metadatatable/MetaDataTableImpl.java
- https://github.com/flyway/flyway/blob/flyway-4.2.0/flyway-core/src/main/resources/org/flywaydb/core/internal/dbsupport/h2/upgradeMetaDataTable.sql
- https://github.com/flyway/flyway/releases/tag/flyway-13.10.0
- https://h2database.github.io/html/migration-to-v2.html

Flyway 4.2.0 is an offline test bridge only, never a runtime dependency. Modern Flyway is test-scope only. This rehearsal does not change the packaged application's H2/Flyway drivers, produce a production migration CLI, prove full application compatibility, cover post-switch delta recovery or validate real MySQL. Exact-head CI must pass before merge; local DNS/dependency access is unavailable. Results and blockers must be recorded without interpreting a failed experiment as migration support.
