from pipeline.report import generate_run_report
from pipeline.metrics import RunReport, TestResults


def test_generate_run_report():
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
    markdown = generate_run_report(report)
    assert "# Pipeline Run Report" in markdown
    assert "run-001" in markdown
    assert "100%" in markdown
    assert "80%" in markdown
