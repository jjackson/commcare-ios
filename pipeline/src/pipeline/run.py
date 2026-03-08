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
        issues = self.github.list_issues(labels=["ready"])
        return [
            issue for issue in issues
            if not issue.get("assignees")
        ]

    def run_cycle(self) -> None:
        completed = self.orchestrator.check_sessions()
        for session in completed:
            self.metrics.record_task_completion(
                task_id=session.task_id,
                duration_seconds=session.duration_seconds or 0,
                success=session.status.value == "completed",
            )
            print(f"Session {session.task_id}: {session.status.value}")

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
                break


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
