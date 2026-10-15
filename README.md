zkui - Zookeeper UI Dashboard
====================
A UI dashboard that allows CRUD operations on Zookeeper.

Requirements
====================
The maintenance build requires JDK 17 (including `javac`). Maven 3.9.9 is
provided by the included Maven Wrapper. Jetty 12 EE8 retains the `javax.servlet`
application API. History storage uses parameterized JDBC and no longer needs
ActiveJDBC bytecode instrumentation. H2/Flyway versions and V1 remain unchanged;
this is not a historical-database format upgrade. Java 8 is no longer supported.

Setup
====================
1. `./mvnw clean verify` (Windows PowerShell: `.\mvnw.cmd clean verify`).
2. Copy the config.cfg to the folder with the jar file. Modify it to point to the zookeeper instance. Multiple addresses are comma separated, e.g. server1:2181,server2:2181; the first address need not be the leader.
3. Run the jar. ( nohup java -jar zkui-2.0-SNAPSHOT-jar-with-dependencies.jar & )
4. <a href="http://localhost:9090">http://localhost:9090</a> 

Login Info
====================
username: admin, pwd: manager (Admin privileges, CRUD operations supported)
username: appconfig, pwd: appconfig (Readonly privileges, Read operations supported)

You can change this in the config.cfg

Development checks
====================
`./mvnw test` runs unit tests. `./mvnw clean verify` also packages the application
and runs isolated ZooKeeper and HTTP integration tests. Tests start their own
loopback ZooKeeper servers and use temporary H2 databases; no external ZooKeeper,
Docker, database or production configuration is needed. First use requires
network access to download Maven and dependencies.

Historical-database rehearsal tests download a separate candidate H2 driver and
run it only in isolated subprocesses with synthetic database copies. The application
still packages H2 1.3.174; see the [rehearsal scope and blockers](docs/maintenance/H2_MIGRATION_REHEARSAL.md).

The Java 17 GitHub Actions workflow runs the same command on Linux, Windows and
macOS and uploads test reports; Chrome and a non-root Java 17 Docker smoke are also checked.
See [test strategy](docs/maintenance/TEST_STRATEGY.md) and
[build validation](docs/maintenance/BUILD_VALIDATION.md) for verified scope and
remaining security, compatibility and deployment work.

Technology Stack
====================
1. Embedded Jetty Server.
2. Freemarker template.
3. H2 DB.
4. JDBC history storage.
5. JSON.
6. SLF4J.
7. Zookeeper.
8. Apache Commons File upload.
9. Bootstrap.
10. Jquery.
11. Flyway DB migration.

Features
====================
1. CRUD operation on zookeeper properties.
2. Export properties.
3. Import properties via call back url.
4. Import properties via file upload.
5. History of changes + Path specific history of changes.
6. Search feature.
7. Rest API for accessing Zookeeper properties.
8. Basic Role based authentication.
9. LDAP authentication supported.
10. Root node /zookeeper hidden for safety.
11. ACL supported global level.

Import File Format
====================
# add property
/appconfig/path=property=value
# remove a property
-/path/property

You can either upload a file or specify a http url of the version control system that way all your zookeeper changes will be in version control. 

Export File Format
====================
/appconfig/path=property=value

You can export a file and then use the same format to import.

SOPA/PIPA BLACKLISTED VALUE
====================
All password will be displayed as SOPA/PIPA BLACKLISTED VALUE for a normal user. Admins will be able to view and edit the actual value upon login.
Password will be not shown on search / export / view for normal user.
For a property to be eligible for black listing it should have (PWD / pwd / PASSWORD / password) in the property name.

LDAP
====================
If you want to use LDAP authentication provide the ldap url. This will take precedence over roleSet property file authentication.
ldapUrl=ldap://<ldap_host>:<ldap_port>/dc=mycom,dc=com
If you dont provide this then default roleSet file authentication will be used.

Import authorization
--------------------

