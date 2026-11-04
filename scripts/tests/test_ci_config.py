"""Tests for the CI definitions: .github/workflows/ci.yml, .github/workflows/linux-snapshots.yml and .gitlab-ci.yml.

They prove NFR-09.1 (the five jobs and their commands), NFR-09.2 (every pin), NFR-09.3 (permissions and secrets) and
NFR-09.6 (both platforms run the quality gates), plus the update mode that the Linux snapshot generators use
(NFR-09.5). The expected pins are the verified values of plan C3, so a typo in a SHA or a digest fails here, not in
the first CI run. The files are parsed with PyYAML (scripts/requirements-ci.txt); a missing PyYAML or a missing CI
file is a failure, never a skip.
"""
import json
import re
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ci_test_support import CI_DIR, ROOT, git  # noqa: E402

try:
    import yaml
except ImportError:  # reported by every test through load_yaml, with the fix
    yaml = None

GITHUB_CI = ".github/workflows/ci.yml"
GITHUB_SNAPSHOTS = ".github/workflows/linux-snapshots.yml"
GITLAB_CI = ".gitlab-ci.yml"
CI_FILES = [GITHUB_CI, GITHUB_SNAPSHOTS, GITLAB_CI]
WORKFLOWS = [GITHUB_CI, GITHUB_SNAPSHOTS]

JOBS = ["build", "frontend-unit", "e2e", "quality-gates", "claude-review"]
GITLAB_STAGES = ["build", "test", "e2e", "review"]
GITLAB_JOB_STAGE = {"build": "build", "frontend-unit": "test", "quality-gates": "test", "e2e": "e2e",
                    "claude-review": "review", "e2e-update-linux-snapshots": "e2e"}
TIMEOUT_MINUTES = {"build": 40, "frontend-unit": 15, "e2e": 45, "quality-gates": 20, "claude-review": 40}
RUNNER = "ubuntu-24.04"

# Plan C1: the commands that both platforms run, per job (identical text on both).
GATE_COMMANDS = {
    "build": ["cd backend && ./mvnw -B verify"],
    "frontend-unit": ["cd frontend && npm ci && npm test && npm run build"],
    "e2e": ["cd frontend && npm ci && npx playwright test"],
    "quality-gates": ["pip install -r scripts/requirements-ci.txt", "python scripts/ci/quality_gates.py"],
    "claude-review": ["npm install -g @anthropic-ai/claude-code@2.1.311", "python3 scripts/ci/claude_review.py run",
                      "--out ci-out/review", "python3 scripts/ci/claude_review.py comment"],
}

# Plan C3: the pins, verified on 2026-11-04.
ACTION_PINS = {
    "actions/checkout": ("3d3c42e5aac5ba805825da76410c181273ba90b1", "v7.0.1"),
    "actions/setup-java": ("b843ebf9b913146b38da2eaa7aeddf76074ae1ad", "v6.1.0"),
    "actions/setup-node": ("949feb2413d6458794dcd2491c4babbbce0c15c1", "v7.1.0"),
    "actions/setup-python": ("6d4fd22b20465ccc0f7354476588e8df7c9ca0e0", "v7.1.0"),
    "actions/cache": ("55cc8345863c7cc4c66a329aec7e433d2d1c52a9", "v6.1.0"),
    "actions/upload-artifact": ("cf430e030ddbb5b0abf93d22962f4752f3646cd9", "v7.0.2"),
    "actions/download-artifact": ("9000827ccba6bdab643e8b6fd33ac0654aef8333", "v8.0.2"),
}
PLAYWRIGHT_IMAGE = "mcr.microsoft.com/playwright:v1.64.1-noble@sha256:" \
                   "2f05f8a734509a7b3f26a912639488ee08f58a7b292d14d62271bcff9038768f"
TEMURIN_IMAGE = "eclipse-temurin:21.0.13_9-jdk-noble@sha256:" \
                "114cabb73033ca4ce7ceec3deb62396bc1b716282bae051ed3c1d82e38fcab2d"
NODE_IMAGE = "node:22.11.0-bookworm@sha256:ec878c763e9fad09d22aae86e2edcb7a05b397dfe8411c16e2b90158d595e2ce"
PYTHON_IMAGE = "python:3.14.10-trixie@sha256:72628634e335b4b0a40727f398a73ae7ba983c319ca901fc034f92a216f7a781"
JAVA_VERSION = "21.0.13+9"
PYTHON_VERSION = "3.14.10"
JRE_URL = ("https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13%2B9/"
           "OpenJDK21U-jre_x64_linux_hotspot_21.0.13_9.tar.gz")
JRE_SHA256 = "a535a1df9c6284d9618872984e026ae0352d1f7e9e0605425c0e6114f47351dd"
CLAUDE_CLI = "@anthropic-ai/claude-code@2.1.311"
PLAYWRIGHT_MCP = "@playwright/mcp@0.0.84"

IMAGE_PATTERN = re.compile(r"^[a-z0-9][\w./-]*:[\w][\w.+-]*@sha256:[0-9a-f]{64}$")
USES_LINE = re.compile(r"^\s*(?:-\s+)?uses:\s+(\S+?)@([0-9a-f]{40})\s+#\s+(v\d+\.\d+\.\d+)\s*$")
GITLAB_RESERVED = {"workflow", "stages", "variables", "default", "include", "cache", "image", "services",
                   "before_script", "after_script", "pages"}


