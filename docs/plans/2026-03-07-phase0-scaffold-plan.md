# Phase 0: Scaffold — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build the autonomous AI development pipeline infrastructure that powers all subsequent phases of CommCare iOS development.

**Architecture:** Python-based pipeline tooling in this repo (`pipeline/`), GitHub Issues as the task queue, Claude Code MAX as the AI agent runtime, GitHub Actions for CI verification, and commcare-core's `MockApp` test harness as the oracle engine for correctness verification.

**Tech Stack:** Python 3.12+, pytest, PyYAML, GitHub CLI (`gh`), Claude Code CLI, Kotlin 2.3.x, Compose Multiplatform 1.10.x, Gradle 8.11+, GitHub Actions.

---

## Prerequisites

Before starting, ensure:
- Python 3.12+ installed
- `gh` CLI installed and authenticated (`gh auth login`)
- `claude` CLI installed (Claude Code)
- Git configured
- JDK 17+ installed (for Kotlin/Gradle work)

---

## Task 1: Python Pipeline Project Setup

**Files:**
- Create: `pipeline/pyproject.toml`
- Create: `pipeline/src/pipeline/__init__.py`
- Create: `pipeline/src/pipeline/config.py`
- Create: `pipeline/tests/__init__.py`
- Create: `pipeline/tests/test_config.py`

**Step 1: Create project structure**

```bash
mkdir -p pipeline/src/pipeline pipeline/tests
```

**Step 2: Write pyproject.toml**

```toml
[build-system]
requires = ["hatchling"]
build-backend = "hatchling.build"

[project]
name = "commcare-pipeline"
version = "0.1.0"
requires-python = ">=3.12"
dependencies = [
    "pyyaml>=6.0",
    "pydantic>=2.0",
]

[project.optional-dependencies]
dev = [
    "pytest>=8.0",
    "pytest-cov>=4.0",
]

[tool.hatch.build.targets.wheel]
packages = ["src/pipeline"]

[tool.pytest.ini_options]
testpaths = ["tests"]
```

**Step 3: Write config module with test**

`pipeline/src/pipeline/config.py`:
```python
from pathlib import Path
from pydantic import BaseModel


class PipelineConfig(BaseModel):
    repo_root: Path
    commcare_core_path: Path
    max_parallel_agents: int = 3
    github_repo: str = ""
    auto_merge_enabled: bool = False

    @classmethod
    def from_env(cls, repo_root: Path | None = None) -> "PipelineConfig":
        root = repo_root or Path.cwd()
        return cls(
            repo_root=root,
            commcare_core_path=root.parent / "commcare-core",
        )
```

`pipeline/tests/test_config.py`:
```python
from pathlib import Path
from pipeline.config import PipelineConfig


def test_config_defaults():
    config = PipelineConfig(
        repo_root=Path("/tmp/test"),
        commcare_core_path=Path("/tmp/commcare-core"),
    )
    assert config.max_parallel_agents == 3
    assert config.auto_merge_enabled is False


def test_config_from_env(tmp_path):
    config = PipelineConfig.from_env(repo_root=tmp_path)
    assert config.repo_root == tmp_path
    assert config.commcare_core_path == tmp_path.parent / "commcare-core"
```

**Step 4: Install and run tests**

```bash
cd pipeline
pip install -e ".[dev]"
pytest tests/test_config.py -v
```

Expected: 2 tests PASS.

**Step 5: Commit**

```bash
git add pipeline/
git commit -m "feat: initialize pipeline Python project with config module"
```

---

## Task 2: Task Schema and Models

**Files:**
- Create: `pipeline/src/pipeline/models.py`
- Create: `pipeline/tests/test_models.py`

**Step 1: Write the failing test**

`pipeline/tests/test_models.py`:
```python
from pipeline.models import TaskSpec, TaskPhase, TaskStatus, WaveAssignment


def test_task_spec_creation():
    task = TaskSpec(
        id="core-port-javarosa-util",
        title="Port org.javarosa.core.util to Kotlin",
        phase=TaskPhase.CORE_PORT,
        wave=1,
        description="Convert all Java files in org.javarosa.core.util to Kotlin.",
        files_to_read=["src/main/java/org/javarosa/core/util/*.java"],
        files_to_modify=["src/main/kotlin/org/javarosa/core/util/*.kt"],
        test_criteria=["All existing tests in org.javarosa.core.util pass"],
        depends_on=[],
        labels=["phase/1", "wave/1", "auto-merge"],
    )
    assert task.phase == TaskPhase.CORE_PORT
    assert task.wave == 1
    assert len(task.depends_on) == 0


def test_task_spec_with_dependencies():
    task = TaskSpec(
        id="core-port-javarosa-model",
        title="Port org.javarosa.core.model to Kotlin",
        phase=TaskPhase.CORE_PORT,
        wave=2,
        description="Convert core model classes.",
        files_to_read=["src/main/java/org/javarosa/core/model/*.java"],
        files_to_modify=["src/main/kotlin/org/javarosa/core/model/*.kt"],
        test_criteria=["All existing model tests pass"],
        depends_on=["core-port-javarosa-util", "core-port-javarosa-data"],
        labels=["phase/1", "wave/2"],
    )
    assert len(task.depends_on) == 2
    assert task.status == TaskStatus.PENDING


def test_wave_assignment():
    wave = WaveAssignment(
        wave_number=1,
        task_ids=["a", "b", "c"],
        estimated_parallelism=3,
    )
    assert wave.wave_number == 1
    assert len(wave.task_ids) == 3


def test_task_to_github_issue_body():
    task = TaskSpec(
        id="core-port-javarosa-util",
        title="Port org.javarosa.core.util to Kotlin",
        phase=TaskPhase.CORE_PORT,
        wave=1,
        description="Convert all Java files in org.javarosa.core.util to Kotlin.",
        files_to_read=["src/main/java/org/javarosa/core/util/ArrayUtilities.java"],
        files_to_modify=["src/main/kotlin/org/javarosa/core/util/ArrayUtilities.kt"],
        test_criteria=[
            "org.javarosa.core.util.ArrayUtilitiesTest passes",
            "No compilation errors in dependent packages",
        ],
        depends_on=[],
        labels=["phase/1", "wave/1", "auto-merge"],
    )
    body = task.to_github_issue_body()
    assert "## Context" in body
    assert "## Files to Read" in body
    assert "## What to Do" in body
    assert "## Tests That Must Pass" in body
    assert "## Dependencies" in body
    assert "ArrayUtilities.java" in body
```

**Step 2: Run test to verify it fails**

```bash
cd pipeline && pytest tests/test_models.py -v
```

Expected: FAIL (module not found).

**Step 3: Write the implementation**

`pipeline/src/pipeline/models.py`:
```python
from enum import Enum
from pydantic import BaseModel


class TaskPhase(str, Enum):
    SCAFFOLD = "scaffold"
    CORE_PORT = "core_port"
    APP_SHELL = "app_shell"
    FEATURES = "features"
    POLISH = "polish"


class TaskStatus(str, Enum):
    PENDING = "pending"
    READY = "ready"
    IN_PROGRESS = "in_progress"
    COMPLETED = "completed"
    FAILED = "failed"


class TaskSpec(BaseModel):
    id: str
    title: str
    phase: TaskPhase
    wave: int
    description: str
    files_to_read: list[str]
    files_to_modify: list[str]
    test_criteria: list[str]
    depends_on: list[str]
    labels: list[str]
    status: TaskStatus = TaskStatus.PENDING

    def to_github_issue_body(self) -> str:
        sections = []

        sections.append(f"## Context\n\n{self.description}")

        files_read = "\n".join(f"- `{f}`" for f in self.files_to_read)
        sections.append(f"## Files to Read\n\n{files_read}")

        sections.append(
            "## What to Do\n\n"
            "1. Read the files listed above to understand the current implementation.\n"
            "2. Port the Java code to idiomatic Kotlin.\n"
            "3. Run the tests listed below and ensure they all pass.\n"
            "4. Create a PR with the changes."
        )

        files_modify = "\n".join(f"- `{f}`" for f in self.files_to_modify)
        sections.append(f"## Files to Modify\n\n{files_modify}")

        tests = "\n".join(f"- [ ] {t}" for t in self.test_criteria)
        sections.append(f"## Tests That Must Pass\n\n{tests}")

        if self.depends_on:
            deps = "\n".join(f"- #{d}" for d in self.depends_on)
            sections.append(f"## Dependencies\n\nThis task depends on:\n{deps}")
        else:
            sections.append("## Dependencies\n\nNone — this task can start immediately.")

        metadata = (
            f"**Phase:** {self.phase.value} | "
            f"**Wave:** {self.wave} | "
            f"**ID:** {self.id}"
        )
        sections.append(f"## Metadata\n\n{metadata}")

        return "\n\n".join(sections)


class WaveAssignment(BaseModel):
    wave_number: int
    task_ids: list[str]
    estimated_parallelism: int
```

