# ZooKeeper client and template dependency upgrade

Candidate D01 maintenance step:
- ZooKeeper3.5.7 ->3.9.6, including its published Netty4.1.137.Final dependency set. Embedded-server-only metrics4.2.37 and Snappy1.1.10.12 are test-scope, outside runtime packaging.
- Legacy freemarker:freemarker2.3.8 -> org.freemarker:freemarker2.3.35.
- Explicit Commons IO2.22.0 prevents an older transitive version from winning resolution.

FreeMarker now uses one private configured/cached engine, UTF8 loading, explicit VERSION_2_3_35 behavior, rethrow instead of debug-template output, no automatic template exception logging, disabled API builtin and an ALLOWS_NOTHING class resolver. Existing templates keep their explicit ?html escaping; automatic escaping remains disabled to avoid double escaping. No user-provided FTL source is evaluated.

Required checks: full three-platform isolated JAR/LDAP/Digest/root/pagination/H2/migration/security suite, real Chrome and non-root container, the existing real MySQL/TLS job, and an additional real legacy ZooKeeper3.5.7 container connected by the3.9.6 client. The legacy check covers ensemble fallback, Digest/ACL denial, exact UTF8, versioned writes, reconnect and cleanup of one generated subtree. Old server is loopback/CI-owned only; no production topology is accessed. New embedded-server tests are not evidence for arbitrary old cluster versions/TLS/SASL topologies.

Official version/API basis:
- https://zookeeper.apache.org/releases/
- https://zookeeper.apache.org/doc/current/releasenotes.html
- https://github.com/apache/zookeeper/blob/release-3.9.6/pom.xml
- https://freemarker.apache.org/freemarkerdownload.html
- https://freemarker.apache.org/docs/versions_2_3_35.html
- https://commons.apache.org/proper/commons-io/download_io.cgi
- https://github.com/dropwizard/metrics/releases/tag/v4.2.37
- https://github.com/xerial/snappy-java/releases/tag/v1.1.10.12

Not a clean vulnerability-scan, license-clearance, H2/Flyway upgrade or final release claim. Exact-head CI must pass and the observed legacy image digest must be pinned before merge.

First legacy check reached the3.5.7 server but the test used List.of for an ACL; ZooKeeper3.9 validateACL calls contains(null), which that JDK immutable list rejects. Use a normal mutable ACL list, matching production ACL construction, and pin observed legacy image digest sha256:883b014b6535574503bda8fc6a7430ba009c0273242f86d401095689652e5731. A successful new exact-head rerun is still required.