def normalise_list(value):
    if value is None:
        return []
    return [value] if isinstance(value, str) else list(value)


class CiConfigTestCase(unittest.TestCase):
    """Loading helpers. A missing PyYAML or a missing file fails the test with a message that says what to do."""

    def read(self, relative):
        path = ROOT / relative
        if not path.is_file():
            self.fail(f"{relative} does not exist")
        return path.read_text(encoding="utf-8")

    def load_yaml(self, relative):
        if yaml is None:
            self.fail("PyYAML is not installed (pip install -r scripts/requirements-ci.txt)")
        data = yaml.safe_load(self.read(relative))
        self.assertIsInstance(data, dict, f"{relative} must be a YAML mapping")
        return data

    def workflow(self, relative):
        """A GitHub workflow. PyYAML reads the key `on` as the boolean True (P-9), so it is stored under 'on'."""
        data = self.load_yaml(relative)
        if True in data and "on" not in data:
            data["on"] = data.pop(True)
        self.assertIn("on", data, f"{relative} has no `on:` trigger")
        self.assertIsInstance(data.get("jobs"), dict, f"{relative} has no jobs")
        return data

    def gitlab(self):
        data = self.load_yaml(GITLAB_CI)
        data["_jobs"] = {name: cfg for name, cfg in data.items()
                         if isinstance(cfg, dict) and not name.startswith((".", "_")) and name not in GITLAB_RESERVED}
        return data

    @staticmethod
    def steps(job):
        return job.get("steps") or []

    @staticmethod
    def github_run_texts(job):
        return [step["run"] for step in job.get("steps") or [] if isinstance(step.get("run"), str)]

    @staticmethod
    def github_job_script(job):
        return "\n".join(CiConfigTestCase.github_run_texts(job))

    @staticmethod
    def gitlab_script_blocks(job):
        """The scripts of a GitLab job, one string per section (before_script, script, after_script)."""
        blocks = []
        for key in ("before_script", "script", "after_script"):
            lines = normalise_list(job.get(key))
            if lines:
                blocks.append("\n".join(str(line) for line in lines))
        return blocks

    @classmethod
    def gitlab_job_script(cls, job):
        return "\n".join(cls.gitlab_script_blocks(job))

    def all_script_blocks(self):
        """Every shell script of every CI file as (file, job, text)."""
        blocks = []
        for rel in WORKFLOWS:
            for name, job in self.workflow(rel)["jobs"].items():
                blocks += [(rel, name, text) for text in self.github_run_texts(job)]
        for name, job in self.gitlab()["_jobs"].items():
            blocks += [(GITLAB_CI, name, text) for text in self.gitlab_script_blocks(job)]
        return blocks

    def ci_text(self):
        return {rel: self.read(rel) for rel in CI_FILES}

    @staticmethod
    def image_of(job):
        """The image of a GitLab job (a string or an {name:} mapping) or of a GitHub job (`container`)."""
        image = job.get("image", job.get("container"))
        return image.get("name", image.get("image")) if isinstance(image, dict) else image

    @staticmethod
    def is_always(condition):
        return isinstance(condition, str) and "always()" in condition

    @staticmethod
    def is_failure(condition):
        return isinstance(condition, str) and "failure()" in condition


# ------------------------------------------------------------------------------------------------ NFR-09.1