**Step 4: Run tests to verify they pass**

```bash
cd pipeline && pytest tests/test_models.py -v
```

Expected: 4 tests PASS.

**Step 5: Commit**

```bash
git add pipeline/src/pipeline/models.py pipeline/tests/test_models.py
git commit -m "feat: add task spec models with GitHub Issue body generation"
```

---

## Task 3: GitHub Issue Client

**Files:**
- Create: `pipeline/src/pipeline/github_client.py`
- Create: `pipeline/tests/test_github_client.py`

**Step 1: Write the failing test**

`pipeline/tests/test_github_client.py`:
```python
import json
import subprocess
from unittest.mock import patch, MagicMock
from pipeline.github_client import GitHubClient
from pipeline.models import TaskSpec, TaskPhase


def _make_task(task_id: str = "test-task", depends_on: list[str] | None = None) -> TaskSpec:
    return TaskSpec(
        id=task_id,
        title=f"Test task {task_id}",
        phase=TaskPhase.CORE_PORT,
        wave=1,
        description="Test description",
        files_to_read=["file.java"],
        files_to_modify=["file.kt"],
        test_criteria=["tests pass"],
        depends_on=depends_on or [],
        labels=["phase/1", "wave/1"],
    )


@patch("pipeline.github_client.subprocess.run")
def test_create_issue(mock_run):
    mock_run.return_value = MagicMock(
        returncode=0,
        stdout=json.dumps({"number": 42, "url": "https://github.com/test/repo/issues/42"}),
    )
    client = GitHubClient(repo="test/repo", dry_run=False)
    result = client.create_issue(_make_task())
    assert result["number"] == 42
    mock_run.assert_called_once()
    cmd = mock_run.call_args[0][0]
    assert "gh" in cmd[0]
    assert "issue" in cmd
    assert "create" in cmd


@patch("pipeline.github_client.subprocess.run")
def test_create_issue_dry_run(mock_run):
    client = GitHubClient(repo="test/repo", dry_run=True)
    result = client.create_issue(_make_task())
    assert result["number"] == 0
    assert result["dry_run"] is True
    mock_run.assert_not_called()


@patch("pipeline.github_client.subprocess.run")
def test_list_issues(mock_run):
    mock_run.return_value = MagicMock(
        returncode=0,
        stdout=json.dumps([
            {"number": 1, "title": "Task 1", "labels": [{"name": "ready"}]},
            {"number": 2, "title": "Task 2", "labels": [{"name": "in-progress"}]},
        ]),
    )
    client = GitHubClient(repo="test/repo", dry_run=False)
    issues = client.list_issues(labels=["ready"])
    assert len(issues) == 2


@patch("pipeline.github_client.subprocess.run")
def test_add_labels(mock_run):
    mock_run.return_value = MagicMock(returncode=0, stdout="")
    client = GitHubClient(repo="test/repo", dry_run=False)
    client.add_labels(issue_number=42, labels=["ready", "auto-merge"])
    mock_run.assert_called_once()
```

**Step 2: Run test to verify it fails**

```bash
cd pipeline && pytest tests/test_github_client.py -v
```

Expected: FAIL.

**Step 3: Write the implementation**

`pipeline/src/pipeline/github_client.py`:
```python
import json
import subprocess
from pipeline.models import TaskSpec


class GitHubClient:
    def __init__(self, repo: str, dry_run: bool = True):
        self.repo = repo
        self.dry_run = dry_run

    def _run_gh(self, args: list[str], input_text: str | None = None) -> str:
        cmd = ["gh"] + args + ["-R", self.repo]
        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            input=input_text,
        )
        if result.returncode != 0:
            raise RuntimeError(f"gh command failed: {result.stderr}")
        return result.stdout

    def create_issue(self, task: TaskSpec) -> dict:
        if self.dry_run:
            return {
                "number": 0,
                "url": f"https://github.com/{self.repo}/issues/DRY_RUN",
                "title": task.title,
                "dry_run": True,
            }

        body = task.to_github_issue_body()
        label_args = []
        for label in task.labels:
            label_args.extend(["--label", label])

        args = [
            "issue", "create",
            "--title", task.title,
            "--body", body,
        ] + label_args

        output = self._run_gh(args)
        # gh issue create outputs JSON when --json is used, or URL otherwise
        # Let's use --json for structured output
        # Actually, gh issue create doesn't support --json, it returns the URL
        # We need to parse the issue number from the URL
        url = output.strip()
        number = int(url.rstrip("/").split("/")[-1]) if url else 0
        return {"number": number, "url": url}

    def list_issues(self, labels: list[str] | None = None, state: str = "open") -> list[dict]:
        args = ["issue", "list", "--state", state, "--json", "number,title,labels,assignees"]
        if labels:
            for label in labels:
                args.extend(["--label", label])
        output = self._run_gh(args)
        return json.loads(output) if output.strip() else []

    def add_labels(self, issue_number: int, labels: list[str]) -> None:
        if self.dry_run:
            return
        args = ["issue", "edit", str(issue_number)]
        for label in labels:
            args.extend(["--add-label", label])
        self._run_gh(args)

    def close_issue(self, issue_number: int) -> None:
        if self.dry_run:
            return
        self._run_gh(["issue", "close", str(issue_number)])

    def get_issue(self, issue_number: int) -> dict:
        output = self._run_gh([
            "issue", "view", str(issue_number),
            "--json", "number,title,body,labels,assignees,state",
        ])
        return json.loads(output)
```

**Step 4: Run tests**

```bash
cd pipeline && pytest tests/test_github_client.py -v
```

Expected: 4 tests PASS.

**Step 5: Commit**

```bash
git add pipeline/src/pipeline/github_client.py pipeline/tests/test_github_client.py
git commit -m "feat: add GitHub Issue client for task queue management"
```

---

## Task 4: commcare-core Package Analyzer

**Files:**
- Create: `pipeline/src/pipeline/analyzer.py`
- Create: `pipeline/tests/test_analyzer.py`
- Create: `pipeline/tests/fixtures/fake_java_project/` (test fixture)

This script scans the commcare-core Java source tree, maps packages, determines inter-package dependencies, and assigns dependency waves.

**Step 1: Write the failing test**

