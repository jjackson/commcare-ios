from dataclasses import dataclass, field
from pydantic import BaseModel


class TestResults(BaseModel):
    passed: int
    failed: int
    total: int

    @property
    def pass_rate(self) -> float:
        return self.passed / self.total if self.total > 0 else 0.0


class RunReport(BaseModel):
    run_id: str
    unit_tests: TestResults
    oracle_tests: TestResults
    parity_tests: TestResults
    e2e_tests: TestResults
    tasks_completed: int
    tasks_total: int
    human_interventions: int

    @property
    def task_completion_rate(self) -> float:
        return self.tasks_completed / self.tasks_total if self.tasks_total > 0 else 0.0

    def to_scorecard(self) -> str:
        def fmt(name: str, results: TestResults) -> str:
            pct = f"{results.pass_rate * 100:.0f}%"
            status = "PASS" if results.pass_rate == 1.0 else "FAIL"
            return f"{name:<20} {results.passed:>5} / {results.total:<5} ({pct:>4}) {status}"

        lines = [
            f"CORRECTNESS REPORT — {self.run_id}",
            "=" * 55,
            fmt("Unit Tests:", self.unit_tests),
            fmt("Oracle Tests:", self.oracle_tests),
            fmt("Parity Tests:", self.parity_tests),
            fmt("E2E Tests:", self.e2e_tests),
            "=" * 55,
            f"Tasks: {self.tasks_completed}/{self.tasks_total} "
            f"({self.task_completion_rate * 100:.0f}%)",
            f"Human Interventions: {self.human_interventions}",
        ]
        return "\n".join(lines)


@dataclass
class MetricsCollector:
    _task_results: list[dict] = field(default_factory=list)

    def record_task_completion(
        self, task_id: str, duration_seconds: float, success: bool
    ) -> None:
        self._task_results.append({
            "task_id": task_id,
            "duration_seconds": duration_seconds,
            "success": success,
        })

    @property
    def tasks_completed(self) -> int:
        return sum(1 for r in self._task_results if r["success"])

    @property
    def tasks_failed(self) -> int:
        return sum(1 for r in self._task_results if not r["success"])

    @property
    def avg_task_duration(self) -> float:
        if not self._task_results:
            return 0.0
        return sum(r["duration_seconds"] for r in self._task_results) / len(
            self._task_results
        )
