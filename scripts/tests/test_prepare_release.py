import base64
import hashlib
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from prepare_release import expected_certificate, package_release, restore_keystore, validate_manifest, validate_signer
from release_version import parse_tag


class ReleasePackageTest(unittest.TestCase):
    def setUp(self):
        self.version = parse_tag("v1.2.3")
        self.manifest = "package: name='com.kudo.android' versionCode='1002004' versionName='1.2.3'\nsdkVersion:'26'\nnative-code: 'arm64-v8a'\n"
        self.certificate = b"test-only public certificate"
        self.fingerprint = hashlib.sha256(self.certificate).hexdigest()
        self.signer = f"Signer #1 certificate SHA-256 digest: {self.fingerprint}\n"

    def test_manifest_accepts_the_expected_release(self):
        validate_manifest(self.manifest, self.version)

    def test_manifest_rejects_debug_wrong_package_and_wrong_versions(self):
        for report in (
            "", self.manifest + "application-debuggable\n",
            self.manifest.replace("com.kudo.android", "com.other.app"),
            self.manifest.replace("1002004", "1"), self.manifest.replace("1.2.3", "1.2.4"),
        ):
            with self.subTest(report=report), self.assertRaises(ValueError):
                validate_manifest(report, self.version)

    def test_manifest_rejects_missing_or_non_arm64_native_code(self):
        for native_code in ("", "native-code: 'armeabi-v7a'", "native-code: 'arm64-v8a' 'x86_64'"):
            report = self.manifest.replace("native-code: 'arm64-v8a'", native_code)
            with self.subTest(native_code=native_code), self.assertRaises(ValueError):
                validate_manifest(report, self.version)

    def test_signer_must_be_exactly_the_expected_certificate(self):
        validate_signer(self.signer, self.fingerprint.upper())
        for report in ("", self.signer.replace(self.fingerprint, "0" * 64), self.signer * 2):
            with self.subTest(report=report), self.assertRaises(ValueError):
                validate_signer(report, self.fingerprint)

    def test_base64_restore_is_private_and_never_overwrites(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "signing" / "release.p12"
            encoded = base64.b64encode(b"test-only key bytes").decode("ascii")
            restore_keystore(f"\n{encoded}\n", target)
            self.assertEqual(b"test-only key bytes", target.read_bytes())
            with self.assertRaises(FileExistsError):
                restore_keystore(base64.b64encode(b"replacement").decode("ascii"), target)
            self.assertEqual(b"test-only key bytes", target.read_bytes())
            if os.name != "nt":
                self.assertEqual(0o600, target.stat().st_mode & 0o777)

    def test_invalid_base64_does_not_create_a_key_file(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "release.p12"
            for value in ("", "  \n", "not base64!", "YWJj$", "a"):
                with self.subTest(value=value), self.assertRaises(ValueError):
                    restore_keystore(value, target)
                self.assertFalse(target.exists())

    def test_password_is_passed_through_environment_not_command_arguments(self):
        values = {"RELEASE_KEYSTORE_PATH": "test.p12", "RELEASE_KEY_ALIAS": "test", "RELEASE_STORE_PASSWORD": "test-password-not-for-argv"}
        with patch.dict(os.environ, values), patch("prepare_release.keytool_path", return_value="keytool"), patch("prepare_release.run_tool", return_value=self.certificate) as run:
            self.assertEqual(self.certificate, expected_certificate())
            arguments = run.call_args.args[0]
            self.assertNotIn(values["RELEASE_STORE_PASSWORD"], arguments)
            self.assertEqual(["-storepass:env", "RELEASE_STORE_PASSWORD"], arguments[-2:])

    def test_only_verified_bytes_and_matching_checksum_are_packaged(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            apk = root / "input.apk"
            apk.write_bytes(b"test-only APK bytes")
            with patch("prepare_release.expected_certificate", return_value=self.certificate), patch("prepare_release.run_tool", side_effect=[self.signer.encode(), self.manifest.encode()]):
                output, checksum = package_release(apk, self.version, root / "dist", root / "tools")
            self.assertEqual(apk.read_bytes(), output.read_bytes())
            self.assertEqual(
                f"{hashlib.sha256(apk.read_bytes()).hexdigest()}  {self.version.apk_name}\n",
                checksum.read_text(encoding="ascii"),
            )
            self.assertEqual({self.version.apk_name, f"{self.version.apk_name}.sha256"}, {path.name for path in output.parent.iterdir()})

    def test_failed_signature_or_version_check_creates_no_publication_files(self):
        for reports in ([b"wrong signer"], [self.signer.encode(), b"wrong manifest"]):
            with self.subTest(reports=reports), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                with patch("prepare_release.expected_certificate", return_value=self.certificate), patch("prepare_release.run_tool", side_effect=reports):
                    with self.assertRaises(ValueError):
                        package_release(root / "input.apk", self.version, root / "dist", root / "tools")
                self.assertFalse((root / "dist").exists())


if __name__ == "__main__":
    unittest.main()
