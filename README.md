zkui - Zookeeper UI Dashboard
====================
A UI dashboard that allows CRUD operations on Zookeeper.

Requirements
====================
The maintenance build is verified with JDK 8 (including `javac`). Maven 3.9.9 is
provided by the included Maven Wrapper. JDK 17 builds currently fail in the legacy
ActiveJDBC instrumentation plugin; newer JDK support remains a separate task.

Setup
====================
1. `./mvnw clean verify` (Windows PowerShell: `.\mvnw.cmd clean verify`).
2. Copy the config.cfg to the folder with the jar file. Modify it to point to the zookeeper instance. Multiple zk instances are coma separated.  eg: server1:2181,server2:2181. First server should always be the leader.
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

The Java 8 GitHub Actions workflow runs the same command and uploads test reports.
See [test strategy](docs/maintenance/TEST_STRATEGY.md) and
[build validation](docs/maintenance/BUILD_VALIDATION.md) for verified scope and
remaining security, compatibility and deployment work.

Technology Stack
====================
1. Embedded Jetty Server.
2. Freemarker template.
3. H2 DB.
4. Active JDBC.
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