`pipeline/tests/test_analyzer.py`:
```python
from pathlib import Path
from pipeline.analyzer import PackageAnalyzer, PackageInfo, DependencyGraph


FIXTURE_DIR = Path(__file__).parent / "fixtures" / "fake_java_project"


def test_discover_packages(tmp_path):
    # Create fake Java source tree
    pkg_a = tmp_path / "src/main/java/org/example/core/util"
    pkg_a.mkdir(parents=True)
    (pkg_a / "StringUtils.java").write_text(
        "package org.example.core.util;\n"
        "public class StringUtils { }\n"
    )
    (pkg_a / "MathUtils.java").write_text(
        "package org.example.core.util;\n"
        "public class MathUtils { }\n"
    )

    pkg_b = tmp_path / "src/main/java/org/example/core/model"
    pkg_b.mkdir(parents=True)
    (pkg_b / "FormDef.java").write_text(
        "package org.example.core.model;\n"
        "import org.example.core.util.StringUtils;\n"
        "public class FormDef { }\n"
    )

    analyzer = PackageAnalyzer(source_root=tmp_path / "src/main/java")
    packages = analyzer.discover_packages()

    assert len(packages) == 2
    names = {p.package_name for p in packages}
    assert "org.example.core.util" in names
    assert "org.example.core.model" in names


def test_analyze_dependencies(tmp_path):
    pkg_a = tmp_path / "src/main/java/org/example/core/util"
    pkg_a.mkdir(parents=True)
    (pkg_a / "StringUtils.java").write_text(
        "package org.example.core.util;\n"
        "public class StringUtils { }\n"
    )

    pkg_b = tmp_path / "src/main/java/org/example/core/model"
    pkg_b.mkdir(parents=True)
    (pkg_b / "FormDef.java").write_text(
        "package org.example.core.model;\n"
        "import org.example.core.util.StringUtils;\n"
        "public class FormDef {\n"
        "    StringUtils utils = new StringUtils();\n"
        "}\n"
    )

    analyzer = PackageAnalyzer(source_root=tmp_path / "src/main/java")
    packages = analyzer.discover_packages()
    graph = analyzer.build_dependency_graph(packages)

    model_deps = graph.dependencies_of("org.example.core.model")
    assert "org.example.core.util" in model_deps

    util_deps = graph.dependencies_of("org.example.core.util")
    assert len(util_deps) == 0


def test_assign_waves(tmp_path):
    pkg_a = tmp_path / "src/main/java/org/example/a"
    pkg_a.mkdir(parents=True)
    (pkg_a / "A.java").write_text("package org.example.a;\npublic class A { }\n")

    pkg_b = tmp_path / "src/main/java/org/example/b"
    pkg_b.mkdir(parents=True)
    (pkg_b / "B.java").write_text(
        "package org.example.b;\nimport org.example.a.A;\npublic class B { }\n"
    )

    pkg_c = tmp_path / "src/main/java/org/example/c"
    pkg_c.mkdir(parents=True)
    (pkg_c / "C.java").write_text(
        "package org.example.c;\nimport org.example.b.B;\npublic class C { }\n"
    )

    analyzer = PackageAnalyzer(source_root=tmp_path / "src/main/java")
    packages = analyzer.discover_packages()
    graph = analyzer.build_dependency_graph(packages)
    waves = graph.assign_waves()

    # a has no deps -> wave 1
    # b depends on a -> wave 2
    # c depends on b -> wave 3
    assert waves["org.example.a"] == 1
    assert waves["org.example.b"] == 2
    assert waves["org.example.c"] == 3


def test_generate_task_specs(tmp_path):
    pkg_a = tmp_path / "src/main/java/org/example/util"
    pkg_a.mkdir(parents=True)
    (pkg_a / "Helper.java").write_text("package org.example.util;\npublic class Helper { }\n")

    analyzer = PackageAnalyzer(source_root=tmp_path / "src/main/java")
    packages = analyzer.discover_packages()
    graph = analyzer.build_dependency_graph(packages)
    tasks = analyzer.generate_task_specs(packages, graph)

    assert len(tasks) == 1
    task = tasks[0]
    assert task.phase.value == "core_port"
    assert "org.example.util" in task.title
    assert task.wave == 1
```

**Step 2: Run test to verify it fails**

```bash
cd pipeline && pytest tests/test_analyzer.py -v
```

Expected: FAIL.

**Step 3: Write the implementation**

`pipeline/src/pipeline/analyzer.py`:
```python
import re
from dataclasses import dataclass, field
from pathlib import Path
from pipeline.models import TaskSpec, TaskPhase


@dataclass
class PackageInfo:
    package_name: str
    java_files: list[Path]
    source_root: Path

    @property
    def file_count(self) -> int:
        return len(self.java_files)

    @property
    def relative_path(self) -> str:
        return self.package_name.replace(".", "/")


@dataclass
class DependencyGraph:
    """Directed graph of package dependencies."""
    _deps: dict[str, set[str]] = field(default_factory=dict)
    _all_packages: set[str] = field(default_factory=set)

    def add_package(self, name: str) -> None:
        self._all_packages.add(name)
        if name not in self._deps:
            self._deps[name] = set()

    def add_dependency(self, from_pkg: str, to_pkg: str) -> None:
        if from_pkg not in self._deps:
            self._deps[from_pkg] = set()
        self._deps[from_pkg].add(to_pkg)

    def dependencies_of(self, package: str) -> set[str]:
        return self._deps.get(package, set())

    def assign_waves(self) -> dict[str, int]:
        """Topological sort into waves. Wave 1 = no dependencies."""
        waves: dict[str, int] = {}
        remaining = set(self._all_packages)

        wave_num = 0
        while remaining:
            wave_num += 1
            # Find packages whose dependencies are all already assigned
            current_wave = set()
            for pkg in remaining:
                deps = self._deps.get(pkg, set()) & self._all_packages
                if all(d in waves for d in deps):
                    current_wave.add(pkg)

            if not current_wave:
                # Circular dependency — assign remaining to current wave
                for pkg in remaining:
                    waves[pkg] = wave_num
                break

            for pkg in current_wave:
                waves[pkg] = wave_num
            remaining -= current_wave

        return waves


IMPORT_PATTERN = re.compile(r"^import\s+([\w.]+)\s*;", re.MULTILINE)
PACKAGE_PATTERN = re.compile(r"^package\s+([\w.]+)\s*;", re.MULTILINE)


class PackageAnalyzer:
    def __init__(self, source_root: Path):
        self.source_root = source_root

    def discover_packages(self) -> list[PackageInfo]:
        """Find all Java packages in the source tree."""
        packages: dict[str, list[Path]] = {}

        for java_file in self.source_root.rglob("*.java"):
            content = java_file.read_text(errors="replace")
            match = PACKAGE_PATTERN.search(content)
            if match:
                pkg_name = match.group(1)
                if pkg_name not in packages:
                    packages[pkg_name] = []
                packages[pkg_name].append(java_file)

        return [
            PackageInfo(
                package_name=name,
                java_files=sorted(files),
                source_root=self.source_root,
            )
            for name, files in sorted(packages.items())
        ]

    def build_dependency_graph(self, packages: list[PackageInfo]) -> DependencyGraph:
        """Analyze imports to determine inter-package dependencies."""
        graph = DependencyGraph()
        known_packages = {p.package_name for p in packages}

        for pkg in packages:
            graph.add_package(pkg.package_name)

        for pkg in packages:
            pkg_imports: set[str] = set()
            for java_file in pkg.java_files:
                content = java_file.read_text(errors="replace")
                for match in IMPORT_PATTERN.finditer(content):
                    imported = match.group(1)
                    # Extract package from fully qualified class name
                    import_pkg = ".".join(imported.split(".")[:-1])
                    if import_pkg in known_packages and import_pkg != pkg.package_name:
                        pkg_imports.add(import_pkg)

            for dep in pkg_imports:
                graph.add_dependency(pkg.package_name, dep)

        return graph

    def generate_task_specs(
        self, packages: list[PackageInfo], graph: DependencyGraph
    ) -> list[TaskSpec]:
        """Generate TaskSpec objects for each package port."""
        waves = graph.assign_waves()
        tasks = []

        for pkg in packages:
            wave = waves.get(pkg.package_name, 1)
            deps = graph.dependencies_of(pkg.package_name)
            dep_task_ids = [
                f"core-port-{d.replace('.', '-')}" for d in sorted(deps)
            ]

            java_files = [
                str(f.relative_to(pkg.source_root.parent.parent.parent))
                for f in pkg.java_files
            ]

            task = TaskSpec(
                id=f"core-port-{pkg.package_name.replace('.', '-')}",
                title=f"Port {pkg.package_name} to Kotlin",
                phase=TaskPhase.CORE_PORT,
                wave=wave,
                description=(
                    f"Convert all {pkg.file_count} Java files in "
                    f"`{pkg.package_name}` to idiomatic Kotlin.\n\n"
                    f"Use IntelliJ's Java-to-Kotlin converter as a starting point, "
                    f"then clean up for idiomatic Kotlin (data classes, null safety, "
                    f"extension functions where appropriate)."
                ),
                files_to_read=java_files,
                files_to_modify=[
                    f.replace("/java/", "/kotlin/").replace(".java", ".kt")
                    for f in java_files
                ],
                test_criteria=[
                    f"All existing tests that reference {pkg.package_name} pass",
                    "No compilation errors in dependent packages",
                    "Kotlin code passes ktlint checks",
                ],
                depends_on=dep_task_ids,
                labels=[f"phase/1", f"wave/{wave}", "auto-merge"],
            )
            tasks.append(task)

        return sorted(tasks, key=lambda t: (t.wave, t.id))
```

