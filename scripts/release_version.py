"""Map a stable vMAJOR.MINOR.PATCH tag to reproducible Android version values."""

import argparse
from dataclasses import dataclass
from pathlib import Path
import re


RELEASE_ABI = "arm64-v8a"


@dataclass(frozen=True)
class ReleaseVersion:
    name: str
    code: int

    @property
    def apk_name(self) -> str:
        return f"hudo_{self.name}_release_{RELEASE_ABI}.apk"


def parse_tag(tag: str) -> ReleaseVersion:
    match = re.fullmatch(r"v([0-9])\.([0-9])\.([0-9])", tag)
    if not match:
        raise ValueError("Use vX.Y.Z with each component from 0 to 9 and no suffixes.")
    major, minor, patch = map(int, match.groups())
    code = major * 1_000_000 + minor * 1_000 + patch + 1
    if code > 2_100_000_000:
        raise ValueError("The tag exceeds Android's maximum versionCode (2100000000).")
    return ReleaseVersion(tag[1:], code)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("tag")
    parser.add_argument("--github-output", type=Path)
    arguments = parser.parse_args()
    try:
        version = parse_tag(arguments.tag)
        if arguments.github_output:
            with arguments.github_output.open("a", encoding="ascii", newline="\n") as output:
                output.write(f"version_name={version.name}\nversion_code={version.code}\napk_name={version.apk_name}\n")
    except (ValueError, OSError) as error:
        parser.exit(1, f"{error}\n")
    print(f"{arguments.tag}: versionName={version.name}, versionCode={version.code}")


if __name__ == "__main__":
    main()
