"""Create private Android signing material once; never replace an existing identity."""

import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import tempfile


def keytool_path() -> str:
    executable = "keytool.exe" if os.name == "nt" else "keytool"
    java_home = os.environ.get("JAVA_HOME")
    candidate = Path(java_home, "bin", executable) if java_home else None
    if candidate and candidate.is_file():
        return str(candidate)
    found = shutil.which(executable)
    if not found:
        raise ValueError("JDK keytool was not found. Set JAVA_HOME to your JDK.")
    return found


def run_keytool(tool: str, arguments: list[str], password: str) -> bytes:
    environment = os.environ.copy()
    environment["KUDO_KEYTOOL_PASSWORD"] = password
    result = subprocess.run(
        [tool, *arguments, "-storepass:env", "KUDO_KEYTOOL_PASSWORD"],
        env=environment, capture_output=True, check=False,
    )
    if result.returncode:
        # Do not echo command output: keytool may include sensitive configuration.
        raise ValueError(f"keytool failed (exit {result.returncode}); no signing configuration was published.")
    return result.stdout


def write_new(path: Path, content: bytes, mode: int = 0o600) -> None:
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, mode)
    with os.fdopen(descriptor, "wb") as stream:
        stream.write(content)


def generate(root: Path) -> list[dict[str, str]]:
    root = root.resolve()
    signing = root / "signing"
    configuration = root / "signing.properties"
    if configuration.exists() or signing.exists():
        raise ValueError("Signing files already exist. Refusing to overwrite private keys or passwords.")
    tool = keytool_path()
    signing.mkdir(mode=0o700)
    properties = ["# Private signing configuration. Back up this file together with signing/.\n"]
    certificates = []
    with tempfile.TemporaryDirectory(prefix=".generating-", dir=signing) as temporary:
        staging = Path(temporary).resolve()
        # TemporaryDirectory only cleans up this freshly created directory under signing/.
        if not staging.is_relative_to(signing.resolve()):
            raise ValueError("Unexpected signing staging directory.")
        for variant in ("debug", "release"):
            alias = f"kudo-{variant}"
            password = secrets.token_urlsafe(36)
            keystore = staging / f"{alias}.p12"
            run_keytool(tool, [
                "-genkeypair", "-noprompt", "-storetype", "PKCS12",
                "-keystore", str(keystore), "-alias", alias,
                "-keyalg", "RSA", "-keysize", "4096", "-sigalg", "SHA256withRSA",
                "-validity", "10000", "-dname", f"CN=Kudo {variant.title()},O=Kudo,OU=Android",
                "-keypass:env", "KUDO_KEYTOOL_PASSWORD",
            ], password)
            certificate = run_keytool(tool, ["-exportcert", "-keystore", str(keystore), "-alias", alias], password)
            encoded = base64.b64encode(certificate).decode("ascii")
            pem = "-----BEGIN CERTIFICATE-----\n" + "\n".join(encoded[index:index + 64] for index in range(0, len(encoded), 64)) + "\n-----END CERTIFICATE-----\n"
            (staging / f"{alias}.pem").write_text(pem, encoding="ascii")
            certificates.append({"variant": variant, "alias": alias, "sha256": hashlib.sha256(certificate).hexdigest()})
            properties.extend([
                f"{variant}.storeFile=signing/{alias}.p12\n",
                f"{variant}.storePassword={password}\n",
                f"{variant}.keyAlias={alias}\n",
                f"{variant}.keyPassword={password}\n",
                f"{variant}.storeType=PKCS12\n\n",
            ])
        for source in staging.iterdir():
            write_new(signing / source.name, source.read_bytes())
        write_new(signing / "certificates.json", (json.dumps(certificates, indent=2) + "\n").encode("utf-8"))
        write_new(configuration, "".join(properties).encode("ascii"))
    return certificates


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1], help="Repository root")
    arguments = parser.parse_args()
    try:
        certificates = generate(arguments.root)
    except (ValueError, OSError) as error:
        parser.exit(1, f"{error}\n")
    for certificate in certificates:
        print(f"{certificate['variant']}: SHA256 {certificate['sha256']}")
    print("Created signing/ and signing.properties. Keep a private backup of both; do not commit them.")


if __name__ == "__main__":
    main()