**Step 4: Run tests**

```bash
cd pipeline && pytest tests/test_analyzer.py -v
```

Expected: 4 tests PASS.

**Step 5: Commit**

```bash
git add pipeline/src/pipeline/analyzer.py pipeline/tests/test_analyzer.py
git commit -m "feat: add commcare-core package analyzer with dependency wave assignment"
```

---

## Task 5: Task Generator CLI

**Files:**
- Create: `pipeline/src/pipeline/generate_tasks.py`
- Create: `pipeline/tests/test_generate_tasks.py`

This is the CLI entry point that ties the analyzer to the GitHub client: scan commcare-core, generate TaskSpecs, create GitHub Issues.

**Step 1: Write the failing test**

`pipeline/tests/test_generate_tasks.py`:
```python
from pathlib import Path
from unittest.mock import patch, MagicMock
from pipeline.generate_tasks import generate_phase1_tasks


def test_generate_phase1_tasks_dry_run(tmp_path):
    # Create minimal fake Java project
    pkg = tmp_path / "src/main/java/org/example/util"
    pkg.mkdir(parents=True)
    (pkg / "Helper.java").write_text(
        "package org.example.util;\npublic class Helper { }\n"
    )

    results = generate_phase1_tasks(
        source_root=tmp_path / "src/main/java",
        repo="test/repo",
        dry_run=True,
    )

    assert len(results) == 1
    assert results[0]["dry_run"] is True
    assert "org.example.util" in results[0]["title"]
```

**Step 2: Run to verify failure, then implement**

`pipeline/src/pipeline/generate_tasks.py`:
```python
import argparse
import json
from pathlib import Path
from pipeline.analyzer import PackageAnalyzer
from pipeline.github_client import GitHubClient


def generate_phase1_tasks(
    source_root: Path,
    repo: str,
    dry_run: bool = True,
) -> list[dict]:
    analyzer = PackageAnalyzer(source_root=source_root)
    packages = analyzer.discover_packages()
    graph = analyzer.build_dependency_graph(packages)
    tasks = analyzer.generate_task_specs(packages, graph)

    client = GitHubClient(repo=repo, dry_run=dry_run)
    results = []
    for task in tasks:
        result = client.create_issue(task)
        results.append(result)
        if dry_run:
            print(f"[DRY RUN] Would create: {task.title} (wave {task.wave})")
        else:
            print(f"Created issue #{result['number']}: {task.title}")

    print(f"\nTotal: {len(tasks)} tasks across {max(t.wave for t in tasks)} waves")
    return results


def main():
    parser = argparse.ArgumentParser(description="Generate Phase 1 tasks for commcare-core port")
    parser.add_argument("source_root", type=Path, help="Path to commcare-core src/main/java")
    parser.add_argument("--repo", required=True, help="GitHub repo (owner/name)")
    parser.add_argument("--dry-run", action="store_true", default=True, help="Print tasks without creating issues")
    parser.add_argument("--create", action="store_true", help="Actually create GitHub Issues")
    args = parser.parse_args()

    generate_phase1_tasks(
        source_root=args.source_root,
        repo=args.repo,
        dry_run=not args.create,
    )


if __name__ == "__main__":
    main()
```

**Step 3: Run tests**

```bash
cd pipeline && pytest tests/test_generate_tasks.py -v
```

Expected: PASS.

**Step 4: Commit**

```bash
git add pipeline/src/pipeline/generate_tasks.py pipeline/tests/test_generate_tasks.py
git commit -m "feat: add task generator CLI for Phase 1 commcare-core port"
```

---

## Task 6: Orchestrator — Agent Session Manager

**Files:**
- Create: `pipeline/src/pipeline/orchestrator.py`
- Create: `pipeline/tests/test_orchestrator.py`

The orchestrator manages parallel Claude Code sessions. Each session:
1. Claims a task (GitHub Issue)
2. Creates a git worktree
3. Runs Claude Code with the task spec as prompt
4. Monitors completion
5. Reports results

**Step 1: Write the failing test**

`pipeline/tests/test_orchestrator.py`:
```python
import json
from unittest.mock import patch, MagicMock, AsyncMock
from pipeline.orchestrator import Orchestrator, AgentSession, SessionStatus


def test_agent_session_creation():
    session = AgentSession(
        task_id="test-task",
        issue_number=42,
        worktree_path="/tmp/worktree-42",
        branch_name="task/test-task",
    )
    assert session.status == SessionStatus.PENDING
    assert session.task_id == "test-task"


@patch("pipeline.orchestrator.subprocess.Popen")
def test_orchestrator_spawn_session(mock_popen):
    mock_process = MagicMock()
    mock_process.pid = 12345
    mock_process.poll.return_value = None  # Still running
    mock_popen.return_value = mock_process

    orch = Orchestrator(
        repo="test/repo",
        repo_root="/tmp/test-repo",
        max_parallel=3,
        dry_run=True,
    )
    session = orch.create_session(
        task_id="test-task",
        issue_number=42,
        prompt="Implement the thing",
    )
    assert session.status == SessionStatus.PENDING
    assert session.task_id == "test-task"


def test_orchestrator_respects_max_parallel():
    orch = Orchestrator(
        repo="test/repo",
        repo_root="/tmp/test-repo",
        max_parallel=2,
        dry_run=True,
    )
    assert orch.max_parallel == 2
    assert orch.active_session_count() == 0


def test_orchestrator_build_prompt():
    orch = Orchestrator(
        repo="test/repo",
        repo_root="/tmp/test-repo",
        max_parallel=3,
        dry_run=True,
    )
    issue_body = "## Context\n\nPort org.javarosa.core.util to Kotlin."
    prompt = orch.build_agent_prompt(
        issue_number=42,
        issue_body=issue_body,
    )
    assert "issue #42" in prompt.lower() or "#42" in prompt
    assert "org.javarosa.core.util" in prompt
    assert "test" in prompt.lower()  # Should mention running tests
    assert "PR" in prompt or "pull request" in prompt.lower()
```

**Step 2: Run to verify failure, then implement**

`pipeline/src/pipeline/orchestrator.py`:
```python
import subprocess
import time
from dataclasses import dataclass, field
from enum import Enum
from pathlib import Path


class SessionStatus(str, Enum):
    PENDING = "pending"
    RUNNING = "running"
    COMPLETED = "completed"
    FAILED = "failed"


@dataclass
class AgentSession:
    task_id: str
    issue_number: int
    worktree_path: str
    branch_name: str
    status: SessionStatus = SessionStatus.PENDING
    process: subprocess.Popen | None = None
    start_time: float | None = None
    end_time: float | None = None
    output: str = ""

    @property
    def duration_seconds(self) -> float | None:
        if self.start_time and self.end_time:
            return self.end_time - self.start_time
        return None


class Orchestrator:
    def __init__(
        self,
        repo: str,
        repo_root: str,
        max_parallel: int = 3,
        dry_run: bool = True,
    ):
        self.repo = repo
        self.repo_root = Path(repo_root)
        self.max_parallel = max_parallel
        self.dry_run = dry_run
        self._sessions: dict[str, AgentSession] = {}

    def active_session_count(self) -> int:
        return sum(
            1 for s in self._sessions.values()
            if s.status == SessionStatus.RUNNING
        )

    def create_session(
        self,
        task_id: str,
        issue_number: int,
        prompt: str,
    ) -> AgentSession:
        branch_name = f"task/{task_id}"
        worktree_path = str(self.repo_root / ".claude" / "worktrees" / task_id)

        session = AgentSession(
            task_id=task_id,
            issue_number=issue_number,
            worktree_path=worktree_path,
            branch_name=branch_name,
        )
        self._sessions[task_id] = session
        return session

    def start_session(self, session: AgentSession, prompt: str) -> None:
        if self.dry_run:
            print(f"[DRY RUN] Would start session for task {session.task_id}")
            session.status = SessionStatus.RUNNING
            return

        # Create worktree
        subprocess.run(
            ["git", "worktree", "add", session.worktree_path, "-b", session.branch_name],
            cwd=self.repo_root,
            check=True,
        )

        # Spawn claude session
        session.process = subprocess.Popen(
            ["claude", "--print", "--dangerously-skip-permissions", prompt],
            cwd=session.worktree_path,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
        session.status = SessionStatus.RUNNING
        session.start_time = time.time()

    def check_sessions(self) -> list[AgentSession]:
        """Check running sessions, return newly completed ones."""
        completed = []
        for session in self._sessions.values():
            if session.status != SessionStatus.RUNNING or session.process is None:
                continue
            retcode = session.process.poll()
            if retcode is not None:
                session.end_time = time.time()
                session.output = session.process.stdout.read() if session.process.stdout else ""
                session.status = (
                    SessionStatus.COMPLETED if retcode == 0
                    else SessionStatus.FAILED
                )
                completed.append(session)
        return completed

    def build_agent_prompt(self, issue_number: int, issue_body: str) -> str:
        return (
            f"You are implementing GitHub Issue #{issue_number} for the CommCare "
            f"core Kotlin port project.\n\n"
            f"## Task\n\n{issue_body}\n\n"
            f"## Instructions\n\n"
            f"1. Read all files listed in 'Files to Read' to understand the code.\n"
            f"2. Port the Java code to idiomatic Kotlin.\n"
            f"3. Run the test suite to verify all tests pass.\n"
            f"4. Create a PR with your changes.\n\n"
            f"## Important\n"
            f"- Run tests before creating the PR.\n"
            f"- The PR title should reference issue #{issue_number}.\n"
            f"- If tests fail and you cannot fix them after 2 attempts, "
            f"add a comment to the issue explaining what went wrong.\n"
        )

    def get_all_sessions(self) -> list[AgentSession]:
        return list(self._sessions.values())
```

