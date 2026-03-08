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
