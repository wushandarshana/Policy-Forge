"""Tests for scripts/ci/fetch_tools.py: the pinned, checksum-verified actionlint and shellcheck fetcher.

Archives are built locally and handed to the fetcher through its `download` hook, so no network is used.
"""
import contextlib
import hashlib
import io
import os
import ssl
import sys
import tarfile
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ci_test_support import CI_DIR, load_script  # noqa: E402

FAKE_EXE = b"#!/bin/sh\necho fake tool\n"
GITHUB = "https://github.com"
# The pins of plan C3, written out again here so that a change to the manifest has to change a test too.
EXPECTED = {
    "actionlint": {
        "version": "1.7.12",
        "linux-x86_64": (f"{GITHUB}/rhysd/actionlint/releases/download/v1.7.12/actionlint_1.7.12_linux_amd64.tar.gz",
                         "8aca8db96f1b94770f1b0d72b6dddcb1ebb8123cb3712530b08cc387b349a3d8"),
        "windows-x86_64": (f"{GITHUB}/rhysd/actionlint/releases/download/v1.7.12/actionlint_1.7.12_windows_amd64.zip",
                           "6e7241b51e6817ea6a047693d8e6fed13b31819c9a0dd6c5a726e1592d22f6e9"),
    },
    "shellcheck": {
        "version": "v0.11.0",
        "linux-x86_64": (f"{GITHUB}/koalaman/shellcheck/releases/download/v0.11.0/"
                         "shellcheck-v0.11.0.linux.x86_64.tar.xz",
                         "8c3be12b05d5c177a04c29e3c78ce89ac86f1595681cab149b65b97c4e227198"),
        "windows-x86_64": (f"{GITHUB}/koalaman/shellcheck/releases/download/v0.11.0/shellcheck-v0.11.0.zip",
                           "8a4e35ab0b331c85d73567b12f2a444df187f483e5079ceffa6bda1faa2e740e"),
    },
}