File uploads and remote URL imports require a logged-in ADMIN. USER and unknown roles receive HTTP 403 before upload parsing or remote fetching. Import files can create, overwrite and delete nodes, so read-only users must not submit them.

REST call
====================
Shell scripts can read ZooKeeper properties through this read-only HTTP endpoint. **Maintenance change:** REST now requires a login session by default; anonymous requests receive HTTP 401. This also applies to existing configurations that omit the new `allowAnonymousRest` setting.

`blockPwdOverRest=true` is the new default. Password property names containing `pwd`, `password` or `passwd` are masked case-insensitively. Only an authenticated ADMIN with explicit `blockPwdOverRest=false` can read their original values. Missing or invalid settings keep masking enabled; USER and unknown roles never receive ADMIN access through this flag. Responses use `Cache-Control: no-store`.

For scripts migrating from anonymous REST, log in and reuse the session cookie as shown below. An explicit `allowAnonymousRest=true` restores anonymous lookups for compatibility, with password masking enforced even when `blockPwdOverRest=false`. Name-based masking does not detect every secret: tokens and private values under other names can still be exposed. Enable anonymous compatibility only for intentionally public configuration on a trusted network. Host/app/cluster parameters select lookup paths; they do not authorize a caller to a particular host or subtree.

Missing/empty `propNames` returns HTTP 400; an unknown host mapping returns HTTP 404.

Eg:
http://localhost:9090/acd/appconfig?propNames=foo&host=myhost.com
This will first lookup the host name under /appconfig/hosts and then find out which path the host point to. Then it will look for the property under that path.

There are 2 additional properties that can be added to give better control.
cluster=cluster1
http://localhost:9090/acd/appconfig?propNames=foo&cluster=cluster1&host=myhost.com
In this case the lookup will happen on lookup path + cluster1.

app=myapp
http://localhost:9090/acd/appconfig?propNames=foo&app=myapp&host=myhost.com
In this case the lookup will happen on lookup path + myapp.

A shell script can log in with a dedicated USER account and reuse its cookie:

```sh
# Set ZKUI_URL, ZKUI_USER and ZKUI_PASSWORD in your environment.
umask 077
CSRF_TOKEN="$(curl --fail --silent --show-error --cookie-jar zkui-cookies.txt \
  "$ZKUI_URL/login" | sed -n 's/.*name="csrfToken" value="\([^" ]*\)".*/\1/p' | head -n 1)"
curl --fail --silent --show-error --cookie zkui-cookies.txt --cookie-jar zkui-cookies.txt \
  --data-urlencode "username=$ZKUI_USER" --data-urlencode "password=$ZKUI_PASSWORD" \
  --data-urlencode "csrfToken=$CSRF_TOKEN" "$ZKUI_URL/login"
MY_PROPERTY="$(curl --fail --silent --show-error --cookie zkui-cookies.txt \
  --get --data-urlencode 'propNames=foo' --data-urlencode "host=$(hostname -f)" \
  "$ZKUI_URL/acd/appconfig" | cut -d '=' -f 2-)"
echo "$MY_PROPERTY"
rm -f zkui-cookies.txt
```

A cookie is only usable after successful login; expired sessions require login again. Protect the cookie file as a credential. HTTP Basic authentication is not implemented by this endpoint.

Standardization
====================
Zookeeper doesnt enforce any order in which properties are stored and retrieved. ZKUI however organizes properties in the following manner for easy lookup.
Each server/box has its hostname listed under /appconfig/hosts and that points to the path where properties reside for that path. So when the lookup for a property occurs over a rest call it first finds the hostname entry under /appconfig/hosts and then looks for that property in the location mentioned.
eg: /appconfig/hosts/myserver.com=/appconfig/dev/app1 
This means that when myserver.com tries to lookup the propery it looks under /appconfig/dev/app1

You can also append app name to make lookup easy.
eg: /appconfig/hosts/myserver.com:testapp=/appconfig/dev/test/app1 
eg: /appconfig/hosts/myserver.com:prodapp=/appconfig/dev/prod/app1

