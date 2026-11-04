"""STUB for the TDD red step: quality_gates.py is implemented in the feat(green) commit that follows."""
import sys


class GateUnavailable(Exception):
    pass


class MissingTool(GateUnavailable):
    pass


class Gate:
    def __init__(self, name, source, cwd=None, display=None):
        self.name = name

    def commands(self):
        return []


def build_gates(resolve_tool=None, repo_root=None, module_available=None):
    return []


def gate_environment(env=None):
    return dict(env or {})


def main(argv=None, gates=None, runner=None, stream=None):
    return 0


if __name__ == "__main__":
    sys.exit(main())