def sha256_of(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


class FetchToolsTestCase(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.fetch_tools = load_script("fetch_tools")

    def setUp(self):
        work = tempfile.TemporaryDirectory(prefix="pf-ci-fetch-")
        self.addCleanup(work.cleanup)
        self.work = Path(work.name)
        self.dest = self.work / "ci-tools"
        self.downloads = []

    def make_tar(self, name, members, mode="w:gz"):
        path = self.work / name
        with tarfile.open(path, mode) as archive:
            for member, data in members.items():
                info = tarfile.TarInfo(member)
                info.size = len(data)
                archive.addfile(info, io.BytesIO(data))
        return path

    def make_zip(self, name, members):
        path = self.work / name
        with zipfile.ZipFile(path, "w") as archive:
            for member, data in members.items():
                archive.writestr(member, data)
        return path

    def manifest_for(self, archive, executable="actionlint", fmt="tar.gz", sha=None, platform="linux-x86_64"):
        return {"actionlint": {"version": "1.7.12", "platforms": {platform: {
            "url": "https://example.invalid/" + archive.name, "sha256": sha or sha256_of(archive),
            "format": fmt, "executable": executable}}}}

    def downloader(self, archive):
        def download(url, target):
            self.downloads.append(url)
            Path(target).write_bytes(Path(archive).read_bytes())
        return download

    def run_main(self, argv, manifest, archive):
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = self.fetch_tools.main(argv, manifest=manifest, download=self.downloader(archive))
        return code, out.getvalue(), err.getvalue()

    def files_under(self, root):
        return [p for p in Path(root).rglob("*") if p.is_file()] if Path(root).exists() else []


class ManifestTests(FetchToolsTestCase):
    def test_manifest_holds_the_c3_urls_and_sha256_values(self):
        """NFR-09.6: the manifest pins actionlint 1.7.12 and shellcheck v0.11.0 for linux and windows, with SHA-256."""
        manifest = self.fetch_tools.MANIFEST
        for tool, expected in EXPECTED.items():
            self.assertEqual(manifest[tool]["version"], expected["version"])
            for platform in ("linux-x86_64", "windows-x86_64"):
                entry = manifest[tool]["platforms"][platform]
                self.assertEqual((entry["url"], entry["sha256"]), expected[platform], f"{tool} {platform}")
                self.assertRegex(entry["sha256"], r"^[0-9a-f]{64}$")
                self.assertTrue(entry["url"].startswith("https://"))

    def test_platform_names_are_detected_for_the_ci_machines(self):
        """NFR-09.6: Linux x86_64 and Windows AMD64 map to the manifest's platform keys; others are rejected."""
        detect = self.fetch_tools.detect_platform
        self.assertEqual(detect("Linux", "x86_64"), "linux-x86_64")
        self.assertEqual(detect("Windows", "AMD64"), "windows-x86_64")
        with self.assertRaises(self.fetch_tools.FetchError):
            detect("Darwin", "arm64")


class FetchBehaviourTests(FetchToolsTestCase):
    def test_a_verified_download_is_extracted_and_its_path_is_printed(self):
        """NFR-09.6: a download whose SHA-256 matches is extracted into the destination and the path is printed."""
        archive = self.make_tar("actionlint.tar.gz", {"actionlint": FAKE_EXE, "docs/usage.md": b"docs"})
        code, out, err = self.run_main(["actionlint", "--platform", "linux-x86_64", "--dest", str(self.dest)],
                                       self.manifest_for(archive), archive)
        self.assertEqual(code, 0, err)
        printed = Path(out.strip())
        self.assertTrue(printed.is_file(), f"printed path {out!r} is not a file")
        self.assertEqual(printed.read_bytes(), FAKE_EXE)
        self.assertEqual(len(out.strip().splitlines()), 1, "only the tool path may be printed on stdout")
        self.assertTrue(str(printed).startswith(str(self.dest)))
        if os.name == "posix":
            self.assertTrue(printed.stat().st_mode & 0o111, "the tool must be executable")
        self.assertFalse([p for p in self.files_under(self.dest) if p.name.endswith(".tar.gz")],
                         "the downloaded archive must not be kept")

    def test_a_checksum_mismatch_exits_non_zero_deletes_the_download_and_extracts_nothing(self):
        """NFR-09.6: a wrong SHA-256 fails the run, removes the download and never reaches extraction."""
        archive = self.make_tar("actionlint.tar.gz", {"actionlint": FAKE_EXE})
        manifest = self.manifest_for(archive, sha="0" * 64)
        with mock.patch.object(self.fetch_tools, "extract_member", side_effect=AssertionError("extracted")) as extract:
            code, out, err = self.run_main(["actionlint", "--platform", "linux-x86_64", "--dest", str(self.dest)],
                                           manifest, archive)
        self.assertNotEqual(code, 0)
        self.assertEqual(out.strip(), "")
        self.assertIn("checksum", err.lower())
        extract.assert_not_called()
        self.assertEqual(self.files_under(self.dest), [], "no file may remain after a checksum mismatch")

    def test_a_verified_copy_is_reused_without_downloading_again(self):
        """NFR-09.6: a second run finds the verified copy and does not download."""
        archive = self.make_tar("actionlint.tar.gz", {"actionlint": FAKE_EXE})
        manifest = self.manifest_for(archive)
        first = self.fetch_tools.fetch("actionlint", "linux-x86_64", self.dest, manifest, self.downloader(archive))
        self.assertEqual(len(self.downloads), 1)
        second = self.fetch_tools.fetch("actionlint", "linux-x86_64", self.dest, manifest, self.downloader(archive))
        self.assertEqual(len(self.downloads), 1, "the verified copy should have been reused")
        self.assertEqual(first, second)

    def test_a_tampered_copy_is_replaced_by_a_fresh_verified_download(self):
        """NFR-09.6: if the extracted tool changed after verification, it is downloaded and verified again."""
        archive = self.make_tar("actionlint.tar.gz", {"actionlint": FAKE_EXE})
        manifest = self.manifest_for(archive)
        path = self.fetch_tools.fetch("actionlint", "linux-x86_64", self.dest, manifest, self.downloader(archive))
        path.write_bytes(b"#!/bin/sh\necho tampered\n")
        again = self.fetch_tools.fetch("actionlint", "linux-x86_64", self.dest, manifest, self.downloader(archive))
        self.assertEqual(len(self.downloads), 2)
        self.assertEqual(again.read_bytes(), FAKE_EXE)

    def test_zip_and_nested_tar_xz_archives_are_supported(self):
        """NFR-09.6: the Windows zip and the nested shellcheck tar.xz layouts both yield the executable."""
        zipped = self.make_zip("actionlint.zip", {"actionlint.exe": FAKE_EXE, "LICENSE.txt": b"mit"})
        manifest = self.manifest_for(zipped, executable="actionlint.exe", fmt="zip", platform="windows-x86_64")
        path = self.fetch_tools.fetch("actionlint", "windows-x86_64", self.dest, manifest, self.downloader(zipped))
        self.assertEqual(path.name, "actionlint.exe")
        self.assertEqual(path.read_bytes(), FAKE_EXE)
        xz = self.make_tar("shellcheck.tar.xz", {"shellcheck-v0.11.0/shellcheck": FAKE_EXE,
                                                 "shellcheck-v0.11.0/README.txt": b"readme"}, mode="w:xz")
        manifest = {"shellcheck": {"version": "v0.11.0", "platforms": {"linux-x86_64": {
            "url": "https://example.invalid/shellcheck.tar.xz", "sha256": sha256_of(xz), "format": "tar.xz",
            "executable": "shellcheck"}}}}
        path = self.fetch_tools.fetch("shellcheck", "linux-x86_64", self.dest, manifest, self.downloader(xz))
        self.assertEqual(path.name, "shellcheck")
        self.assertEqual(path.read_bytes(), FAKE_EXE)

    def test_an_archive_without_the_executable_fails_and_leaves_nothing(self):
        """NFR-09.6: a checksum-valid archive that lacks the tool is an error, and nothing is left behind."""
        archive = self.make_tar("actionlint.tar.gz", {"README.md": b"no tool here"})
        code, out, err = self.run_main(["actionlint", "--platform", "linux-x86_64", "--dest", str(self.dest)],
                                       self.manifest_for(archive), archive)
        self.assertNotEqual(code, 0)
        self.assertEqual(out.strip(), "")
        self.assertEqual(self.files_under(self.dest), [])

    def test_path_traversal_members_are_never_written(self):
        """NFR-09.6: members with .. or absolute names are ignored; only the named executable is extracted."""
        evil = self.work / "escaped.txt"
        archive = self.make_tar("actionlint.tar.gz", {"../escaped.txt": b"evil", "actionlint": FAKE_EXE})
        path = self.fetch_tools.fetch("actionlint", "linux-x86_64", self.dest, self.manifest_for(archive),
                                      self.downloader(archive))
        self.assertEqual(path.read_bytes(), FAKE_EXE)
        self.assertFalse(evil.exists())

    def test_unknown_tool_and_unknown_platform_are_usage_errors(self):
        """NFR-09.6: asking for a tool or platform that is not in the manifest exits 2 without downloading."""
        archive = self.make_tar("actionlint.tar.gz", {"actionlint": FAKE_EXE})
        manifest = self.manifest_for(archive)
        for argv in (["nosuchtool", "--platform", "linux-x86_64"], ["actionlint", "--platform", "plan9-mips"]):
            with self.subTest(argv=argv):
                code, out, err = self.run_main([*argv, "--dest", str(self.dest)], manifest, archive)
                self.assertEqual(code, 2, err)
        self.assertEqual(self.downloads, [])


class TransportTests(FetchToolsTestCase):
    def test_tls_verification_is_on(self):
        """NFR-09.6: the SSL context requires a valid certificate and host name, and nothing disables it."""
        context = self.fetch_tools.ssl_context()
        self.assertEqual(context.verify_mode, ssl.CERT_REQUIRED)
        self.assertTrue(context.check_hostname)
        source = (CI_DIR / "fetch_tools.py").read_text(encoding="utf-8")
        for forbidden in ("_create_unverified_context", "CERT_NONE", "check_hostname = False", "verify=False"):
            self.assertNotIn(forbidden, source)

    def test_the_default_downloader_passes_the_verifying_context_to_urlopen(self):
        """NFR-09.6: download_file hands the verifying SSL context to urlopen and writes what it reads."""
        response = mock.MagicMock()
        response.__enter__.return_value = response
        response.read.side_effect = [b"payload", b""]
        target = self.work / "download.bin"
        with mock.patch.object(self.fetch_tools.urllib.request, "urlopen", return_value=response) as urlopen:
            self.fetch_tools.download_file("https://example.invalid/tool.tar.gz", target)
        self.assertEqual(target.read_bytes(), b"payload")
        context = urlopen.call_args.kwargs["context"]
        self.assertEqual(context.verify_mode, ssl.CERT_REQUIRED)

    def test_the_default_downloader_refuses_plain_http(self):
        """NFR-09.6: only https URLs are downloaded."""
        with mock.patch.object(self.fetch_tools.urllib.request, "urlopen") as urlopen:
            with self.assertRaises(self.fetch_tools.FetchError):
                self.fetch_tools.download_file("http://example.invalid/tool.tar.gz", self.work / "x.bin")
        urlopen.assert_not_called()


if __name__ == "__main__":
    unittest.main()
