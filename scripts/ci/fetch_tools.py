#!/usr/bin/env python3
"""Download actionlint and shellcheck at pinned versions, verify their SHA-256 and print the tool path.

This is the only place where CI tools are downloaded (NFR-09.2, NFR-09.6). For each tool the manifest below fixes
the exact release URL and the SHA-256 of the archive, for linux-x86_64 and windows-x86_64.

Behaviour:
  * the download goes to `.ci-tools/downloads/` over https with TLS verification on (no way to switch it off);
  * the SHA-256 is checked BEFORE anything is extracted; on a mismatch the download is deleted, nothing is
    extracted and the exit status is non-zero;
  * only the named executable is read out of the archive, by exact file name, never by archive path, so a hostile
    archive cannot write anywhere else;
  * the archive is deleted after extraction; a marker file records the archive and executable digests, and a later
    run reuses the extracted tool only if both still match;
  * stdout carries nothing but the absolute path of the executable.

Usage:
    python scripts/ci/fetch_tools.py actionlint
    python scripts/ci/fetch_tools.py shellcheck [--platform linux-x86_64] [--dest .ci-tools]

Standard library only; runs on Python 3.11 or later.
"""
import argparse
import hashlib
import json
import platform as platform_module
import shutil
import ssl
import sys
import tarfile
import urllib.request
import zipfile
from pathlib import Path, PurePosixPath

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_DEST = ROOT / ".ci-tools"
MAX_DOWNLOAD_BYTES = 200 * 1024 * 1024
MAX_EXECUTABLE_BYTES = 200 * 1024 * 1024
CHUNK = 1024 * 1024
MARKER = ".verified.json"

ACTIONLINT_BASE = "https://github.com/rhysd/actionlint/releases/download/v1.7.12"
SHELLCHECK_BASE = "https://github.com/koalaman/shellcheck/releases/download/v0.11.0"

# Pins of the Sprint 5 plan (C3), verified on 2026-11-04. A change here is a deliberate, reviewed supply-chain change.
MANIFEST = {
    "actionlint": {
        "version": "1.7.12",
        "platforms": {
            "linux-x86_64": {
                "url": f"{ACTIONLINT_BASE}/actionlint_1.7.12_linux_amd64.tar.gz",
                "sha256": "8aca8db96f1b94770f1b0d72b6dddcb1ebb8123cb3712530b08cc387b349a3d8",
                "format": "tar.gz",
                "executable": "actionlint",
            },
            "windows-x86_64": {
                "url": f"{ACTIONLINT_BASE}/actionlint_1.7.12_windows_amd64.zip",
                "sha256": "6e7241b51e6817ea6a047693d8e6fed13b31819c9a0dd6c5a726e1592d22f6e9",
                "format": "zip",
                "executable": "actionlint.exe",
            },
        },
    },
    "shellcheck": {
        "version": "v0.11.0",
        "platforms": {
            "linux-x86_64": {
                "url": f"{SHELLCHECK_BASE}/shellcheck-v0.11.0.linux.x86_64.tar.xz",
                "sha256": "8c3be12b05d5c177a04c29e3c78ce89ac86f1595681cab149b65b97c4e227198",
                "format": "tar.xz",
                "executable": "shellcheck",
            },
            "windows-x86_64": {
                "url": f"{SHELLCHECK_BASE}/shellcheck-v0.11.0.zip",
                "sha256": "8a4e35ab0b331c85d73567b12f2a444df187f483e5079ceffa6bda1faa2e740e",
                "format": "zip",
                "executable": "shellcheck.exe",
            },
        },
    },
}


class FetchError(Exception):
    """A tool could not be fetched or verified. The message is safe to print."""


def detect_platform(system=None, machine=None):
    """Map the running OS and CPU to a manifest platform key."""
    system = (system or platform_module.system()).lower()
    machine = (machine or platform_module.machine()).lower()
    if machine in ("x86_64", "amd64", "x64"):
        if system == "linux":
            return "linux-x86_64"
        if system == "windows":
            return "windows-x86_64"
    raise FetchError(f"unsupported platform {system}/{machine}; the manifest has linux-x86_64 and windows-x86_64")


def ssl_context():
    """The default verifying context: certificate chain and host name are always checked."""
    return ssl.create_default_context()


def sha256_file(path):
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(CHUNK), b""):
            digest.update(chunk)
    return digest.hexdigest()


def download_file(url, target):
    """Download `url` (https only) to `target`, never more than MAX_DOWNLOAD_BYTES."""
    if not url.startswith("https://"):
        raise FetchError("refusing to download a non-https URL")
    target = Path(target)
    target.parent.mkdir(parents=True, exist_ok=True)
    total = 0
    try:
        with urllib.request.urlopen(url, timeout=120, context=ssl_context()) as response, open(target, "wb") as out:
            while True:
                chunk = response.read(CHUNK)
                if not chunk:
                    break
                total += len(chunk)
                if total > MAX_DOWNLOAD_BYTES:
                    raise FetchError("download is larger than the allowed maximum")
                out.write(chunk)
    except OSError as exc:  # URLError, HTTPError, ssl.SSLError and socket errors are all OSError subclasses
        target.unlink(missing_ok=True)
        raise FetchError(f"download failed: {type(exc).__name__}: {exc}") from exc
    except FetchError:
        target.unlink(missing_ok=True)
        raise


