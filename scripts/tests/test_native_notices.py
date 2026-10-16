import contextlib
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import native_notices


class CaseSensitivePath(type(Path())):
    def __lt__(self, other):
        return self.as_posix() < other.as_posix()


class CaseInsensitivePath(type(Path())):
    def __lt__(self, other):
        return self.as_posix().casefold() < other.as_posix().casefold()


class NativeNoticesTest(unittest.TestCase):
    def test_identical_licenses_generate_identical_bytes_with_windows_and_posix_path_ordering(self):
        with tempfile.TemporaryDirectory() as temporary:
            repo = Path(temporary)
            folder = repo / "registry/aws-lc-sys"
            for name, text in [("LICENSE", "Root license"), ("aws-lc/LICENSE", "AWS license"),
                               ("aws-lc/third_party/fiat/LICENSE", "Fiat license")]:
                target = folder / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(text + "\n", encoding="utf-8")
            output = repo / "app/src/main/assets/licenses/native-doh-NOTICES.txt"
            output.parent.mkdir(parents=True)
            metadata = {
                "resolve": {"root": "app", "nodes": [{"id": "app", "deps": [{"pkg": "aws"}]}, {"id": "aws", "deps": []}]},
                "packages": [{"id": "aws", "name": "aws-lc-sys", "version": "1.0.0", "license": "ISC",
                              "manifest_path": str(folder / "Cargo.toml")}],
            }
            results = []
            for path_type in (CaseInsensitivePath, CaseSensitivePath):
                with patch.object(native_notices, "Path", path_type), \
                     patch.object(native_notices, "__file__", str(repo / "scripts/native_notices.py")), \
                     patch.object(native_notices.subprocess, "check_output", return_value=json.dumps(metadata)), \
                     patch.object(sys, "argv", ["native_notices.py"]), contextlib.redirect_stdout(io.StringIO()):
                    native_notices.main()
                results.append(output.read_bytes())
            self.assertEqual(results[0], results[1], "许可证内容不能依赖 Windows/Linux 的路径排序规则")
            for text in (b"Root license", b"AWS license", b"Fiat license"):
                self.assertIn(text, results[0])


if __name__ == "__main__":
    unittest.main()
