"""Exercise the actual release workflow's toolchain and publication contract."""
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]


class ReleaseWorkflowTest(unittest.TestCase):
    def setUp(self):
        self.workflow = (ROOT / ".github/workflows/release.yml").read_text(encoding="utf-8")

    def step(self, name):
        marker = f"      - name: {name}\n"
        self.assertTrue(marker in self.workflow, f"Release must provide {name} before compilation")
        return self.workflow.split(marker, 1)[1].split("      - name:", 1)[0]

    def test_android_setup_installs_the_native_builder_requirements(self):
        builder = (ROOT / "scripts/build_doh.py").read_text(encoding="utf-8")
        ndk = re.search(r'add_argument\("--ndk", default="([^"]+)"\)', builder).group(1)
        setup = self.step("Set up Android SDK")
        packages = re.search(r"packages: '([^']+)'", setup).group(1).split()
        for package in (f"ndk;{ndk}", "cmake;3.22.1", "platforms;android-37.0", "build-tools;37.0.0"):
            self.assertIn(package, packages)
        self.assertLess(self.workflow.index("name: Set up Android SDK"), self.workflow.index("name: Build signed release"))

    def test_package_step_uses_the_installed_build_tools(self):
        setup_packages = re.search(r"packages: '([^']+)'", self.step("Set up Android SDK")).group(1)
        installed = re.search(r"build-tools;([0-9.]+)", setup_packages).group(1)
        verify = self.step("Verify signature and APK version, then create checksum")
        self.assertIn(f'"$ANDROID_HOME/build-tools/{installed}"', verify)

    def test_rust_setup_installs_the_android_target_and_stops_on_install_failure(self):
        step = self.step("Set up Rust")
        version = "1.96.1"
        self.assertIn("        run: |\n", step)
        shell = "\n".join(line[10:] for line in step.split("        run: |\n", 1)[1].splitlines())
        bash = r"C:\Program Files\Git\bin\bash.exe" if os.name == "nt" else shutil.which("bash")
        if not bash or not Path(bash).is_file():
            self.skipTest("Bash is required for the workflow fixture")
        stub = '''set -euo pipefail
rustup() { printf 'rustup %s\\n' "$*" >> calls.txt; [[ "$SCENARIO" != failure || "$1" != toolchain ]]; }
rustc() { printf 'rustc %s\\n' "$*" >> calls.txt; }
cargo() { printf 'cargo %s\\n' "$*" >> calls.txt; }
'''
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "setup.sh").write_text(stub + shell + "\n", encoding="utf-8", newline="\n")
            for scenario in ("success", "failure"):
                with self.subTest(scenario=scenario):
                    calls = root / "calls.txt"
                    calls.unlink(missing_ok=True)
                    result = subprocess.run([bash, "setup.sh"], cwd=root, capture_output=True,
                        env={**os.environ, "SCENARIO": scenario})
                    recorded = calls.read_text(encoding="utf-8").splitlines()
                    install = f"rustup toolchain install {version} --profile minimal --target aarch64-linux-android"
                    self.assertEqual(scenario == "success", result.returncode == 0, result.stderr)
                    self.assertEqual(install, recorded[0])
                    if scenario == "success":
                        self.assertEqual([install, f"rustup default {version}", "rustc --version", "cargo --version"], recorded)
                    else:
                        self.assertEqual([install], recorded)

    def test_branch_and_tag_triggers(self):
        triggers = self.workflow.split("permissions:", 1)[0]
        self.assertRegex(triggers, r"branches:\s*\n\s*- kudo-base")
        self.assertRegex(triggers, r"tags:\s*\n\s*- 'v\*'")
        match = re.search(r"    paths:\n((?:      - [^\n]+\n)+)", triggers)
        self.assertIsNotNone(match, "Ordinary source or documentation pushes must not start the main build")

    def test_release_steps_run_only_on_tags_and_sign_with_the_shared_keystore(self):
        for name in ("Validate tag and calculate version", "Build signed release",
                     "Verify signature and APK version, then create checksum",
                     "Organize release notes from commits", "Upload verified release files"):
            with self.subTest(step=name):
                self.assertIn("        if: github.ref_type == 'tag'\n", self.step(name))
        # 发布签名必须与 nightly 同身份：release 包要能 install -r 覆盖开发版。
        for name in ("Build signed release", "Verify signature and APK version, then create checksum"):
            with self.subTest(step=name):
                self.assertIn("RELEASE_KEYSTORE_PATH: ci/hudo-debug.p12", self.step(name))
        self.assertNotIn("RELEASE_KEYSTORE_BASE64", self.workflow)
        publish = self.workflow.split("  publish:\n", 1)[1]
        self.assertIn("    if: github.ref_type == 'tag'\n", publish)
        self.assertIn('"虎Do $VERSION_NAME"', publish)


if __name__ == "__main__":
    unittest.main()
