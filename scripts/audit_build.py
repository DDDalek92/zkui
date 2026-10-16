#!/usr/bin/env python3
"""Audit exact resolved plugin JARs. Preserve optional-goal findings separately.

Classification is the checked-in clean/package/verify CI goal set, not a claim
that optional plugin code is unreachable or safe to invoke manually.
"""
import argparse
import datetime
import hashlib
import json
from pathlib import Path
import re
import shlex
import sys
import xml.etree.ElementTree as ET
from audit_runtime import query_osv

ACTIVE = frozenset("maven-clean-plugin maven-resources-plugin maven-compiler-plugin maven-surefire-plugin maven-jar-plugin maven-assembly-plugin maven-dependency-plugin maven-failsafe-plugin cyclonedx-maven-plugin".split())
OPTIONAL = frozenset("maven-site-plugin maven-install-plugin maven-deploy-plugin maven-release-plugin maven-antrun-plugin".split())
ROW = re.compile(r"^( +)([^: ]+):([^: ]+):(jar|maven-plugin|pom):(?:(?P<classifier>[^:]+):)?(?P<version>[^:]+):(?P<path>(?:/|[A-Za-z]:[\\/]).+)$")


def check_classification(pom, workflow):
    root = ET.fromstring(pom)
    namespace = root.tag.split("}")[0] + "}" if "}" in root.tag else ""
    for plugin in root.iter(namespace + "plugin"):
        if plugin.findtext(namespace + "artifactId") in OPTIONAL and plugin.find(namespace + "executions") is not None:
            raise ValueError("Optional plugin has executions; update CI classification")
    for line in workflow.splitlines():
        match = re.search(r"\bmvnw(?:\.cmd)?\s+(.*)", line)
        if not match:
            continue
        if line.rstrip().endswith("\\"):
            raise ValueError("Reclassify multiline Maven invocation before accepting inventory")
        for token in shlex.split(match.group(1)):
            if token.startswith("-"):
                continue
            if token in ("site", "install", "deploy") or any(
                    token.startswith(name.removeprefix("maven-").removesuffix("-plugin") + ":")
                    or ":" + name + ":" in token for name in OPTIONAL):
                raise ValueError("CI invokes an optional goal; update classification")


def parse_inventory(text):
    entries = []
    plugin = None
    roots = set()
    for line in text.splitlines():
        match = ROW.fullmatch(line)
        if not match:
            if line.strip() and not line.startswith("The following"):
                raise ValueError("Unrecognized plugin inventory line")
            continue
        indent, group, artifact, _ = match.group(1, 2, 3, 4)
        if match.group(4) == "pom":
            # Resolver may include an aggregator descriptor with no executable JAR.
            continue
        if len(indent) == 3:
            plugin = artifact
            if plugin not in ACTIVE | OPTIONAL:
                raise ValueError("Unclassified build plugin: " + plugin)
            roots.add(plugin)
        if plugin is None:
            raise ValueError("Dependency without a plugin root")
        entries.append({"plugin": plugin, "package": group + ":" + artifact,
                        "version": match.group("version"), "classifier": match.group("classifier") or "",
                        "path": match.group("path")})
    if not ACTIVE <= roots:
        raise ValueError("Missing CI plugin coverage: " + str(sorted(ACTIVE - roots)))
    return entries


def findings(entries, query=query_osv):
    packages = sorted({(entry["package"], entry["version"]) for entry in entries})
    queries = query(packages)
    if {(row["package"], row["version"]) for row in queries} != set(packages):
        raise ValueError("Plugin vulnerability query coverage mismatch")
    for row in queries:
        origins = sorted({e["plugin"] for e in entries if (e["package"], e["version"]) == (row["package"], row["version"])})
        row["plugins"] = origins
        row["used_by_ci_goals"] = bool(set(origins) & ACTIVE)
    active = [row for row in queries if row["vulnerabilities"] and row["used_by_ci_goals"]]
    optional = [row for row in queries if row["vulnerabilities"] and not row["used_by_ci_goals"]]
    return {"status": "active_findings" if active else ("unexecuted_plugin_findings" if optional else "no_matches"),
            "queries": queries, "active_affected_coordinates": len(active), "optional_affected_coordinates": len(optional)}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--resolved", type=Path, default=Path("target/audit/build-plugin-dependencies.txt"))
    parser.add_argument("--output", type=Path, default=Path("target/audit"))
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    report = {"status": "error", "queried_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
              "scope": "resolved Maven plugin graphs; CI goal classification is not a reachability/VEX approval for optional goals",
              "active_plugins": sorted(ACTIVE), "unexecuted_plugins": sorted(OPTIONAL)}
    try:
        pom = Path("pom.xml").read_bytes()
        workflow = Path(".github/workflows/ci.yml").read_bytes()
        check_classification(pom.decode(), workflow.decode())
        report["classification_inputs_sha256"] = {"pom": hashlib.sha256(pom).hexdigest(), "workflow": hashlib.sha256(workflow).hexdigest()}
        data = args.resolved.read_bytes()
        report["inventory_sha256"] = hashlib.sha256(data).hexdigest()
        entries = parse_inventory(data.decode())
        binaries = {}
        for entry in entries:
            path = Path(entry["path"])
            key = (entry["package"], entry["version"], entry["classifier"])
            sha = hashlib.sha256(path.read_bytes()).hexdigest()
            if key in binaries and binaries[key]["sha256"] != sha:
                raise ValueError("Same plugin coordinate resolved to different bytes")
            binaries.setdefault(key, {"package": key[0], "version": key[1], "classifier": key[2], "sha256": sha})
        report["binaries"] = list(binaries.values())
        report.update(findings(entries))
    except Exception as error:
        report["error"] = type(error).__name__ + ": " + str(error)
    (args.output / "build-plugin-osv.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps({key: value for key, value in report.items() if key != "binaries"}, indent=2))
    return 0 if report["status"] in ("no_matches", "unexecuted_plugin_findings") else 1


if __name__ == "__main__":
    sys.exit(main())