**Step 3: Run tests**

```bash
cd pipeline && pytest tests/test_orchestrator.py -v
```

Expected: 4 tests PASS.

**Step 4: Commit**

```bash
git add pipeline/src/pipeline/orchestrator.py pipeline/tests/test_orchestrator.py
git commit -m "feat: add orchestrator for managing parallel Claude Code sessions"
```

---

## Task 7: Behavior Catalog

**Files:**
- Create: `pipeline/src/pipeline/behavior_catalog.py`
- Create: `pipeline/tests/test_behavior_catalog.py`
- Create: `pipeline/behavior_catalog.yaml`

**Step 1: Write the test**

`pipeline/tests/test_behavior_catalog.py`:
```python
from pathlib import Path
from pipeline.behavior_catalog import BehaviorCatalog, BehaviorEntry, OracleType, TestMode


def test_load_catalog(tmp_path):
    catalog_file = tmp_path / "catalog.yaml"
    catalog_file.write_text("""
behaviors:
  - id: xpath_evaluation
    description: XPath expressions should evaluate identically
    oracle: formplayer
    category: engine
    test_mode: output_match
    known_differences: []

  - id: auto_advance_single_select
    description: Mobile auto-advances after single select choice
    oracle: android
    category: mobile_specific
    test_mode: android_parity
    known_differences:
      - "FormPlayer shows Next button; mobile auto-advances"
""")

    catalog = BehaviorCatalog.from_yaml(catalog_file)
    assert len(catalog.behaviors) == 2

    xpath = catalog.get("xpath_evaluation")
    assert xpath is not None
    assert xpath.oracle == OracleType.FORMPLAYER
    assert xpath.test_mode == TestMode.OUTPUT_MATCH

    auto_adv = catalog.get("auto_advance_single_select")
    assert auto_adv is not None
    assert auto_adv.oracle == OracleType.ANDROID
    assert len(auto_adv.known_differences) == 1


def test_catalog_filter_by_oracle(tmp_path):
    catalog_file = tmp_path / "catalog.yaml"
    catalog_file.write_text("""
behaviors:
  - id: a
    description: test
    oracle: formplayer
    category: engine
    test_mode: output_match
    known_differences: []
  - id: b
    description: test
    oracle: android
    category: mobile_specific
    test_mode: android_parity
    known_differences: []
  - id: c
    description: test
    oracle: formplayer
    category: engine
    test_mode: output_match
    known_differences: []
""")

    catalog = BehaviorCatalog.from_yaml(catalog_file)
    fp_behaviors = catalog.filter_by_oracle(OracleType.FORMPLAYER)
    assert len(fp_behaviors) == 2
    android_behaviors = catalog.filter_by_oracle(OracleType.ANDROID)
    assert len(android_behaviors) == 1
```

**Step 2: Implement**

`pipeline/src/pipeline/behavior_catalog.py`:
```python
from enum import Enum
from pathlib import Path
import yaml
from pydantic import BaseModel


class OracleType(str, Enum):
    FORMPLAYER = "formplayer"
    ANDROID = "android"
    SPEC_ONLY = "spec_only"


class TestMode(str, Enum):
    OUTPUT_MATCH = "output_match"
    ANDROID_PARITY = "android_parity"
    SPEC_CONFORMANCE = "spec_conformance"


class BehaviorEntry(BaseModel):
    id: str
    description: str
    oracle: OracleType
    category: str
    test_mode: TestMode
    known_differences: list[str]


class BehaviorCatalog(BaseModel):
    behaviors: list[BehaviorEntry]

    @classmethod
    def from_yaml(cls, path: Path) -> "BehaviorCatalog":
        with open(path) as f:
            data = yaml.safe_load(f)
        return cls(behaviors=[BehaviorEntry(**b) for b in data["behaviors"]])

    def get(self, behavior_id: str) -> BehaviorEntry | None:
        for b in self.behaviors:
            if b.id == behavior_id:
                return b
        return None

    def filter_by_oracle(self, oracle: OracleType) -> list[BehaviorEntry]:
        return [b for b in self.behaviors if b.oracle == oracle]
```

**Step 3: Create initial behavior catalog**

`pipeline/behavior_catalog.yaml`:
```yaml
# CommCare Behavior Catalog
# Documents known behavioral differences between Web Apps (FormPlayer) and Mobile (Android/iOS).
# Used by the oracle test harness to determine expected outcomes.

behaviors:
  # === ENGINE BEHAVIORS (should match FormPlayer exactly) ===

  - id: xpath_evaluation
    description: XPath expressions evaluate identically across platforms
    oracle: formplayer
    category: engine
    test_mode: output_match
    known_differences: []

  - id: form_validation
    description: Constraint validation produces identical results
    oracle: formplayer
    category: engine
    test_mode: output_match
    known_differences: []

  - id: skip_logic
    description: Relevancy conditions evaluate identically
    oracle: formplayer
    category: engine
    test_mode: output_match
    known_differences: []

  - id: calculations
    description: Calculated values produce identical results
    oracle: formplayer
    category: engine
    test_mode: output_match
    known_differences: []

  - id: case_transactions
    description: Case create/update/close/index operations produce identical XML
    oracle: formplayer
    category: engine
    test_mode: output_match
    known_differences: []

  - id: form_submission_xml
    description: Submitted form instance XML is identical
    oracle: formplayer
    category: engine
    test_mode: output_match
    known_differences: []

  # === MOBILE-SPECIFIC BEHAVIORS (Android is the oracle) ===

  - id: auto_advance_single_select
    description: Mobile auto-advances to next question after single select
    oracle: android
    category: mobile_specific
    test_mode: android_parity
    known_differences:
      - "FormPlayer shows Next button; mobile auto-advances on selection"

  - id: offline_form_save
    description: Forms can be saved incomplete and resumed offline
    oracle: android
    category: mobile_specific
    test_mode: android_parity
    known_differences:
      - "FormPlayer relies on server-side session; mobile saves to local SQLite"

  - id: background_sync
    description: Data syncs in the background periodically
    oracle: android
    category: mobile_specific
    test_mode: android_parity
    known_differences:
      - "FormPlayer syncs on-demand via REST; mobile uses background task scheduler"

  - id: multimedia_capture
    description: Camera, audio, video capture from device hardware
    oracle: android
    category: mobile_specific
    test_mode: android_parity
    known_differences:
      - "FormPlayer uses file upload; mobile captures via native camera/mic APIs"

  - id: gps_capture
    description: GPS location capture from device
    oracle: android
    category: mobile_specific
    test_mode: android_parity
    known_differences:
      - "FormPlayer may use browser geolocation; mobile uses native GPS"

  - id: push_notifications
    description: Push notifications for sync triggers
    oracle: android
    category: mobile_specific
    test_mode: android_parity
    known_differences:
      - "FormPlayer has no push notifications; mobile uses FCM/APNs"

  - id: biometric_auth
    description: Fingerprint/face authentication for login
    oracle: android
    category: mobile_specific
    test_mode: android_parity
    known_differences:
      - "FormPlayer uses Django session auth; mobile supports biometric unlock"

  - id: sync_state_hash
    description: Device computes state hash for sync verification
    oracle: android
    category: mobile_specific
    test_mode: android_parity
    known_differences:
      - "Same algorithm but mobile must compute locally; FormPlayer delegates to server"
```

