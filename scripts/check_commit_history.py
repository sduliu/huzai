"""Validate every commit subject in a Git revision range."""

from __future__ import annotations

import argparse
from pathlib import Path
import subprocess

from check_commit_message import validate


def parse_records(output: str) -> list[tuple[str, str]]:
    records: list[tuple[str, str]] = []
    for record in output.splitlines():
        if "\x00" not in record:
            continue
        commit, subject = record.split("\x00", 1)
        records.append((commit, subject))
    return records


def invalid_records(records: list[tuple[str, str]]) -> list[tuple[str, str]]:
    return [(commit, subject) for commit, subject in records if not validate(subject)[0]]


def read_records(revision: str, cwd: Path | None = None) -> list[tuple[str, str]]:
    result = subprocess.run(
        ["git", "log", "--format=%H%x00%s", revision],
        cwd=cwd,
        capture_output=True,
        encoding="utf-8",
        check=False,
    )
    if result.returncode:
        raise ValueError(f"无法读取提交范围：{revision}")
    return parse_records(result.stdout)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("revision", help="Git revision range, for example BASE..HEAD")
    arguments = parser.parse_args()
    try:
        records = read_records(arguments.revision)
        invalid = invalid_records(records)
    except ValueError as error:
        parser.exit(2, f"{error}\n")
    if invalid:
        print("Invalid commit subjects:")
        for commit, subject in invalid:
            print(f"- {commit[:12]} {subject}")
        return 1
    print(f"ok: checked {len(records)} commit(s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