class JobDefinitionTests(CiConfigTestCase):
    def test_github_ci_defines_the_five_jobs(self):
        """NFR-09.1: .github/workflows/ci.yml is named CI and defines exactly build, frontend-unit, e2e,
        quality-gates and claude-review, each named like its ID, on ubuntu-24.04 with its timeout."""
        wf = self.workflow(GITHUB_CI)
        self.assertEqual(wf.get("name"), "CI")
        self.assertEqual(sorted(wf["jobs"]), sorted(JOBS))
        for name, job in wf["jobs"].items():
            with self.subTest(job=name):
                self.assertEqual(job.get("name"), name, "the check name must equal the job ID (stable for required checks)")
                self.assertEqual(job.get("runs-on"), RUNNER)
                self.assertEqual(job.get("timeout-minutes"), TIMEOUT_MINUTES[name])

    def test_gitlab_ci_defines_the_five_jobs(self):
        """NFR-09.1: .gitlab-ci.yml has the stages build, test, e2e and review and the same five jobs, plus the
        manual Linux snapshot job, each in its stage with its timeout; frontend-unit and quality-gates start at once."""
        data = self.gitlab()
        self.assertEqual(data.get("stages"), GITLAB_STAGES)
        self.assertEqual(sorted(data["_jobs"]), sorted([*JOBS, "e2e-update-linux-snapshots"]))
        for name, job in data["_jobs"].items():
            with self.subTest(job=name):
                self.assertEqual(job.get("stage"), GITLAB_JOB_STAGE[name])
        for name, minutes in TIMEOUT_MINUTES.items():
            with self.subTest(job=name, check="timeout"):
                self.assertEqual(data["_jobs"][name].get("timeout"), f"{minutes} minutes")
        for name in ("frontend-unit", "quality-gates"):
            with self.subTest(job=name, check="needs"):
                self.assertEqual(data["_jobs"][name].get("needs"), [])

    def test_both_platforms_run_the_same_gate_commands(self):
        """NFR-09.1: each of the five jobs runs the plan C1 commands, with the same text on GitHub and on GitLab."""
        github = self.workflow(GITHUB_CI)["jobs"]
        gitlab = self.gitlab()["_jobs"]
        for job, commands in GATE_COMMANDS.items():
            for command in commands:
                with self.subTest(job=job, command=command):
                    self.assertIn(command, self.github_job_script(github[job]), "GitHub")
                    self.assertIn(command, self.gitlab_job_script(gitlab[job]), "GitLab")

    def test_triggers_are_pushes_and_pull_requests_into_dev_and_main(self):
        """NFR-09.1: ci.yml runs for push to dev and main, pull_request into dev and main and manual dispatch;
        .gitlab-ci.yml runs only for merge requests into dev or main, pushes to them and the web source (C2)."""
        on = self.workflow(GITHUB_CI)["on"]
        self.assertEqual(sorted(on), ["pull_request", "push", "workflow_dispatch"])
        self.assertEqual(on["push"], {"branches": ["dev", "main"]})
        self.assertEqual(on["pull_request"], {"branches": ["dev", "main"]}, "default pull_request types")
        rules = self.gitlab()["workflow"]["rules"]
        self.assertEqual(len(rules), 3, "no catch-all rule: nothing else may start a pipeline")
        merge, push, web = (str(rule.get("if")) for rule in rules)
        for token in ("merge_request_event", "CI_MERGE_REQUEST_TARGET_BRANCH_NAME", '"dev"', '"main"'):
            self.assertIn(token, merge)
        for token in ('CI_PIPELINE_SOURCE == "push"', "CI_COMMIT_BRANCH", '"dev"', '"main"'):
            self.assertIn(token, push)
        self.assertIn('CI_PIPELINE_SOURCE == "web"', web)
        for rule in rules:
            self.assertNotIn(rule.get("when"), ("always",), "a rule must not widen the pipeline conditions")

    def test_concurrency_cancels_only_superseded_pull_request_runs(self):
        """NFR-09.1: pull request runs of one ref share a concurrency group and cancel older runs; a push to dev or
        main has its own group and is never cancelled (P-11)."""
        concurrency = self.workflow(GITHUB_CI).get("concurrency")
        self.assertIsInstance(concurrency, dict, "ci.yml needs a concurrency block")
        self.assertIn("pull_request", str(concurrency.get("cancel-in-progress")))
        self.assertIsNot(concurrency.get("cancel-in-progress"), True)
        group = str(concurrency.get("group"))
        self.assertIn("github.ref", group)
        self.assertIn("github.run_id", group, "pushes need a per-run group so that they are never cancelled")

    def test_claude_review_needs_the_other_four_jobs_and_cannot_fail(self):
        """NFR-09.1: claude-review needs build, frontend-unit, e2e and quality-gates, and is never allowed to fail:
        GitHub has no continue-on-error and no `|| true`, GitLab sets allow_failure: false."""
        others = sorted(set(JOBS) - {"claude-review"})
        github = self.workflow(GITHUB_CI)["jobs"]
        self.assertEqual(sorted(normalise_list(github["claude-review"].get("needs"))), others)
        self.assertEqual(normalise_list(github["e2e"].get("needs")), ["build"])
        gitlab = self.gitlab()["_jobs"]
        self.assertEqual(sorted(gitlab["claude-review"].get("needs")), others)
        self.assertEqual(gitlab["e2e"].get("needs"), ["build"])
        self.assertIs(gitlab["claude-review"].get("allow_failure"), False, "allow_failure: false, written out")
        for name in JOBS:
            self.assertIsNot(gitlab[name].get("allow_failure"), True, name)
            self.assertNotIn("rules", gitlab[name], f"{name} must run in every pipeline")
        for rel, text in self.ci_text().items():
            with self.subTest(file=rel):
                self.assertNotIn("continue-on-error", text)
                self.assertNotIn("|| true", text)
                self.assertNotIn("!reference", text)

    def test_backend_mvnw_is_executable_in_the_index(self):
        """NFR-09.1: backend/mvnw has mode 100755, so ./mvnw runs on a Linux runner."""
        entry = git(ROOT, "ls-files", "-s", "backend/mvnw")
        self.assertTrue(entry.startswith("100755 "), f"git ls-files -s backend/mvnw: {entry!r}")

    def test_e2e_runs_the_packaged_jar_in_the_playwright_image(self):
        """NFR-09.1: e2e needs the build job's JAR and runs Playwright in the pinned Playwright image with a Temurin
        JRE; GitHub uses --ipc=host, downloads policyforge-jar and points PF_E2E_JAR at it."""
        job = self.workflow(GITHUB_CI)["jobs"]["e2e"]
        self.assertEqual(job["container"]["image"], PLAYWRIGHT_IMAGE)
        self.assertEqual(job["container"].get("options"), "--ipc=host")
        steps = self.steps(job)
        download = [s for s in steps if str(s.get("uses", "")).startswith("actions/download-artifact@")]
        self.assertEqual([s["with"]["name"] for s in download], ["policyforge-jar"])
        java = [s for s in steps if str(s.get("uses", "")).startswith("actions/setup-java@")]
        self.assertEqual(len(java), 1)
        self.assertEqual(java[0]["with"].get("java-package"), "jre")
        self.assertEqual(java[0]["with"].get("distribution"), "temurin")
        text = json.dumps(job)
        self.assertIn("PF_E2E_JAR", text)
        gitlab = self.gitlab()["_jobs"]["e2e"]
        self.assertEqual(self.image_of(gitlab), PLAYWRIGHT_IMAGE)
        self.assertIn("PF_E2E_JAR", json.dumps(gitlab))
        self.assertNotIn("PF_VISUAL_BASELINES", json.dumps([job, gitlab]),
                         "the regular e2e job runs in auto mode and must not set the policy variable")
        self.assertNotIn("--update-snapshots", self.github_job_script(job) + self.gitlab_job_script(gitlab))

    def test_artifacts_of_the_five_jobs_are_published(self):
        """NFR-09.1: the GitHub jobs upload the C4 artifacts with the right condition and retention, and the GitLab
        jobs keep the same files."""
        expected = {  # name -> (path, condition, retention-days)
            "policyforge-jar": ("backend/target/policyforge-*.jar", None, 7),
            "jacoco-report": ("backend/target/site/jacoco/", "always", 14),
            "backend-test-reports": ("backend/target/surefire-reports/", "failure", 14),
            "frontend-coverage": ("frontend/coverage/lcov.info", "always", 14),
            "playwright-report": ("frontend/playwright-report/", "failure", 14),
            "playwright-traces": ("frontend/test-results/", "failure", 14),
            "visual-baselines-missing": ("frontend/test-results/visual-baselines-missing.txt", "always", 14),
            "quality-gates-report": ("ci-out/quality-gates/", "always", 14),
            "claude-review": ("ci-out/review/", "always", 14),
        }
        seen = {}
        for job in self.workflow(GITHUB_CI)["jobs"].values():
            for step in self.steps(job):
                if str(step.get("uses", "")).startswith("actions/upload-artifact@"):
                    seen[step["with"]["name"]] = step
        self.assertEqual(sorted(seen), sorted(expected))
        for name, (path, condition, days) in expected.items():
            with self.subTest(artifact=name):
                step = seen[name]
                self.assertIn(path, str(step["with"]["path"]))
                self.assertEqual(step["with"].get("retention-days"), days)
                if condition == "always":
                    self.assertTrue(self.is_always(step.get("if")), step.get("if"))
                elif condition == "failure":
                    self.assertTrue(self.is_failure(step.get("if")), step.get("if"))
        gitlab = self.gitlab()["_jobs"]
        for job, paths in {"build": ["backend/target/policyforge-*.jar", "backend/target/site/jacoco/"],
                           "frontend-unit": ["frontend/coverage/lcov.info"],
                           "e2e": ["frontend/playwright-report/", "frontend/test-results/"],
                           "quality-gates": ["ci-out/quality-gates/"],
                           "claude-review": ["ci-out/review/"]}.items():
            with self.subTest(gitlab_job=job):
                artifacts = gitlab[job].get("artifacts") or {}
                for path in paths:
                    self.assertIn(path, artifacts.get("paths", []))
                self.assertIn(artifacts.get("when"), ("always", "on_failure"))
                self.assertRegex(str(artifacts.get("expire_in")), r"^\d+ days$")