**Step 4: Run tests and commit**

```bash
cd pipeline && pytest tests/test_behavior_catalog.py -v
git add pipeline/src/pipeline/behavior_catalog.py pipeline/tests/test_behavior_catalog.py pipeline/behavior_catalog.yaml
git commit -m "feat: add behavior catalog for documenting web/mobile differences"
```

---

## Task 8: Metrics Collector and Scorecard

**Files:**
- Create: `pipeline/src/pipeline/metrics.py`
- Create: `pipeline/tests/test_metrics.py`

**Step 1: Write tests**

`pipeline/tests/test_metrics.py`:
```python
from pipeline.metrics import MetricsCollector, RunReport, TestResults


def test_test_results_parity():
    results = TestResults(passed=90, failed=10, total=100)
    assert results.pass_rate == 0.9


def test_run_report_overall_score():
    report = RunReport(
        run_id="run-001",
        unit_tests=TestResults(passed=100, failed=0, total=100),
        oracle_tests=TestResults(passed=80, failed=20, total=100),
        parity_tests=TestResults(passed=30, failed=10, total=40),
        e2e_tests=TestResults(passed=10, failed=5, total=15),
        tasks_completed=50,
        tasks_total=100,
        human_interventions=3,
    )
    assert report.unit_tests.pass_rate == 1.0
    assert report.oracle_tests.pass_rate == 0.8
    assert report.task_completion_rate == 0.5
    scorecard = report.to_scorecard()
    assert "100%" in scorecard  # unit tests
    assert "80%" in scorecard   # oracle tests


def test_metrics_collector():
    collector = MetricsCollector()
    collector.record_task_completion("task-1", duration_seconds=120, success=True)
    collector.record_task_completion("task-2", duration_seconds=60, success=False)
    assert collector.tasks_completed == 1
    assert collector.tasks_failed == 1
    assert collector.avg_task_duration == 90.0
```

**Step 2: Implement**

`pipeline/src/pipeline/metrics.py`:
```python
from dataclasses import dataclass, field
from pydantic import BaseModel


class TestResults(BaseModel):
    passed: int
    failed: int
    total: int

    @property
    def pass_rate(self) -> float:
        return self.passed / self.total if self.total > 0 else 0.0


class RunReport(BaseModel):
    run_id: str
    unit_tests: TestResults
    oracle_tests: TestResults
    parity_tests: TestResults
    e2e_tests: TestResults
    tasks_completed: int
    tasks_total: int
    human_interventions: int

    @property
    def task_completion_rate(self) -> float:
        return self.tasks_completed / self.tasks_total if self.tasks_total > 0 else 0.0

    def to_scorecard(self) -> str:
        def fmt(name: str, results: TestResults) -> str:
            pct = f"{results.pass_rate * 100:.0f}%"
            status = "PASS" if results.pass_rate == 1.0 else "FAIL"
            return f"{name:<20} {results.passed:>5} / {results.total:<5} ({pct:>4}) {status}"

        lines = [
            f"CORRECTNESS REPORT — {self.run_id}",
            "=" * 55,
            fmt("Unit Tests:", self.unit_tests),
            fmt("Oracle Tests:", self.oracle_tests),
            fmt("Parity Tests:", self.parity_tests),
            fmt("E2E Tests:", self.e2e_tests),
            "=" * 55,
            f"Tasks: {self.tasks_completed}/{self.tasks_total} "
            f"({self.task_completion_rate * 100:.0f}%)",
            f"Human Interventions: {self.human_interventions}",
        ]
        return "\n".join(lines)


@dataclass
class MetricsCollector:
    _task_results: list[dict] = field(default_factory=list)

    def record_task_completion(
        self, task_id: str, duration_seconds: float, success: bool
    ) -> None:
        self._task_results.append({
            "task_id": task_id,
            "duration_seconds": duration_seconds,
            "success": success,
        })

    @property
    def tasks_completed(self) -> int:
        return sum(1 for r in self._task_results if r["success"])

    @property
    def tasks_failed(self) -> int:
        return sum(1 for r in self._task_results if not r["success"])

    @property
    def avg_task_duration(self) -> float:
        if not self._task_results:
            return 0.0
        return sum(r["duration_seconds"] for r in self._task_results) / len(
            self._task_results
        )
```

**Step 3: Run tests and commit**

```bash
cd pipeline && pytest tests/test_metrics.py -v
git add pipeline/src/pipeline/metrics.py pipeline/tests/test_metrics.py
git commit -m "feat: add metrics collector and correctness scorecard"
```

---

## Task 9: CI Workflow — Kotlin Tests (Linux)

**Files:**
- Create: `.github/workflows/kotlin-tests.yml`

This workflow runs on every PR and tests the Kotlin/KMP build on Linux (free for public repos).

**Step 1: Write the workflow**

`.github/workflows/kotlin-tests.yml`:
```yaml
name: Kotlin Tests

on:
  pull_request:
    paths:
      - 'commcare-core/**'
      - '*.gradle.kts'
      - 'gradle/**'
  push:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          submodules: recursive

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17

      - uses: gradle/actions/setup-gradle@v4

      - name: Run JVM tests
        run: ./gradlew :commcare-core:jvmTest --no-daemon

      - name: Check Kotlin compilation for iOS target
        run: ./gradlew :commcare-core:compileKotlinIosSimulatorArm64 --no-daemon

      - name: Upload test results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-results
          path: commcare-core/build/reports/tests/
```

**Step 2: Commit**

```bash
git add .github/workflows/kotlin-tests.yml
git commit -m "ci: add Kotlin test workflow for commcare-core KMP"
```

---

## Task 10: CI Workflow — iOS Build (macOS)

**Files:**
- Create: `.github/workflows/ios-build.yml`

**Step 1: Write the workflow**

`.github/workflows/ios-build.yml`:
```yaml
name: iOS Build & Test

on:
  pull_request:
    paths:
      - 'app/**'
      - 'commcare-core/**'
      - '*.gradle.kts'
  push:
    branches: [main]

jobs:
  build:
    runs-on: macos-14  # Apple Silicon runner
    steps:
      - uses: actions/checkout@v4
        with:
          submodules: recursive

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17

      - uses: gradle/actions/setup-gradle@v4

      - name: Build iOS framework
        run: ./gradlew :commcare-core:linkDebugFrameworkIosSimulatorArm64 --no-daemon

      - name: Build iOS app
        run: ./gradlew :app:composeApp:iosSimulatorArm64Test --no-daemon

      - name: Upload test results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: ios-test-results
          path: app/composeApp/build/reports/tests/
```

**Step 2: Commit**

```bash
git add .github/workflows/ios-build.yml
git commit -m "ci: add iOS build and test workflow using macOS runner"
```

---

## Task 11: Pipeline Run Script (Main Entry Point)

**Files:**
- Create: `pipeline/src/pipeline/run.py`
- Create: `pipeline/tests/test_run.py`

This is the main entry point that ties everything together: reads the task queue, spawns agents, monitors progress, generates reports.

**Step 1: Write test**

`pipeline/tests/test_run.py`:
```python
from unittest.mock import patch, MagicMock
from pipeline.run import PipelineRunner


def test_pipeline_runner_init():
    runner = PipelineRunner(
        repo="dimagi/commcare-ios",
        repo_root="/tmp/test",
        max_parallel=3,
        dry_run=True,
    )
    assert runner.dry_run is True


@patch("pipeline.run.GitHubClient")
def test_pipeline_find_ready_tasks(mock_client_cls):
    mock_client = MagicMock()
    mock_client.list_issues.return_value = [
        {"number": 1, "title": "Task 1", "labels": [{"name": "ready"}], "assignees": []},
        {"number": 2, "title": "Task 2", "labels": [{"name": "ready"}], "assignees": [{"login": "bot"}]},
    ]
    mock_client_cls.return_value = mock_client

    runner = PipelineRunner(
        repo="test/repo",
        repo_root="/tmp/test",
        max_parallel=3,
        dry_run=True,
    )
    ready = runner.find_ready_tasks()
    # Should only return unassigned tasks
    assert len(ready) == 1
    assert ready[0]["number"] == 1
```

