# Explicit hosts metadata bootstrap

Disabled by default. To initialize missing hosts metadata on startup, configure:

```properties
initializeHosts=true
hostsBootstrapOverwrite=false
hostsBootstrapEntries={"web01":"app=cluster"}
```

The hosts location follows zkRootPath/zkHostsPath and existing ZooKeeper chroot settings. The configured namespace root must already exist. Only missing ancestors within that root and configured host entries are created, using the configured backend identity/default ACL. Existing node data/ACLs are preserved; unspecified entries are never deleted. Entries contain the exact mapping text expected by the existing REST setup.

Only hostsBootstrapOverwrite=true allows replacement of an existing mapping value. It reads the current version and uses that version for setData, refusing a conflicting concurrent update. Equal values are not rewritten. Root/ancestor data and all existing ACLs are preserved even in overwrite mode.

Before any connection/write: validate all keys/values and namespace/reserved paths. Limits:64KiB JSON,1,000 entries,255 UTF8 bytes per key,8KiB per value,128 relative ancestor components. No arbitrary URL, automatic root creation, metadata reconciliation/delete or permission elevation is provided.

The helper closes its backend session on success/failure. Bootstrap or its audit-write failure prevents HTTP startup. Startup audit stores only created/updated counts with system actor startup/local, never mapping values. Writes across ZooKeeper nodes/audit DB are not a transaction; a failure may leave earlier creations/updates applied, so inspect state before retrying. No compensating deletion/overwrite is attempted.

Tests cover disabled/no network, nested creation/exact UTF8/idempotence, explicit overwrite, prevalidation/no write, namespace/root refusal, backend ACL refusal/session release and real-JAR startup/count-only history. Exact-head CI must pass before merge. Addresses the optional-create/update intent of upstream#49 without silently changing user metadata at default startup.
