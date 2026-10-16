"""Validate the repository's Conventional Commit subject format."""

from pathlib import Path
import re
import sys


SUBJECT = re.compile(
    r"^(?:feat|fix|perf|doc|docs|test|build|ci|chore|refactor|style)"
    r"(?:\([^()]+\))?!?:\s*\S.*$"
)


def first_subject(message: str) -> str:
    return next((line.strip() for line in message.splitlines() if line.strip() and not line.startswith("#")), "")


def validate(message: str) -> tuple[bool, str]:
    subject = first_subject(message)
    if subject.startswith(("Merge ", "合并分支")):
        return True, subject
    if SUBJECT.fullmatch(subject):
        return True, subject
    return False, subject


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: check_commit_message.py COMMIT_EDITMSG", file=sys.stderr)
        return 2
    path = Path(sys.argv[1])
    try:
        message = path.read_text(encoding="utf-8")
    except OSError as error:
        print(f"cannot read commit message: {error}", file=sys.stderr)
        return 2
    valid, subject = validate(message)
    if valid:
        return 0
    print(
        "Invalid commit subject. Use feat:, fix:, perf:, doc:, docs:, test:, build:, ci:, "
        f"chore:, refactor: or style: (subject: {subject!r})",
        file=sys.stderr,
    )
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
