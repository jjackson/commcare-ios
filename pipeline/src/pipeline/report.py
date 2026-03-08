from pipeline.metrics import RunReport


def generate_run_report(report: RunReport) -> str:
    sections = [
        f"# Pipeline Run Report: {report.run_id}\n",
        "## Correctness Scorecard\n",
        report.to_scorecard(),
        "",
        "## Progress\n",
        f"- Tasks completed: {report.tasks_completed} / {report.tasks_total} "
        f"({report.task_completion_rate * 100:.0f}%)",
        f"- Human interventions: {report.human_interventions}",
        "",
        "## Failing Areas\n",
    ]

    if report.oracle_tests.failed > 0:
        sections.append(f"- Oracle tests: {report.oracle_tests.failed} failures")
    if report.parity_tests.failed > 0:
        sections.append(f"- Parity tests: {report.parity_tests.failed} failures")
    if report.e2e_tests.failed > 0:
        sections.append(f"- E2E tests: {report.e2e_tests.failed} failures")

    if report.unit_tests.pass_rate == 1.0 and report.oracle_tests.pass_rate == 1.0:
        sections.append("None — all core tests passing!")

    return "\n".join(sections)