# ------------------------------------------------------------------------------------------------ NFR-09.2

class PinTests(CiConfigTestCase):
    def test_every_action_is_pinned_to_a_full_sha_with_a_version_comment(self):
        """NFR-09.2: every `uses:` is owner/repo@<40 hex> with a `# vX.Y.Z` comment and equals the verified C3 pin."""
        used = 0
        for rel in WORKFLOWS:
            for number, line in enumerate(self.read(rel).splitlines(), 1):
                if not re.match(r"^\s*(?:-\s+)?uses:", line):
                    continue
                used += 1
                with self.subTest(file=rel, line=number):
                    match = USES_LINE.match(line)
                    self.assertIsNotNone(match, f"not pinned to a 40-character SHA with a version comment: {line.strip()}")
                    repo, sha, version = match.groups()
                    self.assertIn(repo, ACTION_PINS, "an action that is not in plan C3")
                    self.assertEqual((sha, version), ACTION_PINS[repo])
        self.assertGreater(used, 0)
        for rel, text in self.ci_text().items():
            self.assertNotIn("docker://", text, rel)

    def test_every_image_is_an_exact_tag_with_a_digest(self):
        """NFR-09.2: every container image is tag@sha256:<64 hex>, and each equals the verified C3 image."""
        images = []
        for rel in WORKFLOWS:
            for name, job in self.workflow(rel)["jobs"].items():
                if "container" in job:
                    images.append((f"{rel}:{name}", self.image_of(job)))
                for service in (job.get("services") or {}).values():
                    images.append((f"{rel}:{name}:service", self.image_of({"image": service})))
        gitlab = self.gitlab()
        images += [(f"{GITLAB_CI}:{name}", self.image_of(job)) for name, job in gitlab["_jobs"].items()]
        if "image" in gitlab:
            images.append((f"{GITLAB_CI}:image", self.image_of(gitlab)))
        self.assertGreaterEqual(len(images), 7)
        for where, image in images:
            with self.subTest(image=where):
                self.assertIsInstance(image, str, "every job names its image (no runner default)")
                self.assertRegex(image, IMAGE_PATTERN)
                self.assertIn(image, (PLAYWRIGHT_IMAGE, TEMURIN_IMAGE, NODE_IMAGE, PYTHON_IMAGE))

    def test_playwright_image_equals_the_locked_playwright_version(self):
        """NFR-09.2: the Playwright image tag is v + the @playwright/test version in package-lock.json, and
        package.json pins that version without a range."""
        package = json.loads((ROOT / "frontend" / "package.json").read_text(encoding="utf-8"))
        lock = json.loads((ROOT / "frontend" / "package-lock.json").read_text(encoding="utf-8"))
        spec = package["devDependencies"]["@playwright/test"]
        locked = lock["packages"]["node_modules/@playwright/test"]["version"]
        self.assertRegex(spec, r"^\d+\.\d+\.\d+$", "@playwright/test must be an exact version, not a range")
        self.assertEqual(spec, locked)
        self.assertEqual(lock["packages"][""]["devDependencies"]["@playwright/test"], spec)
        expected = f"mcr.microsoft.com/playwright:v{locked}-noble@sha256:"
        github = self.workflow(GITHUB_CI)["jobs"]["e2e"]
        snapshots = self.workflow(GITHUB_SNAPSHOTS)["jobs"]
        gitlab = self.gitlab()["_jobs"]
        images = [self.image_of(github), self.image_of(gitlab["e2e"]), self.image_of(gitlab["e2e-update-linux-snapshots"])]
        images += [self.image_of(job) for job in snapshots.values() if "container" in job]
        self.assertGreaterEqual(len(images), 4)
        for image in images:
            self.assertTrue(image.startswith(expected), image)

    def test_ci_node_version_equals_the_pom(self):
        """NFR-09.2: the Node version of setup-node and of the Node images equals frontend.node.version in
        backend/pom.xml."""
        pom = (ROOT / "backend" / "pom.xml").read_text(encoding="utf-8")
        version = re.search(r"<frontend\.node\.version>v?(\d+\.\d+\.\d+)</frontend\.node\.version>", pom).group(1)
        setups = [step["with"]["node-version"] for rel in WORKFLOWS for job in self.workflow(rel)["jobs"].values()
                  for step in self.steps(job) if str(step.get("uses", "")).startswith("actions/setup-node@")]
        self.assertGreaterEqual(len(setups), 2, "frontend-unit and claude-review use setup-node")
        for value in setups:
            self.assertEqual(str(value), version)
        node_images = [self.image_of(job) for job in self.gitlab()["_jobs"].values()
                       if str(self.image_of(job)).startswith("node:")]
        self.assertGreaterEqual(len(node_images), 2)
        for image in node_images:
            self.assertTrue(image.startswith(f"node:{version}-"), image)

    def test_java_and_python_versions_are_exact_and_equal_on_both_platforms(self):
        """NFR-09.2: Temurin 21.0.13+9 and Python 3.14.10 are named exactly, and GitHub and GitLab use the same."""
        github = self.workflow(GITHUB_CI)["jobs"]
        java = [step["with"] for rel in WORKFLOWS for job in self.workflow(rel)["jobs"].values()
                for step in self.steps(job) if str(step.get("uses", "")).startswith("actions/setup-java@")]
        self.assertGreaterEqual(len(java), 3, "build (JDK), e2e (JRE) and the snapshot workflow's JAR build")
        for options in java:
            self.assertEqual(str(options["java-version"]), JAVA_VERSION)
            self.assertEqual(options["distribution"], "temurin")
        python = [step["with"] for job in github.values() for step in self.steps(job)
                  if str(step.get("uses", "")).startswith("actions/setup-python@")]
        self.assertEqual([str(options["python-version"]) for options in python], [PYTHON_VERSION])
        gitlab = self.gitlab()["_jobs"]
        self.assertEqual(self.image_of(gitlab["build"]), TEMURIN_IMAGE)
        self.assertEqual(self.image_of(gitlab["quality-gates"]), PYTHON_IMAGE)

    def test_npm_and_pip_packages_run_by_ci_or_mcp_are_exact(self):
        """NFR-09.2: the Claude Code CLI is @anthropic-ai/claude-code@2.1.311 (the runner's pinned version),
        .mcp.json pins @playwright/mcp@0.0.84, CI runs no unpinned npm package and installs pip packages only
        from the pinned requirements file."""
        runner = (CI_DIR / "claude_review.py").read_text(encoding="utf-8")
        self.assertEqual(re.search(r'^PINNED_VERSION = "([^"]+)"', runner, re.M).group(1), CLAUDE_CLI.rsplit("@", 1)[1])
        mcp = json.loads((ROOT / ".mcp.json").read_text(encoding="utf-8"))
        self.assertIn(PLAYWRIGHT_MCP, mcp["mcpServers"]["playwright"]["args"])
        requirements = (ROOT / "scripts" / "requirements-ci.txt").read_text(encoding="utf-8").split()
        self.assertEqual(sorted(requirements), ["PyYAML==6.0.3", "check-jsonschema==0.39.0"])
        scripts = [text for _, _, text in self.all_script_blocks()]
        self.assertGreater(len(scripts), 0)
        joined = "\n".join(scripts)
        self.assertIn(f"npm install -g {CLAUDE_CLI}", joined)
        for package in re.findall(r"npm (?:install|i)\s+(?:-g\s+|--global\s+)?(@?[\w./-]+(?:@\S+)?)", joined):
            self.assertRegex(package, r"@\d+\.\d+\.\d+$", f"npm package without an exact version: {package}")
        for command in re.findall(r"\bnpx\s+(\S+)", joined):
            self.assertEqual(command, "playwright", "npx may only run the locked local Playwright")
        for command in re.findall(r"\bpip3?\s+install\s+(.+)", joined):
            self.assertEqual(command.split()[:2], ["-r", "scripts/requirements-ci.txt"])

    def test_nothing_uses_a_floating_reference(self):
        """NFR-09.2: the strings @latest, :latest and ubuntu-latest occur in none of the CI files or .mcp.json,
        and no action is referenced by a branch name."""
        texts = {**self.ci_text(), ".mcp.json": self.read(".mcp.json")}
        for rel, text in texts.items():
            for needle in ("@latest", ":latest", "ubuntu-latest"):
                with self.subTest(file=rel, needle=needle):
                    self.assertNotIn(needle, text)
        for rel in WORKFLOWS:
            self.assertNotRegex(self.read(rel), r"uses:\s*\S+@(main|master|dev|v\d+)\s*$", rel)

    def test_every_downloaded_file_has_a_sha256_next_to_it(self):
        """NFR-09.2: every curl or wget download is followed by a sha256sum -c check of a 64-hex digest before the
        file is used, and the Temurin JRE tarball has the verified C3 URL and digest."""
        downloads = 0
        for rel, job, text in self.all_script_blocks():
            if not re.search(r"\b(curl|wget)\b", text):
                continue
            downloads += 1
            with self.subTest(file=rel, job=job):
                self.assertRegex(text, r"\b[0-9a-f]{64}\b")
                self.assertIn("sha256sum -c", text)
                self.assertLess(text.index("sha256sum -c"), text.index("tar "), "check the digest before extracting")
        self.assertGreaterEqual(downloads, 2, "the e2e and e2e-update-linux-snapshots jobs install the JRE tarball")
        gitlab = self.gitlab()["_jobs"]
        for name in ("e2e", "e2e-update-linux-snapshots"):
            script = self.gitlab_job_script(gitlab[name])
            self.assertIn(JRE_URL, script, name)
            self.assertIn(JRE_SHA256, script, name)
        for rel, text in self.ci_text().items():
            for hostile in ("--insecure", "curl -k", "--no-check-certificate", "Invoke-WebRequest"):
                self.assertNotIn(hostile, text, f"{rel}: {hostile}")
            self.assertNotRegex(text, r"\|\s*(ba|z)?sh\b", f"{rel}: a download is never piped into a shell")


