"""Test-owned CI container only; no ZooKeeper writes or external services."""
import http.cookiejar
import re
import time
import urllib.error
import urllib.parse
import urllib.request

BASE = "http://127.0.0.1:19090/container"
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None

client = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()), NoRedirect())
for attempt in range(60):
    try:
        with client.open(BASE + "/login", timeout=1) as response:
            html = response.read().decode("utf-8")
            assert response.status == 200
            break
    except (urllib.error.URLError, ConnectionError):
        if attempt == 59:
            raise
        time.sleep(0.25)
assert "Container smoke" in html
assert 'action="/container/login"' in html
token = re.search(r'name="csrfToken" value="([A-Za-z0-9_-]+)"', html).group(1)
form = urllib.parse.urlencode(dict(username="container-test", password="synthetic-container-only", csrfToken=token)).encode()
try:
    client.open(BASE + "/login", data=form, timeout=3)
    raise AssertionError("Login should redirect")
except urllib.error.HTTPError as response:
    assert response.code == 302
    assert urllib.parse.urlparse(response.headers["Location"]).path == "/container/home"
for resource in ("/css/zkacd.css", "/js/table-sort.js", "/images/zookeeper.png"):
    with client.open(BASE + resource, timeout=3) as response:
        assert response.status == 200
        assert response.read()
print("Container login, environment overrides and prefixed assets passed")
