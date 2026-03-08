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

        subprocess.run(
            ["git", "worktree", "add", session.worktree_path, "-b", session.branch_name],
            cwd=self.repo_root,
            check=True,
        )

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
