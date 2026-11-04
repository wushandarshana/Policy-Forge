"""STUB for the TDD red step: fetch_tools.py is implemented in the feat(green) commit that follows."""
import sys
import urllib.request  # noqa: F401  (the tests patch urllib.request.urlopen through this module)

MANIFEST = {}


class FetchError(Exception):
    pass


def detect_platform(system, machine):
    return None


def ssl_context():
    return None


def download_file(url, target):
    return None


def extract_member(archive, fmt, executable, target):
    return None


def fetch(tool, platform=None, dest=None, manifest=None, download=None):
    return None


def main(argv=None, manifest=None, download=None):
    return 0


if __name__ == "__main__":
    sys.exit(main())