Lookup can be done by grouping of app and cluster. A cluster can have many apps under it. When the bootloader entry looks like this /appconfig/hosts/myserver.com=/appconfig/dev the rest lookup happens on the following paths.
/appconfig/dev/..
/appconfig/dev/hostname..
/appconfig/dev/app..
/appconfig/dev/cluster..
/appconfig/dev/cluster/app..

This standardization is only needed if you choose to use the rest lookup. You can use zkui to update properties in general without worry about this organizing structure.

HTTPS
====================
You can enable https if needed. 
keytool -keystore keystore -alias jetty -genkey -keyalg RSA


Limitations
====================
1. ACLs are fully supported but at a global level.

Screenshots
====================
Basic Role Based Authentication
<br/>
<img src="https://raw.github.com/DeemOpen/zkui/master/images/zkui-0.png"/>
<br/>

Dashboard Console
<br/>
<img src="https://raw.github.com/DeemOpen/zkui/master/images/zkui-1.png"/>
<br/>

CRUD Operations
<br/>
<img src="https://raw.github.com/DeemOpen/zkui/master/images/zkui-2.png"/>
<br/>

Import Feature
<br/>
<img src="https://raw.github.com/DeemOpen/zkui/master/images/zkui-3.png"/>
<br/>

Track History of changes
<br/>
<img src="https://raw.github.com/DeemOpen/zkui/master/images/zkui-4.png"/>
<br/>

Status of Zookeeper Servers
<br/>
<img src="https://raw.github.com/DeemOpen/zkui/master/images/zkui-5.png"/>
<br/>

License & Contribution
====================

ZKUI is released under the Apache 2.0 license. Comments, bugs, pull requests, and other contributions are all welcomed!

Thanks to Jozef Krajčovič for creating the logo which has been used in the project.
https://www.iconfinder.com/iconsets/origami-birds

Session and CSRF protection
---------------------------

Successful login rotates both the session ID and the CSRF token. Session cookies are HttpOnly and URL-only session IDs are disabled. Every POST (including login, history search and logout) requires the current session's `csrfToken` form field or `X-CSRF-Token` header. Multipart imports also accept a `csrfToken` multipart field. Fetch `/login` before login to obtain the initial cookie and token; fetch `/home` or `/login` with the new cookie after login to obtain the rotated token for later POSTs. Do not put tokens into URLs. GET `/logout` returns 405; use the rendered logout form or a token-protected POST. REST GET requests need no CSRF token.

Import input limits
-------------------

Imports accept one UTF-8 text file up to 1 MiB, at most 32 multipart parts (including CSRF and controls), a 2 MiB complete multipart request, and at most 10,000 records. All records and paths are checked before any ZooKeeper write. Root/system `/zookeeper` paths are refused. Oversized content returns 413; invalid, unavailable or unapproved input returns 400. The import history stores counts/overwrite intent rather than original values, filenames or URLs. ZooKeeper failures during application can still leave a partially applied batch; this is not a transaction.

Remote import is disabled by default and its UI controls are hidden. Operators may set `remoteImportEnabled=true` and list exact, complete HTTP(S) URLs in `remoteImportAllowedUrls`, separated by `|`. Requests can submit `remoteUrl` or the legacy SCM fields; the resulting complete URL must exactly match the operator's list. Userinfo/fragments, `file:` and other schemes are refused. Redirects are never followed. Responses must be HTTP 200, are capped at 1 MiB, and use 3-second connection/read timeouts plus a 10-second body-read deadline. Only list endpoints whose DNS, content and availability you trust; enabling a URL explicitly grants the application network access to that endpoint, including internal hosts if deliberately listed. Keep the list empty when remote import is unnecessary.

History and displayed values
----------------------------

