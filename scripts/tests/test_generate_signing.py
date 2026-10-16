import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from generate_signing import generate, write_new


class SigningProtectionTest(unittest.TestCase):
    def test_existing_keys_and_configuration_are_never_replaced(self):
        for existing in ("signing", "signing.properties"):
            with self.subTest(existing=existing), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                path = root / existing
                if existing == "signing":
                    path.mkdir()
                    path = path / "release.p12"
                path.write_bytes(b"existing private material")
                with patch("generate_signing.keytool_path") as lookup:
                    with self.assertRaisesRegex(ValueError, "Refusing to overwrite"):
                        generate(root)
                    lookup.assert_not_called()
                self.assertEqual(b"existing private material", path.read_bytes())

    def test_missing_jdk_does_not_leave_a_half_initialized_signing_directory(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with patch("generate_signing.keytool_path", side_effect=ValueError("missing JDK")):
                with self.assertRaisesRegex(ValueError, "missing JDK"):
                    generate(root)
            self.assertEqual([], list(root.iterdir()))

    def test_exclusive_write_preserves_existing_contents(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "private.properties"
            write_new(path, b"original")
            with self.assertRaises(FileExistsError):
                write_new(path, b"replacement")
            self.assertEqual(b"original", path.read_bytes())
            if os.name != "nt":
                self.assertEqual(0o600, path.stat().st_mode & 0o777)


if __name__ == "__main__":
    unittest.main()
