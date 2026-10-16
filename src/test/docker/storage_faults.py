"""Owned Linux/Docker fixtures: real permissions and bounded tmpfs ENOSPC.

Requires an explicit CI guard; never accepts a user database, device or image.
"""
import hashlib
import http.cookiejar
from html.parser import HTMLParser
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

IMAGE = "zkui:storage-ci"
PREFIX = "zkui-storage-"
USER_SET = '{"users":[{"username":"storage-admin","password":"synthetic-storage-only","role":"ADMIN"}]}'
NAMES = [PREFIX + name for name in ("readonly", "full-startup", "live", "zk")]
NETWORK = PREFIX + "network"
EVIDENCE = Path("target/storage-evidence")


def command(*args, check=True):
    return subprocess.run(args, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                          timeout=60, check=check)


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


def client():
    return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()), NoRedirect())


def request(opener, path, values=None):
    data = None if values is None else urllib.parse.urlencode(values).encode()
    try:
        response = opener.open("http://127.0.0.1:19094" + path, data=data, timeout=10)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        return response.code, response.read().decode("utf-8"), response.headers


def ready():
    opener = client()
    for attempt in range(80):
        try:
            code, html, _ = request(opener, "/login")
            if code == 200:
                token = re.search(r'name="csrfToken" value="([A-Za-z0-9_-]+)"', html).group(1)
                assert request(opener, "/login", {"username": "storage-admin", "password": "synthetic-storage-only", "csrfToken": token})[0] == 302
                return opener
        except (urllib.error.URLError, ConnectionError, TimeoutError):
            pass
        time.sleep(0.25)
    raise AssertionError("Owned application did not start")


def mutate(opener, name, value):
    code, html, _ = request(opener, "/login")
    assert code == 200
    token = re.search(r'name="csrfToken" value="([A-Za-z0-9_-]+)"', html).group(1)
    return request(opener, "/home", {"action": "Save Property", "currentPath": "/", "displayPath": "/",
                                      "newProperty": name, "newValue": value, "csrfToken": token})


class HistoryRows(HTMLParser):
    def __init__(self):
        super().__init__()
        self.rows, self.row, self.cell = [], None, None

    def handle_starttag(self, tag, attrs):
        if tag == "tr": self.row = []
        if tag == "td" and self.row is not None: self.cell = []

    def handle_data(self, data):
        if self.cell is not None: self.cell.append(data)

    def handle_endtag(self, tag):
        if tag == "td" and self.cell is not None:
            self.row.append("".join(self.cell).strip()); self.cell = None
        if tag == "tr" and self.row is not None:
            if self.row: self.rows.append(self.row)
            self.row = None


def histories(opener):
    code, html, _ = request(opener, "/history")
    assert code == 200
    parser = HistoryRows(); parser.feed(html)
    return parser.rows


def failed_startup(name, marker):
    for attempt in range(80):
        state = json.loads(command("docker", "inspect", "--format", "{{json .State}}", name).stdout)
        if not state["Running"]:
            assert state["ExitCode"] != 0
            log = command("docker", "logs", name).stdout
            (EVIDENCE / (name + ".log")).write_text(log)
            assert "History database initialization failed" in log, log
            assert marker in log, log
            return
        time.sleep(0.25)
    raise AssertionError("Failed history startup must stop promptly")


