from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from release_version import parse_tag


class ReleaseVersionTest(unittest.TestCase):
    def test_expected_versions_and_android_limits(self):
        for tag, name, code in (
            ("v0.0.0", "0.0.0", 1),
            ("v0.1.0", "0.1.0", 1001),
            ("v0.1.6", "0.1.6", 1007),
            ("v0.1.7", "0.1.7", 1008),
            ("v1.2.3", "1.2.3", 1_002_004),
            ("v9.9.9", "9.9.9", 9_009_010),
        ):
            with self.subTest(tag=tag):
                version = parse_tag(tag)
                self.assertEqual((name, code), (version.name, version.code))
                self.assertEqual(f"kodo_{name}_release_arm64-v8a.apk", version.apk_name)

    def test_component_rollovers_preserve_upgrade_order(self):
        tags = ("v0.0.8", "v0.0.9", "v0.1.0", "v0.1.8", "v0.1.9", "v0.2.0", "v9.8.9", "v9.9.0", "v9.9.9")
        codes = [parse_tag(tag).code for tag in tags]
        self.assertTrue(all(left < right for left, right in zip(codes, codes[1:])))

    def test_invalid_ambiguous_and_unsafe_tags_are_rejected(self):
        for tag in (
            "1.2.3", "V1.2.3", "v1.2", "v1.2.3.4", "v01.2.3", "v1.02.3", "v1.2.03",
            "v1.2.3-rc.1", "v1.2.3+build", "v1.10.0", "v1.0.10", "v10.0.0", "v2100.0.0",
            "v-1.2.3", "v１.2.3", "v1.2.3 ", "v1.2.3\n", "v1.2.3\nversion_code=1",
            "v1.2.3;echo unsafe", "v$(echo 1).2.3", "v1/2/3", "v" + "9" * 5000 + ".0.0",
        ):
            with self.subTest(tag=tag[:80]), self.assertRaises(ValueError):
                parse_tag(tag)

    def test_cli_writes_outputs_only_for_a_valid_tag(self):
        script = Path(__file__).resolve().parents[1] / "release_version.py"
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "github-output"
            command = [sys.executable, str(script), "v1.2.3", "--github-output", str(output)]
            result = subprocess.run(command, capture_output=True)
            self.assertEqual(0, result.returncode)
            self.assertEqual(
                "version_name=1.2.3\nversion_code=1002004\napk_name=kodo_1.2.3_release_arm64-v8a.apk\n",
                output.read_text(encoding="ascii"),
            )
            before = output.read_bytes()
            command[2] = "v1.2.3\nversion_code=1"
            self.assertNotEqual(0, subprocess.run(command, capture_output=True).returncode)
            self.assertEqual(before, output.read_bytes())


if __name__ == "__main__":
    unittest.main()
