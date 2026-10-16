"""Collect complete license/notice texts for the locked Android Rust dependency graph."""
import argparse
import json
from pathlib import Path
import subprocess


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    repo = Path(__file__).resolve().parent.parent
    data = json.loads(subprocess.check_output(["cargo", "metadata", "--locked", "--offline", "--manifest-path",
        str(repo / "native/doh/Cargo.toml"), "--filter-platform", "aarch64-linux-android", "--format-version", "1"], text=True, encoding="utf-8"))
    nodes = {n["id"]: n for n in data["resolve"]["nodes"]}
    root = data["resolve"]["root"]
    pending, reached = [root], set()
    while pending:
        item = pending.pop()
        if item in reached:
            continue
        reached.add(item)
        pending.extend(d["pkg"] for d in nodes[item]["deps"])
    packages = sorted((p for p in data["packages"] if p["id"] in reached and p["id"] != root), key=lambda p: (p["name"], p["version"]))
    parts = ["Kudo native DoH / ECH third-party notices\n\n"
             "Generated from native/doh/Cargo.lock for aarch64-linux-android.\n"
             "Includes associated source/build dependencies; not every crate is linked at runtime.\n"
             "Each component retains its own copyright and license.\n\n"]
    for package in packages:
        folder = Path(package["manifest_path"]).parent
        files = [p for p in folder.iterdir() if p.is_file() and p.name.upper().startswith(("LICENSE", "COPYING", "COPYRIGHT", "NOTICE"))]
        if package.get("license_file"):
            files.append(folder / package["license_file"])
        if package["name"] == "aws-lc-sys":
            files.extend([folder / "aws-lc/LICENSE", folder / "aws-lc/third_party/fiat/LICENSE"])
        if package["name"] == "jni-sys-macros" and package["version"] == "0.4.1":
            # The crate omits its workspace licenses. Copies come from its .cargo_vcs_info commit:
            # https://github.com/jni-rs/jni-sys/tree/64d77b7a5f119d7b55b4e2c169a4668067ff59e6
            files.extend((repo / "native/doh/licenses").glob("jni-sys-macros-0.4.1-LICENSE-*"))
        if not files:
            raise RuntimeError("Missing license texts: " + package["name"])
        parts.append("=" * 72 + f"\n{package['name']} {package['version']}\nLicense: {package.get('license') or 'See text below'}\n"
                     f"Source: https://crates.io/crates/{package['name']}/{package['version']}\n")
        seen = set()
        def file_label(path):
            return path.relative_to(folder).as_posix() if path.is_relative_to(folder) else path.name
        for path in sorted(set(files), key=lambda path: (file_label(path).casefold(), file_label(path))):
            text = path.read_text(encoding="utf-8")
            if text in seen:
                continue
            seen.add(text)
            label = file_label(path)
            parts.append(f"\n--- {label} ---\n{text.rstrip()}\n")
        parts.append("\n")
    output = repo / "app/src/main/assets/licenses/native-doh-NOTICES.txt"
    content = "".join(parts).encode("utf-8")
    if args.check:
        if not output.is_file() or output.read_text(encoding="utf-8").encode("utf-8") != content:
            raise RuntimeError("Native notices are stale; run python scripts/native_notices.py")
    else:
        output.write_bytes(content)
    print(f"Native notices: {len(packages)} components, {len(content)} bytes")


if __name__ == "__main__":
    main()
