#!/usr/bin/env python3
"""Inventory resolved runtime JARs and query OSV. Network/coverage errors fail closed.

Only public Maven coordinates and versions are sent to OSV. No credentials or
application configuration are read. Evidence is separate from release approval.
"""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import sys
import subprocess
import urllib.parse
import urllib.request
import zipfile


def digest(data):
    return hashlib.sha256(data).hexdigest()


def request_json(path, payload=None):
    data = None if payload is None else json.dumps(payload).encode()
    request = urllib.request.Request("https://api.osv.dev/v1/" + path, data=data,
                                     headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)


def query_osv(packages, request=request_json):
    """Follow per-query pagination; never treat incomplete responses as clean."""
    pending = [(p, {"package": {"ecosystem": "Maven", "name": p[0]}, "version": p[1]}) for p in packages]
    found = {p: {} for p in packages}
    seen_tokens = set()
    while pending:
        results = request("querybatch", {"queries": [q for _, q in pending]})["results"]
        if len(results) != len(pending):
            raise ValueError("OSV response count mismatch")
        following = []
        for (package, query), result in zip(pending, results):
            if "error" in result:
                raise ValueError("OSV query error")
            for vuln in result.get("vulns", []):
                found[package][vuln["id"]] = vuln
            token = result.get("next_page_token")
            if token:
                marker = (package, token)
                if marker in seen_tokens:
                    raise ValueError("OSV repeated page token")
                seen_tokens.add(marker)
                following.append((package, dict(query, page_token=token)))
        pending = following
    return [{"package": name, "version": version, "vulnerabilities": list(found[(name, version)].values())}
            for name, version in packages]


def resolved_jars(path):
    jars = {}
    for line in path.read_text().splitlines():
        fields = line.strip().split(" -- ", 1)[0].split(":")
        if len(fields) < 6 or fields[2] != "jar":
            continue
        # dependency:list: group:artifact:type[:classifier]:version:scope:absolute-path
        scope_index = 5 if fields[5] in ("compile", "runtime") else 4
        if fields[scope_index] not in ("compile", "runtime"):
            continue
        group, artifact = fields[:2]
        version = fields[scope_index - 1]
        classifier = fields[3] if scope_index == 5 else ""
        jar = Path(":".join(fields[scope_index + 1:]))
        key = (group, artifact, version, classifier)
        if key in jars or not jar.is_file():
            raise ValueError("Duplicate or missing resolved JAR: " + str(key))
        jars[key] = jar
    if not jars:
        raise ValueError("Empty resolved runtime inventory")
    return jars


def is_legal_document(name):
    """Match the document's basename, never a parent Java package named license."""
    basename = PurePosixPath(name).name
    if not re.fullmatch(r"(?:LICENSE|NOTICE|COPYING)(?:[._-][A-Za-z0-9][A-Za-z0-9._-]*)?", basename, re.I):
        return False
    # In particular, com/mysql/.../Notice.class is executable code, not a notice.
    return PurePosixPath(basename).suffix.lower() not in {
        ".class", ".java", ".jar", ".zip", ".gz", ".so", ".dll", ".exe",
        ".png", ".jpg", ".jpeg", ".gif", ".woff", ".woff2", ".ttf", ".ico"}


def inventory(bom, jars, output):
    components = bom.get("components", [])
    if not components:
        raise ValueError("Empty SBOM")
    rows = []
    used = set()
    with zipfile.ZipFile(output / "third-party-notices.zip", "w", zipfile.ZIP_DEFLATED) as notices:
        for component in components:
            purl = urllib.parse.urlsplit(component["purl"])
            qualifiers = urllib.parse.parse_qs(purl.query)
            classifier = qualifiers.get("classifier", [""])[0]
            key = (component["group"], component["name"], component["version"], classifier)
            if key in used or key not in jars:
                raise ValueError("SBOM/resolved inventory mismatch: " + str(key))
            used.add(key)
            jar = jars[key]
            sha = digest(jar.read_bytes())
            hashes = {x["alg"]: x["content"].lower() for x in component.get("hashes", [])}
            if hashes.get("SHA-256") != sha:
                raise ValueError("SBOM/resolved JAR hash mismatch: " + str(key))
            if not component.get("licenses"):
                raise ValueError("Missing license metadata: " + str(key))
            legal = []
            with zipfile.ZipFile(jar) as source:
                for item in source.infolist():
                    if item.is_dir() or not is_legal_document(item.filename):
                        continue
                    name = PurePosixPath(item.filename)
                    if name.is_absolute() or ".." in name.parts:
                        raise ValueError("Unsafe legal entry path")
                    entry = "/".join((*key[:3], classifier or "main", item.filename))
                    if entry in legal:
                        raise ValueError("Duplicate legal entry path: " + item.filename)
                    notices.writestr(entry, source.read(item))
                    legal.append(entry)
            rows.append({"purl": component["purl"], "sha256": sha, "licenses": component["licenses"], "legal_entries": legal})
    if used != set(jars):
        raise ValueError("Resolved JARs missing from SBOM")
    (output / "runtime-inventory.json").write_text(json.dumps(rows, indent=2) + "\n")
    return rows


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--bom", type=Path, default=Path("target/audit/runtime-bom.json"))
    parser.add_argument("--resolved", type=Path, default=Path("target/audit/resolved-runtime.txt"))
    parser.add_argument("--output", type=Path, default=Path("target/audit"))
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    report = {"status": "error", "queried_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
              "scope": "resolved Maven runtime only; not frontend, test/build plugins or container OS"}
    try:
        report["source_commit"] = os.environ.get("GITHUB_SHA") or subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
        report["source_tree_dirty"] = bool(subprocess.check_output(["git", "status", "--porcelain"], text=True).strip())
        report["bom_sha256"] = digest(args.bom.read_bytes())
        bom = json.loads(args.bom.read_text())
        rows = inventory(bom, resolved_jars(args.resolved), args.output)
        packages = sorted({(c["group"] + ":" + c["name"], c["version"]) for c in bom["components"]})
        report["components"] = len(rows)
        report["queries"] = query_osv(packages)
        report["status"] = "findings" if any(q["vulnerabilities"] for q in report["queries"]) else "no_matches"
        report["legal_entries"] = sum(len(r["legal_entries"]) for r in rows)
        report["missing_embedded_legal"] = [r["purl"] for r in rows if not r["legal_entries"]]
        report["license_status"] = "metadata_inventory_only; redistribution review is separate"
    except Exception as error:
        report["error"] = type(error).__name__ + ": " + str(error)
    (args.output / "osv-report.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))
    return 0 if report["status"] == "no_matches" else 1


if __name__ == "__main__":
    sys.exit(main())
