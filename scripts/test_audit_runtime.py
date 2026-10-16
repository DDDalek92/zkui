import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("audit_runtime", Path(__file__).with_name("audit_runtime.py"))
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)


class AuditFailureTests(unittest.TestCase):
    def test_follows_pagination_without_losing_first_page_findings(self):
        requests = []

        def request(path, payload):
            requests.append(payload)
            if len(requests) == 1:
                return {"results": [{"vulns": [{"id": "TEST-FIRST"}], "next_page_token": "next"}, {}]}
            self.assertEqual("next", payload["queries"][0]["page_token"])
            self.assertEqual(1, len(payload["queries"]))
            return {"results": [{"vulns": [{"id": "TEST-SECOND"}]}]}

        results = audit.query_osv([("test:first", "1"), ("test:second", "2")], request)
        self.assertEqual(["TEST-FIRST", "TEST-SECOND"], [v["id"] for v in results[0]["vulnerabilities"]])
        self.assertEqual([], results[1]["vulnerabilities"])

    def test_missing_response_is_not_clean(self):
        with self.assertRaises(ValueError):
            audit.query_osv([("test:first", "1")], lambda *args: {"results": []})

    def test_query_error_is_not_clean(self):
        with self.assertRaises(ValueError):
            audit.query_osv([("test:first", "1")], lambda *args: {"results": [{"error": "unavailable"}]})

    def test_repeated_page_token_is_not_clean(self):
        with self.assertRaises(ValueError):
            audit.query_osv([("test:first", "1")], lambda *args: {"results": [{"next_page_token": "same"}]})

    def test_missing_jar_and_empty_bom_are_not_accepted(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            listing = path / "list.txt"
            listing.write_text("test:missing:jar:1:compile:/not-an-artifact.jar\n")
            with self.assertRaises(ValueError):
                audit.resolved_jars(listing)
            with self.assertRaises(ValueError):
                audit.inventory({"components": []}, {}, path)

    def test_mismatched_binary_hash_is_not_accepted(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            jar = path / "changed.jar"
            jar.write_bytes(b"unexpected artifact")
            component = {"group": "test", "name": "changed", "version": "1", "purl": "pkg:maven/test/changed@1",
                         "hashes": [{"alg": "SHA-256", "content": "0" * 64}]}
            with self.assertRaisesRegex(ValueError, "hash mismatch"):
                audit.inventory({"components": [component]}, {("test", "changed", "1", ""): jar}, path)


if __name__ == "__main__":
    unittest.main()
