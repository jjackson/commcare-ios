import argparse
from pathlib import Path
from pipeline.analyzer import PackageAnalyzer
from pipeline.github_client import GitHubClient


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
        tasks = analyzer.generate_grouped_task_specs(packages, graph)
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
