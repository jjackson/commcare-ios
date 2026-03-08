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
        # Try to parse as JSON first (when --json flag is used or output is JSON)
        try:
            return json.loads(output)
        except (json.JSONDecodeError, ValueError):
            # Fall back to parsing the URL from plain text output
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
