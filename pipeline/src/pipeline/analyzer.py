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
        waves: dict[str, int] = {}
        remaining = set(self._all_packages)

        wave_num = 0
        while remaining:
            wave_num += 1
            current_wave = set()
            for pkg in remaining:
                deps = self._deps.get(pkg, set()) & self._all_packages
                if all(d in waves for d in deps):
                    current_wave.add(pkg)

            if not current_wave:
                # Circular dependency detected; assign all remaining to this wave
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
                    # Strip the class name to get the package
                    import_pkg = ".".join(imported.split(".")[:-1])
                    if import_pkg in known_packages and import_pkg != pkg.package_name:
                        pkg_imports.add(import_pkg)

            for dep in pkg_imports:
                graph.add_dependency(pkg.package_name, dep)

        return graph

    def generate_task_specs(
        self, packages: list[PackageInfo], graph: DependencyGraph
    ) -> list[TaskSpec]:
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
