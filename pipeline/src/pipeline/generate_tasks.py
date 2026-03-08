import argparse
from pathlib import Path
from pipeline.analyzer import PackageAnalyzer
from pipeline.github_client import GitHubClient
from pipeline.models import TaskSpec, TaskPhase


def _make_infra_tasks(port_tasks: list[TaskSpec]) -> list[TaskSpec]:
    """Create infrastructure tasks that bookend the porting tasks."""
    setup_task = TaskSpec(
        id="core-port-kotlin-toolchain",
        title="Add Kotlin toolchain to commcare-core Gradle build",
        phase=TaskPhase.CORE_PORT,
        wave=0,
        description=(
            "Add the Kotlin JVM plugin to commcare-core's build.gradle so Java and "
            "Kotlin can coexist. Add Gradle wrapper. Verify existing tests still pass.\n\n"
            "**Steps:**\n"
            "1. Add `id 'org.jetbrains.kotlin.jvm' version '2.0.21'` to plugins block\n"
            "2. Add `implementation 'org.jetbrains.kotlin:kotlin-stdlib'` to dependencies\n"
            "3. Run `gradle wrapper --gradle-version 8.10` to create wrapper scripts\n"
            "4. Create a smoke-test Kotlin file in `src/main/java/org/commcare/modern/`\n"
            "5. Verify `./gradlew test` passes"
        ),
        files_to_read=["build.gradle"],
        files_to_modify=["build.gradle"],
        test_criteria=[
            "All existing Java tests pass with `./gradlew test`",
            "Kotlin smoke-test file compiles alongside Java",
            "Gradle wrapper scripts are committed",
        ],
        depends_on=[],
        labels=["phase/1", "wave/0", "infra"],
    )

    first_port_id = port_tasks[0].id if port_tasks else ""

    kmp_task = TaskSpec(
        id="core-port-kmp-targets",
        title="Add KMP multiplatform targets (JVM + iOS Native)",
        phase=TaskPhase.CORE_PORT,
        wave=9,
        description=(
            "Convert from kotlin('jvm') to kotlin('multiplatform') with JVM and iOS "
            "Native targets. Move shared code to commonMain, JVM-specific code to jvmMain, "
            "create iOS stubs in iosMain.\n\n"
            "**Steps:**\n"
            "1. Convert build.gradle to build.gradle.kts (Kotlin DSL)\n"
            "2. Configure JVM and iOS Native targets\n"
            "3. Reorganize source directories (commonMain/jvmMain/iosMain)\n"
            "4. Identify platform-specific code, create expect/actual declarations\n"
            "5. Verify `./gradlew jvmTest` passes\n"
            "6. Verify iOS target compiles"
        ),
        files_to_read=["build.gradle"],
        files_to_modify=["build.gradle.kts"],
        test_criteria=[
            "All existing tests pass with `./gradlew jvmTest`",
            "iOS Native target compiles without errors",
            "JVM JAR output is backward-compatible",
        ],
        depends_on=[t.id for t in port_tasks],
        labels=["phase/1", "wave/9", "infra"],
    )

    verify_task = TaskSpec(
        id="core-port-verification",
        title="Phase 1 final integration verification",
        phase=TaskPhase.CORE_PORT,
        wave=10,
        description=(
            "Final verification that the complete Java-to-Kotlin port is correct.\n\n"
            "**Steps:**\n"
            "1. Run full test suite (`./gradlew jvmTest`)\n"
            "2. Build all artifacts (JVM JAR, iOS framework)\n"
            "3. Compare JVM JAR API surface with original Java version\n"
            "4. Generate Phase 1 completion report"
        ),
        files_to_read=[],
        files_to_modify=[],
        test_criteria=[
            "100% test pass rate on `./gradlew jvmTest`",
            "iOS Native target compiles",
            "JVM JAR API is backward-compatible with original",
        ],
        depends_on=["core-port-kmp-targets"],
        labels=["phase/1", "wave/10", "infra"],
    )

    return [setup_task] + port_tasks + [kmp_task, verify_task]


def generate_phase1_tasks(
    source_root: Path,
    repo: str,
    dry_run: bool = True,
    grouped: bool = False,
) -> list[dict]:
    analyzer = PackageAnalyzer(source_root=source_root)
    packages = analyzer.discover_packages()
    graph = analyzer.build_dependency_graph(packages)

    if grouped:
        port_tasks = analyzer.generate_grouped_task_specs(packages, graph)
        tasks = _make_infra_tasks(port_tasks)
    else:
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

    if tasks:
        print(f"\nTotal: {len(tasks)} tasks across {max(t.wave for t in tasks)} waves")
    else:
        print("\nTotal: 0 tasks")
    return results


def main():
    parser = argparse.ArgumentParser(description="Generate Phase 1 tasks for commcare-core port")
    parser.add_argument("source_root", type=Path, help="Path to commcare-core src/main/java")
    parser.add_argument("--repo", required=True, help="GitHub repo (owner/name)")
    parser.add_argument("--dry-run", action="store_true", default=True, help="Print tasks without creating issues")
    parser.add_argument("--create", action="store_true", help="Actually create GitHub Issues")
    parser.add_argument("--grouped", action="store_true", help="Group packages by functional area (handles circular deps)")
    args = parser.parse_args()

    generate_phase1_tasks(
        source_root=args.source_root,
        repo=args.repo,
        dry_run=not args.create,
        grouped=args.grouped,
    )


if __name__ == "__main__":
    main()