# ------------------------------------------------------------------------------------------------ NFR-09.3

class PermissionAndSecretTests(CiConfigTestCase):
    def test_permissions_are_read_only_except_for_claude_review(self):
        """NFR-09.3: every workflow has top-level permissions contents: read; only the claude-review job of ci.yml
        adds pull-requests: write, and nothing else gets a write permission."""
        for rel in WORKFLOWS:
            wf = self.workflow(rel)
            with self.subTest(file=rel):
                self.assertEqual(wf.get("permissions"), {"contents": "read"})
                for name, job in wf["jobs"].items():
                    permissions = job.get("permissions")
                    if rel == GITHUB_CI and name == "claude-review":
                        self.assertEqual(permissions, {"contents": "read", "pull-requests": "write"})
                    else:
                        self.assertIn(permissions, (None, {"contents": "read"}), f"{name} must not widen permissions")
            self.assertNotRegex(self.read(rel), r"write-all|read-all|id-token", rel)

    def test_no_pull_request_target_and_every_checkout_drops_credentials(self):
        """NFR-09.3: no workflow uses pull_request_target or workflow_run, and every actions/checkout sets
        persist-credentials: false."""
        for rel in WORKFLOWS:
            wf = self.workflow(rel)
            with self.subTest(file=rel):
                self.assertNotIn("pull_request_target", self.read(rel))
                self.assertNotIn("workflow_run", self.read(rel))
                for name, job in wf["jobs"].items():
                    checkouts = [s for s in self.steps(job) if str(s.get("uses", "")).startswith("actions/checkout@")]
                    self.assertEqual(len(checkouts), 1, f"{name} checks out exactly once")
                    self.assertIs(checkouts[0].get("with", {}).get("persist-credentials"), False, name)
        review = [s for s in self.steps(self.workflow(GITHUB_CI)["jobs"]["claude-review"])
                  if str(s.get("uses", "")).startswith("actions/checkout@")]
        self.assertEqual(review[0]["with"].get("fetch-depth"), 0, "the review reads the whole history (P-7)")

    def test_no_run_script_interpolates_an_event_expression(self):
        """NFR-09.3: no `run:` script contains a ${{ github.event... }} expression (or another attacker-controlled
        context); event values reach scripts through env:. GitLab scripts never read user-written text variables."""
        for rel in WORKFLOWS:
            for name, job in self.workflow(rel)["jobs"].items():
                for text in self.github_run_texts(job):
                    with self.subTest(file=rel, job=name):
                        self.assertNotRegex(text, r"\$\{\{[^}]*\bgithub\.(event|head_ref|ref_name|actor)")
                        self.assertNotRegex(text, r"\$\{\{[^}]*\b(inputs|steps|needs)\.")
        for _, name, text in self.all_script_blocks():
            with self.subTest(job=name):
                self.assertNotRegex(text, r"CI_MERGE_REQUEST_(TITLE|DESCRIPTION|SOURCE_BRANCH_NAME|LABELS)"
                                          r"|CI_COMMIT_(MESSAGE|DESCRIPTION|TITLE|TAG_MESSAGE|REF_NAME|BRANCH)\b")
        review = self.workflow(GITHUB_CI)["jobs"]["claude-review"]
        env = [step["env"] for step in self.steps(review) if "claude_review.py run" in str(step.get("run"))][0]
        self.assertIn("github.event", json.dumps(env), "the event values are passed through env")

    def test_api_key_reaches_only_the_review_step_and_is_never_printed(self):
        """NFR-09.3: ANTHROPIC_API_KEY is referenced in exactly one place, the env of the review step of the
        claude-review job (GitHub) or the claude-review job (GitLab); the PR comment step has the token and no key."""
        references = []
        for rel in WORKFLOWS:
            wf = self.workflow(rel)
            self.assertNotIn("ANTHROPIC_API_KEY", json.dumps({k: v for k, v in wf.items() if k != "jobs"}))
            for name, job in wf["jobs"].items():
                self.assertNotIn("ANTHROPIC_API_KEY", json.dumps({k: v for k, v in job.items() if k != "steps"}))
                for step in self.steps(job):
                    if "ANTHROPIC_API_KEY" in json.dumps(step):
                        references.append((rel, name, step))
            secrets = re.findall(r"secrets\.([A-Za-z0-9_]+)", self.read(rel))
            self.assertEqual(secrets, ["ANTHROPIC_API_KEY"] if rel == GITHUB_CI else [], f"{rel}: secrets used")
        self.assertEqual(len(references), 1, "exactly one step may name the key")
        rel, name, step = references[0]
        self.assertEqual((rel, name), (GITHUB_CI, "claude-review"))
        self.assertEqual(step["env"]["ANTHROPIC_API_KEY"], "${{ secrets.ANTHROPIC_API_KEY }}")
        self.assertNotIn("ANTHROPIC_API_KEY", str(step.get("run")), "the script reads the key from env, nothing prints it")
        for forbidden in ("GH_TOKEN", "GITHUB_TOKEN", "github.token"):
            self.assertNotIn(forbidden, json.dumps(step), "the Claude step never receives a GitHub token")
        comment = [s for s in self.steps(self.workflow(GITHUB_CI)["jobs"]["claude-review"])
                   if "claude_review.py comment" in str(s.get("run"))]
        self.assertEqual(len(comment), 1)
        self.assertEqual(comment[0]["env"], {"GH_TOKEN": "${{ github.token }}"})
        self.assertIn("pull_request", comment[0]["if"])
        self.assertTrue(self.is_always(comment[0]["if"]))
        gitlab = self.gitlab()
        self.assertEqual(gitlab["_jobs"]["claude-review"].get("environment"), "claude-review",
                         "GitLab gives every job the project variables; the environment scope limits the key to this job")
        for name, job in gitlab["_jobs"].items():
            if name != "claude-review":
                self.assertNotIn("ANTHROPIC_API_KEY", json.dumps(job), name)
                self.assertNotIn("PF_GITLAB_REVIEW_TOKEN", json.dumps(job), name)
        for key in ("variables", "default", "workflow"):
            self.assertNotIn("ANTHROPIC_API_KEY", json.dumps(gitlab.get(key)))

    def test_no_ci_file_traces_commands_dumps_the_environment_or_disables_tls_checks(self):
        """NFR-09.3: no CI file has `set -x`, echoes ANTHROPIC_API_KEY, dumps the environment, turns on a debug
        trace or sets NODE_TLS_REJECT_UNAUTHORIZED."""
        patterns = [r"set\s+-[a-z]*x", r"xtrace", r"NODE_TLS_REJECT_UNAUTHORIZED", r"echo\s+[^\n]*ANTHROPIC_API_KEY",
                    r"printf\s+[^\n]*ANTHROPIC_API_KEY", r"\bprintenv\b", r"^\s*(?:- )?env\s*(\||$)", r"\bexport\s+-p\b",
                    r"CI_DEBUG_TRACE", r"CI_DEBUG_SERVICES", r"ACTIONS_STEP_DEBUG", r"ACTIONS_RUNNER_DEBUG"]
        for rel, text in self.ci_text().items():
            for pattern in patterns:
                with self.subTest(file=rel, pattern=pattern):
                    self.assertIsNone(re.search(pattern, text, re.M), f"{rel} matches {pattern}")

    def test_no_api_key_literal_is_tracked(self):
        """NFR-09.3: git grep finds no sk-ant- literal of 20 or more characters in any tracked file."""
        found = git(ROOT, "grep", "-I", "-n", "-E", "sk-ant-[A-Za-z0-9_-]{20,}", check=False)
        self.assertEqual(found, "", "a key-shaped literal is tracked (build fake keys by concatenation)")


