import unittest
from audit_build import ACTIVE, check_classification, findings, parse_inventory


class BuildAuditTests(unittest.TestCase):
    def inventory(self):
        return "\n".join("   test:" + name + ":jar:1:/owned/" + name + ".jar" for name in sorted(ACTIVE))

    def test_parse_root_dependency_classifier_and_windows_path(self):
        text = self.inventory() + "\n      test:library:jar:native:2:C:\\owned\\library.jar"
        result = parse_inventory(text)[-1]
        self.assertEqual("native", result["classifier"])
        self.assertEqual("2", result["version"])
        self.assertEqual("C:\\owned\\library.jar", result["path"])

    def test_missing_unknown_and_malformed_inventory_are_not_clean(self):
        for text in ("", self.inventory() + "\n   test:unknown:jar:1:/owned/unknown.jar", self.inventory() + "\ntruncated row"):
            with self.assertRaises(ValueError):
                parse_inventory(text)

    def test_optional_finding_is_retained_and_shared_dependency_still_blocks(self):
        entries = [{"plugin": "maven-site-plugin", "package": "test:shared", "version": "1"}]
        def query(packages):
            return [{"package": name, "version": version, "vulnerabilities": [{"id": "TEST-FINDING"}]} for name, version in packages]
        result = findings(entries, query)
        self.assertEqual("unexecuted_plugin_findings", result["status"])
        self.assertEqual(1, result["optional_affected_coordinates"])
        entries.append({"plugin": "maven-compiler-plugin", "package": "test:shared", "version": "1"})
        result = findings(entries, query)
        self.assertEqual("active_findings", result["status"])
        self.assertEqual(1, result["active_affected_coordinates"])

    def test_missing_query_result_is_not_clean(self):
        with self.assertRaises(ValueError):
            findings([{"plugin": "maven-compiler-plugin", "package": "test:one", "version": "1"}], lambda packages: [])

    def test_aggregator_pom_is_recognized_as_non_executable_metadata(self):
        result = parse_inventory(self.inventory() + "\n      test:parent:pom:1:/owned/parent.pom")
        self.assertEqual(len(ACTIVE), len(result))

    def test_optional_goal_or_execution_requires_explicit_reclassification(self):
        empty = "<project/>"
        check_classification(empty, "npm install\n./mvnw -B clean verify")
        for workflow in ("./mvnw install", "./mvnw site:run", "./mvnw org.apache.maven.plugins:maven-release-plugin:3:prepare", "./mvnw verify \\"):
            with self.assertRaises(ValueError):
                check_classification(empty, workflow)
        with self.assertRaises(ValueError):
            check_classification("<project><plugin><artifactId>maven-site-plugin</artifactId><executions/></plugin></project>", "")


if __name__ == "__main__":
    unittest.main()
