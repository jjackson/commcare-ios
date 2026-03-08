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
    assert len(ready) == 1
    assert ready[0]["number"] == 1