# ------------------------------------------------------------------------------------------------ NFR-09.6

class QualityGateWiringTests(CiConfigTestCase):
    def test_both_platforms_run_the_quality_gates_runner_and_cannot_skip_a_gate(self):
        """NFR-09.6: the quality-gates job of GitHub and of GitLab installs scripts/requirements-ci.txt and runs
        scripts/ci/quality_gates.py without --only, so every one of the eight gates runs and fails the job."""
        github = self.github_job_script(self.workflow(GITHUB_CI)["jobs"]["quality-gates"])
        gitlab = self.gitlab_job_script(self.gitlab()["_jobs"]["quality-gates"])
        for where, script in (("GitHub", github), ("GitLab", gitlab)):
            with self.subTest(platform=where):
                self.assertIn("pip install -r scripts/requirements-ci.txt", script)
                self.assertIn("python scripts/ci/quality_gates.py", script)
                self.assertLess(script.index("pip install"), script.index("quality_gates.py"))
                self.assertNotIn("--only", script)
                self.assertNotIn("python scripts/ac_audit.py", script, "the audit is a gate of the runner, run once")
        steps = self.steps(self.workflow(GITHUB_CI)["jobs"]["quality-gates"])
        runner = [s for s in steps if "quality_gates.py" in str(s.get("run"))]
        self.assertEqual(len(runner), 1)
        self.assertIsNone(runner[0].get("continue-on-error"))
        self.assertIsNone(runner[0].get("if"), "the runner step runs on every event")
        self.assertTrue([s for s in steps if str(s.get("uses", "")).startswith("actions/setup-python@")])

    def test_the_quality_gates_report_is_kept_even_when_a_gate_fails(self):
        """NFR-09.6: the runner writes its report under ci-out/quality-gates/, and both platforms keep it as an
        artifact when the job fails (the job reports each gate before it fails)."""
        for where, script in (("GitHub", self.github_job_script(self.workflow(GITHUB_CI)["jobs"]["quality-gates"])),
                              ("GitLab", self.gitlab_job_script(self.gitlab()["_jobs"]["quality-gates"]))):
            with self.subTest(platform=where):
                self.assertIn("--report ci-out/quality-gates/report.md", script)
        artifacts = self.gitlab()["_jobs"]["quality-gates"]["artifacts"]
        self.assertEqual(artifacts.get("when"), "always")


