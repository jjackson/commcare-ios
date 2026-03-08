from unittest.mock import patch, MagicMock
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
    mock_process.poll.return_value = None
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
    assert "test" in prompt.lower()
    assert "PR" in prompt or "pull request" in prompt.lower()
