"""Restore CI signing material and package an APK only after signature/version checks."""

import argparse
import base64
import binascii
import hashlib
import os
from pathlib import Path
import re
import subprocess

from generate_signing import keytool_path, write_new
from release_version import RELEASE_ABI, ReleaseVersion, parse_tag


def required_environment(name: str) -> str:
    value = os.environ.get(name)
    if not value or not value.strip():
        raise ValueError(f"{name} is required.")
    return value


def restore_keystore(encoded: str, target: Path) -> None:
    try:
        content = base64.b64decode("".join(encoded.split()), validate=True)
    except (ValueError, binascii.Error):
        raise ValueError("RELEASE_KEYSTORE_BASE64 is not valid base64.") from None
    if not content:
        raise ValueError("RELEASE_KEYSTORE_BASE64 is empty.")
    target.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    write_new(target, content)


def run_tool(arguments: list[str], failure: str) -> bytes:
    result = subprocess.run(arguments, capture_output=True, check=False)
    if result.returncode:
        # Tool output is deliberately omitted: signing credentials must never reach logs.
        raise ValueError(failure)
    return result.stdout


def expected_certificate() -> bytes:
    keystore = required_environment("RELEASE_KEYSTORE_PATH")
    alias = required_environment("RELEASE_KEY_ALIAS")
    required_environment("RELEASE_STORE_PASSWORD")
    certificate = run_tool([
        keytool_path(), "-exportcert", "-keystore", keystore,
        "-storetype", os.environ.get("RELEASE_KEYSTORE_TYPE", "PKCS12"),
        "-alias", alias, "-storepass:env", "RELEASE_STORE_PASSWORD",
    ], "Could not export the expected release certificate. Check the keystore credentials.")
    if not certificate:
        raise ValueError("The release certificate is empty.")
    return certificate


def validate_signer(report: str, expected_sha256: str) -> None:
    signers = re.findall(r"^Signer #[0-9]+ certificate SHA-256 digest: ([0-9a-fA-F]{64})\s*$", report, re.MULTILINE)
    if [signer.lower() for signer in signers] != [expected_sha256.lower()]:
        raise ValueError(f"APK signer does not match the single expected release certificate. "
            f"actual={signers} expected={expected_sha256}")


def validate_manifest(report: str, version: ReleaseVersion) -> None:
    package = next((line for line in report.splitlines() if line.startswith("package: ")), "")
    attributes = dict(re.findall(r"([a-zA-Z][a-zA-Z0-9]*)='([^']*)'", package))
    expected = {"name": "com.huzai.kudo", "versionCode": str(version.code), "versionName": version.name}
    if any(attributes.get(key) != value for key, value in expected.items()):
        raise ValueError("APK package/version does not match com.huzai.kudo and the release tag.")
    if re.search(r"^application-debuggable(?:\s|$)", report, re.MULTILINE):
        raise ValueError("Refusing to publish a debuggable APK.")
    native_abis = [
        abi
        for line in re.findall(r"^native-code:\s*(.*)$", report, re.MULTILINE)
        for abi in re.findall(r"'([^']+)'", line)
    ]
    if native_abis != [RELEASE_ABI]:
        raise ValueError(f"APK native ABI must be exactly {RELEASE_ABI}.")


def package_release(apk: Path, version: ReleaseVersion, destination: Path, build_tools: Path) -> tuple[Path, Path]:
    expected_sha256 = hashlib.sha256(expected_certificate()).hexdigest()
    signer_tool = build_tools / ("apksigner.bat" if os.name == "nt" else "apksigner")
    manifest_tool = build_tools / ("aapt.exe" if os.name == "nt" else "aapt")
    signer_report = run_tool([
        str(signer_tool), "verify", "--verbose", "--print-certs", str(apk),
    ], "APK signature verification failed.").decode("utf-8", errors="replace")
    validate_signer(signer_report, expected_sha256)
    manifest_report = run_tool([
        str(manifest_tool), "dump", "badging", str(apk),
    ], "Could not read the APK manifest.").decode("utf-8", errors="replace")
    validate_manifest(manifest_report, version)

    content = apk.read_bytes()
    destination.mkdir(parents=True, exist_ok=True)
    published_apk = destination / version.apk_name
    checksum = destination / f"{version.apk_name}.sha256"
    published_apk.write_bytes(content)
    checksum.write_text(f"{hashlib.sha256(content).hexdigest()}  {version.apk_name}\n", encoding="ascii", newline="\n")
    print(f"Verified release signer SHA256: {expected_sha256}")
    return published_apk, checksum


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    restore = commands.add_parser("restore", help="Decode RELEASE_KEYSTORE_BASE64 into a private temporary file")
    restore.add_argument("--output", type=Path, required=True)
    package = commands.add_parser("package", help="Verify and copy the APK and SHA256 into a publication directory")
    package.add_argument("--apk", type=Path, required=True)
    package.add_argument("--tag", required=True)
    package.add_argument("--output-dir", type=Path, required=True)
    package.add_argument("--build-tools", type=Path, required=True)
    arguments = parser.parse_args()
    try:
        if arguments.command == "restore":
            restore_keystore(required_environment("RELEASE_KEYSTORE_BASE64"), arguments.output)
            print("Release keystore restored to the private temporary file.")
        else:
            version = parse_tag(arguments.tag)
            apk, checksum = package_release(arguments.apk, version, arguments.output_dir, arguments.build_tools)
            print(f"Prepared {apk.name} and {checksum.name}")
    except (ValueError, OSError) as error:
        parser.exit(1, f"{error}\n")


if __name__ == "__main__":
    main()
