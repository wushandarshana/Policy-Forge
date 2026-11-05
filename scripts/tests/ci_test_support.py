"""Shared helpers for the CI script tests (not a test module: unittest only collects test_*.py).

Loads scripts/ci/<name>.py by path, builds throw-away git repositories and a scrubbed child environment, so the
tests never read the developer's or the runner's real credentials.
"""
import importlib.util
import os
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCRIPTS = ROOT / "scripts"
CI_DIR = SCRIPTS / "ci"
FIXTURES = Path(__file__).resolve().parent / "fixtures"
FAKE_CLAUDE = FIXTURES / "fake_claude.py"
SCHEMA_PATH = CI_DIR / "review-verdict.schema.json"

# Fake credentials are built by concatenation so that no key-shaped literal is ever committed (P-6).
FAKE_API_KEY = "sk-ant-" + "api03-" + "FAKEFAKEFAKE" + "FAKEFAKEFAKE" + "0123456789"
FAKE_LEAK_KEY = "sk-ant-" + "api03-" + "LEAKEDLEAKED" + "LEAKEDLEAKED" + "9876543210"

# Environment variables that must never leak from the machine running the tests into a child process.
_SCRUB_PREFIXES = ("GITHUB_", "GH_", "CI_", "PF_", "FAKE_CLAUDE_", "ANTHROPIC_", "CLAUDE_", "RUNNER_")
_SCRUB_NAMES = {"CI", "GITLAB_CI", "NODE_TLS_REJECT_UNAUTHORIZED"}


def load_script(name, directory=CI_DIR):
    """Import scripts/ci/<name>.py (or another directory's) as a fresh module, without needing a package."""
    path = Path(directory) / f"{name}.py"
    spec = importlib.util.spec_from_file_location(f"pf_{path.parent.name}_{name}", path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def clean_env(**extra):
    """A copy of os.environ without CI or credential variables, plus the given overrides."""
    env = {k: v for k, v in os.environ.items()
           if k.upper() not in _SCRUB_NAMES and not k.upper().startswith(_SCRUB_PREFIXES)}
    env.update(extra)
    return env


def run_python(args, env=None, cwd=None, input_bytes=None, timeout=120):
    """Run `python <args>` and return the CompletedProcess with text-decoded output."""
    proc = subprocess.run([sys.executable, *map(str, args)], input=input_bytes, capture_output=True,
                          env=env if env is not None else clean_env(), cwd=cwd, timeout=timeout)
    proc.stdout_text = proc.stdout.decode("utf-8", "replace")
    proc.stderr_text = proc.stderr.decode("utf-8", "replace")
    return proc


def git(repo, *args, check=True):
    """Run git inside `repo` with a hermetic configuration and return stripped stdout."""
    cmd = ["git", "-C", str(repo), "-c", "user.name=Asha Test", "-c", "user.email=asha.test@example.invalid",
           "-c", "commit.gpgsign=false", "-c", "core.autocrlf=false", "-c", "core.safecrlf=false",
           "-c", "init.defaultBranch=work", *args]
    proc = subprocess.run(cmd, capture_output=True, env=clean_env(), timeout=60)
    out = proc.stdout.decode("utf-8", "replace").strip()
    if check and proc.returncode != 0:
        raise AssertionError(f"git {' '.join(args)} failed: {proc.stderr.decode('utf-8', 'replace')}")
    return out


def init_repo(prefix="pf-ci-test-"):
    """Create an empty git repository in a temporary directory; the caller owns the TemporaryDirectory."""
    holder = tempfile.TemporaryDirectory(prefix=prefix)
    repo = Path(holder.name)
    git(repo, "init", "-q")
    git(repo, "config", "core.autocrlf", "false")
    git(repo, "config", "core.safecrlf", "false")
    return holder, repo


def write_bytes(repo, relative, data):
    path = Path(repo) / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
    return path


def commit_all(repo, message):
    """Stage everything in the temporary repo, commit it and return the new commit's full SHA."""
    git(repo, "add", "-A")
    git(repo, "commit", "-q", "-m", message, "--allow-empty")
    return git(repo, "rev-parse", "HEAD")


def agent_files():
    """The real reviewer agent files, as bytes, keyed by the file name they are copied under."""
    agents = ROOT / ".claude" / "agents"
    return {name: (agents / name).read_bytes() for name in ("security-reviewer.md", "test-engineer.md")}
