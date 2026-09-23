#!/usr/bin/env python3
"""Print the download URL of a JDK wheel published on PyPI.

Used by tools/setup-toolchain.sh: `jdk4py` publishes Temurin runtime builds as
wheels, which is the one place a JDK can be fetched from inside a sandbox that
cannot reach the usual JDK download hosts.
"""
import json
import sys
import urllib.request


def main() -> int:
    name, version = sys.argv[1], sys.argv[2]
    with urllib.request.urlopen(f"https://pypi.org/pypi/{name}/{version}/json") as fh:
        data = json.load(fh)
    matches = [
        f for f in data["urls"]
        if f["filename"].endswith(".whl")
        and "linux" in f["filename"]
        and "x86_64" in f["filename"]
    ]
    if not matches:
        print(f"no linux x86_64 wheel for {name} {version}", file=sys.stderr)
        return 1
    print(matches[0]["url"])
    return 0


if __name__ == "__main__":
    sys.exit(main())