**Step 2: Implement**

`pipeline/src/pipeline/run.py`:
```python
import argparse
import time
from pathlib import Path
from pipeline.github_client import GitHubClient
from pipeline.orchestrator import Orchestrator
from pipeline.metrics import MetricsCollector


class PipelineRunner:
    def __init__(
        self,
        repo: str,
        repo_root: str,
        max_parallel: int = 3,
        dry_run: bool = True,
    ):
        self.repo = repo
        self.repo_root = repo_root
        self.max_parallel = max_parallel
        self.dry_run = dry_run
        self.github = GitHubClient(repo=repo, dry_run=dry_run)
        self.orchestrator = Orchestrator(
            repo=repo,
            repo_root=repo_root,
            max_parallel=max_parallel,
            dry_run=dry_run,
        )
        self.metrics = MetricsCollector()

    def find_ready_tasks(self) -> list[dict]:
        """Find issues labeled 'ready' that are unassigned."""
        issues = self.github.list_issues(labels=["ready"])
        return [
            issue for issue in issues
            if not issue.get("assignees")
        ]

    def run_cycle(self) -> None:
        """Run one cycle: find tasks, spawn agents, check completions."""
        # Check for completed sessions
        completed = self.orchestrator.check_sessions()
        for session in completed:
            self.metrics.record_task_completion(
                task_id=session.task_id,
                duration_seconds=session.duration_seconds or 0,
                success=session.status.value == "completed",
            )
            print(f"Session {session.task_id}: {session.status.value}")

        # Spawn new sessions if capacity available
        available_slots = self.max_parallel - self.orchestrator.active_session_count()
        if available_slots <= 0:
            return

        ready_tasks = self.find_ready_tasks()
        for task in ready_tasks[:available_slots]:
            issue = self.github.get_issue(task["number"]) if not self.dry_run else task
            prompt = self.orchestrator.build_agent_prompt(
                issue_number=task["number"],
                issue_body=issue.get("body", task.get("title", "")),
            )
            session = self.orchestrator.create_session(
                task_id=f"issue-{task['number']}",
                issue_number=task["number"],
                prompt=prompt,
            )
            self.orchestrator.start_session(session, prompt)
            print(f"Started session for issue #{task['number']}")

    def run(self, max_cycles: int = 100, cycle_interval: int = 60) -> None:
        """Run the pipeline loop."""
        print(f"Pipeline started (max_parallel={self.max_parallel}, dry_run={self.dry_run})")
        for cycle in range(max_cycles):
            print(f"\n--- Cycle {cycle + 1} ---")
            self.run_cycle()

            active = self.orchestrator.active_session_count()
            ready = len(self.find_ready_tasks())
            print(f"Active: {active} | Ready: {ready} | "
                  f"Completed: {self.metrics.tasks_completed} | "
                  f"Failed: {self.metrics.tasks_failed}")

            if active == 0 and ready == 0:
                print("No more tasks. Pipeline complete.")
                break

            if not self.dry_run:
                time.sleep(cycle_interval)
            else:
                break  # Only one cycle in dry run


def main():
    parser = argparse.ArgumentParser(description="Run the CommCare development pipeline")
    parser.add_argument("--repo", required=True, help="GitHub repo (owner/name)")
    parser.add_argument("--repo-root", type=Path, default=Path.cwd(), help="Local repo root")
    parser.add_argument("--max-parallel", type=int, default=3, help="Max parallel agents")
    parser.add_argument("--dry-run", action="store_true", help="Don't actually spawn agents")
    parser.add_argument("--max-cycles", type=int, default=100, help="Max pipeline cycles")
    args = parser.parse_args()

    runner = PipelineRunner(
        repo=args.repo,
        repo_root=str(args.repo_root),
        max_parallel=args.max_parallel,
        dry_run=args.dry_run,
    )
    runner.run(max_cycles=args.max_cycles)


if __name__ == "__main__":
    main()
```

**Step 3: Run tests and commit**

```bash
cd pipeline && pytest tests/test_run.py -v
git add pipeline/src/pipeline/run.py pipeline/tests/test_run.py
git commit -m "feat: add pipeline runner main entry point"
```

---

## Task 12: Oracle Test Harness

**Files:**
- Create: `pipeline/src/pipeline/oracle.py`
- Create: `pipeline/tests/test_oracle.py`

The oracle harness runs commcare-core (Java) with test apps and records outputs. Later, the same inputs are run through the Kotlin KMP version and compared.

The oracle uses commcare-core's existing `MockApp` test infrastructure to load .ccz archives, navigate menus, enter forms, and capture outputs — all without needing a running FormPlayer server.

**Step 1: Write tests**

`pipeline/tests/test_oracle.py`:
```python
from pathlib import Path
from pipeline.oracle import OracleFixture, OracleHarness, ComparisonResult


def test_oracle_fixture_serialization(tmp_path):
    fixture = OracleFixture(
        app_id="basic",
        scenario="navigate_menu_0_form_0",
        inputs={"selections": ["0", "0"], "answers": {"0": "test"}},
        expected_outputs={
            "questions": [{"type": "text", "text": "Name?"}],
            "submission_xml": "<data><name>test</name></data>",
        },
    )
    fixture_path = tmp_path / "fixture.json"
    fixture.save(fixture_path)

    loaded = OracleFixture.load(fixture_path)
    assert loaded.app_id == "basic"
    assert loaded.expected_outputs["submission_xml"] == "<data><name>test</name></data>"


def test_comparison_result_pass():
    result = ComparisonResult(
        fixture_id="basic/navigate_menu_0",
        passed=True,
        differences=[],
    )
    assert result.passed is True


def test_comparison_result_fail():
    result = ComparisonResult(
        fixture_id="basic/navigate_menu_0",
        passed=False,
        differences=["Question text mismatch: expected 'Name?' got 'First Name?'"],
    )
    assert result.passed is False
    assert len(result.differences) == 1


def test_oracle_harness_compare(tmp_path):
    fixture = OracleFixture(
        app_id="basic",
        scenario="test_scenario",
        inputs={"answer": "hello"},
        expected_outputs={"result": "hello_processed"},
    )

    harness = OracleHarness(fixtures_dir=tmp_path)
    # When actual matches expected, should pass
    result = harness.compare(
        fixture=fixture,
        actual_outputs={"result": "hello_processed"},
    )
    assert result.passed is True

    # When actual differs, should fail
    result = harness.compare(
        fixture=fixture,
        actual_outputs={"result": "different"},
    )
    assert result.passed is False
    assert len(result.differences) > 0
```

**Step 2: Implement**

