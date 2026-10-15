# Build and run the local image

Run from the repository root:

```sh
docker build -f docker/Dockerfile -t zkui:local .
# Equivalent: make build
export ZK_SERVER='zk1.example:2181,zk2.example:2181/application'
export USER_SET='{"users":[{"username":"operator","password":"replace-with-your-own-password","role":"ADMIN"}]}'
sh run.sh
```

`zkui:local` is built locally. No public `zkui:latest` image is assumed or published. `make` only builds. An operator may explicitly tag/push to a registry they control with `make publish PUBLISH_IMAGE=registry/namespace/zkui:tag`; maintenance CI never publishes images.

The multi-stage Dockerfile uses a Temurin JDK8 builder and JRE8 runtime, builds the repository's Maven Wrapper, and runs as numeric user/group `10001:10001`. Base tags are mutable and are not a reproducible digest lock; the next release validation must lock reviewed digests. The image still contains the Java8-era application dependency limitations in `docs/maintenance/DEPENDENCIES.md`.

The default container H2 URL is `jdbc:h2:/var/app/data/history`; run.sh uses a named `zkui-data` volume for this directory. Preserve that volume when replacing containers. Do not mount an old production H2 file into a newer database engine without the tested migration/rollback procedure. Other deployment settings may be supplied by mounting your config at `/var/app/config.cfg:ro`; its permissions must permit UID10001 to read it. Startup does not edit this file. A mounted config chooses its own JDBC URL; point it at `/var/app/data` if using the supplied volume.

Environment overrides take precedence over the loaded file, for both JAR and container launches:

| Variable | Configuration property |
| --- | --- |
| ZK_SERVER | zkServer |
| USER_SET | userSet (complete JSON) |
| LOGIN_MESSAGE | loginMessage |
| ZKUI_BASEURL | baseURL |

Values pass directly to Java Properties and are not interpolated into shell/sed commands. `ZKUI_ZK_SERVER`/`ZKUI_ADMIN_PW` belong to other images and are not supported here. The unmodified repository sample is admin/manager, not admin/admin; provide your own userSet for deployment. Blank/invalid overrides do not silently restore the sample account.

`localhost` inside a container means that container. For a separate ZooKeeper container use its service/network name on a shared Docker network. No Docker socket mount is needed. run.sh defaults to binding `127.0.0.1:9090`; use a trusted reverse proxy or explicitly select BIND_ADDRESS/HTTP_PORT. Optional IMAGE, CONTAINER_NAME and DATA_VOLUME select local resources; existing containers are not replaced automatically.

The bootstrap script is LF-only and invokes Java with exec so it receives container stop signals. CI builds the local image, checks its numeric non-root user, and starts a test-owned container with synthetic users to verify prefixed login/assets. These checks do not establish production ZooKeeper networking, real database migration or an image-security scan result.