def main():
    if os.environ.get("ZKUI_OWNED_STORAGE_FIXTURE") != "true" or not os.environ.get("RUNNER_TEMP"):
        raise AssertionError("Explicit owned CI storage fixture required")
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    root = Path(tempfile.mkdtemp(prefix=PREFIX, dir=os.environ["RUNNER_TEMP"]))
    mounted = []
    created = []
    network = False
    try:
        assert command("docker", "image", "inspect", "--format", "{{.Config.User}}", IMAGE).stdout.strip() == "10001:10001"
        readonly = root / "readonly"; readonly.mkdir()
        sentinel = readonly / "sealed-source.txt"; sentinel.write_bytes(b"owned-original-preserved\n")
        before = hashlib.sha256(sentinel.read_bytes()).hexdigest()
        command("sudo", "chown", "10001:10001", str(readonly))
        command("sudo", "chmod", "0555", str(readonly))
        # Actual filesystem permissions, not JDBC ACCESS_MODE_DATA=r.
        name = NAMES[0]
        command("docker", "run", "-d", "--name", name, "--network", "none", "-v", f"{readonly}:/var/app/data:rw",
                "-e", "USER_SET=" + USER_SET, IMAGE)
        created.append(name)
        failed_startup(name, "History database initialization failed")
        assert hashlib.sha256(sentinel.read_bytes()).hexdigest() == before
        assert sorted(p.name for p in readonly.iterdir()) == ["sealed-source.txt"]
        print("STORAGE_POSIX_UNWRITABLE_FRESH_STARTUP_REFUSED_SOURCE_PRESERVED_OK", flush=True)

        for name, size in (("full", "8m"), ("live", "16m")):
            path = root / name; path.mkdir()
            command("sudo", "mount", "-t", "tmpfs", "-o", f"size={size},mode=0700,uid=10001,gid=10001", "tmpfs", str(path))
            mounted.append(path)

        fill = "set -eu; test $(id -u) = 10001 || exit 90; test $(stat -f -c %T /var/app/data) = tmpfs || exit 90; " \
               "if LC_ALL=C dd if=/dev/zero of=/var/app/data/fill bs=4096 count=8192 2>/tmp/fill.log; then exit 91; " \
               "else grep -q 'No space left on device' /tmp/fill.log || exit 92; fi"
        name = NAMES[1]
        command("docker", "run", "-d", "--name", name, "--network", "none", "-v", f"{root / 'full'}:/var/app/data:rw",
                "-e", "USER_SET=" + USER_SET, "--entrypoint", "sh", IMAGE, "-c",
                fill + "; echo OWNED_TMPFS_ENOSPC_CONFIRMED; exec sh /var/app/bootstrap.sh")
        created.append(name)
        failed_startup(name, "OWNED_TMPFS_ENOSPC_CONFIRMED")
        print("STORAGE_ENOSPC_FRESH_STARTUP_REFUSED_OK", flush=True)

        # Keep the host-owned tmpfs mounted across container restart for real recovery.
        config = re.sub(r"(?m)^jdbcUrl=.*$", "jdbcUrl=jdbc:h2:/var/app/data/history;WRITE_DELAY=0", Path("config.cfg").read_text())
        config_path = root / "config.cfg"; config_path.write_text(config); config_path.chmod(0o644)
        command("sudo", "chmod", "0755", str(root))
        command("docker", "network", "create", NETWORK); network = True
        name = NAMES[3]
        command("docker", "run", "-d", "--name", name, "--network", NETWORK, "-e", "JVMFLAGS=-Xmx128m",
                "zookeeper:3.5.7@sha256:883b014b6535574503bda8fc6a7430ba009c0273242f86d401095689652e5731")
        created.append(name)
        for attempt in range(40):
            if command("docker", "exec", name, "zkServer.sh", "status", check=False).returncode == 0:
                break
            time.sleep(0.25)
        else:
            raise AssertionError("Owned ZooKeeper did not start")
        name = NAMES[2]
        command("docker", "run", "-d", "--name", name, "--network", NETWORK, "-p", "127.0.0.1:19094:9090",
                "-v", f"{root / 'live'}:/var/app/data:rw", "-v", f"{config_path}:/var/app/config.cfg:ro",
                "-e", "USER_SET=" + USER_SET, "-e", "ZK_SERVER=" + NAMES[3] + ":2181", "-e", "JAVA_TOOL_OPTIONS=-Xmx256m", IMAGE)
        created.append(name)
        opener = ready()
        assert mutate(opener, "storage-fixture-before", "synthetic-before-value")[0] == 302
        baseline = histories(opener)
        assert baseline and any("storage-fixture-before" in str(row) for row in baseline)
        command("docker", "exec", name, "sh", "-c", fill)
        code, html, _ = mutate(opener, "storage-fixture-after", "synthetic-after-value")
        assert code == 503 and "inspect state before retrying" in html, (code, html)
        code, home, _ = request(opener, "/home?zkPath=/")
        assert code == 200 and "storage-fixture-after" in home and "synthetic-after-value" in home
        assert "Property Saved!" not in home
        command("docker", "exec", name, "rm", "/var/app/data/fill")
        command("docker", "restart", name)
        restored = histories(ready())
        assert baseline == restored, (baseline, restored)
        log = command("docker", "logs", name).stdout
        assert "synthetic-after-value" not in log and "synthetic-before-value" not in log
        (EVIDENCE / "history-before-after.json").write_text(json.dumps({"before": baseline, "after_restart": restored}, indent=2))
        print("STORAGE_ENOSPC_AUDIT_503_ZK_APPLIED_NO_SUCCESS_FLASH_RESTART_BASELINE_PRESERVED_OK", flush=True)
    finally:
        for name in reversed(created):
            logs = command("docker", "logs", name, check=False)
            (EVIDENCE / (name + ".log")).write_text(logs.stdout)
            command("docker", "rm", "-fv", name, check=False)
        if network: command("docker", "network", "rm", NETWORK, check=False)
        for path in reversed(mounted):
            command("sudo", "umount", str(path))
        # All mounts are gone; remove only this newly created test directory.
        command("sudo", "rm", "-rf", "--", str(root))


if __name__ == "__main__":
    main()