# ------------------------------------------------------------------------------------------------ NFR-09.5

class LinuxSnapshotGeneratorTests(CiConfigTestCase):
    def test_github_workflow_writes_only_missing_linux_baselines_and_uploads_them(self):
        """NFR-09.5: the Linux snapshots workflow runs on the update-linux-snapshots label or by hand, has no
        secrets, builds the packaged JAR with tests skipped, runs the e2e image and server in update mode with
        --update-snapshots=missing, and uploads exactly the -linux PNGs plus the report."""
        wf = self.workflow(GITHUB_SNAPSHOTS)
        self.assertEqual(wf.get("name"), "Linux snapshots")
        on = wf["on"]
        self.assertEqual(sorted(on), ["pull_request", "workflow_dispatch"])
        self.assertEqual(on["pull_request"], {"types": ["labeled"], "branches": ["dev", "main"]})
        self.assertNotIn("secrets.", self.read(GITHUB_SNAPSHOTS))
        for job in wf["jobs"].values():
            condition = job.get("if")
            self.assertIn("workflow_dispatch", str(condition))
            self.assertIn("update-linux-snapshots", str(condition))
        script = "\n".join(self.github_job_script(job) for job in wf["jobs"].values())
        self.assertRegex(script, r"\./mvnw -B (-DskipTests|-Dmaven\.test\.skip=true) package")
        self.assertNotIn("-Dskip.frontend", script, "the SPA must be packaged into the JAR")
        self.assertIn("npx playwright test --update-snapshots=missing", script)
        snapshot_job = [job for job in wf["jobs"].values() if "container" in job]
        self.assertEqual(len(snapshot_job), 1)
        self.assertEqual(snapshot_job[0]["container"]["image"], PLAYWRIGHT_IMAGE)
        self.assertEqual(snapshot_job[0]["container"].get("options"), "--ipc=host")
        self.assertIn("PF_VISUAL_BASELINES", json.dumps(snapshot_job[0]))
        self.assertEqual(json.dumps(snapshot_job[0]).count('"PF_VISUAL_BASELINES"'), 1)
        self.assertIn('"PF_VISUAL_BASELINES": "update"', json.dumps(snapshot_job[0]))
        uploads = {s["with"]["name"]: s["with"] for job in wf["jobs"].values() for s in self.steps(job)
                   if str(s.get("uses", "")).startswith("actions/upload-artifact@") and "name" in s.get("with", {})}
        self.assertIn("linux-snapshot-baselines", uploads)
        self.assertEqual(str(uploads["linux-snapshot-baselines"]["path"]).strip(),
                         "frontend/e2e/__snapshots__/*-linux.png")
        self.assertEqual(uploads["linux-snapshot-baselines"].get("if-no-files-found"), "error")
        self.assertIn("linux-snapshot-report", uploads)
        self.assertIn("frontend/playwright-report/", str(uploads["linux-snapshot-report"]["path"]))

    def test_gitlab_manual_job_generates_linux_baselines_without_blocking_the_pipeline(self):
        """NFR-09.5: e2e-update-linux-snapshots is manual, may fail, runs the e2e server in update mode with
        --update-snapshots=missing and keeps the -linux PNGs and the report."""
        job = self.gitlab()["_jobs"]["e2e-update-linux-snapshots"]
        self.assertEqual(job.get("when"), "manual")
        self.assertIs(job.get("allow_failure"), True)
        self.assertEqual(job.get("needs"), ["build"])
        self.assertEqual((job.get("variables") or {}).get("PF_VISUAL_BASELINES"), "update")
        self.assertIn("npx playwright test --update-snapshots=missing", self.gitlab_job_script(job))
        paths = job["artifacts"]["paths"]
        self.assertIn("frontend/e2e/__snapshots__/*-linux.png", paths)
        self.assertIn("frontend/playwright-report/", paths)


if __name__ == "__main__":
    unittest.main()
