from pipeline.metrics import MetricsCollector, RunReport, TestResults


def test_test_results_parity():
    results = TestResults(passed=90, failed=10, total=100)
    assert results.pass_rate == 0.9


def test_run_report_overall_score():
    report = RunReport(
        run_id="run-001",
        unit_tests=TestResults(passed=100, failed=0, total=100),
        oracle_tests=TestResults(passed=80, failed=20, total=100),
        parity_tests=TestResults(passed=30, failed=10, total=40),
        e2e_tests=TestResults(passed=10, failed=5, total=15),
        tasks_completed=50,
        tasks_total=100,
        human_interventions=3,
    )
    assert report.unit_tests.pass_rate == 1.0
    assert report.oracle_tests.pass_rate == 0.8
    assert report.task_completion_rate == 0.5
    scorecard = report.to_scorecard()
    assert "100%" in scorecard
    assert "80%" in scorecard


def test_metrics_collector():
    collector = MetricsCollector()
    collector.record_task_completion("task-1", duration_seconds=120, success=True)
    collector.record_task_completion("task-2", duration_seconds=60, success=False)
    assert collector.tasks_completed == 1
    assert collector.tasks_failed == 1
    assert collector.avg_task_duration == 90.0