`pipeline/src/pipeline/oracle.py`:
```python
import json
from pathlib import Path
from pydantic import BaseModel


class OracleFixture(BaseModel):
    app_id: str
    scenario: str
    inputs: dict
    expected_outputs: dict

    def save(self, path: Path) -> None:
        path.write_text(self.model_dump_json(indent=2))

    @classmethod
    def load(cls, path: Path) -> "OracleFixture":
        return cls.model_validate_json(path.read_text())


class ComparisonResult(BaseModel):
    fixture_id: str
    passed: bool
    differences: list[str]


class OracleHarness:
    def __init__(self, fixtures_dir: Path):
        self.fixtures_dir = fixtures_dir

    def compare(
        self,
        fixture: OracleFixture,
        actual_outputs: dict,
    ) -> ComparisonResult:
        differences = []
        fixture_id = f"{fixture.app_id}/{fixture.scenario}"

        self._compare_dicts(
            expected=fixture.expected_outputs,
            actual=actual_outputs,
            path="",
            differences=differences,
        )

        return ComparisonResult(
            fixture_id=fixture_id,
            passed=len(differences) == 0,
            differences=differences,
        )

    def _compare_dicts(
        self,
        expected: dict,
        actual: dict,
        path: str,
        differences: list[str],
    ) -> None:
        for key in expected:
            full_path = f"{path}.{key}" if path else key
            if key not in actual:
                differences.append(f"Missing key: {full_path}")
                continue

            exp_val = expected[key]
            act_val = actual[key]

            if isinstance(exp_val, dict) and isinstance(act_val, dict):
                self._compare_dicts(exp_val, act_val, full_path, differences)
            elif isinstance(exp_val, list) and isinstance(act_val, list):
                if len(exp_val) != len(act_val):
                    differences.append(
                        f"List length mismatch at {full_path}: "
                        f"expected {len(exp_val)}, got {len(act_val)}"
                    )
                else:
                    for i, (e, a) in enumerate(zip(exp_val, act_val)):
                        if e != a:
                            differences.append(
                                f"Mismatch at {full_path}[{i}]: "
                                f"expected {e!r}, got {a!r}"
                            )
            elif exp_val != act_val:
                differences.append(
                    f"Value mismatch at {full_path}: "
                    f"expected {exp_val!r}, got {act_val!r}"
                )

    def load_fixtures(self, app_id: str | None = None) -> list[OracleFixture]:
        """Load all fixtures, optionally filtered by app_id."""
        fixtures = []
        for fixture_file in self.fixtures_dir.rglob("*.json"):
            fixture = OracleFixture.load(fixture_file)
            if app_id is None or fixture.app_id == app_id:
                fixtures.append(fixture)
        return fixtures

    def run_comparison_suite(
        self,
        actual_outputs_by_scenario: dict[str, dict],
    ) -> list[ComparisonResult]:
        """Run all fixtures and return results."""
        fixtures = self.load_fixtures()
        results = []
        for fixture in fixtures:
            scenario_key = f"{fixture.app_id}/{fixture.scenario}"
            if scenario_key in actual_outputs_by_scenario:
                result = self.compare(
                    fixture=fixture,
                    actual_outputs=actual_outputs_by_scenario[scenario_key],
                )
                results.append(result)
        return results
```

**Step 3: Run tests and commit**

```bash
cd pipeline && pytest tests/test_oracle.py -v
git add pipeline/src/pipeline/oracle.py pipeline/tests/test_oracle.py
git commit -m "feat: add oracle test harness for correctness verification"
```

---

## Task 13: KMP Project Setup in commcare-core (Specification)

> **Note:** This task modifies the `commcare-core` repository, not this repo. It should be executed against a fork/branch of `dimagi/commcare-core`.

**Goal:** Add Kotlin Multiplatform configuration to commcare-core so it can compile for JVM (existing) and iOS Native (new).

**Files to create/modify in commcare-core:**
- Modify: `settings.gradle` → `settings.gradle.kts`
- Modify: `build.gradle` → `build.gradle.kts`
- Create: `gradle/libs.versions.toml`
- Create: `src/commonMain/kotlin/` (initially empty, will be populated during Phase 1)
- Create: `src/iosMain/kotlin/` (initially empty)
- Modify: keep `src/main/java/` as `src/jvmMain/java/` (or configure source sets to point to existing dir)

**Key Gradle configuration:**

```kotlin
plugins {
    kotlin("multiplatform") version "2.3.10"
}

kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()

    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "CommCareCore"
            isStatic = true
        }
    }

    sourceSets {
        // Point JVM source set at existing Java sources
        jvmMain {
            kotlin.srcDir("src/main/java")
            resources.srcDir("src/main/resources")
        }
        jvmTest {
            kotlin.srcDir("src/test/java")
            resources.srcDir("src/test/resources")
        }
    }
}
```

**Critical gotcha:** The `kotlin-multiplatform` plugin cannot coexist with the `java` or `java-library` plugin in the same module. The existing `build.gradle` likely applies `java-library`. This must be removed and replaced with the KMP plugin, using `jvm()` target instead.

**Acceptance criteria:**
- `./gradlew jvmTest` passes all existing tests (backward compat)
- `./gradlew compileKotlinIosSimulatorArm64` succeeds (even if iosMain is empty)
- Existing consumers (commcare-android, formplayer) can still depend on the JVM artifact

---

## Task 14: Compose Multiplatform App Project (Specification)

> **Note:** This creates the app project structure, either in this repo or a new shared repo.

**Goal:** "Hello CommCare" app that builds for both Android and iOS using Compose Multiplatform.

**Project structure:**
```
app/
  composeApp/
    build.gradle.kts
    src/
      commonMain/kotlin/org/commcare/app/App.kt
      androidMain/kotlin/org/commcare/app/MainActivity.kt
      iosMain/kotlin/org/commcare/app/MainViewController.kt
  iosApp/
    iosApp.xcodeproj/
    iosApp/ContentView.swift
```

**Acceptance criteria:**
- App builds and runs on Android emulator
- App builds and runs on iOS simulator (via CI)
- Displays "Hello CommCare" with platform name
- Can import the CommCareCore KMP framework (once Task 13 is complete)

---

## Task 15: Run Report Generator

**Files:**
- Create: `pipeline/src/pipeline/report.py`
- Create: `pipeline/tests/test_report.py`

Generates a markdown run report summarizing pipeline performance.

**Step 1: Write test**

```python
from pipeline.report import generate_run_report
from pipeline.metrics import RunReport, TestResults


def test_generate_run_report():
    report = RunReport(
        run_id="run-001",
        unit_tests=TestResults(passed=100, failed=0, total=100),
        oracle_tests=TestResults(passed=80, failed=20, total=100),
        parity_tests=TestResults(passed=30, failed=10, total=40),
        e2e_tests=TestResults(passed=10, failed=5, total=15),
        tasks_completed=50,
        tasks_total=100,
        human_interventions=3,
    )
    markdown = generate_run_report(report)
    assert "# Pipeline Run Report" in markdown
    assert "run-001" in markdown
    assert "100%" in markdown
    assert "80%" in markdown
```

**Step 2: Implement and commit**

`pipeline/src/pipeline/report.py`:
```python
from pipeline.metrics import RunReport


def generate_run_report(report: RunReport) -> str:
    sections = [
        f"# Pipeline Run Report: {report.run_id}\n",
        "## Correctness Scorecard\n",
        report.to_scorecard(),
        "",
        "## Progress\n",
        f"- Tasks completed: {report.tasks_completed} / {report.tasks_total} "
        f"({report.task_completion_rate * 100:.0f}%)",
        f"- Human interventions: {report.human_interventions}",
        "",
        "## Failing Areas\n",
    ]

    if report.oracle_tests.failed > 0:
        sections.append(f"- Oracle tests: {report.oracle_tests.failed} failures")
    if report.parity_tests.failed > 0:
        sections.append(f"- Parity tests: {report.parity_tests.failed} failures")
    if report.e2e_tests.failed > 0:
        sections.append(f"- E2E tests: {report.e2e_tests.failed} failures")

    if report.unit_tests.pass_rate == 1.0 and report.oracle_tests.pass_rate == 1.0:
        sections.append("None — all core tests passing!")

    return "\n".join(sections)
```

```bash
cd pipeline && pytest tests/test_report.py -v
git add pipeline/src/pipeline/report.py pipeline/tests/test_report.py
git commit -m "feat: add pipeline run report generator"
```

---

## Summary: Phase 0 Task Dependency Graph

```
Task 1: Python project setup         ──┐
                                        ├── Task 2: Task models
                                        │     ├── Task 3: GitHub client
                                        │     │     └── Task 5: Task generator CLI
                                        │     └── Task 4: Package analyzer
                                        │           └── Task 5: Task generator CLI
                                        ├── Task 6: Orchestrator
                                        │     └── Task 11: Pipeline runner
                                        ├── Task 7: Behavior catalog
                                        ├── Task 8: Metrics collector
                                        │     └── Task 15: Run report
                                        └── Task 12: Oracle harness

Task 9:  CI Kotlin workflow           (independent)
Task 10: CI iOS workflow              (independent)
Task 13: KMP project setup           (independent, in commcare-core)
Task 14: Compose app project          (depends on Task 13)
```

**Maximum parallelism:** After Task 1, up to 5 tasks can run simultaneously.

---

## Execution Handoff

Plan complete and saved to `docs/plans/2026-03-07-phase0-scaffold-plan.md`.

**Two execution options:**

**1. Subagent-Driven (this session)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Parallel Session (separate)** — Open new session with executing-plans, batch execution with checkpoints.

**Which approach?**