New create/update history contains paths, and import history contains only counts and overwrite intent. Raw values are no longer written to application DEBUG logs/history. History access requires ADMIN because older database records may contain secrets; upgrades do not erase those records. Review existing backups/history retention separately. Node names, values and search output are HTML-escaped; multiline display preserves whitespace (upstream #109 by gouzhuang).

ZooKeeper connection behavior
-----------------------------

`zkServer` is the complete native ensemble string, for example `host1:2181,host2:2181/appconfig` or bracketed IPv6 endpoints. zkui passes the whole string to ZooKeeper; it no longer selects only the first member. `zkSessionTimeout` controls the requested ZK session timeout; `zkConnectTimeout` independently bounds initial connection readiness (seconds, 1–60, default 5). Failure returns HTTP 503 rather than an unconnected/null client. UI sessions reuse their connection; REST owns and closes its separate short-lived connection. Monitor parses chroot/IPv6 and continues to other endpoints if one is down. This does not repair server-side ACL, DNS, firewall, packet-size or cluster health problems.

ZooKeeper Digest identity and ACL
--------------------------------

Web login and ZooKeeper authentication are separate. For a Digest-protected ensemble configure:

```properties
zkAuthScheme=digest
zkAuthCredentials=your-user:your-password
defaultAcl={"acls":[{"scheme":"auth","id":"","perms":"*"}]}
```

The credentials authenticate the application's shared ZK service identity. The `auth` ACL grants newly created nodes to that authenticated identity; it does not change existing ACLs. Leave both authentication fields empty for an unauthenticated client. Invalid/incomplete credentials and malformed/empty ACL lists stop startup instead of falling back to open access. Blank `defaultAcl` retains the legacy world-open creation policy.

For explicit `digest` ACL entries use `username:base64(SHA1(username:password))` as the ID, never the plaintext credential. `defaultAcl` is not a login mechanism. See the [official 3.5.7 ACL guide](https://zookeeper.apache.org/doc/r3.5.7/zookeeperProgrammers.html#sc_ZooKeeperAccessControl). Protect credentials in config files and in transit. UI ADMIN cannot bypass a read-only or unauthorized ZK identity; affected operations return HTTP 403. Per-web-user ZK identities and Kerberos are not implemented by these settings.

LDAP login configuration
------------------------

`ldapAuth=true` uses LDAP; otherwise `userSet` provides web users. The legacy default constructs `DOMAIN\username` from each comma-separated `ldapDomain`; already-qualified `DOMAIN\username` and `user@example.test` inputs are sent unchanged. Do not confuse an AD NetBIOS domain with the DNS domain. LDAP roles match the submitted username case-insensitively: an explicit account always overrides `*`, regardless of array order; unmatched users default to USER.

For a directory requiring a full DN:

```properties
ldapAuth=true
ldapUrl=ldaps://directory.example.test:636
ldapUserDnPattern=uid={0},ou=people,dc=example,dc=test
ldapConnectTimeout=3000
ldapReadTimeout=3000
ldapRoleSet={"users":[{"username":"alice","role":"ADMIN"},{"username":"*","role":"USER"}]}
```

The `{0}` username is escaped as a DN value. An empty pattern preserves domain/UPN behavior; `userDNPattern` is accepted as an alias. Passwords must be nonempty. Timeouts are milliseconds, 1–30000, per bind; up to eight space-separated LDAP URLs and eight domain candidates are supported. Authentication referrals fail closed. LDAPS uses normal JVM trust/certificate checks; configure a trusted directory certificate. StartTLS is not implemented here. Real loopback LDAP binds are tested; production AD and LDAPS certificate-chain compatibility still require deployment verification. For login failures inspect the chosen principal format, URL/reachability, TLS trust, and role keys; application responses intentionally omit directory diagnostics and passwords.

Text values and parent nodes
---------------------------
HTTP forms, node writes, uploaded text and exported text use UTF-8 explicitly. Configuration value whitespace is preserved. The home page displays the current node’s own value even when it has children, with HTML escaping and the same password-name masking as other values; this display is read-only. The legacy export format escapes newlines as literal `\n` and removes carriage returns: it is not a lossless binary backup format.

Root namespace and subtree search
---------------------------------
`zkRootPath` defaults to `/`. Set an existing absolute ZooKeeper path to confine UI reads/writes, export, import and REST lookups to that namespace. Paths are relative to any native `zkServer` chroot. The home page and a missing export path start there; parent navigation stops there. Search runs inside the current displayed node. Out-of-namespace paths return 403; malformed paths return 400. The configured root itself and `/zookeeper` are protected from modification/deletion. Complete import/delete batches are checked before any write, but backend failures after validation can still partially apply a batch.

`zkHostsPath` optionally selects the REST host-mapping node within the root. Its default is `/appconfig/hosts` when root is `/`, otherwise `<zkRootPath>/hosts`. Create it and its mappings explicitly; startup does not mutate ZooKeeper. Mapping targets must remain inside the root, and REST property inheritance never consults outside ancestors. Root limits supplement ZooKeeper ACLs and chroot; web roles remain application-wide. Search/export still collect trees in memory and are unsuitable for unbounded trees.

Offline table sorting
---------------------
Use the Name/Value headings on the home table or Path/Name/Value on search results to toggle ascending/descending text order. Sorting is case-insensitive and stable for equal text; it preserves selected rows and edit actions. Buttons also work with Enter/Space. Assets are bundled locally; sorting adds no CDN or production JavaScript dependency. Browser regression checks use synthetic pages rendered by the packaged application: after `./mvnw clean verify`, run `npm ci --ignore-scripts` and `ZKUI_BROWSER_CHANNEL=chrome npm test` with Chrome installed (or install Playwright Chromium and omit the channel setting). Node 20+ and Playwright are development/test tools only.

History configuration
---------------------
`historyLimit` controls the maximum records returned by both history queries (default 50, integer 1–1000); invalid values fail startup. The heading reflects the configured maximum. Omitted `https` and `X-Forwarded-For` flags default to false, and an omitted `env` defaults to production behavior. Never set `env=dev` against a database you want to preserve: that explicit legacy mode cleans it before migration. History connections now close even after database errors. Property values retain their original whitespace.

Reverse proxy and context path
------------------------------
Set `baseURL=/zkui/` to serve zkui under `/zkui` (default `/`). It is a local path prefix, not a full external URL. Login/logout redirects, cookie paths, forms and bundled assets use it. Preserve the same prefix at the reverse proxy; a minimal nginx location is:

```nginx
location /zkui/ {
    proxy_pass http://127.0.0.1:9090;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

The `proxy_pass` here has no trailing URI slash, so nginx keeps `/zkui/` upstream. Visit `/zkui/`; do not strip the prefix upstream while configuring zkui with it. Only enable `X-Forwarded-For=true` when requests reach zkui exclusively through a trusted proxy. Direct application/context behavior is tested; deployment-specific nginx/TLS settings still require verification.

Docker build and environment
----------------------------
Build from this repository root with `docker build -f docker/Dockerfile -t zkui:local .` (or `make build`); then set `ZK_SERVER` and your `USER_SET`, and run `sh run.sh`. This builds a local image and does not rely on a published `zkui:latest`. The runtime uses UID/GID10001, preserves history in `/var/app/data`, and accepts `ZK_SERVER`, `USER_SET`, `LOGIN_MESSAGE`, `ZKUI_BASEURL` directly in Java without rewriting config files. See [Docker instructions](docker/Docker.md) for volumes, read-only configuration, network names and complete examples. Other images’ `ZKUI_ADMIN_PW`/`ZKUI_ZK_SERVER` flags do not apply.

For build, missing hosts metadata, NoAuth/ConnectionLoss, history SQL, port conflicts and supported feature limits, see the [operations notes](docs/maintenance/OPERATIONS.md). The [issue dispositions](docs/maintenance/ISSUE_TRIAGE.md) distinguish verified fixes from documentation, requests and unresolved environments.
