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

    def generate_grouped_task_specs(
        self, packages: list[PackageInfo], graph: DependencyGraph
    ) -> list[TaskSpec]:
        """Generate tasks grouped by functional area instead of per-package.

        This handles circular dependencies by grouping related packages that
        frequently depend on each other. Each group is ported as a unit while
        other groups remain as Java (Kotlin/Java interop handles cross-group calls).
        """
        groups = self._assign_functional_groups(packages)
        tasks = []

        # Define port order (foundational → dependent)
        group_order = {
            "javarosa-utilities": 1,
            "javarosa-model": 2,
            "xpath-engine": 3,
            "xform-parser": 4,
            "case-management": 5,
            "suite-and-session": 6,
            "resources": 7,
            "commcare-core-services": 8,
        }

        for group_id, group_packages in sorted(
            groups.items(), key=lambda x: group_order.get(x[0], 99)
        ):
            wave = group_order.get(group_id, 99)
            all_java_files = []
            all_package_names = []
            total_files = 0

            for pkg in sorted(group_packages, key=lambda p: p.package_name):
                all_package_names.append(pkg.package_name)
                total_files += pkg.file_count
                for f in pkg.java_files:
                    all_java_files.append(
                        str(f.relative_to(pkg.source_root.parent.parent.parent))
                    )

            # Dependencies are on earlier groups
            dep_task_ids = []
            for dep_group_id, dep_wave in group_order.items():
                if dep_wave < wave and dep_group_id in groups:
                    dep_task_ids.append(f"core-port-{dep_group_id}")

            group_title = group_id.replace("-", " ").title()
            pkg_list = "\n".join(f"- `{p}`" for p in all_package_names)

            task = TaskSpec(
                id=f"core-port-{group_id}",
                title=f"Port {group_title} to Kotlin ({total_files} files)",
                phase=TaskPhase.CORE_PORT,
                wave=wave,
                description=(
                    f"Convert all {total_files} Java files in the "
                    f"{group_title} functional group to idiomatic Kotlin.\n\n"
                    f"**Packages in this group:**\n{pkg_list}\n\n"
                    f"**Approach:**\n"
                    f"1. Use IntelliJ's Java-to-Kotlin converter on all files in this group\n"
                    f"2. Fix any compilation errors from the conversion\n"
                    f"3. Clean up for idiomatic Kotlin (data classes, null safety, "
                    f"extension functions)\n"
                    f"4. Other groups remain as Java — Kotlin/Java interop handles "
                    f"cross-group calls\n"
                    f"5. Run the full test suite to verify no regressions"
                ),
                files_to_read=all_java_files,
                files_to_modify=[
                    f.replace("/java/", "/kotlin/").replace(".java", ".kt")
                    for f in all_java_files
                ],
                test_criteria=[
                    "All existing commcare-core tests pass",
                    "No compilation errors in any package",
                    "Kotlin code compiles cleanly alongside remaining Java code",
                ],
                depends_on=dep_task_ids,
                labels=["phase/1", f"wave/{wave}"],
            )
            tasks.append(task)

        return sorted(tasks, key=lambda t: t.wave)

    @staticmethod
    def _assign_functional_groups(
        packages: list["PackageInfo"],
    ) -> dict[str, list["PackageInfo"]]:
        """Assign packages to functional groups based on namespace patterns."""
        groups: dict[str, list[PackageInfo]] = {}

        for pkg in packages:
            name = pkg.package_name
            if name.startswith("org.javarosa.core.util") or \
               name.startswith("org.javarosa.core.io") or \
               name.startswith("org.javarosa.core.services") or \
               name.startswith("org.javarosa.core.model.data") or \
               name.startswith("org.commcare.modern"):
                group = "javarosa-utilities"
            elif name.startswith("org.javarosa.core.model") or \
                 name.startswith("org.javarosa.core.api") or \
                 name.startswith("org.javarosa.core.data") or \
                 name.startswith("org.javarosa.core.log") or \
                 name.startswith("org.javarosa.core.reference") or \
                 name.startswith("org.javarosa.xml"):
                group = "javarosa-model"
            elif name.startswith("org.javarosa.xpath"):
                group = "xpath-engine"
            elif name.startswith("org.javarosa.xform") or \
                 name.startswith("org.javarosa.form") or \
                 name.startswith("org.javarosa.model"):
                group = "xform-parser"
            elif name.startswith("org.commcare.cases") or \
                 name.startswith("org.commcare.data"):
                group = "case-management"
            elif name.startswith("org.commcare.suite") or \
                 name.startswith("org.commcare.session") or \
                 name.startswith("org.commcare.xml"):
                group = "suite-and-session"
            elif name.startswith("org.commcare.resources"):
                group = "resources"
            else:
                group = "commcare-core-services"

            if group not in groups:
                groups[group] = []
            groups[group].append(pkg)

        return groups