def extract_member(archive, fmt, executable, target):
    """Write the archive member whose file name is exactly `executable` to `target` (mode 0755)."""
    data = None
    if fmt == "zip":
        with zipfile.ZipFile(archive) as bundle:
            for info in bundle.infolist():
                if not info.is_dir() and PurePosixPath(info.filename.replace("\\", "/")).name == executable:
                    if info.file_size > MAX_EXECUTABLE_BYTES:
                        raise FetchError("the executable in the archive is unreasonably large")
                    data = bundle.read(info)
                    break
    elif fmt in ("tar.gz", "tar.xz"):
        with tarfile.open(archive, "r:*") as bundle:
            for member in bundle:
                if member.isfile() and PurePosixPath(member.name).name == executable:
                    if member.size > MAX_EXECUTABLE_BYTES:
                        raise FetchError("the executable in the archive is unreasonably large")
                    data = bundle.extractfile(member).read()
                    break
    else:
        raise FetchError(f"unsupported archive format {fmt}")
    if data is None:
        raise FetchError(f"the archive has no file named {executable}")
    target = Path(target)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(data)
    target.chmod(0o755)


def _select(manifest, tool, platform):
    if tool not in manifest:
        raise FetchError(f"unknown tool {tool}; known tools: {', '.join(sorted(manifest))}")
    platforms = manifest[tool]["platforms"]
    if platform not in platforms:
        raise FetchError(f"{tool} has no entry for platform {platform}; known: {', '.join(sorted(platforms))}")
    return manifest[tool], platforms[platform]


def _reusable(install_dir, executable, expected_archive_sha):
    """True when a previously verified copy is intact: marker, archive digest and executable digest all match."""
    exe = install_dir / executable
    marker = install_dir / MARKER
    try:
        recorded = json.loads(marker.read_text(encoding="utf-8"))
        return (exe.is_file() and recorded.get("archive_sha256") == expected_archive_sha
                and recorded.get("executable_sha256") == sha256_file(exe))
    except (OSError, ValueError, AttributeError):
        return False


def fetch(tool, platform=None, dest=None, manifest=None, download=None):
    """Return the path of the verified `tool` executable, downloading it first when needed."""
    manifest = MANIFEST if manifest is None else manifest
    download = download_file if download is None else download
    platform = platform or detect_platform()
    entry, spec = _select(manifest, tool, platform)
    dest = Path(dest) if dest is not None else DEFAULT_DEST
    executable = spec["executable"]
    install_dir = dest / f"{tool}-{entry['version']}-{platform}"
    if _reusable(install_dir, executable, spec["sha256"]):
        return (install_dir / executable).absolute()

    shutil.rmtree(install_dir, ignore_errors=True)
    archive = dest / "downloads" / PurePosixPath(spec["url"]).name
    archive.parent.mkdir(parents=True, exist_ok=True)
    try:
        download(spec["url"], archive)
        actual = sha256_file(archive)
        if actual != spec["sha256"]:
            raise FetchError(f"checksum mismatch for {tool}: expected sha256 {spec['sha256']}, got {actual}")
        extract_member(archive, spec["format"], executable, install_dir / executable)
    except BaseException:
        shutil.rmtree(install_dir, ignore_errors=True)
        raise
    finally:
        archive.unlink(missing_ok=True)
        try:
            archive.parent.rmdir()
        except OSError:
            pass
    exe = install_dir / executable
    (install_dir / MARKER).write_text(json.dumps({"archive_sha256": spec["sha256"], "url": spec["url"],
                                                  "executable_sha256": sha256_file(exe)}, indent=2) + "\n",
                                      encoding="utf-8")
    return exe.absolute()


def main(argv=None, manifest=None, download=None):
    parser = argparse.ArgumentParser(description="Fetch a pinned, checksum-verified CI tool and print its path.")
    parser.add_argument("tool", help="actionlint or shellcheck")
    parser.add_argument("--platform", help="linux-x86_64 or windows-x86_64 (default: this machine)")
    parser.add_argument("--dest", help="download and install directory (default: .ci-tools in the repository)")
    args = parser.parse_args(argv)
    try:
        path = fetch(args.tool, args.platform, args.dest, manifest, download)
    except FetchError as exc:
        print(f"fetch_tools: {exc}", file=sys.stderr)
        # an unknown tool or platform is a usage error (2); everything else is a failed fetch (1)
        return 2 if str(exc).startswith(("unknown tool", f"{args.tool} has no entry", "unsupported")) else 1
    print(path)
    return 0


if __name__ == "__main__":
    sys.exit(main())
