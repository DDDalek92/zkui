import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location("audit_runtime", Path(__file__).with_name("audit_runtime.py"))
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)


class AuditFailureTests(unittest.TestCase):
    def legal_fixture(self, directory, entries, name="fixture"):
        jar = directory / (name + ".jar")
        with zipfile.ZipFile(jar, "w") as archive:
            for path, content in entries:
                archive.writestr(path, content)
        component = {"group": "test", "name": name, "version": "1", "purl": "pkg:maven/test/" + name + "@1",
                     "hashes": [{"alg": "SHA-256", "content": audit.digest(jar.read_bytes())}],
                     "licenses": [{"license": {"id": "MIT"}}]}
        return component, {("test", name, "1", ""): jar}

    def test_legal_documents_are_not_license_package_classes(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            documents = [("META-INF/LICENSE", b"first original\r\n"),
                         ("META-INF/NOTICE.md", b"original notice"),
                         ("META-INF/license/LICENSE.boringssl.txt", b"nested original")]
            component, jars = self.legal_fixture(path, documents + [
                ("org/example/license/LicenseProvider.class", b"\xca\xfe\xba\xbe"),
                ("com/example/Notice.class", b"\xca\xfe\xba\xbe"),
                ("org/example/license/helper.txt", b"not a legal document")])
            rows = audit.inventory({"components": [component]}, jars, path)
            prefix = "test/fixture/1/main/"
            self.assertEqual([prefix + name for name, _ in documents], rows[0]["legal_entries"])
            with zipfile.ZipFile(path / "third-party-notices.zip") as archive:
                self.assertEqual(rows[0]["legal_entries"], archive.namelist())
                for name, content in documents:
                    self.assertEqual(content, archive.read(prefix + name))

    def test_binary_only_jar_still_has_no_embedded_legal_documents(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            component, jars = self.legal_fixture(path, [("package/license/Provider.class", b"binary"),
                                                       ("package/Notice.class", b"binary")])
            self.assertEqual([], audit.inventory({"components": [component]}, jars, path)[0]["legal_entries"])

    def test_same_document_names_from_distinct_jars_retain_original_bytes(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            first, jars = self.legal_fixture(path, [("LICENSE", b"first\r\n\xff")], "first")
            second, more = self.legal_fixture(path, [("LICENSE", b"second\n")], "second")
            jars.update(more)
            rows = audit.inventory({"components": [first, second]}, jars, path)
            self.assertEqual(2, sum(len(row["legal_entries"]) for row in rows))
            with zipfile.ZipFile(path / "third-party-notices.zip") as archive:
                self.assertEqual(b"first\r\n\xff", archive.read("test/first/1/main/LICENSE"))
                self.assertEqual(b"second\n", archive.read("test/second/1/main/LICENSE"))

    def test_unsafe_and_duplicate_legal_paths_are_rejected(self):
        for entries in [[("../LICENSE", b"unsafe")], [("LICENSE", b"first"), ("LICENSE", b"second")]]:
            with self.subTest(entries=entries), tempfile.TemporaryDirectory() as directory:
                path = Path(directory)
                component, jars = self.legal_fixture(path, entries)
                with self.assertRaises(ValueError):
                    audit.inventory({"components": [component]}, jars, path)

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
